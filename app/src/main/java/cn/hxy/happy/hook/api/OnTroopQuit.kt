package cn.hxy.happy.hook.api

import com.tencent.mobileqq.troop.api.impl.TroopMemberInfoServiceImpl
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseApiHookItem
import cn.hxy.happy.hook.base.Listener
import cn.hxy.happy.utils.hook.hookAfter

@HookItemAnnotation("监听用户退群")
object OnTroopQuit : BaseApiHookItem<TroopQuitListener>() {
    override fun loadHook() {

        TroopMemberInfoServiceImpl::class.java
            .getDeclaredMethod(
                "deleteTroopMember",
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            .hookAfter(this) { param ->
                val troopUin = param.args[0] as String
                val memberUin = param.args[1] as String
                forEachChecked { it.onQuit(troopUin, memberUin) }
            }


    }
}

fun interface TroopQuitListener : Listener {
    fun onQuit(troopUin: String, memberUin: String)
}