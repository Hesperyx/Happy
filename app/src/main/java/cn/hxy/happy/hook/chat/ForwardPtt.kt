package cn.hxy.happy.hook.chat

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msglist.holder.component.ptt.AIOPttContentComponent
import com.tencent.mobileqq.selectmember.ResultRecord
import com.tencent.qqnt.aio.forward.NtMsgForwardUtils
import kotlinx.coroutines.delay
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.loader.hookapi.Chain
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.hook.hookReplace
import cn.hxy.happy.utils.hook.invokeOriginal
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.MsgTool
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.findMethodOrNull
import cn.hxy.happy.utils.reflect.getObject
import cn.hxy.happy.utils.reflect.getObjectByType
import cn.hxy.happy.utils.reflect.newInstanceWithArgs
import cn.hxy.happy.utils.reflect.setObject
import cn.hxy.happy.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

@HookItemAnnotation(
    "转发语音",
    "语音长按菜单出现转发按钮（支持私聊，群聊，临时会话）",
    HookCategory.CHAT
)
object ForwardPtt : BaseSwitchHookItem(), DexKitTask {

    private const val MSG_TYPE_PTT = 6
    private const val MSG_TYPE_TEXT = 2
    private const val INVALID_TYPE = 114514

    /** 分享面板转发处理器（QQ 侧 QRoute 实现类） */
    private const val HANDLER_IMPL = "com.tencent.mobileqq.sharepanel.forward.handler.AIOForwardHandler"

    /** 备用实现类：QRoute 注册的实现若发生迁移，两个都挂上 */
    private const val HANDLER_IMPL_ALT = "com.tencent.mobileqq.sharepanel.api.impl.AIOForwardHandlerImpl"

    private lateinit var setMenu: Method
    private lateinit var sendIntent: Method
    private var onActivityResult: Method? = null

    /** 转发处理器可能存在的多个重载/实现，全部 hook */
    private val handleForwards = linkedSetOf<Method>()

    private var lastAIOMsgItem: AIOMsgItem? = null
    private lateinit var forwardMenuItem: Class<*>

