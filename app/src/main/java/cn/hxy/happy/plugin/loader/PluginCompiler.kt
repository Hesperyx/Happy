package cn.hxy.happy.plugin.loader

import bsh.BshMethod
import bsh.Interpreter
import cn.hxy.happy.hook.api.MenuClickListener
import cn.hxy.happy.hook.api.OnMenuBuild
import cn.hxy.happy.hook.api.OnPaiYiPai
import cn.hxy.happy.hook.api.OnReceiveMsg
import cn.hxy.happy.hook.api.OnSendMsg
import cn.hxy.happy.hook.api.OnTroopJoin
import cn.hxy.happy.hook.api.OnTroopQuit
import cn.hxy.happy.hook.api.OnTroopShutUp
import cn.hxy.happy.lifecycle.DynamicActivityRegistry
import cn.hxy.happy.plugin.api.PluginCallback
import cn.hxy.happy.plugin.api.PluginMethod
import cn.hxy.happy.plugin.bean.MsgData
import cn.hxy.happy.plugin.bean.PluginInfo
import cn.hxy.happy.utils.log.PluginError
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.reflect.ClassUtils
import java.io.File
import java.lang.reflect.Modifier

class PluginCompiler(val info: PluginInfo) {
    var interpreter = Interpreter()
    val menuItems = linkedMapOf<String, String>()
    val msgMenuItem = mutableSetOf<String>()
    val registedActivitys = mutableSetOf<String>()

    private val api = PluginMethod(this)
    val callback = PluginCallback(this)
    val loader = FixClassLoader()

    @Synchronized
    fun start() {
        if (info.isRunning) {
            stop(false)
        }

        info.updateFromDisk()

        val scriptFile = File(info.dirPath, "main.java")
        if (!scriptFile.exists()) throw IllegalStateException("main.java not found")

        try {
            interpreter = Interpreter()

            interpreter.apply {
                set("context", HostInfo.hostContext)
                set("myUin", QQCurrentEnv.currentUin)
                set("classLoader", ClassUtils.hostClassLoader)
                set("pluginPath", info.dirPath)
                set("pluginId", info.id)
                setClassLoader(loader)

                PluginMethod::class.java.declaredMethods
                    .filter { Modifier.isPublic(it.modifiers) }
                    .filterNot { it.name.contains("$") }
                    .forEach { nameSpace.setMethod(BshMethod(it, api)) }

                source(scriptFile.absolutePath)
            }
            registerCallbacks()
            info.isRunning = true
        } catch (e: Exception) {
            stop(false)
            throw e
        }
    }

    @Synchronized
    fun stop(invokeCallback: Boolean = true) {
        if (!info.isRunning) return

        if (invokeCallback) {
            try {
                callback.unLoadPlugin()
            } catch (e: Exception) {
                PluginError.callError(e, info)
            }
        }

        try {
            registedActivitys.forEach {
                DynamicActivityRegistry.unregister(it)
            }
            removeCallbacks()
            interpreter.nameSpace.clear()
            menuItems.clear()
            msgMenuItem.clear()
            registedActivitys.clear()
        } catch (e: Exception) {
            PluginError.callError(e, info)
        } finally {
            info.isRunning = false
        }
    }

    private fun registerCallbacks() {
        OnReceiveMsg.addListener(callback.receiveMsgListener)
        OnSendMsg.addListener(callback.sendMsgListener)
        OnTroopJoin.addListener(callback.troopJoinListener)
        OnTroopQuit.addListener(callback.troopQuitListener)
        OnTroopShutUp.addListener(callback.troopShutUpListener)
        OnPaiYiPai.addListener(callback.paiYiPaiListener)
        msgMenuItem.forEach { item ->
            val args = item.split(",")
            if (args.size >= 4) {
                OnMenuBuild.addListener(object : MenuClickListener {
                    override val menuKey: String get() = item
                    override fun onClick(msgData: MsgData) {
                        callback.invokeMsgMenuItem(args[3], msgData)
                    }
                })
            }
        }
    }

    private fun removeCallbacks() {
        OnReceiveMsg.removeListener(callback.receiveMsgListener)
        OnSendMsg.removeListener(callback.sendMsgListener)
        OnTroopJoin.removeListener(callback.troopJoinListener)
        OnTroopQuit.removeListener(callback.troopQuitListener)
        OnTroopShutUp.removeListener(callback.troopShutUpListener)
        OnPaiYiPai.removeListener(callback.paiYiPaiListener)
        msgMenuItem.forEach { item ->
            OnMenuBuild.listenerSet.removeIf { it.menuKey == item }
        }
    }
}