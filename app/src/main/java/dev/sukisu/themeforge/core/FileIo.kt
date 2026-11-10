package dev.sukisu.themeforge.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

object FileIo {

    fun read(context: Context, uri: Uri, maxBytes: Long): ByteArray {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: throw IllegalArgumentException("无法打开文件")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                require(total <= maxBytes) { "文件超过大小上限" }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }

    fun write(context: Context, uri: Uri, bytes: ByteArray) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalArgumentException("无法写入文件")
        output.use { it.write(bytes) }
    }

    fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
        }.getOrNull()
        return name ?: "theme.sukisu"
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
