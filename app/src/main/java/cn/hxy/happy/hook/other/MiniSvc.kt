package cn.hxy.happy.hook.other

import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.reflect.callMethod
import cn.hxy.happy.utils.reflect.toClass
import java.lang.reflect.Modifier

/**
 * 宿主小程序 SSO 相关的反射封装（无注解，不参与 HookRegistry 注册）。
 *
 * 等价于 TCQT 的 MiniSvc.judgeTiming()，全部走反射，不 import 任何宿主类。
 */
internal object MiniSvc {

    private const val REQ_CLASS = "NS_MINI_INTERFACE.INTERFACE\$StJudgeTimingReq"
    private const val RSP_CLASS = "NS_MINI_INTERFACE.INTERFACE\$StJudgeTimingRsp"
    private const val CMD_HELPER_CLASS = "com.tencent.mobileqq.mini.servlet.MiniAppSSOCmdHelper"
    private const val SEND_METHOD = "sendSSOCmdRequest"
    private const val CMD = "LightAppSvc.mini_app_growguard.JudgeTiming"
    private const val APPID = "1112173744"

    /**
     * 上报「达人补登卡」对应的 JudgeTiming 请求。
     */
    fun judgeTiming() {
        val req = REQ_CLASS.toClass
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance()

        val nowSeconds = System.currentTimeMillis() / 1000

        // 以下字段含义沿用 TCQT 的注释：定时上报场景值
        req.setPbValue("appid", APPID)
        req.setPbValue("factType", 13)
        req.setPbValue("duration", 32)
        req.setPbValue("reportTime", nowSeconds)
        req.setPbValue("totalTime", 300)
        req.setPbValue("launchId", nowSeconds.toString())
        req.setPbValue("via", "2016_4")
        req.setPbValue("appType", 1)
        req.setPbValue("scene", 2014)
        req.setPbValue("afterCertify", 1)
        req.setPbValue("AdsTotalTime", 0)
        req.setPbValue("sourceID", "")

        sendJudgeTiming(req)
    }

    private fun sendJudgeTiming(req: Any) {
        val helperClass = CMD_HELPER_CLASS.toClass
        val rspClass = RSP_CLASS.toClass

        val candidates = generateSequence(helperClass) { it.superclass }
            .toList()
            .flatMap { it.declaredMethods.toList() }
            .filter { it.name == SEND_METHOD && it.parameterCount == 5 }
            .onEach { it.isAccessible = true }

        if (candidates.isEmpty()) {
            throw NoSuchMethodException("$SEND_METHOD not found in ${helperClass.name}")
        }

        var lastError: Throwable? = null

        for (method in candidates) {
            val isStatic = Modifier.isStatic(method.modifiers)
            val receiver = if (isStatic) null else resolveInstance(helperClass)
            if (!isStatic && receiver == null) continue

            val error = runCatching {
                method.invoke(receiver, CMD, APPID, req, rspClass, null)
            }.exceptionOrNull()

            if (error == null) return
            lastError = error
        }

        throw lastError ?: IllegalStateException("sendSSOCmdRequest 调用失败")
    }

    /**
     * 单例：优先取类型匹配的静态字段（INSTANCE 之类），否则尝试无参构造。
     */
    private fun resolveInstance(clazz: Class<*>): Any? {
        val singleton = clazz.declaredFields
            .firstOrNull { Modifier.isStatic(it.modifiers) && clazz.isAssignableFrom(it.type) }
            ?.apply { isAccessible = true }
            ?.let { field -> runCatching { field.get(null) }.getOrNull() }

        if (singleton != null) return singleton

        return runCatching {
            clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }.getOrNull()
    }

    /**
     * 写入 protobuf 包装字段（PBUInt32Field / PBStringField 等）。
     * set 的重载在宿主里不确定，按 Int → Long 顺序试写。
     */
    private fun Any.setPbValue(name: String, value: Any?) {
        val pbField = findFieldDeep(this::class.java, name)
            ?.let { field -> runCatching { field.get(this) }.getOrNull() }
            ?: return

        val candidates: List<Any?> = when (value) {
            is Int -> listOf<Any?>(value, value.toLong())
            is Long -> listOf<Any?>(value, value.toInt())
            else -> listOf<Any?>(value)
        }

        for (candidate in candidates) {
            if (runCatching { pbField.callMethod("set", candidate) }.isSuccess) return
        }

        LogUtils.d("MiniSvc: 字段 $name 写入失败")
    }

    private fun findFieldDeep(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        return generateSequence(clazz) { it.superclass }
            .firstNotNullOfOrNull { cls ->
                runCatching { cls.getDeclaredField(name) }.getOrNull()
            }
            ?.apply { isAccessible = true }
    }
}
