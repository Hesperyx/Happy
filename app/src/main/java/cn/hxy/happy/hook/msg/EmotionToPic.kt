package cn.hxy.happy.hook.msg

import com.tencent.qqnt.aio.adapter.api.impl.RichMediaBrowserApiImpl
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.returnConstant
import cn.hxy.happy.utils.reflect.findMethod

@HookItemAnnotation(
    "以图片方式打开表情",
    "将表情包作为普通图片打开，方便下载",
    HookCategory.MSG
)
object EmotionToPic : BaseSwitchHookItem() {

    override fun onHook() {
        RichMediaBrowserApiImpl::class.java
            .findMethod {
                name = "checkIsFavPicAndShowPreview"
            }
            .returnConstant(this, false)
    }
}