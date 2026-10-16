package cn.hxy.happy.hook.msg

import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.returnConstant
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findMethod
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method

@HookItemAnnotation(
    "查看聊天记录回复消息",
    "强制查看聊天记录回复消息中包含的图片消息",
    HookCategory.MSG
)
object RecordsReplyCheck : BaseSwitchHookItem(), DexKitTask {

    private lateinit var check: Method

    override fun onInit(): Boolean {
        if (HostInfo.isQQ && HostInfo.versionCode > 9906) {
            check = requireClass("repo")
                .findMethod {
                    returnType = long
                    paramTypes(MsgRecord::class.java, AIOMsgItem::class.java)
                }
            return super.onInit()
        }
        return false
    }

    override fun onHook() {
        check.returnConstant(this, 1L)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "repo" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.msgrepo")
            matcher {
                usingStrings("AIOMsgRepo MsgReplyAbility")
            }
        }
    )

}