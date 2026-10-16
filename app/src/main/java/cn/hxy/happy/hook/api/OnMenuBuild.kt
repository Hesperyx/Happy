package cn.hxy.happy.hook.api

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.aio.menu.ui.QQCustomMenuExpandableLayout
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import cn.hxy.happy.R
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseApiHookItem
import cn.hxy.happy.hook.base.Listener
import cn.hxy.happy.plugin.bean.MsgData
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.hook.hookReplace
import cn.hxy.happy.utils.hook.invokeOriginal
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.getObjectByType
import cn.hxy.happy.utils.reflect.getObjectByTypeOrNull
import cn.hxy.happy.utils.reflect.newInstanceWithArgs
import cn.hxy.happy.utils.reflect.setObjectByType
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation("监听消息菜单")
object OnMenuBuild : BaseApiHookItem<MenuClickListener>(), DexKitTask {

    private const val PREFIX = "[Happy]"

    private const val MENU_TYPE = "CopyMenuItem"

    override fun loadHook() {

        val itemClass = requireClass(MENU_TYPE)
        val itemSuperClass = itemClass.superclass!!

        QQCustomMenuExpandableLayout::class.java
            .findMethod {
                name = "setMenu"
            }.hookBefore(this) { param ->
                val customMenu = param.args[0] ?: return@hookBefore

                val items =
                    customMenu.getObjectByTypeOrNull<MutableList<Any>>(customMenu.javaClass.superclass)
                if (items.isNullOrEmpty()) return@hookBefore
                val aioMsgItem = items[0].getObjectByType<AIOMsgItem>(itemSuperClass)
                val msgRecord = aioMsgItem.msgRecord
                val msgType = msgRecord.msgType.toString()


               forEachChecked { listener ->

                    if (items.any { isModuleItem(it, listener.menuKey) }) return@forEachChecked

                    val args = listener.menuKey.split(",")
                    if (args.size < 5) return@forEachChecked

                    val targetTypes = args.subList(4, args.size).filter { it.isNotEmpty() }
                    if (targetTypes.isNotEmpty() && !targetTypes.contains(msgType)) {
                        return@forEachChecked
                    }

                    addMenuItem(items, itemClass, listener.menuKey, aioMsgItem)
                }

            }

        QQCustomMenuExpandableLayout::class.java.findMethod {
            returnType = view
            paramTypes(int, itemClass, boolean, floatArr)
        }.hookReplace(this) { param ->
            val menuItem = param.args[1] ?: return@hookReplace param.invokeOriginal()
            if (menuItem.javaClass != itemClass)
                return@hookReplace param.invokeOriginal()
            val label = menuItem.getObjectByType<String>()

            if (label.startsWith(PREFIX)) {

                val aioMsgItem = menuItem.getObjectByType<AIOMsgItem>(itemSuperClass)
                val msgRecord = aioMsgItem.msgRecord

                return@hookReplace createMenuItemView(
                    label,
                    msgRecord,
                    param.thisObject as QQCustomMenuExpandableLayout
                )
            }

            return@hookReplace param.invokeOriginal()
        }
    }

    private fun addMenuItem(
        items: MutableList<Any>,
        itemClass: Class<*>,
        key: String,
        aioMsgItem: AIOMsgItem
    ) {
        val context = QQCurrentEnv.activity ?: QQCurrentEnv.qQAppInterface.application
        val newItem = itemClass.newInstanceWithArgs(context, aioMsgItem)
        newItem.setObjectByType(key)
        items.add(0, newItem)
    }

    private fun createMenuItemView(
        menuKey: String,
        msgRecord: MsgRecord,
        expandable: QQCustomMenuExpandableLayout
    ): View {

        val activity = QQCurrentEnv.activity ?: throw IllegalStateException("Activity is null")
        val args = menuKey.split(",")
        val menuName = args[2]

        val layout = LayoutInflater.from(activity).inflate(R.layout.item_msg_menu, null)
        val nameText = layout.findViewById<TextView>(R.id.tv_msg_menu)
        nameText.apply {
            text = menuName
            setTextColor(Color.WHITE)
        }
        layout.setOnClickListener {
            try {
                listenerSet.single { it.menuKey == menuKey }.onClick(MsgData(msgRecord))
            } catch (t: Throwable) {
                LogUtils.e(this, t)
            } finally {
                expandable.dismiss()
            }
        }
        return layout
    }

    private fun isModuleItem(item: Any, key: String): Boolean {
        return runCatching {
            val label = item.getObjectByType<String>()
            label == key
        }.getOrElse { false }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        MENU_TYPE to FindClass().apply {
            searchPackages("com.tencent.qqnt.aio.menu")
            matcher {
                usingStrings(MENU_TYPE)
            }
        }
    )

}

interface MenuClickListener : Listener {
    val menuKey: String
    fun onClick(msgData: MsgData)
}