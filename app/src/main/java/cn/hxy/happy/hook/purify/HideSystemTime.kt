package cn.hxy.happy.hook.purify

import android.util.SparseArray
import androidx.core.util.forEach
import com.tencent.mobileqq.aio.msglist.holder.component.timestamp.AIOTimestampComponent
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.toClass

@HookItemAnnotation(
    "隐藏系统时间",
    "隐藏聊天界面中居中显示的系统时间",
    HookCategory.PURIFY
)
object HideSystemTime : BaseSwitchHookItem() {

    private const val TARGET_CLASS = $$"com.tencent.mobileqq.aio.msglist.holder.AIOItemComponentProvider$mComponentList$2"

    override fun onHook() {
        TARGET_CLASS.toClass
            .findMethod {
                returnType = SparseArray::class.java
                name = "invoke"
            }.hookAfter(this) { param ->
                val array = param.result as SparseArray<*>
                array.forEach { k, v ->
                    if (v is AIOTimestampComponent) {
                        array.remove(k)
                        return@hookAfter
                    }
                }
            }
    }
    
}