package cn.hxy.happy.hook.entry

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import cn.hxy.happy.R
import cn.hxy.happy.activity.PluginActivity
import cn.hxy.happy.activity.SettingActivity
import cn.hxy.happy.activity.StorageCleanActivity
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseApiHookItem
import cn.hxy.happy.hook.base.Listener
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.ClassUtils
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.getStaticObject
import cn.hxy.happy.utils.reflect.newInstanceWithArgs
import cn.hxy.happy.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Proxy

@HookItemAnnotation("QQ设置入口")
object QQSettingInject : BaseApiHookItem<Listener>(), DexKitTask {

    private const val TOP_TITLE = "模块"
    private const val BOTTOM_TITLE = ""
    private const val MODULE_ORDER = 10

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

        val providerList = mutableListOf<Class<*>?>()
        val newProvider = "com.tencent.mobileqq.setting.main.NewSettingConfigProvider".clazz
        val mainProvider = "com.tencent.mobileqq.setting.main.MainSettingConfigProvider".clazz

        if (HostInfo.isQQ && HostInfo.versionCode >= 12288) {
            val obfProvider = requireClass("ConfigProvider")
            providerList.add(obfProvider)
        } else {
            providerList.add(newProvider)
            providerList.add(mainProvider)
        }

        if (providerList.isEmpty()) throw ClassNotFoundException("SettingConfigProvider")

        val itemClass = requireClass("ItemProcessor")

        val setOnClickListener = itemClass.findMethod {
            returnType = void
            paramTypes("kotlin.jvm.functions.Function0".toClass)
        }

        providerList.forEach {
            it?.findMethod {
                returnType = list
                paramTypes(context)
            }?.hookAfter(this) { param ->
                val context = param.args[0] as Context
                val result = param.result as MutableList<Any>

                val settingEntry = makeItem(
                    itemClass,
                    context,
                    MODULE_ORDER,
                    "Happy",
                    R.drawable.ic_launcher
                )
                setOnClickListener.invoke(
                    settingEntry, makeProxy(
                        context,
                        SettingActivity::class.java
                    )
                )

                val pluginEntry = makeItem(
                    itemClass,
                    context,
                    MODULE_ORDER,
                    "JavaPlugin",
                    R.drawable.ic_float_ball
                )
                setOnClickListener.invoke(
                    pluginEntry, makeProxy(
                        context,
                        PluginActivity::class.java
                    )
                )

                val cleanEntry = makeItem(
                    itemClass,
                    context,
                    MODULE_ORDER,
                    "缓存清理",
                    deleteIconRes
                )
                setOnClickListener.invoke(
                    cleanEntry, makeProxy(
                        context,
                        StorageCleanActivity::class.java
                    )
                )

                result.add(
                    1,
                    result[0].javaClass.newInstanceWithArgs(
                        listOf(settingEntry, pluginEntry, cleanEntry),
                        TOP_TITLE,
                        BOTTOM_TITLE,
                        0,
                        null
                    )
                )

            }

        }

    }

    private fun makeItem(itemClass: Class<*>, vararg args: Any?): Any {
        return runCatching {
            itemClass.newInstanceWithArgs(*args, null)
        }.getOrElse {
            itemClass.newInstanceWithArgs(*args)
        }
    }

    private fun makeProxy(
        context: Context,
        activityClass: Class<*>
    ): Any {

        val unit = "kotlin.Unit".toClass.getStaticObject("INSTANCE")

        return Proxy.newProxyInstance(
            ClassUtils.hostClassLoader,
            arrayOf("kotlin.jvm.functions.Function0".toClass)
        ) { _, method, _ ->
            if (method.name == "invoke") {
                runCatching {
                    context.startActivity(Intent(context, activityClass))
                }.onFailure {
                    LogUtils.e(this, it)
                }
            }
            unit
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> {
        val item = if (HostInfo.isQQ && HostInfo.versionCode >= 14498) {
            FindClass().apply {
                matcher {
                    usingStrings("context", "leftText", "SimpleItemProcessor")
                }
            }
        } else {
            FindClass().apply {
                searchPackages("com.tencent.mobileqq.setting.processor")
                matcher {
                    usingStrings("context", "leftText")
                }
            }
        }

        return mapOf(
            "ConfigProvider" to FindClass().apply {
                searchPackages("com.tencent.mobileqq.setting.main")
                matcher {
                    superClass("com.tencent.mobileqq.setting.processor.SettingConfigProvider")
                }
            },
            "ItemProcessor" to item
        )
    }

}
