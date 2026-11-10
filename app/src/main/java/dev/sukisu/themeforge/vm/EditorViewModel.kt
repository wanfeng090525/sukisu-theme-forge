package dev.sukisu.themeforge.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.sukisu.themeforge.core.ExportOptions
import dev.sukisu.themeforge.core.ThemeDocument
import dev.sukisu.themeforge.core.ThemePackager
import dev.sukisu.themeforge.core.ThemeSchema
import dev.sukisu.themeforge.core.ThemeSlots
import org.json.JSONObject

class EditorViewModel : ViewModel() {

    var doc by mutableStateOf<ThemeDocument?>(null)
        private set

    var projectName by mutableStateOf("MyTheme")
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var jsonError by mutableStateOf<String?>(null)
        private set

    fun newTheme() {
        doc = ThemeDocument.blank()
        projectName = "MyTheme"
        jsonError = null
        message = "已创建空白自适应主题"
    }

    fun openPackage(data: ByteArray, fileName: String) {
        runCatching { ThemePackager.parse(data) }
            .onSuccess {
                doc = it
                projectName = fileName.substringBeforeLast('.').ifBlank { "MyTheme" }
                message = it.importNote ?: "已导入 $fileName"
            }
            .onFailure { error = it.message ?: "解析主题包失败" }
    }

    fun close() {
        doc = null
        jsonError = null
    }

    fun renameProject(name: String) {
        projectName = name
    }

    fun buildPackage(options: ExportOptions = ExportOptions()): ByteArray? =
        doc?.let { ThemePackager.build(it, options) }

    fun exportName(): String {
        val safe = projectName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "theme" }
        return if (safe.endsWith(".sukisu")) safe else "$safe.sukisu"
    }

    /** Human readable consequences of the current export settings. */
    fun exportWarnings(options: ExportOptions): List<String> {
        val current = doc ?: return emptyList()
        val warnings = ArrayList<String>()
        if (options.singleVariant) {
            warnings += "v2 单套格式：只保留一套图片与配置（优先浅色），明暗不再区分"
            if (current.hasAppearance()) warnings += "appearance 覆盖需要 v4，导出时会被丢弃"
            if (current.images.keys.any { ThemeSlots.isBottomBarIcon(it) }) {
                warnings += "底栏图标需要 v5，导出时会被丢弃"
            }
            if (listOf(false, true).any { current.string(it, "homeLayout", "classic") == "immersive" }) {
                warnings += "沉浸式布局需要 v5，导出时自动改为经典布局"
            }
        } else {
            val required = current.requiredVersion()
            if (options.targetVersion in 3..4 && required > options.targetVersion) {
                warnings += "内容需要 v$required，将自动提升版本"
            }
            if (!options.includeBackground) warnings += "不包含壁纸，导出后 hasBackground = false"
            if (!options.includeCardBackground) warnings += "不包含首页卡片图"
            if (!options.includeStickers) warnings += "不包含首页贴纸"
            if (!options.includeBottomBarIcons) warnings += "不包含底栏图标"
        }
        return warnings
    }

    fun loadFullTemplate(): Boolean {
        val current = doc ?: return false
        jsonError = null
        return runCatching { current.replaceRoot(ThemeSchema.fullTemplate()) }
            .onSuccess { message = "已载入完整字段模板" }
            .onFailure { jsonError = it.message ?: "模板生成失败" }
            .isSuccess
    }

    fun applyJson(text: String): Boolean {
        val current = doc ?: return false
        jsonError = null
        return runCatching { current.replaceRoot(JSONObject(text)) }
            .onSuccess { message = "JSON 已应用" }
            .onFailure { jsonError = it.message ?: "JSON 无效" }
            .isSuccess
    }

    fun formatJson(text: String): String? {
        jsonError = null
        return runCatching { JSONObject(text).toString(2) }
            .onFailure { jsonError = it.message ?: "JSON 无效" }
            .getOrNull()
    }

    fun reportError(text: String) {
        error = text
    }

    fun notify(text: String) {
        message = text
    }

    fun clearMessage() {
        message = null
    }

    fun dismissError() {
        error = null
    }
}
