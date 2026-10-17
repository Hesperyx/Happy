package cn.hxy.happy.hook.other

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findMethod
import java.util.regex.Pattern

@HookItemAnnotation(
    "去你大爷的QQ浏览器",
    "在宿主内访问网页时，强制使用系统默认浏览器打开，而非使用内置浏览器。",
    HookCategory.OTHER,
    "All"
)
object FxxkQQBrowser : BaseSwitchHookItem() {

    private const val QQBROWSER_ACTIVITY_FLAG = "QQBrowserActivity"
    private const val URL_EXTRA_KEY = "url"

    private val URL_PATTERN = Pattern.compile(
        "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$|^www\\.[^.]+\\.[^.]+$|^[^.]+\\.[^.]+$"
    )

    private val HOST_WHITELIST = listOf(
        "qq.com",
        "tenpay.com",
        "tencent.com",
        "cdn-go.cn",
        "wechat.com"
    )

    override fun onHook() {
        Instrumentation::class.java.findMethod {
            name = "execStartActivity"
            paramTypes(
                context,
                IBinder::class.java,
                IBinder::class.java,
                Activity::class.java,
                Intent::class.java,
                int,
                bundle
            )
        }.hookBefore(this) { param ->
            val intent = param.args.getOrNull(4) as? Intent ?: return@hookBefore
            val url = intent.getStringExtra(URL_EXTRA_KEY) ?: return@hookBefore

            if (!shouldHijack(intent, url)) return@hookBefore

            openWithSystemBrowser(url)
            param.result = null
        }
    }

    private fun shouldHijack(intent: Intent, url: String): Boolean {
        if (!URL_PATTERN.matcher(url.lowercase()).matches()) return false
        if (isHostWhitelisted(url)) return false

        val shortName = intent.component?.shortClassName ?: return false
        return shortName.contains(QQBROWSER_ACTIVITY_FLAG)
    }

    private fun openWithSystemBrowser(rawUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, toWebUri(rawUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { HostInfo.hostContext.startActivity(intent) }
            .onFailure { LogUtils.e(this, it) }
    }

    private fun toWebUri(rawUrl: String): Uri {
        return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            Uri.parse(rawUrl)
        } else {
            Uri.parse("http://$rawUrl")
        }
    }

    private fun isHostWhitelisted(url: String): Boolean {
        val host = runCatching {
            val normalized = if (url.contains("://")) url else "http://$url"
            Uri.parse(normalized).host?.lowercase()
        }.getOrNull() ?: return false

        return HOST_WHITELIST.any { host.endsWith(it) }
    }
}
