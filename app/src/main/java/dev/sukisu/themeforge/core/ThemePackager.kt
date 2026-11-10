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

/** Export knobs exposed by the export sheet. */
data class ExportOptions(
    /** 0 = keep the document version (raised automatically when the content needs more). */
    val targetVersion: Int = 0,
    /** Write a legacy v2 single-variant package instead of an adaptive one. */
    val singleVariant: Boolean = false,
    val includeBackground: Boolean = true,
    val includeCardBackground: Boolean = true,
    val includeStickers: Boolean = true,
    val includeBottomBarIcons: Boolean = true
)

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
        val legacy = mutableStateMapOf<String, ImageAsset>()
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

                    name in ThemeSlots.all || name in ThemeSchema.LEGACY_ENTRIES -> {
                        if (images.containsKey(name) || legacy.containsKey(name)) {
                            throw IllegalArgumentException("条目重复：$name")
                        }
                        val bytes = readEntry(zis, MAX_IMAGE)
                        totalImages += bytes.size
                        require(totalImages <= MAX_ZIP) { "图片总大小超过上限" }
                        val size = ImageInfo.decodeSize(bytes)
                            ?: throw IllegalArgumentException("图片无法解码：$name")
                        val asset = ImageAsset(bytes, name, size.first, size.second)
                        if (name in ThemeSchema.LEGACY_ENTRIES) legacy[name] = asset else images[name] = asset
                    }

                    else -> throw IllegalArgumentException("未知主题包条目：$name")
                }
            }
        }

        var root = json ?: throw IllegalArgumentException("主题包缺少 theme.json")
        val rawVersion = root.optInt("version", 0)
        require(rawVersion in ThemeSchema.LEGACY_MIN_VERSION..ThemeSchema.MAX_VERSION) {
            "仅支持 v1 - v5 主题包（当前 v$rawVersion）"
        }
        val note: String?
        if (ThemeSchema.isLegacy(root)) {
            val versionLabel = if (rawVersion < 3) "v$rawVersion" else "非自适应"
            root = ThemeSchema.upgradeLegacy(root)
            note = "已识别 $versionLabel 旧格式，自动升级为 v${ThemeSchema.MIN_VERSION} 双变体"
            mirrorLegacyImages(legacy, root, images)
            legacy.clear()
            val doc = ThemeDocument.from(root, images)
            doc.syncMeta()
            doc.importNote = note
            return doc
        }

        note = null
        require(root.optJSONObject("light") != null && root.optJSONObject("dark") != null) {
            "自适应主题包必须同时包含 light 和 dark"
        }
        validateShape(root, images)
        return ThemeDocument.from(root, images).also { it.importNote = note }
    }

    private fun mirrorLegacyImages(
        legacy: Map<String, ImageAsset>,
        root: JSONObject,
        target: MutableMap<String, ImageAsset>
    ) {
        fun mirror(legacyEntry: String?, slot: (Boolean) -> String, declared: Boolean) {
            val asset = legacyEntry?.let(legacy::get) ?: return
            if (!declared) return
            for (dark in listOf(false, true)) {
                val name = slot(dark)
                target[name] = ImageAsset(asset.bytes, name, asset.width, asset.height)
            }
        }
        mirror(
            "background",
            { dark -> ThemeSlots.background(dark) },
            root.optJSONObject("light")?.optBoolean("hasBackground", true) ?: true
        )
        mirror(
            "card_background",
            { dark -> ThemeSlots.card(dark) },
            root.optJSONObject("light")?.optBoolean("hasCardBackground", true) ?: true
        )
        for (index in 0 until ThemeSchema.STICKER_COUNT) {
            val asset = legacy["card_sticker_$index"] ?: continue
            for (dark in listOf(false, true)) {
                val name = ThemeSlots.sticker(dark, index)
                target[name] = ImageAsset(asset.bytes, name, asset.width, asset.height)
            }
        }
    }

    private fun validateShape(root: JSONObject, images: Map<String, ImageAsset>) {
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
    }

    fun build(doc: ThemeDocument, options: ExportOptions = ExportOptions()): ByteArray {
        return if (options.singleVariant) buildLegacy(doc, options) else buildAdaptive(doc, options)
    }

    private fun buildAdaptive(doc: ThemeDocument, options: ExportOptions): ByteArray {
        val out = JSONObject(doc.root.toString())
        out.put("adaptive", true)
        val required = doc.requiredVersion()
        val requested = options.targetVersion
            .takeIf { it in ThemeSchema.MIN_VERSION..ThemeSchema.MAX_VERSION }
        out.put("version", maxOf(requested ?: doc.version, required))

        validateShape(out, doc.images)

        val iconSlots = if (options.includeBottomBarIcons) {
            ThemeSchema.BOTTOM_BAR_SLOTS.map { it.key }
                .filter { doc.images.containsKey(ThemeSlots.bottomBarIcon(it)) }
        } else {
            emptyList()
        }
        if (iconSlots.isEmpty()) out.remove("bottomBarIcons") else {
            out.put("bottomBarIcons", JSONArray().apply { iconSlots.forEach { put(it) } })
        }

        val entries = ArrayList<Pair<String, ByteArray>>()
        entries += "theme.json" to out.toString().toByteArray(Charsets.UTF_8)
        for (dark in listOf(false, true)) {
            val key = ThemeSlots.mode(dark)
            val variant = out.optJSONObject(key) ?: JSONObject().also { out.put(key, it) }

            val background = if (options.includeBackground) doc.images[ThemeSlots.background(dark)] else null
            variant.put("hasBackground", background != null)
            val card = if (options.includeCardBackground) doc.images[ThemeSlots.card(dark)] else null
            variant.put("hasCardBackground", card != null)
            writeStickerTransforms(variant, doc, dark, options.includeStickers)

            background?.let { entries += it.entry to it.bytes }
            card?.let { entries += it.entry to it.bytes }
            if (options.includeStickers) {
                for (i in 0 until ThemeSchema.STICKER_COUNT) {
                    doc.images[ThemeSlots.sticker(dark, i)]?.let { entries += it.entry to it.bytes }
                }
            }
        }
        entries[0] = "theme.json" to out.toString().toByteArray(Charsets.UTF_8)
        iconSlots.forEach { slot ->
            doc.images[ThemeSlots.bottomBarIcon(slot)]?.let { entries += it.entry to it.bytes }
        }
        return seal(entries)
    }

    /** Writes a v2 single-variant package: legacy entry names, one image set, one config. */
    private fun buildLegacy(doc: ThemeDocument, options: ExportOptions): ByteArray {
        val light = doc.variant(false)
        val dark = doc.variant(true)
        val out = JSONObject()
        out.put("version", ThemeSchema.LEGACY_MAX_VERSION)
        for (key in listOf("appIconId", "customHomeTitle", "customTexts")) {
            doc.root.opt(key)?.let { if (it != JSONObject.NULL) out.put(key, it) }
        }
        ThemeSchema.GLOBAL_KEYS
            .filter { it.startsWith("moduleSort") }
            .forEach { key -> doc.root.opt(key)?.let { if (it != JSONObject.NULL) out.put(key, it) } }
        val keys = light.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "hasBackground" || key == "hasCardBackground") continue
            if (key == "homeCardStickerTransforms") continue
            val value = light.opt(key) ?: continue
            if (value == JSONObject.NULL) continue
            out.put(key, value)
        }
        out.put("homeLayout", "classic")

        fun pick(lightSlot: String, darkSlot: String): ImageAsset? =
            doc.images[lightSlot] ?: doc.images[darkSlot]

        val entries = ArrayList<Pair<String, ByteArray>>()
        val background = if (options.includeBackground) {
            pick(ThemeSlots.background(false), ThemeSlots.background(true))
        } else null
        val card = if (options.includeCardBackground) {
            pick(ThemeSlots.card(false), ThemeSlots.card(true))
        } else null
        out.put("hasBackground", background != null)
        out.put("hasCardBackground", card != null)
        background?.let { entries += "background" to it.bytes }
        card?.let { entries += "card_background" to it.bytes }

        val stickerArray = JSONArray()
        if (options.includeStickers) {
            for (i in 0 until ThemeSchema.STICKER_COUNT) {
                val asset = pick(ThemeSlots.sticker(false, i), ThemeSlots.sticker(true, i)) ?: continue
                entries += "card_sticker_$i" to asset.bytes
                stickerArray.put(doc.stickerTransform(false, i))
            }
        }
        if (stickerArray.length() == 0) out.remove("homeCardStickerTransforms")
        else out.put("homeCardStickerTransforms", stickerArray)

        entries.add(0, "theme.json" to out.toString().toByteArray(Charsets.UTF_8))
        return seal(entries)
    }

    private fun seal(entries: List<Pair<String, ByteArray>>): ByteArray {
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

    private fun writeStickerTransforms(
        variant: JSONObject,
        doc: ThemeDocument,
        dark: Boolean,
        include: Boolean
    ) {
        val present = if (include) doc.presentStickers(dark) else emptyList()
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
