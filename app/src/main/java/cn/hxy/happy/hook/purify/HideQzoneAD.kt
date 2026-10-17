package cn.hxy.happy.hook.purify

import android.content.Context
import android.view.View
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.hook.hookBefore
import cn.hxy.happy.utils.hook.returnConstant
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.findMethodOrNull
import cn.hxy.happy.utils.reflect.setObject

@HookItemAnnotation(
    "隐藏空间广告",
    "隐藏好友动态里那无时无刻不在显示的广告。",
    HookCategory.PURIFY,
    "All"
)
object HideQzoneAD : BaseSwitchHookItem() {

    override fun onInit() = HostInfo.isQQ || HostInfo.isTIM

    override fun onHook() {
        if (HostInfo.isQQ) hookQQ()
        if (HostInfo.isTIM) hookTIM()
    }

    private fun hookQQ() {
        AD_ITEM_VIEW_CLASS_NAMES.forEach { className ->
            val clazz = className.clazz ?: return@forEach
            val ctor = runCatching {
                clazz.getDeclaredConstructor(Context::class.java)
            }.getOrNull() ?: return@forEach
            ctor.hookAfter(this) { param ->
                val view = param.thisObject as? View ?: return@hookAfter
                view.visibility = View.GONE
                view.layoutParams?.let {
                    it.height = 0
                    it.width = 0
                }
            }
        }

        // 广告数据置空：AlumBasicData 是宿主类，主模块不能 import，用反射按字段名写入
        val controller = ALBUM_RECOMMEND_ADV_CONTROLLER.clazz ?: return
        val method = controller.findMethodOrNull {
            name = "initAndRenderData"
            paramCount = 1
        } ?: return
        method.hookBefore(this) { param ->
            val data = param.args[0] ?: return@hookBefore
            AD_URL_FIELDS.forEach { fieldName ->
                runCatching {
                    data.setObject(fieldName, "")
                }
            }
        }
    }

    private fun hookTIM() {
        val clazz = QZONE_AD_FEED_DATA_EXT.clazz ?: return
        val method = clazz.findMethodOrNull {
            name = "isShowingRecommendAd"
            paramCount = 1
        } ?: return
        method.returnConstant(this, true)
    }

    private const val ALBUM_RECOMMEND_ADV_CONTROLLER =
        "com.tencent.mobileqq.vas.adv.qzone.logic.AlbumRecommendAdvController"

    // 注意：isShowingRecommendAd 的入参是 com.qzone.proxy.feedcomponent.model.BusinessFeedData，
    // 该宿主类在主模块不可见，所以只按参数个数匹配，不使用 paramTypes。
    private const val QZONE_AD_FEED_DATA_EXT =
        "com.qzone.proxy.feedcomponent.model.gdt.QZoneAdFeedDataExtKt"

    private val AD_ITEM_VIEW_CLASS_NAMES = listOf(
        // super class
        "com.qzone.reborn.feedpro.itemview.ad.QZoneAdBaseMediaFeedProItemView",
        "com.qzone.reborn.feedx.itemview.ad.QZoneAdBaseFeedItemView",

        // FeedPro
        "com.qzone.reborn.feedpro.itemview.QzoneFeedProGeneralBigCardItemView",
        "com.qzone.reborn.feedpro.itemview.QZoneAdFeedProForwardMixPicVideoItemView",
        "com.qzone.reborn.feedpro.widget.comment.QZoneFeedProDetailBottomAdBlockView", // 说说详情页广告

        // FeedX
        "com.qzone.reborn.feedx.itemview.ad.QZoneAdRewardFeedItemView"
    )

    private val AD_URL_FIELDS = listOf(
        "advimageUrl",
        "videoUrl",
        "videoReportUrl",
        "negativeFeedbackUrl",
        "clickUrl",
        "advLogoUrl"
    )
}
