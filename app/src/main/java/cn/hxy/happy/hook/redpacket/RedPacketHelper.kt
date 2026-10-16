package cn.hxy.happy.hook.redpacket

import android.os.Bundle
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.qwallet.api.INewQWalletApi
import com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.trpcprotocol.qqhb.access.mobile_hb_cgi.MobileHbCgiPB
import com.tencent.qqnt.qwallet.bigdata.BigDataUploader
import com.tenpay.sdk.basebl.EncryptRequest
import com.tenpay.sdk.net.cgi.NetSender
import com.tenpay.sdk.net.gateway.QWalletGatewayServlet
import com.tenpay.sdk.paynet.Net
import kotlinx.coroutines.suspendCancellableCoroutine
import mqq.app.NewIntent
import mqq.observer.BusinessObserver
import org.json.JSONObject
import tencent.im.qqwallet.QWalletHbPreGrab
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlin.text.Charsets
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.plugin.bean.RedPacketContext
import cn.hxy.happy.utils.json.str
import cn.hxy.happy.utils.json.walk
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.AudioConverterUtil
import cn.hxy.happy.utils.qq.CookieTool
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.MsgTool
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.qq.Toasts
import cn.hxy.happy.utils.qq.TroopTool
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.newInstanceWithArgs

/**
 * 抢红包核心逻辑。
 *
 * 支持 QQ 新 API（versionCode >= 14498，走 trpc.qqhb.access.MqqHbTrpc/HBPreGrab）
 * 与旧 API（SSO hb_pre_grap 加密）两套链路；channel 分派普通/口令/专属/语音四种红包。
 */
object RedPacketHelper {

    private const val TAG = "RedPacketHelper"

    /** 新 API 判定：QQ 且 versionCode >= 14498（0x38a2） */
    val isNewApi: Boolean
        get() = HostInfo.isQQ && HostInfo.versionCode >= 14498

    /** 反射查找 QWalletGatewayServlet.sendRequest（6 参数版本） */
    private val sendRequest: Method by lazy {
        QWalletGatewayServlet::class.java.findMethod {
            name = "sendRequest"
            paramCount = 6
        }
    }

