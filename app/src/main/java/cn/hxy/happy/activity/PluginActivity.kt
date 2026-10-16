package cn.hxy.happy.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.hxy.happy.ui.components.dialogs.ConfirmDialog
import cn.hxy.happy.ui.core.theme.HappyTheme
import cn.hxy.happy.ui.pages.plugin.PluginScreen
import cn.hxy.happy.ui.viewmodel.PluginViewModel

@Suppress("DEPRECATION")
class PluginActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: PluginViewModel = viewModel()
            val appMetrics = applicationContext.resources.displayMetrics
            val stableDensity = Density(
                density = appMetrics.density,
                fontScale = appMetrics.scaledDensity / appMetrics.density
            )

            CompositionLocalProvider(LocalDensity provides stableDensity) {
                HappyTheme(isDarkTheme) {
                    PluginScreen(
                        localPlugins = vm.filteredLocalPlugins,
                        isLocalRefreshing = vm.isLocalRefreshing,
                        themeMode = themeMode,
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = ::toggleTheme,
                        onBackClick = ::finish,
                        onCreatePlugin = vm::showCreatePluginDialog,
                        onRefreshLocal = vm::reloadLocalPlugins,
                        onPluginRunToggle = vm::togglePluginRun,
                        onPluginAutoLoadToggle = vm::togglePluginAutoLoad,
                        onPluginDelete = vm::showDeleteConfirm,
                        onPluginReload = vm::reloadPlugin,
                        onPickIcon = ::openPickIcon,
                        showCreateDialog = vm.showCreateDialog,
                        onDismissCreateDialog = vm::dismissCreatePluginDialog,
                        onConfirmCreatePlugin = vm::createPlugin,
                        showSuccessDialog = vm.showSuccessDialog,
                        createdPluginPath = vm.createdPluginPath,
                        onDismissSuccessDialog = vm::dismissSuccessDialog,
                        isSearchActive = vm.isSearchActive,
                        onSearchActiveChange = { vm.isSearchActive = it },
                        searchQuery = vm.searchQuery,
                        onQueryChange = { vm.searchQuery = it },
                        viewModel = vm
                    )

                    ConfirmDialog(
                        visible = vm.showDeleteDialog,
                        title = "删除脚本",
                        message = "确定要删除此脚本吗？",
                        confirmText = "确定",
                        dismissText = "取消",
                        onDismiss = vm::dismissDeleteDialog,
                        onConfirm = vm::confirmDelete
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val uri = data.data ?: return

        if (requestCode == REQUEST_CODE_PICK_ICON) {
            val vm = ViewModelProvider(this)[PluginViewModel::class.java]
            vm.handleIconSelection(this, uri)
        }
    }

    private fun openPickIcon() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_ICON)
    }

    companion object {
        const val REQUEST_CODE_PICK_ICON = 1004
    }
}
