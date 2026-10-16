package cn.hxy.happy.hook.entry

import android.annotation.SuppressLint
import android.content.Intent
import com.tencent.widget.PopupMenuDialog
import com.tencent.widget.PopupMenuDialog.MenuItem
import com.tencent.widget.PopupMenuDialog.OnClickActionListener
import cn.hxy.happy.R
import cn.hxy.happy.activity.PluginActivity
import cn.hxy.happy.activity.SettingActivity
import cn.hxy.happy.activity.StorageCleanActivity
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseApiHookItem
import cn.hxy.happy.hook.base.Listener
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.reflect.findMethod

@HookItemAnnotation("QQ加号入口")
object QQPlusInject : BaseApiHookItem<Listener>() {

    @delegate:SuppressLint("DiscouragedApi")
    private val deleteIconRes by lazy {
        try {
            HostInfo.hostContext.resources.getIdentifier(
                "qui_delete_light_selector",
                "drawable",
                HostInfo.packageName
            )
        } catch (_: Exception) {
            R.drawable.ic_launcher
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun loadHook() {

        PopupMenuDialog::class.java
            .findMethod {
                name = "conversationPlusBuild"
            }.hookBefore(this) { param ->

                val activity = QQCurrentEnv.activity ?: return@hookBefore
                val menuItemList = param.args[1] as MutableList<MenuItem>

                menuItemList.apply {
                    add(
                        0,
                        MenuItem(
                            R.string.storage_clean,
                            "缓存清理",
                            "缓存清理",
                            deleteIconRes
                        )
                    )
                    add(
                        0,
                        MenuItem(
                            R.string.plugin_name,
                            "JavaPlugin",
                            "JavaPlugin",
                            R.drawable.ic_float_ball
                        )
                    )
                    add(
                        0,
                        MenuItem(
                            R.string.app_name,
                            "Happy",
                            "Happy",
                            R.drawable.ic_launcher
                        )
                    )
                }

                val origin = param.args[2] as OnClickActionListener

                param.args[2] = OnClickActionListener { menuItem ->

                    when (menuItem.id) {
                        R.string.app_name -> activity.startActivity(
                            Intent(
                                activity,
                                SettingActivity::class.java
                            )
                        )

                        R.string.plugin_name -> activity.startActivity(
                            Intent(
                                activity,
                                PluginActivity::class.java
                            )
                        )
                        
                        R.string.storage_clean -> activity.startActivity(
                            Intent(
                                activity,
                                StorageCleanActivity::class.java
                            )
                        )

                        else -> origin.onClickAction(menuItem)
                    }

                }

            }

    }

}