    override fun onInit(): Boolean {

        setMenu = resolveMenuProvider()
        LogUtils.d("转发语音 setMenu = ${setMenu.declaringClass.name}#${setMenu.name}")

        forwardMenuItem = requireClass("ForwardMenuItem")
        LogUtils.d("转发语音 ForwardMenuItem = ${forwardMenuItem.name}")

        val isOldVer = HostInfo.isTIM || (HostInfo.isQQ && HostInfo.versionCode < 11820)

        if (isOldVer) {

            val ntMsgForwardUtils = NtMsgForwardUtils::class.java
            onActivityResult = ntMsgForwardUtils.findMethodOrNull {
                returnType = void
                paramTypes(
                    Activity::class.java, int, int, Intent::class.java, null
                )
            }

            sendIntent = ntMsgForwardUtils.findMethod {
                returnType = boolean
                paramTypes(
                    Activity::class.java, null, AIOMsgItem::class.java
                )
            }

        } else {

            sendIntent = resolveSendIntent()
            handleForwards += resolveForwardHandlers()

            LogUtils.d("转发语音 sendIntentNew = ${sendIntent.declaringClass.name}#${sendIntent.name}")
            LogUtils.d("转发语音 forward handlers = ${handleForwards.joinToString { "${it.declaringClass.simpleName}#${it.name}" }}")
        }

        return super.onInit()
    }

    /** 菜单 provider：先按签名找，找不到再按基类抽象方法名找（应对混淆改名） */
    private fun resolveMenuProvider(): Method {
        AIOPttContentComponent::class.java.findMethodOrNull {
            returnType = list
            paramCount = 0
        }?.let { return it }

        val base: Class<*> = AIOPttContentComponent::class.java.superclass
            ?: throw NoSuchMethodException("菜单 provider 基类为空")
        val provider = base.findMethodOrNull {
            returnType = list
            paramCount = 0
        } ?: throw NoSuchMethodException("菜单 provider 未找到: ${base.name}")

        return AIOPttContentComponent::class.java.findMethodOrNull {
            name = provider.name
            paramCount = 0
        } ?: throw NoSuchMethodException("子类未覆写菜单 provider: ${provider.name}")
    }

    /**
     * sendIntentNew：优先用 DexKit 的字符串特征；
     * 特征串（QQ 的日志文案）被删时退化为 NtMsgForwardUtils 中
     * (Activity, AIOContact, AIOMsgItem, int) -> boolean 的静态转发入口。
     */
    private fun resolveSendIntent(): Method {
        runCatching { requireMethod("sendIntentNew") }
            .onSuccess { return it }
            .onFailure { LogUtils.e(name, it) }

        return NtMsgForwardUtils::class.java.findMethodOrNull {
            returnType = boolean
            paramCount = 4
            paramTypes(Activity::class.java, null, AIOMsgItem::class.java, int)
        } ?: throw NoSuchMethodException("sendIntentNew 未找到")
    }

    /** 分享面板转发处理器：Map 重载为主，其它 5 参重载与备用实现类一起挂上 */
    private fun resolveForwardHandlers(): List<Method> {
        val result = linkedSetOf<Method>()

        for (className in listOf(HANDLER_IMPL, HANDLER_IMPL_ALT)) {
            val clazz = runCatching { className.toClass }.getOrNull() ?: continue

            clazz.findMethodOrNull {
                name = "forward"
                paramTypes(map, null, list, string, null)
            }?.let { result += it }

            clazz.findMethodOrNull {
                name = "forward"
                paramCount = 5
            }?.let { result += it }
        }

        if (result.isEmpty()) {
            LogUtils.d("转发语音 未找到 forward 处理器，跳过（旧版流程仍可用）")
        }
        return result.toList()
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    override fun onHook() {

        setMenu.hookAfter(this) { param ->
            try {
                val menu = param.result as? MutableList<Any> ?: return@hookAfter
                val component = param.thisObject
                val aioMsgItem =
                    component.getObjectByType<AIOMsgItem>(component.javaClass.superclass)
                val activity = QQCurrentEnv.activity ?: return@hookAfter

                val menuItem =
                    forwardMenuItem.newInstanceWithArgs(activity, aioMsgItem, component, null)

                menu.add(0, menuItem)
                LogUtils.d("转发语音 已插入转发按钮: ${menuItem.javaClass.simpleName}")

            } catch (t: Throwable) {
                LogUtils.e(this@ForwardPtt, t)
            }
        }

        sendIntent.hookBefore(this) { param ->

            lastAIOMsgItem = null

            val aioMsgItem = param.args[2] as? AIOMsgItem ?: return@hookBefore
            val msgRecord = aioMsgItem.msgRecord

            if (msgRecord.msgType != MSG_TYPE_PTT) return@hookBefore

            msgRecord.msgType = MSG_TYPE_TEXT
            lastAIOMsgItem = aioMsgItem
            LogUtils.d("转发语音 捕获语音消息: ${aioMsgItem.javaClass.simpleName}")
        }

        onActivityResult?.hookReplace(this) { param ->
            val intent = param.args[3] as? Intent ?: return@hookReplace param.invokeOriginal()
            val target = lastAIOMsgItem
            lastAIOMsgItem = null

            val msgId = intent.getLongExtra("forward_nt_msg_id", 0L)
            if (msgId == 0L || target == null) {
                return@hookReplace param.invokeOriginal()
            }

            val contacts = mutableMapOf<String, Int>()

            val elements = target.msgRecord.elements

            val uin = intent.getStringExtra("uin")
            val uinType = intent.getIntExtra("uintype", INVALID_TYPE)

            check(uin, uinType, contacts)

            val list = intent.getParcelableArrayListExtra<Parcelable>("forward_multi_target")
            list?.forEach { parcelable ->
                val result = parcelable as ResultRecord
                check(result.uin, result.uinType, contacts)
            }

            if (contacts.isEmpty()) {
                return@hookReplace param.invokeOriginal()
            }
            ModuleScope.launchIO(name) {
                contacts.forEach { (key, value) ->
                    MsgTool.sendMsg(value, key, elements)
                    delay(500)
                }
            }
            null

        }

        for (handleForward in handleForwards) {
            handleForward.hookReplace(this) { param ->
                forwardReplace(param)
            }
        }

        installSpies()
    }

    /**
     * 临时诊断：9.3.75 的分享面板不再走 AIOForwardHandler.forward，
     * 这里把「按类型分发 + 发送」的整条链路都打点，用日志确认真实调用点。
     */
    private fun installSpies() {
        spy("com.tencent.mobileqq.sharepanel.forward.handler.atom.inner.AIOForwardApi", 1)
        spy("com.tencent.mobileqq.sharepanel.forward.handler.atom.SeparateMsgAtomForwardApi", 1)
        spy("com.tencent.qqnt.kernel.api.impl.MsgService", 1, listOf("forwardMsg", "sendMsg", "addSendMsg", "forwardFile", "multiForwardMsg"))
        spy("com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService\$CppProxy", 1, listOf("native_sendMsg", "native_forwardMsg", "native_multiForwardMsg", "native_addSendMsg"))
    }

    private val spiedMethods = HashSet<String>()

    private fun spy(className: String, minParams: Int, only: List<String>? = null) {
        runCatching {
            val clazz = className.toClass
            var hit = 0
            for (m in clazz.declaredMethods) {
                if (java.lang.reflect.Modifier.isStatic(m.modifiers)) continue
                if (m.parameterCount < minParams) continue
                if (only != null && m.name !in only) continue
                if (!spiedMethods.add("$className#${m.name}/${m.parameterCount}")) continue
                m.hookBefore(this) { p ->
                    LogUtils.d("转发语音[spy] ${clazz.simpleName}.${p.method.name}(${p.args.joinToString(", ") { argBrief(it) }})")
                }
                hit++
            }
            LogUtils.d("转发语音[spy] $className 挂载 $hit 个方法")
        }.onFailure { LogUtils.e(this, it) }
    }

    private fun argBrief(value: Any?): String = runCatching {
        when (value) {
            null -> "null"
            is String -> if (value.length > 40) "\"${value.take(40)}…\"" else "\"$value\""
            is Collection<*> -> "List(${value.size})" + value.take(3).joinToString("") { el ->
                val type = runCatching { el!!.javaClass.getMethod("getMsgType").invoke(el) }.getOrElse { "?" }
                "[${el?.javaClass?.simpleName} type=$type]"
            }
            is Map<*, *> -> "Map(${value.size})keys=${value.keys.take(6)}"
            else -> {
                val cls = value.javaClass
                val extra = runCatching {
                    val uin = cls.getMethod("getPeerUin").invoke(value)
                    val type = cls.getMethod("getPeerType").invoke(value)
                    val uid = runCatching { cls.getMethod("getPeerUid").invoke(value) }.getOrNull()
                    "peerUin=$uin peerUid=$uid peerType=$type"
                }.getOrElse { "" }
                if (extra.isEmpty()) cls.simpleName else "${cls.simpleName}($extra)"
            }
        }
    }.getOrElse { "?" }

    @Suppress("UNCHECKED_CAST")
    private fun forwardReplace(param: Chain): Any? {
        val source = param.args.getOrNull(0)
        val list = param.args.getOrNull(2) as? List<Any> ?: emptyList()

        // Map 重载才是模块自己发送语音的路径；其它重载（IShareData）保持原样
        val map = source as? Map<*, *> ?: run {
            LogUtils.d("转发语音 非 Map 转发载荷(${source?.javaClass?.name})，走原生流程")
            return param.invokeOriginal()
        }

        val target = lastAIOMsgItem ?: itemFromMap(map)
        lastAIOMsgItem = null

        LogUtils.d("转发语音 forward 载荷 keys=${map.keys} 目标=${target != null} 会话数=${list.size}")

        if (target != null && map.size == 1) {

            val elements = target.msgRecord.elements

            ModuleScope.launchIO(name) {
                CopyOnWriteArrayList<Any>(list)
                    .forEach { contact ->

                        val peerUin = contact.getObject("peerUin") as String
                        val peerType = contact.getObject("peerType") as Int

                        contact.setObject("peerType", INVALID_TYPE)

                        delay(500)
                        try {
                            MsgTool.sendMsg(peerType, peerUin, elements)
                            LogUtils.d("转发语音 已发送 peerUin=$peerUin peerType=$peerType")
                        } catch (t: Throwable) {
                            LogUtils.e(this@ForwardPtt, t)
                        }
                    }
            }
        }
        return param.invokeOriginal()
    }

    /** 转发载荷里可能直接带原消息（sendIntentNew 未命中时的兜底） */
    private fun itemFromMap(map: Map<*, *>): AIOMsgItem? {
        for (value in map.values) {
            when (value) {
                is AIOMsgItem -> return value
                is List<*> -> value.forEach { if (it is AIOMsgItem) return it }
                is Array<*> -> value.forEach { if (it is AIOMsgItem) return it }
                is Map<*, *> -> itemFromMap(value)?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun check(
        uin: String?,
        uinType: Int,
        contacts: MutableMap<String, Int>
    ) {
        val chatType = when (uinType) {
            0 -> 1
            1 -> 2
            1000 -> 100
            else -> INVALID_TYPE
        }
        if (uin != null && chatType != INVALID_TYPE) {
            contacts[uin] = chatType
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(

        "ForwardMenuItem" to FindClass().apply {
            searchPackages("com.tencent.qqnt.aio.menu")
            matcher {
                usingStrings("ForwardMenuItem")
            }
        },

        "sendIntentNew" to FindMethod().apply {
            matcher {
                declaredClass(NtMsgForwardUtils::class.java)
                returnType(Boolean::class.java)
                usingStrings("startForwardMsgV2 origin_msg_type=")
            }
        }
    )
}
