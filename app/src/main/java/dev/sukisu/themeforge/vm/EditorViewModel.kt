package dev.sukisu.themeforge.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.sukisu.themeforge.core.ThemeDocument
import dev.sukisu.themeforge.core.ThemePackager
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

    fun newTheme() {
        doc = ThemeDocument.blank()
        projectName = "MyTheme"
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
    }

    fun buildPackage(): ByteArray? = doc?.let { ThemePackager.build(it) }

    fun exportName(): String {
        val safe = projectName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "theme" }
        return "$safe.sukisu"
    }

    fun applyJson(text: String): Boolean {
        val current = doc ?: return false
        return runCatching { current.replaceRoot(JSONObject(text)) }
            .onSuccess { message = "JSON 已应用" }
            .onFailure { error = it.message ?: "JSON 无效" }
            .isSuccess
    }

    fun setError(text: String) {
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
