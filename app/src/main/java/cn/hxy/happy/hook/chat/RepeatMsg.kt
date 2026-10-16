package cn.hxy.happy.hook.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import androidx.compose.runtime.Composable
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mobileqq.aio.msglist.holder.component.msgfollow.AIOMsgFollowComponent
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import cn.hxy.happy.R
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.RepeatConfig
import cn.hxy.happy.hook.api.MenuClickListener
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.plugin.bean.MsgData
import cn.hxy.happy.ui.pages.configs.RepeatMsgPage
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.io.FileUtils
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.qq.Toasts
import cn.hxy.happy.utils.reflect.callMethod
import cn.hxy.happy.utils.reflect.findMethod
import cn.hxy.happy.utils.reflect.getObjectByType
import cn.hxy.happy.utils.reflect.toClass
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method

@HookItemAnnotation(
    "消息复读",
    "支持快捷图标或长按菜单复读，点击配置具体方式及自定义图标",
    HookCategory.CHAT
)
object RepeatMsg : BaseClickableHookItem<RepeatConfig>(RepeatConfig.serializer()),
    MenuClickListener {

    override val defaultConfig: RepeatConfig = RepeatConfig()

    override val menuKey: String
        get() = if (config.mode == 1) "[Happy],RepeatMsg,复读,," else ""

    private lateinit var handleIntent: Method
    private var lastClickTime = 0L
    var bitmap: Bitmap? = null

    override fun onInit(): Boolean {
        handleIntent = AIOMsgFollowComponent::class.java.findMethod {
            returnType = void
            paramTypes(int, AIOMsgItem::class.java.superclass, list)
        }
        return super.onInit()
    }

    override fun onHook() {
        handleIntent.hookAfter(this) { param ->
            val lazy = param.thisObject.getObjectByType("kotlin.Lazy".toClass)
            val repeatView = lazy.callMethod("getValue") as ImageView

            if (config.mode != 0) {
                repeatView.visibility = View.GONE
                return@hookAfter
            }

            val aioMsgItem = param.args[1] as AIOMsgItem
            if (repeatView.context.javaClass.name.contains("MultiForwardActivity")) return@hookAfter

            repeatView.apply {
                visibility = View.VISIBLE
                if (bitmap != null) {
                    setImageBitmap(bitmap)
                } else {
                    setImageResource(R.drawable.ic_action_repeat)
                }
                setOnClickListener {
                    if (!config.doubleClick || isDoubleClick()) {
                        performRepeat(aioMsgItem.msgRecord)
                    }
                }
            }
        }
    }

    override fun onClick(msgData: MsgData) = performRepeat(msgData.data)

    private fun performRepeat(msgRecord: MsgRecord) {
        val msgService = QQCurrentEnv.kernelMsgService
        if (msgService == null) {
            Toasts.toast("获取消息服务失败")
            return
        }
        if (isNeedForward(msgRecord)) forwardSend(msgRecord, msgService)
        else directSend(msgRecord, msgService)

    }

    private fun isNeedForward(msgRecord: MsgRecord): Boolean = when (msgRecord.msgType) {
        in listOf(3, 7) -> true
        2 if msgRecord.elements.mapNotNull { it.textElement }.all { it.atType != 2 } -> true
        else -> false
    }

    private fun forwardSend(msgRecord: MsgRecord, msgService: IKernelMsgService) {
        val contact = Contact(
            msgRecord.chatType,
            msgRecord.peerUid,
            msgRecord.guildId
        )
        msgService.forwardMsg(
            arrayListOf(msgRecord.msgId),
            contact,
            arrayListOf(contact),
            msgRecord.msgAttrs,
            null
        )
    }

    private fun directSend(msgRecord: MsgRecord, msgService: IKernelMsgService) {

        val msgId = msgService.generateMsgUniqueId(
            msgRecord.chatType,
            System.currentTimeMillis()
        )

        msgService.sendMsg(
            msgId,
            Contact(
                msgRecord.chatType,
                msgRecord.peerUid,
                msgRecord.guildId
            ),
            msgRecord.elements,
            msgRecord.msgAttrs,
            null
        )

    }

    private fun isDoubleClick(): Boolean {
        val now = System.currentTimeMillis()
        val time = now - lastClickTime
        lastClickTime = now
        return time < 500
    }

    override fun initData() {
        val file = File("${QQCurrentEnv.currentDir}data/repeat")
        if (file.exists()) {
            bitmap = BitmapFactory.decodeFile(file.absolutePath)
        }
        super.initData()
    }

    override fun saveData() {
        val file = File("${QQCurrentEnv.currentDir}data/repeat")
        if (FileUtils.ensureFile(file)) {
            FileOutputStream(file).use {
                bitmap?.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        super.saveData()
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        RepeatMsgPage(
            currentConfig = config,
            bitmap = bitmap,
            onSave = ::updateConfig,
            onDismiss = onDismiss
        )
    }
}
