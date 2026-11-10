package dev.sukisu.themeforge.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ThemePackager {

    private const val MAX_PACKAGE = 128L * 1024 * 1024
    private const val MAX_ZIP = 512L * 1024 * 1024
    private const val MAX_JSON = 64L * 1024
    private const val MAX_IMAGE = 64L * 1024 * 1024

    fun parse(data: ByteArray): ThemeDocument {
        require(data.size <= MAX_PACKAGE) { "主题包超过 128 MB 上限" }
        val zipBytes = ThemeCrypto.decrypt(data)
        require(zipBytes.size <= MAX_ZIP) { "解压后超过 512 MB 上限" }

        var json: JSONObject? = null
        val images = mutableStateMapOf<String, ImageAsset>()
        var totalImages = 0L

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name
                when {
                    name == "theme.json" -> {
                        if (json != null) throw IllegalArgumentException("theme.json 重复")
                        val bytes = readEntry(zis, MAX_JSON)
                        json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
                            .getOrElse { throw IllegalArgumentException("theme.json 不是合法 JSON：${it.message}") }
                    }

                    name in ThemeSlots.all -> {
                        if (images.containsKey(name)) throw IllegalArgumentException("条目重复：$name")
                        val bytes = readEntry(zis, MAX_IMAGE)
                        totalImages += bytes.size
                        require(totalImages <= MAX_ZIP) { "图片总大小超过上限" }
                        val size = ImageInfo.decodeSize(bytes)
                            ?: throw IllegalArgumentException("图片无法解码：$name")
                        images[name] = ImageAsset(bytes, name, size.first, size.second)
                    }

                    else -> throw IllegalArgumentException("未知主题包条目：$name")
                }
            }
        }

        val root = json ?: throw IllegalArgumentException("主题包缺少 theme.json")
        val version = root.optInt("version", 0)
        require(version in ThemeSchema.MIN_VERSION..ThemeSchema.MAX_VERSION) {
            "仅支持 v3 - v5 主题包（当前 v$version）"
        }
        require(root.optBoolean("adaptive", false)) { "仅支持 adaptive 自适应主题包" }
        require(root.optJSONObject("light") != null && root.optJSONObject("dark") != null) {
            "自适应主题包必须同时包含 light 和 dark"
        }
        val appearance = root.opt("appearance")
        if (appearance != null && appearance != JSONObject.NULL && root.optJSONObject("appearance") == null) {
            throw IllegalArgumentException("appearance 必须是 JSON 对象")
        }
        val customTexts = root.opt("customTexts")
        if (customTexts != null && customTexts != JSONObject.NULL && root.optJSONObject("customTexts") == null) {
            throw IllegalArgumentException("customTexts 必须是 JSON 对象")
        }
        val listed = root.optJSONArray("bottomBarIcons")
        if (listed != null) {
            for (i in 0 until listed.length()) {
                val slot = listed.optString(i)
                require(ThemeSchema.BOTTOM_BAR_SLOTS.any { it.key == slot }) {
                    "未知底栏图标槽位：$slot"
                }
                require(images.containsKey(ThemeSlots.bottomBarIcon(slot))) {
                    "theme.json 声明了底栏图标 $slot，但缺少对应条目"
                }
            }
        }
        return ThemeDocument.from(root, images)
    }

    fun build(doc: ThemeDocument): ByteArray {
        val out = JSONObject(doc.root.toString())
        out.put("adaptive", true)
        out.put("version", doc.effectiveVersion())

        val appearance = out.opt("appearance")
        if (appearance != null && appearance != JSONObject.NULL && out.optJSONObject("appearance") == null) {
            throw IllegalArgumentException("appearance 必须是 JSON 对象")
        }
        val customTexts = out.opt("customTexts")
        if (customTexts != null && customTexts != JSONObject.NULL && out.optJSONObject("customTexts") == null) {
            throw IllegalArgumentException("customTexts 必须是 JSON 对象")
        }
        for (dark in listOf(false, true)) {
            val key = ThemeSlots.mode(dark)
            val variant = out.optJSONObject(key) ?: JSONObject().also { out.put(key, it) }
            variant.put("hasBackground", doc.images.containsKey(ThemeSlots.background(dark)))
            variant.put("hasCardBackground", doc.images.containsKey(ThemeSlots.card(dark)))
            writeStickerTransforms(variant, doc, dark)
        }

        val iconSlots = ThemeSchema.BOTTOM_BAR_SLOTS
            .map { it.key }
            .filter { doc.images.containsKey(ThemeSlots.bottomBarIcon(it)) }
        if (iconSlots.isEmpty()) {
            out.remove("bottomBarIcons")
        } else {
            val array = JSONArray()
            iconSlots.forEach { array.put(it) }
            out.put("bottomBarIcons", array)
        }

        val entries = ArrayList<Pair<String, ByteArray>>()
        entries += "theme.json" to out.toString().toByteArray(Charsets.UTF_8)
        for (dark in listOf(false, true)) {
            doc.images[ThemeSlots.background(dark)]?.let { entries += it.entry to it.bytes }
            doc.images[ThemeSlots.card(dark)]?.let { entries += it.entry to it.bytes }
            for (i in 0 until ThemeSchema.STICKER_COUNT) {
                doc.images[ThemeSlots.sticker(dark, i)]?.let { entries += it.entry to it.bytes }
            }
        }
        ThemeSchema.BOTTOM_BAR_SLOTS.forEach { slot ->
            doc.images[ThemeSlots.bottomBarIcon(slot.key)]
                ?.let { entries += it.entry to it.bytes }
        }

        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { zos ->
            zos.setLevel(0)
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return ThemeCrypto.encrypt(zip.toByteArray())
    }

    private fun writeStickerTransforms(variant: JSONObject, doc: ThemeDocument, dark: Boolean) {
        val present = doc.presentStickers(dark)
        if (present.isEmpty()) {
            variant.remove("homeCardStickerTransforms")
            return
        }
        val array = JSONArray()
        present.forEach { index -> array.put(doc.stickerTransform(dark, index)) }
        variant.put("homeCardStickerTransforms", array)
    }

    private fun readEntry(input: ZipInputStream, max: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= max) { "条目超过大小上限" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}

object ImageInfo {

    fun decodeSize(bytes: ByteArray): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    fun preview(bytes: ByteArray, maxDimension: Int = 1200): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }
}
