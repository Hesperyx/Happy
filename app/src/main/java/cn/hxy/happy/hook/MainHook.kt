package cn.hxy.happy.hook

import androidx.core.content.edit
import cn.hxy.happy.generated.HookRegistry
import cn.hxy.happy.hook.base.BaseApiHookItem
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.hook.base.Listener
import cn.hxy.happy.plugin.MainPlugin
import cn.hxy.happy.ui.pages.configs.ConfigUiRegistry
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.reflect.toClass

object MainHook {

    private var initialized = false
    private val allHookItem = HookRegistry.hookItems
    private val apiHookItemList =
        allHookItem.filterIsInstance<BaseApiHookItem<Listener>>()
    val switchHookItemList =
        allHookItem.filterIsInstance<BaseSwitchHookItem>()
    val clickableHookItemList =
        switchHookItemList.filterIsInstance<BaseClickableHookItem<*>>()


    fun loadHook() {

        loadApiHook()
        initSwitchHookItem()
        hookAccountChange()


    }

    private fun loadApiHook() = apiHookItemList
        .forEach {
            try {
                if (it.isInTargetProcess()) it.loadHook()
            } catch (t: Throwable) {
                LogUtils.e(it, t)
            }
        }

    fun initAllConfigUI() {
        if (initialized) return
        initialized = true

        clickableHookItemList
            .forEach { item ->
                ConfigUiRegistry.register(item.name) { onDismiss ->
                    item.ConfigContent(onDismiss)
                }
            }
    }

    private fun initSwitchHookItem() = switchHookItemList.forEach(BaseSwitchHookItem::init)

    fun processDataForCurrent(tag: String) = clickableHookItemList
        .filter(BaseSwitchHookItem::isAvailable)
        .forEach {
            when (tag) {
                "init" -> it.initData()
                "save" -> it.saveData()
            }
        }


    private fun hookAccountChange() {

        "com.tencent.imcore.message.BaseQQMessageFacade".toClass
            .declaredConstructors.first()
            .hookAfter {
                QQCurrentEnv.globalPreference.edit {
                    putString(
                        "currentUin",
                        QQCurrentEnv.currentUin
                    )
                }
                processDataForCurrent("init")
                MainPlugin.initAllPluginForCurrent()

            }
    }

}