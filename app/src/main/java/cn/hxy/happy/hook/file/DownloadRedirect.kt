package cn.hxy.happy.hook.file

import android.os.Environment
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.annotation.HookItemAnnotation
import cn.hxy.happy.hook.base.BaseSwitchHookItem
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.reflect.findField
import cn.hxy.happy.utils.reflect.toClass

@HookItemAnnotation(
    "下载重定向",
    "重定向下载路径到Download/QQ(TIM)",
    HookCategory.FILE,
    "All"
)
object DownloadRedirect : BaseSwitchHookItem() {

    override val isNeedRestart: Boolean = true

    private val redirectPath =
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/${HostInfo.hostName}/"

    override fun onHook() {
        if (isEnable) {
            "com.tencent.mobileqq.app.AppConstants".toClass
                .findField {
                    name = "SDCARD_FILE_SAVE_PATH"
                }.set(null, redirectPath)
        }
    }

}