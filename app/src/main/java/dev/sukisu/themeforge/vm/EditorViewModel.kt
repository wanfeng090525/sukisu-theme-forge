package dev.sukisu.themeforge.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.sukisu.themeforge.core.ThemeDocument
import dev.sukisu.themeforge.core.ThemePackager
import dev.sukisu.themeforge.core.ThemeSchema
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
                message = "已导入 $fileName"
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

    fun buildPackage(): ByteArray? = doc?.let { ThemePackager.build(it) }

    fun exportName(): String {
        val safe = projectName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "theme" }
        return "$safe.sukisu"
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
