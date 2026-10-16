package cn.hxy.happy.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import cn.hxy.happy.BuildConfig
import cn.hxy.happy.annotation.HookCategory
import cn.hxy.happy.hook.MainHook
import cn.hxy.happy.hook.base.BaseClickableHookItem
import cn.hxy.happy.ui.pages.settings.CategoryData
import cn.hxy.happy.ui.pages.settings.FunctionData
import cn.hxy.happy.utils.io.BackupManager
import cn.hxy.happy.utils.qq.HostInfo
import cn.hxy.happy.utils.qq.Toasts

class SettingViewModel : ViewModel() {

    var categories by mutableStateOf<List<CategoryData>>(emptyList())
        private set

    var activeConfigKey by mutableStateOf<String?>(null)
        private set

    var isSearchActive by mutableStateOf(false)

    var searchQuery by mutableStateOf("")

    val searchResults by derivedStateOf {
        if (searchQuery.isEmpty()) {
            emptyList()
        } else {
            categories.flatMap { category ->
                category.items
                    .filter { item ->
                        item.name.contains(searchQuery, ignoreCase = true) ||
                                item.description.contains(searchQuery, ignoreCase = true)
                    }
                    .map { it to category.name }
            }
        }
    }


    private val allHookItems = MainHook.switchHookItemList

    private val initialRestart =
        allHookItems.filter { it.isNeedRestart }.associate { it.name to it.isEnable }

    var showRestartDialog by mutableStateOf(false)

    var isRestartRequired by mutableStateOf(false)
        private set

    init {
        refreshCategories()
    }

    fun buildVersionInfo(): String {
        return "模块版本：V${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) ${HostInfo.hostName}版本：V${HostInfo.versionName}(${HostInfo.versionCode})"
    }

    fun refreshCategories() {
        categories = HookCategory.ORDER.mapNotNull { category ->
            val itemsInCategory = allHookItems.filter { it.category == category }
            if (itemsInCategory.isNotEmpty()) {
                CategoryData(category, itemsInCategory.map { hookItem ->
                    val isClickable = hookItem is BaseClickableHookItem<*>
                    FunctionData(
                        hookItem.name,
                        hookItem.tag,
                        hookItem.desc,
                        hookItem.isEnable,
                        hookItem.isAvailable,
                        isClickable,
                        if (isClickable) hookItem.name else null
                    )
                })
            } else null
        }
        isRestartRequired = allHookItems.filter { it.isNeedRestart }.any { it.isEnable != initialRestart[it.name] }
    }

    fun toggleFunction(id: String, enabled: Boolean) {
        allHookItems.find { it.name == id }?.let { item ->
            item.isEnable = enabled
            refreshCategories()
        }
    }

    fun handleFunctionClick(id: String) {
        allHookItems.find { it.name == id }?.let { item ->
            if (item is BaseClickableHookItem<*>) {
                item.initData()
                activeConfigKey = item.name
            }
        }
    }

    fun dismissDialog() {
        activeConfigKey = null
    }

    fun performExport(context: Context, uri: Uri) {
        viewModelScope.launch {
            BackupManager.performExport(context, uri).fold(
                onSuccess = { Toasts.qqToast(2, "备份导出成功") },
                onFailure = { Toasts.qqToast(1, "导出失败: ${it.message}") }
            )
        }
    }

    fun performImport(context: Context, uri: Uri) {
        viewModelScope.launch {
            BackupManager.performImport(context, uri).fold(
                onSuccess = {
                    refreshCategories()
                    Toasts.qqToast(2, "配置导入成功")
                },
                onFailure = { Toasts.qqToast(1, "导入失败: ${it.message}") }
            )
        }
    }
}
