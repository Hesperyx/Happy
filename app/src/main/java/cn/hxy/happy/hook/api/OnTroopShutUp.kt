package cn.hxy.happy.hook.api

import com.tencent.qphone.base.remote.FromServiceMsg
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseApiHookItem
import cn.hxy.happy.hook.base.Listener
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.json.ProtoData
import cn.hxy.happy.utils.json.long
import cn.hxy.happy.utils.json.num
import cn.hxy.happy.utils.json.obj
import cn.hxy.happy.utils.json.str
import cn.hxy.happy.utils.json.walk
import cn.hxy.happy.utils.qq.FriendTool
import mqq.app.MSFServlet

@HookItemAnnotation("监听群禁言")
object OnTroopShutUp : BaseApiHookItem<TroopShutUpListener>() {

    private const val SERVICE_CMD = "trpc.msg.olpush.OlPushService.MsgPush"

    override fun loadHook() {

        MSFServlet::class.java
            .getDeclaredMethod("onReceive", FromServiceMsg::class.java)
            .hookAfter(this) { param ->

                val fromServiceMsg = param.args[0] as FromServiceMsg
                val cmd = fromServiceMsg.serviceCmd

                if (cmd != SERVICE_CMD) return@hookAfter

                val wupBuffer = fromServiceMsg.wupBuffer
                val data = ProtoData().apply { fromBytes(wupBuffer) }
                val json = data.toJSON()

                val msgHead = json.walk("1", "2")?.obj ?: return@hookAfter
                if (msgHead["1"].num != 732 || msgHead["2"].num != 12) {
                    return@hookAfter
                }

                val groupInfo = json.walk("1", "3", "2")?.obj ?: return@hookAfter

                val troopUin = groupInfo["1"].long?.toString() ?: return@hookAfter
                val opUid = groupInfo["4"].str ?: ""
                val opUin = FriendTool.getUinFromUid(opUid)

                val shutUpInfo = groupInfo.walk("5", "3")?.obj ?: return@hookAfter

                val memberUid = shutUpInfo["1"].str ?: ""
                val memberUin = FriendTool.getUinFromUid(memberUid)
                val time = shutUpInfo["2"].long ?: 0L

                forEachChecked { it.onShutUp(troopUin, memberUin, time, opUin) }
            }
    }
}

fun interface TroopShutUpListener : Listener {
    fun onShutUp(troopUin: String, memberUin: String, time: Long, opUin: String)
}