package cn.hxy.happy.hook.file

import com.tencent.mobileqq.aio.panel.photo.PhotoPanelVB
import com.tencent.mvi.base.mvi.MviUIState
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.reflect.findMethod
import java.lang.reflect.Method

@HookItemAnnotation(
    "自动勾选原图",
    "发送图片时自动勾选原图（半屏相册）",
    HookCategory.FILE
)
object AutoSendOriginalPic : BaseSwitchHookItem() {

    private lateinit var handleUIState: Method

    private lateinit var setChecked: Method

    override fun onInit(): Boolean {
        handleUIState = PhotoPanelVB::class.java
            .findMethod {
                returnType = void
                paramTypes(MviUIState::class.java)
            }
        setChecked = PhotoPanelVB::class.java
            .findMethod {
                visibility = public
                returnType = void
                paramTypes(boolean)
            }
        return super.onInit()
    }

    override fun onHook() {
        handleUIState.hookAfter(this) {
            setChecked.invoke(it.thisObject, true)
        }
    }

}