    fun startGrab(ctx: RedPacketContext) {
        ModuleScope.launchIO("RedPacket_Main") {
            try {
                LogUtils.d("[RedPacket] startGrab 开始: listId=${ctx.listId}, channel=${ctx.channel}, isAuto=${ctx.isAuto}, isNewApi=$isNewApi, title=${ctx.title}")

                // 自动模式 + 配置了延迟，先延时
                if (ctx.isAuto && ctx.config.delay > 0) {
                    kotlinx.coroutines.delay(ctx.config.delay)
                }

                // 1. 预抢
                val resp = if (isNewApi) preGrabNew(ctx) else preGrabOld(ctx)
                LogUtils.d("[RedPacket] preGrab 完成: resp=$resp")

                // 2. 旧 API 检查 retcode
                if (!isNewApi) {
                    val retcode = resp.optInt("retcode")
                    if (retcode != 0) {
                        if (!ctx.isAuto) {
                            Toasts.toast(resp.optString("retmsg", "抢红包失败"))
                        }
                        return@launchIO
                    }
                }

                // 3. 检查红包详情（已抢完 / 低于最低平均金额则跳过），返回平均金额
                val avg = checkRedPacketDetails(resp, ctx) ?: return@launchIO
                LogUtils.d("[RedPacket] checkRedPacketDetails 通过, total=$avg")

                // 4. 按 channel 分派
                when (ctx.channel) {
                    1 -> handleLuckyRedPacket(ctx, resp, avg)
                    32 -> handleCommandRedPacket(ctx, avg)
                    1024 -> handleSpecifyRedPacket(ctx, resp, avg)
                    65536 -> handleVoiceRedPacket(ctx, resp, avg)
                    else -> {
                        if (!ctx.isAuto) {
                            Toasts.toast("暂不支持的红包类型: ${ctx.channel}")
                        }
                    }
                }
            } catch (t: Throwable) {
                LogUtils.e(TAG, t)
                LogUtils.d("[RedPacket] startGrab 异常: ${t.javaClass.name}: ${t.message}")
                if (!ctx.isAuto) {
                    Toasts.toast("抢红包请求异常: ${t.message}")
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 预抢（新 API）：trpc.qqhb.access.MqqHbTrpc / HBPreGrab
    // ------------------------------------------------------------------
    private suspend fun preGrabNew(ctx: RedPacketContext): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val paramTypes = sendRequest.parameterTypes
            val configClass = paramTypes[4]      // R8 混淆类型
            val callbackClass = paramTypes[5]    // R8 混淆类型
            LogUtils.d("[RedPacket] preGrabNew: configClass=${configClass.name}, callbackClass=${callbackClass.name}, sendRequest=$sendRequest")

            // 构造 config：trpc.qpay.gateway.Gateway.SsoSecure, true, null, false, true, false, false, null, null, null, 1004, null
            val config = configClass.newInstanceWithArgs(
                "trpc.qpay.gateway.Gateway.SsoSecure", true, null,
                false, true, false, false,
                null, null, null, 1004, null
            )
            LogUtils.d("[RedPacket] preGrabNew: config 构造成功 = $config")

            // Proxy 回调：onSuccess(HBPreGrabRsp) / onFail(int, String, ErrDetail)
            // 注意回调基类含 isAlive/getContext/getActivity，
            // isAlive 返回 boolean 基本类型，必须返回 true 否则自动拆箱 NPE / 请求被取消。
            val callback = Proxy.newProxyInstance(
                HostInfo.hostContext.classLoader,
                arrayOf(callbackClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onFail" -> {
                        val msg = args?.getOrNull(1) as? String ?: "预抢失败"
                        LogUtils.d("[RedPacket] preGrabNew onFail: args=${args?.toList()}")
                        if (continuation.isActive) {
                            continuation.resumeWithException(RuntimeException(msg))
                        }
                        null
                    }

                    "onSuccess" -> {
                        LogUtils.d("[RedPacket] preGrabNew onSuccess: args=${args?.toList()}")
                        val rsp = args?.getOrNull(0) as? MobileHbCgiPB.HBPreGrabRsp
                        if (rsp != null) {
                            val json = JSONObject().apply {
                                put("pre_grap_token", rsp.pre_grap_token.get())
                                put(
                                    "send_object",
                                    JSONObject().apply {
                                        put("total_amount", rsp.send_object.total_amount.get())
                                        put("total_num", rsp.send_object.total_num.get())
                                        put("recv_num", rsp.send_object.recv_num.get())
                                    }
                                )
                            }
                            if (continuation.isActive) continuation.resume(json)
                        }
                        null
                    }

                    "isAlive" -> true
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> (args?.getOrNull(0) === proxy)
                    else -> null
                }
            }

            // 构造 HBPreGrabReq
            val req = MobileHbCgiPB.HBPreGrabReq().apply {
                listid.set(ctx.listId)
                group_type.set(1)
                groupid.set(ctx.msgData.peerUin)
                send_uin.set(ctx.msgData.userUin)
                auth.set(
                    MobileHbCgiPB.Auth().apply {
                        authkey.set(ctx.authKey)
                    }
                )
            }

            LogUtils.d("[RedPacket] preGrabNew: 发起 sendRequest, req=$req")
            sendRequest.invoke(
                QWalletGatewayServlet.INSTANCE,
                "trpc.qqhb.access.MqqHbTrpc", "HBPreGrab",
                req, MobileHbCgiPB.HBPreGrabRsp(), config, callback
            )
        }

    // ------------------------------------------------------------------
    // 预抢（旧 API）：SSO hb_pre_grap 加密
    // ------------------------------------------------------------------
    private suspend fun preGrabOld(ctx: RedPacketContext): JSONObject {
        val currentUin = QQCurrentEnv.currentUin
        val transSeq = kotlin.math.abs(Random.nextInt()) % 0x10

        val params = linkedMapOf(
            "listid" to ctx.listId,
            "channel" to ctx.channel.toString(),
            "groupid" to ctx.msgData.peerUin,
            "grouptype" to "1",
            "groupuin" to ctx.msgData.userUin,
            "name" to (QQCurrentEnv.currentNickName ?: ""),
            "pay_flag" to "0",
            "authkey" to ctx.authKey,
            "uin" to currentUin,
            "trans_seq" to transSeq.toString(),
            "hb_from" to "0",
        )

        val hexGuid = QRoute.api(INewQWalletApi::class.java).hexGuid()
        val pskey = CookieTool.getPskey("tenpay.com").orEmpty()
        val body = processMap(params)

        val encryptRequest = EncryptRequest(HostInfo.hostContext)
        val encrypt = encryptRequest.encypt(
            currentUin, "hb_pre_grapver=2.0&chv=3", transSeq, body, pskey, hexGuid
        )
        val encText = encrypt.encText

        val request = QWalletHbPreGrab.QQHBRequest().apply {
            cgiName.set("hb_pre_grap")
            reqText.set(encText)
            random.set(transSeq.toString())
            enType.set(0)
        }
        val data = packet(request.toByteArray())

        val bundle = sendSsoRequest("trpc.qqhb.qqhb_proxy.Handler.sso_handle", data)
        val rspBytes = bundle.getByteArray("rsp_bytes") ?: throw RuntimeException("预抢响应包为空")

        val reply = QWalletHbPreGrab.QQHBReply()
        reply.mergeFrom(rspBytes)
        val rspText = reply.rspText.get()

        val decypt = EncryptRequest(HostInfo.hostContext)
            .decypt(currentUin, "hb_pre_grap", transSeq, rspText)
        val decryptStr = decypt.decryptStr ?: "{}"

        return JSONObject(decryptStr)
    }

    // ------------------------------------------------------------------
    // 检查红包详情：已抢完或低于最低平均金额则返回 null，否则返回平均金额
    // ------------------------------------------------------------------
    private suspend fun checkRedPacketDetails(resp: JSONObject, ctx: RedPacketContext): Double? {
        val sendObject = resp.optJSONObject("send_object") ?: return null
        val totalAmount = sendObject.optDouble("total_amount")
        val totalNum = sendObject.optDouble("total_num")
        val recvNum = sendObject.optDouble("recv_num")

        // 已抢完
        if (totalNum == recvNum) {
            if (!ctx.isAuto) {
                Toasts.toast("手慢了，红包已被领完")
            }
            return null
        }

        // 平均金额过滤：低于预期静默跳过，不抢也不发提示
        if (ctx.isAuto && ctx.config.minAverage > 0) {
            val avg = totalAmount / totalNum
            if (avg < ctx.config.minAverage) {
                LogUtils.d("[RedPacket] 平均金额 $avg 分低于预期 ${ctx.config.minAverage} 分，静默跳过")
                return null
            }
        }

        return totalAmount
    }

    // ------------------------------------------------------------------
    // 普通/拼手气红包：直接用 pre_grap_token 抢
    // ------------------------------------------------------------------
    private suspend fun handleLuckyRedPacket(ctx: RedPacketContext, resp: JSONObject, total: Double) {
        val token = resp.optString("pre_grap_token")
        if (token.isEmpty()) return
        finalGrab(ctx, mapOf("pre_grap_token" to token), total)
    }

    // ------------------------------------------------------------------
    // 口令红包：发口令 -> 带 answer 抢（自动模式发出的口令无需撤回）
    // ------------------------------------------------------------------
    private suspend fun handleCommandRedPacket(ctx: RedPacketContext, total: Double) {
        val title = ctx.title
        if (title.isEmpty()) return

        val sentRecord = sendCommandMsgAndAwait(ctx, title)
        val msgRandom = sentRecord.msgRandom.toString()
        val msgSeq = sentRecord.clientSeq.toString()
        val msgId = sentRecord.msgId

        val extra = mapOf(
            "answer" to title,
            "msg_id" to msgRandom,
            "msg_md5" to getMd5(title),
            "msg_seq" to msgSeq,
        )

        // 自动领取发出的口令无需撤回；手动模式保留撤回
        if (!ctx.isAuto) {
            MsgTool.recallMsg(ctx.msgData.contact, msgId)
        }

        finalGrab(ctx, extra, total)
    }

    // ------------------------------------------------------------------
    // 专属红包（channel 1024）：对齐专属红包请求参数表
    // （listid/groupid/grouptype/groupuin/tinyid/guild_id/sub_guild_id/name/answer/
    //   authkey/uin/channel/senderuin/agreement/[pre_grap_token]，无 msg_id/msg_seq）
    // 仅当指定收礼人是自己时才走到这里（AutoGrabHb 已校验 grapUin）。
    // ------------------------------------------------------------------
    private suspend fun handleSpecifyRedPacket(ctx: RedPacketContext, resp: JSONObject, total: Double) {
        val params = linkedMapOf(
            "listid" to ctx.listId,
            "groupid" to ctx.msgData.peerUin,
            "grouptype" to "1",
            "groupuin" to ctx.msgData.userUin,
            "tinyid" to "",
            "guild_id" to "",
            "sub_guild_id" to "",
            "name" to (QQCurrentEnv.currentNickName ?: ""),
            "answer" to "",
            "authkey" to ctx.authKey,
            "uin" to QQCurrentEnv.currentUin,
            "channel" to ctx.channel.toString(),
            "senderuin" to ctx.msgData.userUin,
            "agreement" to readAgreement(),
        )
        val token = resp.optString("pre_grap_token")
        if (token.isNotEmpty()) params["pre_grap_token"] = token

        LogUtils.d("[RedPacket] handleSpecifyRedPacket: params=$params")
        val grabResp = httpRequest(
            "https://mqq.tenpay.com/cgi-bin/hongbao/qpay_hb_na_grap.cgi?", params
        )
        LogUtils.d("[RedPacket] handleSpecifyRedPacket 响应: resp=$grabResp")

        val amount = grabResp.walk("recv_object", "amount").str ?: return
        onSuccess(ctx, total, amount)
    }

    // ------------------------------------------------------------------
    // 语音红包：TTS 合成口令语音 -> SILK -> BDH 上传拿语音凭证 -> 抢
    // 服务端按 BDH 上传记录做口令校验，
    // 最终抢包请求无需 voice_rate_id，也不需要发送语音消息。
    // 上传请求体 {bill_no, voice_text, make_uin, platform}，
    // 响应 {status, degree}，status==1 才继续抢。
    // ------------------------------------------------------------------
    private suspend fun handleVoiceRedPacket(ctx: RedPacketContext, resp: JSONObject, total: Double) {
        val voiceText = ctx.title
        if (voiceText.isEmpty()) {
            if (!ctx.isAuto) Toasts.toast("语音口令为空")
            return
        }

        val silkPath = "${QQCurrentEnv.currentDir}cache/voice_temp.slk"
        LogUtils.d("[RedPacket] handleVoiceRedPacket: 开始合成语音口令 title=$voiceText, path=$silkPath")
        val ok = AudioConverterUtil.ttsToSilk(voiceText, silkPath)
        if (!ok) {
            LogUtils.d("[RedPacket] handleVoiceRedPacket: 语音合成失败")
            if (!ctx.isAuto) Toasts.toast("语音合成失败，无法抢语音红包")
            return
        }

        var sentPtt = false
        try {
            // 上传请求体对齐宿主：make_uin 为红包发送者 uin
            val extInfo = JSONObject().apply {
                put("bill_no", ctx.listId)
                put("voice_text", voiceText)
                put("make_uin", ctx.msgData.userUin.toLongOrNull() ?: 0L)
                put("platform", 0)
            }.toString().toByteArray(Charsets.UTF_8)

            val rspBytes = uploadVoice(silkPath, extInfo)
            val rsp = JSONObject(String(rspBytes, Charsets.UTF_8))
            val status = rsp.optInt("status")
            LogUtils.d("[RedPacket] handleVoiceRedPacket: 上传响应 status=$status, degree=${rsp.optString("degree")}")

            if (status == 1) {
                // 凭证已由服务端绑定 bill_no，最终抢包不带额外参数
                val grabbed = finalGrab(ctx, null, total)
                if (grabbed && ctx.config.sendVoiceAfterGrab) {
                    // 抢到后把合成的口令语音发到群里（开关控制）
                    runCatching {
                        MsgTool.sendPtt(ctx.msgData.contact, silkPath)
                        sentPtt = true
                    }.onFailure {
                        LogUtils.d("[RedPacket] handleVoiceRedPacket: 发送语音失败 ${it.message}")
                    }
                }
            } else {
                LogUtils.d("[RedPacket] handleVoiceRedPacket: 语音口令校验未通过, rsp=$rsp")
                if (!ctx.isAuto) Toasts.toast("语音口令匹配失败")
            }
        } catch (t: Throwable) {
            LogUtils.e(TAG, t)
            LogUtils.d("[RedPacket] handleVoiceRedPacket: 上传/抢包异常 ${t.message}")
            if (!ctx.isAuto) Toasts.toast("语音上传失败: ${t.message}")
        } finally {
            // 发送语音时保留文件供内核异步上传，其余情况删除临时文件
            if (!sentPtt) runCatching { java.io.File(silkPath).delete() }
        }
    }

    /** 反射匹配 BigDataUploader 上传方法：void (String, byte[])，规避版本混淆改名 */
    private val upload: Method by lazy {
        BigDataUploader::class.java.findMethod {
            returnType = void
            paramTypes(String::class.java, ByteArray::class.java)
        }
    }

    /** 走 BDH 通道上传语音凭证，挂起等响应回调 */
    private suspend fun uploadVoice(filePath: String, extInfo: ByteArray): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val listener = object : BigDataUploader.a {
                override fun a(bArr: ByteArray?) {
                    if (!continuation.isActive) return
                    if (bArr != null) {
                        continuation.resume(bArr)
                    } else {
                        continuation.resumeWithException(RuntimeException("语音上传返回为空"))
                    }
                }

                override fun onError(code: Int, msg: String?) {
                    if (!continuation.isActive) return
                    continuation.resumeWithException(
                        RuntimeException("语音上传失败: $msg (code: $code)")
                    )
                }
            }
            LogUtils.d("[RedPacket] uploadVoice: filePath=$filePath, extInfo=${String(extInfo, Charsets.UTF_8)}")
            upload.invoke(BigDataUploader(listener), filePath, extInfo)
        }

    // ------------------------------------------------------------------
    // 最终抢红包 HTTP 请求，返回是否抢到
    // ------------------------------------------------------------------
    private suspend fun finalGrab(ctx: RedPacketContext, extra: Map<String, String>?, total: Double): Boolean {
        // 对齐宿主抢红包请求的完整参数表
        val params = linkedMapOf(
            "channel" to ctx.channel.toString(),
            "listid" to ctx.listId,
            "groupid" to ctx.msgData.peerUin,
            "grouptype" to "1",
            "groupuin" to ctx.msgData.userUin,
            "tinyid" to "",
            "guild_id" to "",
            "sub_guild_id" to "",
            "pay_flag" to "0",
            "name" to (QQCurrentEnv.currentNickName ?: ""),
            "answer" to "",
            "subchannel" to "",
            "authkey" to ctx.authKey,
            "uin" to QQCurrentEnv.currentUin,
            "senderuin" to ctx.msgData.userUin,
            "agreement" to readAgreement(),
            "hb_from" to "0",
            "msg_id" to ctx.msgData.data.msgRandom.toString(),
            "msg_seq" to ctx.msgData.data.clientSeq.toString(),
            "msg_md5" to "",
        )
        if (extra != null) params.putAll(extra)

        LogUtils.d("[RedPacket] finalGrab: params=$params")
        val resp = httpRequest(
            "https://mqq.tenpay.com/cgi-bin/hongbao/qpay_hb_na_grap.cgi?", params
        )
        LogUtils.d("[RedPacket] finalGrab 响应: resp=$resp")

        val amount = resp.walk("recv_object", "amount").str ?: return false
        onSuccess(ctx, total, amount)
        return true
    }

    /** 读取用户协议签署状态（qb_tenpay_hb_<uin> / agree_wallet_contrace），默认 "0" */
    private fun readAgreement(): String = runCatching {
        val sp = HostInfo.hostContext.getSharedPreferences(
            "qb_tenpay_hb_${QQCurrentEnv.currentUin}", 0
        )
        if (sp.getBoolean("agree_wallet_contrace", false)) "1" else "0"
    }.getOrDefault("0")

    // ------------------------------------------------------------------
    // HTTP 请求（NetSender）
    // ------------------------------------------------------------------
    private suspend fun httpRequest(url: String, params: Map<String, String>): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val listener = object : Net.NetListener {
                override fun onSuccess(url: String, response: JSONObject) {
                    LogUtils.d("[RedPacket] httpRequest onSuccess: url=$url, response=$response")
                    if (continuation.isActive) continuation.resume(response)
                }

                override fun onError(url: String, response: JSONObject) {
                    LogUtils.d("[RedPacket] httpRequest onError: url=$url, response=$response")
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RuntimeException(response.optString("retmsg"))
                        )
                    }
                }

