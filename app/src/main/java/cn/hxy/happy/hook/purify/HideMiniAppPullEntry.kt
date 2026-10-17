package cn.hxy.happy.hook.purify

import android.view.View
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.hook.hookAfter
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.callMethod
import cn.hxy.happy.utils.reflect.clazz
import cn.hxy.happy.utils.reflect.setObject
import java.lang.reflect.Modifier

@HookItemAnnotation(
    "隐藏下拉小程序",
    "生成屏蔽下拉小程序解决方案。",
    HookCategory.PURIFY,
    ""
)
object HideMiniAppPullEntry : BaseSwitchHookItem() {

    override fun onInit() = HostInfo.isQQ

    override fun onHook() {
        val rule = currentHookRule()
        if (rule == null) {
            LogUtils.d("隐藏下拉小程序: 当前 QQ 版本 ${HostInfo.versionCode} 未配置 Hook 规则")
            return
        }

        when (rule.hookPoint) {
            HookPoint.OLD_STYLE_HEADER -> hookOldStyleHeader(rule)
            HookPoint.MINI_APP_REFRESH_PART -> hookMiniAppRefreshPart(rule)
        }
    }

    private fun hookOldStyleHeader(rule: HookRule) {
        hookMiniAppRefreshPartAlphaCallbacks()
        hookHeaderBaseVisibilityCallbacks()
        if (!hookMiniOldStyleHeaderNew(rule)) {
            hookMiniOldStyleHeader(rule)
        }
    }

    private fun hookMiniOldStyleHeaderNew(rule: HookRule): Boolean {
        val clazz = "com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew".clazz ?: return false
        hookHeaderClass(clazz, rule)
        return true
    }

    private fun hookMiniOldStyleHeader(rule: HookRule) {
        val clazz = "com.tencent.qqnt.chats.view.MiniOldStyleHeader".clazz ?: return
        hookHeaderClass(clazz, rule)
    }

