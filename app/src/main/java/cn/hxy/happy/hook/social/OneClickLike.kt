package cn.hxy.happy.hook.social

import com.tencent.mobileqq.app.CardHandler
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookReplace
import cn.hxy.happy.utils.hook.invokeOriginal
import cn.hxy.happy.utils.reflect.findMethod
import java.lang.reflect.Method

@HookItemAnnotation(
    "一键点赞",
    "一次性点赞至每天上限次数",
    HookCategory.SOCIAL
)
object OneClickLike : BaseSwitchHookItem() {

    private lateinit var sendZan: Method

    override fun onInit(): Boolean {
        sendZan = CardHandler::class.java
            .findMethod {
                returnType = void
                paramTypes(long, long, byteArr, int, int, int)
            }
        return super.onInit()
    }

    override fun onHook() {

        sendZan.hookReplace(this) { param ->

            param.args[4] = 10
            repeat(5) { param.invokeOriginal() }

            null
        }
    }
}