package cn.hxy.happy.hook.other

import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.Toasts
import cn.hxy.happy.utils.reflect.toClass
import java.lang.reflect.Method
import java.util.WeakHashMap

@HookItemAnnotation(
    "自动领取达人补登卡",
    "在进入QQ达人页时自动领取当天的补登卡，不用再玩**的**小程序游戏！",
    HookCategory.OTHER,
    ":tool"
)
object CardFetcher : BaseSwitchHookItem() {

    private const val WEBVIEW_CLIENT_CLASS = "com.tencent.smtt.sdk.WebViewClient"
    private const val PAGE_FINISHED_METHOD = "onPageFinished"

    private const val QQ_DAREN_URL_PREFIX = "https://ti.qq.com/qqdaren/index"
    private const val BLANK_URL = "about:blank"
    private const val DELAY_MILLIS = 1500L
    private const val SUCCESS_TIP = "已领取补登卡"

    /** WebView -> 上次触发过的 url，避免同一页面重复请求。 */
    private val triggerHistory = WeakHashMap<Any, String>()

    override fun onHook() {
        // 宿主 X5 类，不直接 import，按类名加载
        val webViewClientClass = WEBVIEW_CLIENT_CLASS.toClass

        val onPageFinished = findOnPageFinished(webViewClientClass)
            ?: throw NoSuchMethodException("$PAGE_FINISHED_METHOD not found in ${webViewClientClass.name}")

        onPageFinished.hookAfter(this) { param ->
            val args = param.args
            if (args.size < 2) return@hookAfter

            val webView = args[0] ?: return@hookAfter
            val url = args[1] as? String ?: return@hookAfter

            if (url == BLANK_URL || !url.startsWith(QQ_DAREN_URL_PREFIX)) return@hookAfter
            if (!markTriggered(webView, url)) return@hookAfter

            ModuleScope.launchMainDelayed(DELAY_MILLIS) {
                runCatching { MiniSvc.judgeTiming() }
                    .onSuccess { Toasts.toast(SUCCESS_TIP) }
                    .onFailure { LogUtils.e(name, it) }
            }
        }
    }

    private fun findOnPageFinished(clazz: Class<*>): Method? {
        return generateSequence(clazz) { it.superclass }
            .toList()
            .flatMap { it.declaredMethods.toList() }
            .firstOrNull { method ->
                method.name == PAGE_FINISHED_METHOD &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[1] == String::class.java
            }
            ?.apply { isAccessible = true }
    }

    private fun markTriggered(webView: Any, url: String): Boolean {
        synchronized(triggerHistory) {
            if (triggerHistory[webView] == url) return false
            triggerHistory[webView] = url
        }
        return true
    }
}