                override fun onBlError(url: String, response: JSONObject) {
                    LogUtils.d("[RedPacket] httpRequest onBlError: url=$url, response=$response")
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RuntimeException(response.optString("retmsg"))
                        )
                    }
                }
            }

            LogUtils.d("[RedPacket] httpRequest: 发起请求 url=$url, params=$params")

            NetSender.with(null, url, params, QQCurrentEnv.currentUin)
                .comeFrom("2")
                .encrypt(true)
                .tokenID(null)
                .request(listener)
        }

    // ------------------------------------------------------------------
    // 发送口令消息并等待返回 MsgRecord
    // ------------------------------------------------------------------
    private suspend fun sendCommandMsgAndAwait(ctx: RedPacketContext, cmd: String): MsgRecord =
        suspendCancellableCoroutine { continuation ->
            val service = QQCurrentEnv.kernelMsgService
            if (service == null) {
                continuation.resumeWithException(RuntimeException("获取 MsgService 失败"))
                return@suspendCancellableCoroutine
            }

            val msgId = service.generateMsgUniqueId(ctx.msgData.type, System.currentTimeMillis())
            val elements = MsgTool.processMessageContent(ctx.msgData.contact, cmd)

            val operateCallback = object : IMsgOperateCallback {
                override fun onResult(result: Int, errMsg: String, msgList: ArrayList<MsgRecord>) {
                    if (!continuation.isActive) return
                    val record = msgList.firstOrNull()
                    if (record != null) {
                        continuation.resume(record)
                    } else {
                        continuation.resumeWithException(RuntimeException("获取发出的口令消息为空"))
                    }
                }
            }

            service.sendMsg(
                msgId, ctx.msgData.contact, elements, HashMap<Int, MsgAttributeInfo>(),
                object : IOperateCallback {
                    override fun onResult(result: Int, errMsg: String) {
                        service.getMsgsByMsgId(
                            ctx.msgData.contact, arrayListOf(msgId), operateCallback
                        )
                    }
                }
            )
        }

    // ------------------------------------------------------------------
    // SSO 请求（旧 API）
    // ------------------------------------------------------------------
    private suspend fun sendSsoRequest(cmd: String, data: ByteArray): Bundle =
        suspendCancellableCoroutine { continuation ->
            val observer = BusinessObserver { _, _, bundle ->
                if (!continuation.isActive) return@BusinessObserver
                if (bundle != null) {
                    continuation.resume(bundle)
                } else {
                    continuation.resumeWithException(RuntimeException("SSO 回调返回 bundle 为空"))
                }
            }

            val servletClass = ManualGrabHb.servletClass
            if (servletClass == null) {
                continuation.resumeWithException(RuntimeException("未找到 QWalletPbServlet"))
                return@suspendCancellableCoroutine
            }

            val intent = NewIntent(HostInfo.hostContext, servletClass).apply {
                putExtra("cmd", cmd)
                putExtra("data", data)
                putExtra("timeout", 30000L)
                setObserver(observer)
            }
            QQCurrentEnv.qQAppInterface.startServlet(intent)
        }

    // ------------------------------------------------------------------
    // 成功回调：抢到后私聊提示（金额=红包总额，已领=我抢到的金额）
    // ------------------------------------------------------------------
    private fun onSuccess(ctx: RedPacketContext, total: Double, recv: String) {
        val label = if (ctx.isAuto) "自动" else "手动"
        Toasts.qqToast(2, "${label}抢红包成功")

        if (ctx.isAuto) {
            ModuleScope.launchIO("RedPacket_Reply") {
                MsgTool.sendMsg(
                    QQCurrentEnv.currentUin,
                    buildSuccessMsg(ctx, total, recv), 1
                )
                ctx.config.autoReply.randomOrNull()?.let {
                    MsgTool.sendMsg(ctx.msgData.contact, it)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 生成成功提示文本（金额单位分转元）
    // ------------------------------------------------------------------
    private suspend fun buildSuccessMsg(ctx: RedPacketContext, total: Double, recv: String): String {
        val troopName = try {
            TroopTool.getGroupInfo(ctx.msgData.peerUin).troopNameFromNT
        } catch (_: Throwable) {
            ""
        }

        val memberName = try {
            TroopTool.getMemberInfo(ctx.msgData.peerUin, ctx.msgData.userUin).uinName
        } catch (_: Throwable) {
            ""
        }

        val time = android.text.format.DateFormat.format(
            "yyyy-MM-dd HH:mm:ss", java.util.Date(ctx.msgData.time * 1000)
        ).toString()

        // 服务端金额单位为分，展示换算成元
        val totalYuan = String.format(Locale.US, "%.2f", total / 100.0)
        val recvYuan = recv.toDoubleOrNull()
            ?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: recv

        return "『Happy』红包抢取成功！\n" +
            "金额: ${totalYuan}元\n" +
            "已领：${recvYuan}元\n" +
            "标题: ${ctx.title}\n" +
            "群聊：$troopName（${ctx.msgData.peerUin}）\n" +
            "发送者∶$memberName（${ctx.msgData.userUin}）\n" +
            "时间：$time"
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------
    private fun packet(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        dos.writeInt(data.size + 4)
        dos.write(data)
        return bos.toByteArray()
    }

    private fun processMap(map: Map<String, String>): String =
        map.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    private fun getMd5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format("%02x", it) }
            .lowercase(Locale.ROOT)
}
