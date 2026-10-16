package cn.hxy.happy.hook.purify

import com.tencent.biz.pubaccount.CustomWebView
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookReplace
import cn.hxy.happy.utils.hook.invokeOriginal

@HookItemAnnotation(
    "解除风险网页拦截",
    "点击消息中链接时不再拦截风险网页",
    HookCategory.PURIFY,
    ":tool"
)
object RemoveRiskWebpageBlock : BaseSwitchHookItem() {

    private var targetUrl: String? = null
    private const val BLOCK_URL = "c.pc.qq.com"

    override fun onHook() {

        CustomWebView::class.java
            .getDeclaredMethod("loadUrl", String::class.java)
            .hookReplace(this) { param ->

                val url = param.args[0] as String

                if (url.contains(BLOCK_URL)) {
                    return@hookReplace param.invokeOriginal(arrayOf(targetUrl))
                } else {
                    targetUrl = url
                    return@hookReplace param.invokeOriginal()
                }
            }
    }

}