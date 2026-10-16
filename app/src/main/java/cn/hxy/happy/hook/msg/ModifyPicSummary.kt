package cn.hxy.happy.hook.msg

import androidx.compose.runtime.Composable
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.conf.SummaryConfig
import cn.hxy.happy.hook.api.SendMsgListener
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.ModifyPicSummaryPage
import cn.hxy.happy.utils.json.findFirstValueByKey
import cn.hxy.happy.utils.net.HttpUtils
import org.json.JSONArray
import org.json.JSONObject

@HookItemAnnotation(
    "修改图片外显",
    "返回值为Json格式时支持读取嵌套的key，为纯文本长度需小于30",
    HookCategory.MSG
)
object ModifyPicSummary : BaseClickableHookItem<SummaryConfig>(SummaryConfig.serializer()),
    SendMsgListener {

    override val defaultConfig: SummaryConfig = SummaryConfig()

    private var cachedSummary: String = ""

    override fun initData() {
        super.initData()
        fetchNextSummary()
    }

    override fun onSend(elements: ArrayList<MsgElement>) {
        if (cachedSummary.isEmpty()) return

        var hasPic = false
        elements.forEach { element ->
            element.picElement?.let {
                it.summary = cachedSummary
                hasPic = true
            }
            element.marketFaceElement?.let {
                it.faceName = cachedSummary
                hasPic = true
            }
        }

        if (hasPic) {
            fetchNextSummary()
        }
    }

    fun fetchNextSummary() {
        if (config.summaryOrUrl.isEmpty()) {
            cachedSummary = ""
            return
        }

        if (!config.summaryOrUrl.startsWith("http")) {
            cachedSummary = if (config.summaryOrUrl.length <= 30) config.summaryOrUrl else ""
            return
        }

        ModuleScope.launchIO(name) {
            val response = HttpUtils.getSuspend(config.summaryOrUrl)
            cachedSummary = try {
                if (config.key.isNotEmpty()) {
                    val json: Any =
                        if (response.startsWith("[")) JSONArray(response) else JSONObject(response)
                    val value = json.findFirstValueByKey(config.key)
                    value?.toString() ?: ""
                } else {
                    if (response.length <= 30) response else config.summaryOrUrl
                }
            } catch (_: Exception) {
                if (config.summaryOrUrl.length <= 30) config.summaryOrUrl else ""
            }
        }
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        ModifyPicSummaryPage(
            currentConfig = config,
            onSave = {
                updateConfig(it)
                fetchNextSummary()
            },
            onDismiss = onDismiss
        )
    }
}
