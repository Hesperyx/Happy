package cn.hxy.happy.hook.beauty

import androidx.compose.runtime.Composable
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.SettingMeEntry
import cn.hxy.happy.conf.SettingMeSimplifyConfig
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.configs.SettingMeSimplifyPage
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.findFieldOrNull
import cn.hxy.happy.utils.reflect.findMethodOrNull
import cn.hxy.happy.utils.reflect.getObjectOrNull

@HookItemAnnotation(
    "侧滑栏精简",
    "对侧滑栏功能入口进行精简隐藏。",
    HookCategory.BEAUTY,
    ""
)
object SimplifyQQSettingMe :
    BaseClickableHookItem<SettingMeSimplifyConfig>(SettingMeSimplifyConfig.serializer()) {

    override val defaultConfig: SettingMeSimplifyConfig = SettingMeSimplifyConfig()

    /** 侧滑栏面板 PartV3，条目数据与 adapter 都挂在它身上。 */
    private const val SETTING_ME_PART = "com.tencent.mobileqq.parts.QQSettingMeMenuPanelPartV3"

    /** 宿主刷新列表用的基类 adapter。 */
    private const val DIFFER_ADAPTER =
        "com.tencent.biz.richframework.part.adapter.AsyncListDifferDelegationAdapter"

    private const val ITEM_BEAN_FIELD = "a"

    override fun onHook() {
        val partClass = SETTING_ME_PART.clazz ?: return
        val onInitView = partClass.findMethodOrNull { name = "onInitView" } ?: return
        val bizDataListField = partClass.findFieldOrNull { type = ArrayList::class.java } ?: return

        onInitView.hookAfter(this) { param ->
            // 实时读 config：开关改动后无需重启，下一次进入侧滑栏即生效。
            val hiddenHostIds = SettingMeEntry.entries
                .filter { it.id in config.hiddenIds }
                .mapTo(mutableSetOf()) { it.hostId }
            if (hiddenHostIds.isEmpty()) return@hookAfter

            val part = param.thisObject
            @Suppress("UNCHECKED_CAST")
            val bizDataList = runCatching { bizDataListField.get(part) as? ArrayList<Any> }
                .getOrNull() ?: return@hookAfter

            val removed = bizDataList.removeAll { item -> needRemove(item, hiddenHostIds) }
            if (!removed) return@hookAfter

            refreshAdapter(partClass, part, bizDataList)
        }
    }

    /** bean 的任意 String 声明字段命中待隐藏 id 即视为需要移除。 */
    private fun needRemove(item: Any, hiddenHostIds: Set<String>): Boolean {
        val bean = runCatching { item.getObjectOrNull(ITEM_BEAN_FIELD) }.getOrNull()
            ?: return false
        return bean::class.java.declaredFields.any { field ->
            field.type == String::class.java && runCatching {
                field.isAccessible = true
                field.get(bean) as? String in hiddenHostIds
            }.getOrDefault(false)
        }
    }

    private fun refreshAdapter(partClass: Class<*>, part: Any, bizDataList: ArrayList<Any>) {
        runCatching {
            val adapterField = partClass.declaredFields
                .firstOrNull { it.type.name.contains("adapter") }
                ?: return
            adapterField.isAccessible = true
            val adapter = adapterField.get(part) ?: return

            val setItems = DIFFER_ADAPTER.clazz
                ?.findMethodOrNull { name = "setItems"; paramCount = 1 }
                ?: return
            setItems.invoke(adapter, bizDataList)
        }.onFailure {
            LogUtils.e(this, it)
        }
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        SettingMeSimplifyPage(
            currentConfig = config,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }
}
