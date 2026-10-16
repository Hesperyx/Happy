package cn.hxy.happy.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.hxy.happy.hook.MainHook
import cn.hxy.happy.hook.chat.RepeatMsg
import cn.hxy.happy.ui.components.dialogs.ConfirmDialog
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.ui.pages.settings.SettingsScreen
import cn.hxy.happy.ui.viewmodel.SettingViewModel
import cn.hxy.happy.utils.qq.AppRestartUtils
import cn.hxy.happy.utils.qq.QQCurrentEnv
import cn.hxy.happy.utils.qq.Toasts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class SettingActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        MainHook.initAllConfigUI()

        setContent {
            val vm: SettingViewModel = viewModel()
            val appMetrics = applicationContext.resources.displayMetrics
            val stableDensity = Density(
                density = appMetrics.density,
                fontScale = appMetrics.scaledDensity / appMetrics.density
            )

            CompositionLocalProvider(LocalDensity provides stableDensity) {
                HappyTheme(isDarkTheme) {
                    SettingsScreen(
                        categories = vm.categories,
                        searchResults = vm.searchResults,
                        searchQuery = vm.searchQuery,
                        onQueryChange = { vm.searchQuery = it },
                        isSearchActive = vm.isSearchActive,
                        onSearchActiveChange = { vm.isSearchActive = it },
                        versionInfo = vm.buildVersionInfo(),
                        themeMode = themeMode,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = ::toggleTheme,
                        onImportConfig = ::startImportConfig,
                        onExportConfig = ::startExportConfig,
                        onFunctionToggle = vm::toggleFunction,
                        onFunctionClick = vm::handleFunctionClick,
                        activeConfigKey = vm.activeConfigKey,
                        onDismissDialog = vm::dismissDialog,
                        isNeedRestart = vm.isRestartRequired,
                        onRestartStateChange = { vm.showRestartDialog = true },
                        onBackClick = ::finish
                    )

                    ConfirmDialog(
                        visible = vm.showRestartDialog,
                        title = "保存并重启",
                        message = "检测到需要重启生效的配置已修改，是否立即重启宿主？",
                        confirmText = "立即重启",
                        dismissText = "稍后重启",
                        onDismiss = ::finish,
                        onConfirm = ::confirmRestart
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        MainHook.processDataForCurrent("save")
        super.onDestroy()
    }

    private fun startExportConfig() {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val fileName = "Happy_Backup_${QQCurrentEnv.currentUin}_$dateStr.zip"
        
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        startActivityForResult(intent, REQUEST_CODE_EXPORT)
    }

    private fun startImportConfig() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val uri = data.data ?: return

        when (requestCode) {
            REQUEST_CODE_EXPORT -> performExport(uri)
            REQUEST_CODE_IMPORT -> performImport(uri)
            REQUEST_CODE_IMAGE -> handleImagePick(uri)
        }
    }

    private fun handleImagePick(uri: Uri) {
        runCatching {
            RepeatMsg.bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            Toasts.qqToast(2, "加一图标导入成功")
        }.onFailure { Toasts.qqToast(1, "加一图标导入失败") }
    }

    private fun performExport(uri: Uri) {
        val vm = ViewModelProvider(this)[SettingViewModel::class.java]
        vm.performExport(this, uri)
    }

    private fun performImport(uri: Uri) {
        val vm = ViewModelProvider(this)[SettingViewModel::class.java]
        vm.performImport(this, uri)
    }

    private fun confirmRestart() {
        AppRestartUtils.restartApp(this)
    }

    companion object {
        private const val REQUEST_CODE_EXPORT = 1001
        private const val REQUEST_CODE_IMPORT = 1002
        const val REQUEST_CODE_IMAGE = 1003
    }
}
