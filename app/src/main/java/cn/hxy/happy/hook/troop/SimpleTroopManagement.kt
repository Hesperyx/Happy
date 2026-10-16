package cn.hxy.happy.hook.troop

import android.app.Activity
import android.view.View
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msglist.holder.component.avatar.AIOAvatarContentComponent
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.loader.hookapi.Chain
import cn.hxy.happy.ui.core.compatibility.HappyBottomDialog
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.hookReplace
import cn.hxy.happy.utils.hook.invokeOriginal
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.MsgTool
import cn.hxy.happy.utils.qq.TroopTool
import cn.hxy.happy.utils.reflect.getObjectByType
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method

@HookItemAnnotation(
    "简洁群管",
    "点击群聊头像开启菜单，省去进入主页管理群员",
    HookCategory.GROUP
)
object SimpleTroopManagement : BaseSwitchHookItem(), DexKitTask {

    lateinit var onClick: Method

    override fun onInit(): Boolean {
        onClick = requireClass("listener")
            .getDeclaredMethod("onClick", View::class.java)
        return super.onInit()
    }

    override fun onHook() {
        onClick.hookReplace(this) { param ->
            val listener = param.thisObject
            val view = param.args[0] as View

            val component = listener.getObjectByType<AIOAvatarContentComponent>()
            val msgItem = component.getObjectByType<AIOMsgItem>()
            val msgRecord = msgItem.msgRecord

            if (msgRecord.chatType != 2) return@hookReplace param.invokeOriginal()

            val troopUin = msgRecord.peerUin.toString()
            val troopInfo = TroopTool.getGroupInfo(troopUin)
            if (!troopInfo.isOwnerOrAdmin) return@hookReplace param.invokeOriginal()

            showManagementSheet(view.context as Activity, msgRecord, param, troopInfo.isOwner)
            return@hookReplace null
        }
    }

    private fun showManagementSheet(
        activity: Activity,
        msgRecord: MsgRecord,
        param: Chain,
        isOwner: Boolean
    ) {
        val troopUin = msgRecord.peerUin.toString()
        val memberUin = msgRecord.senderUin.toString()
        val nick = msgRecord.sendMemberName.ifEmpty { msgRecord.sendNickName }

        fun dismissAndRun(dismiss: () -> Unit, action: () -> Unit) {
            dismiss()
            runAction(action)
        }

        HappyBottomDialog(activity) { dismiss ->
            TroopManagementContent(
                activity = activity,
                memberUin = memberUin,
                memberNick = nick,
                isOwner = isOwner,
                onEnterProfile = { dismissAndRun(dismiss) { param.invokeOriginal() } },
                onRecall = {
                    dismissAndRun(dismiss) {
                        MsgTool.recallMsg(
                            2,
                            troopUin,
                            msgRecord.msgId
                        )
                    }
                },
                onSetAdmin = {
                    dismissAndRun(dismiss) {
                        TroopTool.setGroupAdmin(
                            troopUin,
                            memberUin,
                            true
                        )
                    }
                },
                onCancelAdmin = {
                    dismissAndRun(dismiss) {
                        TroopTool.setGroupAdmin(
                            troopUin,
                            memberUin,
                            false
                        )
                    }
                },
                onSetMute = { duration ->
                    dismissAndRun(dismiss) {
                        TroopTool.shutUp(
                            troopUin,
                            memberUin,
                            duration
                        )
                    }
                },
                onCancelMute = {
                    dismissAndRun(dismiss) {
                        TroopTool.shutUp(
                            troopUin,
                            memberUin,
                            0
                        )
                    }
                },
                onSetTitle = { title ->
                    dismissAndRun(dismiss) {
                        TroopTool.setGroupMemberTitle(
                            troopUin,
                            memberUin,
                            title
                        )
                    }
                },
                onSetCard = { card ->
                    dismissAndRun(dismiss) {
                        TroopTool.changeMemberName(
                            troopUin,
                            memberUin,
                            card
                        )
                        msgRecord.sendMemberName = card
                    }
                },
                onKick = {
                    dismissAndRun(dismiss) {
                        TroopTool.kickGroup(
                            troopUin,
                            memberUin,
                            false
                        )
                    }
                },
                onKickBlock = {
                    dismissAndRun(dismiss) {
                        TroopTool.kickGroup(
                            troopUin,
                            memberUin,
                            true
                        )
                    }
                },
                onMuteAll = { dismissAndRun(dismiss) { TroopTool.shutUpAll(troopUin, true) } },
                onUnmuteAll = { dismissAndRun(dismiss) { TroopTool.shutUpAll(troopUin, false) } },
                getCurrentCard = { nick }
            )
        }.show()
    }

    private fun runAction(action: () -> Unit) {
        runCatching { action() }.onFailure { LogUtils.e(this, it) }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "listener" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.holder.component.avatar")
            matcher {
                addInterface(View.OnClickListener::class.java.name)
                methods {
                    add { name("onClick") }
                }
            }
        }
    )
}
