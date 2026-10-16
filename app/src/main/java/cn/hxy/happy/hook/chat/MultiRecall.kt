package cn.hxy.happy.hook.chat

import android.view.View
import android.widget.LinearLayout
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mvi.mvvm.BaseVM
import com.tencent.mvi.mvvm.framework.FrameworkVM
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import kotlinx.coroutines.delay
import cn.hxy.happy.R
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.common.ModuleScope
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.dexkit.DexKitTask
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.MsgTool
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.qq.Toasts
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.getObject
import cn.hxy.happy.utils.reflect.instance
import cn.hxy.happy.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

@HookItemAnnotation(
    "多选撤回",
    "为多选菜单中添加批量撤回功能",
    HookCategory.CHAT
)
object MultiRecall : BaseSwitchHookItem(), DexKitTask {

    private lateinit var makeView: Method
    private lateinit var createVM: Method
    private lateinit var getMsgList: Method
    private lateinit var getMContext: Method
    private lateinit var invoke: Method

    private var multiSelectBarVM: Any? = null

    override fun onInit(): Boolean {

        val className = if (HostInfo.isTIM) {
            "com.tencent.tim.aio.inputbar.TimMultiSelectBarVB"
        } else {
            "com.tencent.mobileqq.aio.input.multiselect.MultiSelectBarVB"
        }

        val multiSelectBarVBClass = className.toClass
        val operationLayoutClass = $$"$$className$mOperationLayout$2".toClass
        val multiSelectUtil = requireClass("MultiSelectUtil")

        getMsgList = multiSelectUtil.findMethod {
            returnType = list
            paramCount = 1
        }

        getMContext = FrameworkVM::class.java
            .findMethod {
                returnType = getMsgList.parameterTypes[0].superclass
                paramCount = 0
            }

        invoke = operationLayoutClass.findMethod {
            name = "invoke"
        }

        makeView = multiSelectBarVBClass.findMethod {
            returnType = view
            paramTypes(multiSelectBarVBClass, int, int, View.OnClickListener::class.java)
        }


        createVM = multiSelectBarVBClass.findMethod {
            returnType = BaseVM::class.java
            paramCount = 0
        }
        return super.onInit()
    }

    override fun onHook() {

        createVM.hookAfter(this) {
            multiSelectBarVM = it.result
        }

        invoke.hookAfter(this) { param ->
            val operationLayout = param.result as? LinearLayout ?: return@hookAfter

            val multiSelectBarVB = param.thisObject.getObject("this$0")

            val recallButton = makeView.invoke(
                null,
                multiSelectBarVB,
                R.drawable.ic_action_recall,
                R.drawable.ic_action_recall,
                View.OnClickListener {
                    performBatchRecall()
                }
            ) as View

            val index = (operationLayout.childCount - 2).coerceAtLeast(0)
            operationLayout.addView(recallButton, index)
        }
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun performBatchRecall() {

        try {
            val vm = multiSelectBarVM ?: return

            val multiSelectUtil = requireClass("MultiSelectUtil")

            val instance = multiSelectUtil.instance

            val mContext = getMContext.invoke(vm)

            val msgList = getMsgList.invoke(instance, mContext) as List<AIOMsgItem>

            val size = msgList.size

            ModuleScope.launchIO(name) {
                CopyOnWriteArrayList(msgList).forEach {
                    recallSingleItem(it)
                    if (size > 10) delay(300.milliseconds)
                }
            }
            Toasts.qqToast(2, "开始撤回 $size 条消息...")


        } catch (t: Throwable) {
            LogUtils.e(this, t)
            Toasts.qqToast(1, "批量撤回失败: ${t.message}")
        }

        QQCurrentEnv.activity?.onBackPressed()

    }

    private fun recallSingleItem(aioMsgItem: AIOMsgItem) {
        try {

            val msgRecord = aioMsgItem.msgRecord
            val contact = Contact(
                msgRecord.chatType,
                msgRecord.peerUid,
                msgRecord.guildId
            )
            MsgTool.recallMsg(contact, msgRecord.msgId)

        } catch (t: Throwable) {
            LogUtils.e(this, t)
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(

        "MultiSelectUtil" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.holder.component.multifoward")
            matcher {
                usingStrings("msgList")
            }
        }
    )
}