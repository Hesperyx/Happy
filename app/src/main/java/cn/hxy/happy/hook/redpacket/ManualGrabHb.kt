package cn.hxy.happy.hook.redpacket

import mqq.app.MSFServlet
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.conf.RedPacketConfig
import cn.hxy.happy.hook.api.MenuClickListener
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.plugin.bean.MsgData
import cn.hxy.happy.plugin.bean.RedPacketContext
import cn.hxy.happy.utils.dexkit.DexKitTask

/**
 * 手动抢红包：为长按消息菜单添加「打开红包」按钮。
 *
 * 同时负责用 DexKit 定位钱包 SSO Servlet（旧 API SSO 抢红包需要），
 * 结果缓存在 [servletClass] 供 [RedPacketHelper] 使用。
 */
@HookItemAnnotation(
    "手动打开红包",
    "为长按菜单添加打开红包按钮",
    HookCategory.RED_PACKET,
    process = "All"
)
object ManualGrabHb : BaseSwitchHookItem(), MenuClickListener, DexKitTask {

    private const val MENU_KEY = "[Happy],ManualGrab,打开红包,,10"

    @Volatile
    var servletClass: Class<*>? = null
        private set

    override val menuKey: String = MENU_KEY

    override fun onInit(): Boolean {
        servletClass = runCatching { requireClass("servlet") }.getOrNull()
        return super.onInit()
    }

    override fun onClick(msgData: MsgData) {
        val msgRecord = msgData.data
        if (msgRecord.msgType == 10 && msgRecord.chatType == 2) {
            val walletElement = msgRecord.elements?.firstOrNull()?.walletElement ?: return
            RedPacketHelper.startGrab(
                RedPacketContext(
                    msgData = msgData,
                    listId = walletElement.billNo,
                    authKey = walletElement.authkey,
                    channel = walletElement.redChannel,
                    title = walletElement.receiver.title,
                    isAuto = false,
                    config = RedPacketConfig(),
                )
            )
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "servlet" to FindClass().apply {
            matcher {
                superClass(MSFServlet::class.java.name)
                usingStrings(
                    "QWalletHttp-QWalletPbServlet",
                    "qwallet_pb_handle_trpc_error"
                )
            }
        }
    )
}
