package cn.hxy.happy.plugin

import cn.hxy.happy.plugin.loader.PluginManager

object MainPlugin {
    fun initAllPluginForCurrent() {
        PluginManager.stopAllPlugins()
        PluginManager.plugins.clear()
        PluginManager.loadAll()
        PluginManager.autoStart()
    }

}