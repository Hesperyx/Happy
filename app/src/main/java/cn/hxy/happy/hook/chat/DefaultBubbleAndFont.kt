package cn.hxy.happy.hook.chat

import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.api.GetMsgRecordListener
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.qq.QQCurrentEnv

@HookItemAnnotation(
    "默认气泡和字体",
    "在聊天界面中将他人的气泡和字体全部显示为默认",
    HookCategory.CHAT
)
object DefaultBubbleAndFont : BaseSwitchHookItem(), GetMsgRecordListener {

    override fun onGet(msgRecord: MsgRecord) {
        if (msgRecord.senderUin != QQCurrentEnv.currentUin.toLong())
            msgRecord.msgAttrs = HashMap<Int, MsgAttributeInfo>()

    }

}