    private fun hookMiniAppRefreshPart(rule: HookRule) {
        hookMiniAppRefreshPartAlphaCallbacks()
        hookHeaderBaseVisibilityCallbacks()
        MINI_APP_HEADER_CLASS_NAMES
            .mapNotNull { it.clazz }
            .forEach { hookHeaderClass(it, rule) }

        val refreshPart = MINI_APP_REFRESH_PART.clazz
        if (refreshPart == null) {
            LogUtils.d("隐藏下拉小程序: MiniAppRefreshPart 未找到，回退旧版 Header Hook")
            val fallbackRule = fallbackOldStyleRule()
            if (fallbackRule != null) {
                hookOldStyleHeader(fallbackRule)
            } else {
                LogUtils.d(
                    "隐藏下拉小程序: 当前 QQ 版本 ${HostInfo.versionCode} 未配置旧版 Header 回退规则"
                )
            }
            return
        }

        refreshPart.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType.isTwoLevelHeaderClass()
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter(this) { param ->
                    val header = param.result ?: return@hookAfter
                    disableTwoLevelHeader(header, rule)
                    replaceMiniAppContainerWithRefreshHeader(header)
                }
            }
    }

    private fun hookMiniAppRefreshPartAlphaCallbacks() {
        val refreshPart = MINI_APP_REFRESH_PART.clazz ?: return
        if (!hookedMiniAppRefreshPartAlphaCallbackClasses.add(refreshPart.name)) return
        refreshPart.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == java.lang.Void.TYPE &&
                    method.parameterTypes.size == 4 &&
                    View::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == java.lang.Float.TYPE &&
                    method.parameterTypes[2] == java.lang.Float.TYPE &&
                    method.parameterTypes[3] == java.lang.Float.TYPE
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter(this) { param ->
                    val view = param.args.firstOrNull() as? View ?: return@hookAfter
                    keepMiniAppPullAlphaViewVisible(view)
                    view.post { keepMiniAppPullAlphaViewVisible(view) }
                }
            }
    }

    private fun hookHeaderClass(clazz: Class<*>, rule: HookRule) {
        hookHeaderConstructors(clazz, rule)
        hookHeaderVisibilityCallbacks(clazz)
    }

    private fun hookHeaderConstructors(clazz: Class<*>, rule: HookRule) {
        clazz.declaredConstructors.forEach { ctor ->
            ctor.isAccessible = true
            ctor.hookAfter(this) { param ->
                disableTwoLevelHeader(param.thisObject, rule)
                replaceMiniAppContainerWithRefreshHeader(param.thisObject)
            }
        }
    }

    private fun hookHeaderBaseVisibilityCallbacks() {
        MINI_APP_HEADER_BASE_CLASS_NAMES
            .mapNotNull { it.clazz }
            .forEach { hookHeaderVisibilityCallbacks(it) }
    }

    private fun hookHeaderVisibilityCallbacks(clazz: Class<*>) {
        if (!hookedHeaderVisibilityCallbackClasses.add(clazz.name)) return
        clazz.declaredMethods
            .filter { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == java.lang.Void.TYPE &&
                    method.isHeaderVisibilityCallback()
            }
            .forEach { method ->
                method.isAccessible = true
                method.hookAfter(this) { param ->
                    val header = param.thisObject
                    if (header.isMiniAppHeaderInstance()) {
                        replaceMiniAppContainerWithRefreshHeader(header)
                        (header as? View)?.post {
                            replaceMiniAppContainerWithRefreshHeader(header)
                        }
                    }
                }
            }
    }

    private fun java.lang.reflect.Method.isHeaderVisibilityCallback(): Boolean {
        return isRefreshStateCallback() || isHeaderMovingCallback()
    }

    private fun java.lang.reflect.Method.isRefreshStateCallback(): Boolean {
        return parameterTypes.size == 3 &&
            parameterTypes[1].name == REFRESH_STATE_CLASS_NAME &&
            parameterTypes[2].name == REFRESH_STATE_CLASS_NAME
    }

    private fun java.lang.reflect.Method.isHeaderMovingCallback(): Boolean {
        return parameterTypes.isHeaderMovingCallbackTypes(0) ||
            parameterTypes.isHeaderMovingCallbackTypes(1)
    }

    private fun Array<Class<*>>.isHeaderMovingCallbackTypes(offset: Int): Boolean {
        return size == offset + 5 &&
            this[offset] == java.lang.Boolean.TYPE &&
            this[offset + 1] == java.lang.Float.TYPE &&
            this[offset + 2] == java.lang.Integer.TYPE &&
            this[offset + 3] == java.lang.Integer.TYPE &&
            this[offset + 4] == java.lang.Integer.TYPE
    }

    private fun disableTwoLevelHeader(header: Any, rule: HookRule) {
        val fieldName = rule.enableTwoLevelField ?: return
        val ownerClassName = rule.fieldOwnerClassName
        val candidate = if (ownerClassName != null) {
            collectClassHierarchy(header.javaClass).firstOrNull { it.name == ownerClassName }
        } else {
            header.javaClass.superclass?.superclass?.superclass
        }
        val targetClass = candidate ?: return
        runCatching {
            header.setObject(fieldName, false, inParent = targetClass)
        }
    }

    private fun replaceMiniAppContainerWithRefreshHeader(header: Any) {
        hideMiniAppContainer(header)
        showWrappedRefreshHeader(header)
    }

    private fun hideMiniAppContainer(header: Any) {
        getMiniAppContainer(header)
            ?.setViewVisibility(View.GONE)
    }

    private fun getMiniAppContainer(header: Any): Any? {
        return runCatching {
            header.callMethod("s")
        }.recoverCatching {
            header.callMethod("o")
        }.getOrNull()
    }

    private fun showWrappedRefreshHeader(header: Any) {
        val miniAppContainer = getMiniAppContainer(header) as? View
        collectClassHierarchy(header.javaClass)
            .firstOrNull { it.name == SMART_REFRESH_TWO_LEVEL_HEADER }
            ?.declaredFields
            ?.filter { field -> !Modifier.isStatic(field.modifiers) }
            ?.forEach { field ->
                val view = runCatching {
                    field.isAccessible = true
                    field.get(header)?.callMethod("getView") as? View
                }.getOrNull() ?: return@forEach
                if (view !== miniAppContainer) {
                    keepRefreshVisualViewVisible(view)
                }
            }
    }

    private fun keepRefreshVisualViewVisible(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1.0f
        view.background?.alpha = 255
    }

    private fun keepMiniAppPullAlphaViewVisible(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1.0f
    }

    private fun Any.setViewVisibility(visibility: Int) {
        val androidView = this as? View
        if (androidView != null) {
            androidView.visibility = visibility
        } else {
            runCatching { callMethod("setVisibility", visibility) }
        }
    }

    private fun Any.isMiniAppHeaderInstance(): Boolean {
        val classNames = collectClassHierarchy(javaClass).map { it.name }
        return MINI_APP_HEADER_CLASS_NAMES.any { it in classNames }
    }

    private fun currentHookRule(): HookRule? {
        return HOOK_RULES.firstOrNull { it.matchesCurrentVersion() }
    }

    private fun fallbackOldStyleRule(): HookRule? {
        return HOOK_RULES.firstOrNull { rule ->
            rule.hookPoint == HookPoint.OLD_STYLE_HEADER && rule.matchesCurrentVersion()
        }
    }

    private fun HookRule.matchesCurrentVersion(): Boolean {
        if (!HostInfo.isQQ) return false
        val versionCode = HostInfo.versionCode
        return (minVersion == null || versionCode >= minVersion) &&
            (maxVersionExclusive == null || versionCode < maxVersionExclusive)
    }

    private fun collectClassHierarchy(clazz: Class<*>): List<Class<*>> {
        val classes = ArrayList<Class<*>>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            classes.add(current)
            current = current.superclass
        }
        return classes
    }

    private fun Class<*>.isTwoLevelHeaderClass(): Boolean {
        return collectClassHierarchy(this)
            .any { it.name == SMART_REFRESH_TWO_LEVEL_HEADER }
    }

    private enum class HookPoint {
        OLD_STYLE_HEADER,
        MINI_APP_REFRESH_PART
    }

    private data class HookRule(
        val minVersion: Long?,
        val hookPoint: HookPoint,
        val enableTwoLevelField: String?,
        val maxVersionExclusive: Long? = null,
        val fieldOwnerClassName: String? = null
    )

    // 以下三个版本号取自 TCQT 的 QQVersion.kt：
    // QQ_9_1_30 = 8538 / QQ_9_1_70 = 9898 / QQ_9_3_0_BETA_36720 = 14328
    private const val QQ_9_1_30: Long = 8538
    private const val QQ_9_1_70: Long = 9898
    private const val QQ_9_3_0_BETA_36720: Long = 14328

    private const val MINI_APP_REFRESH_PART =
        "com.tencent.mobileqq.activity.home.chats.biz.MiniAppRefreshPart"
    private const val SMART_REFRESH_TWO_LEVEL_HEADER =
        "com.qqnt.widget.smartrefreshlayout.header.TwoLevelHeader"
    private const val REFRESH_STATE_CLASS_NAME =
        "com.qqnt.widget.smartrefreshlayout.layout.constant.RefreshState"

    // 版本适配维护区：按 [minVersion, maxVersionExclusive) 区间限制 Hook 点与字段名。
    // 二级刷新开关的布尔字段名要在 onMoving / 状态切换逻辑里找（进入 ReleaseToTwoLevel 的那个）。
    //
    // 不要 Hook/替换 Conversation 的小程序初始化方法：它同时绑定原生下拉刷新 listener，
    // 替换整段会导致刷新可见但无法真正触发或结束。
    private val HOOK_RULES = listOf(
        HookRule(
            minVersion = QQ_9_3_0_BETA_36720,
            hookPoint = HookPoint.MINI_APP_REFRESH_PART,
            enableTwoLevelField = "t",
            fieldOwnerClassName = SMART_REFRESH_TWO_LEVEL_HEADER
        ),
        HookRule(
            minVersion = QQ_9_1_70,
            maxVersionExclusive = QQ_9_3_0_BETA_36720,
            hookPoint = HookPoint.OLD_STYLE_HEADER,
            enableTwoLevelField = "I"
        ),
        HookRule(
            minVersion = QQ_9_1_30,
            maxVersionExclusive = QQ_9_1_70,
            hookPoint = HookPoint.OLD_STYLE_HEADER,
            enableTwoLevelField = "E"
        ),
        HookRule(
            minVersion = null,
            maxVersionExclusive = QQ_9_1_30,
            hookPoint = HookPoint.OLD_STYLE_HEADER,
            enableTwoLevelField = "D"
        )
    )

    private val MINI_APP_HEADER_CLASS_NAMES = listOf(
        "com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew",
        "com.tencent.qqnt.chats.view.MiniOldStyleHeader",
        "com.tencent.qqnt.chats.view.QQMiniProgramHeader",
        "com.tencent.qqnt.chats.view.MiniProgramHeader"
    )

    private val MINI_APP_HEADER_BASE_CLASS_NAMES = listOf(
        "com.tencent.qqnt.chats.view.QQChatListTwoLevelHeader",
        "com.tencent.qqnt.chats.view.ChatListTwoLevelHeader"
    )

    private val hookedHeaderVisibilityCallbackClasses = mutableSetOf<String>()
    private val hookedMiniAppRefreshPartAlphaCallbackClasses = mutableSetOf<String>()
}
