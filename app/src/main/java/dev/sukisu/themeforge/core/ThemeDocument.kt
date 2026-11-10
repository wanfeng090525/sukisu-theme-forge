package dev.sukisu.themeforge.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.json.JSONArray
import org.json.JSONObject

/** ZIP entry names used by the .sukisu format. */
object ThemeSlots {
    fun mode(dark: Boolean) = if (dark) "dark" else "light"
    fun background(dark: Boolean) = "background_${mode(dark)}"
    fun card(dark: Boolean) = "card_background_${mode(dark)}"
    fun sticker(dark: Boolean, index: Int) = "card_sticker_${mode(dark)}_$index"
    fun bottomBarIcon(slot: String) = "bottom_bar_icon_$slot"

    val all: List<String> = buildList {
        for (m in listOf("light", "dark")) {
            add("background_$m")
            add("card_background_$m")
            for (i in 0 until ThemeSchema.STICKER_COUNT) add("card_sticker_${m}_$i")
        }
        ThemeSchema.BOTTOM_BAR_SLOTS.forEach { add(bottomBarIcon(it.key)) }
    }

    fun isBottomBarIcon(name: String): Boolean =
        name.startsWith("bottom_bar_icon_") &&
            ThemeSchema.BOTTOM_BAR_SLOTS.any { bottomBarIcon(it.key) == name }
}

class ImageAsset(
    val bytes: ByteArray,
    val entry: String,
    val width: Int,
    val height: Int
) {
    val sizeKb: Int get() = (bytes.size / 1024).coerceAtLeast(1)
}

/**
 * Editable, snapshot-backed view over a theme.json object.
 *
 * Images are kept in memory and written to ZIP entries on export. The document keeps
 * `hasBackground`, `hasCardBackground`, sticker transforms and `bottomBarIcons` in sync with the
 * image map, which is what the SukiSU Ultra loader expects.
 */
class ThemeDocument private constructor(
    root: JSONObject,
    val images: SnapshotStateMap<String, ImageAsset>
) {
    var root: JSONObject = root
        private set

    var revision by mutableIntStateOf(0)
        private set

    fun touch() {
        revision++
    }

    val adaptive: Boolean get() = root.optBoolean("adaptive", true)
    val version: Int get() = root.optInt("version", ThemeSchema.MAX_VERSION)

    fun variant(dark: Boolean): JSONObject {
        val key = ThemeSlots.mode(dark)
        return root.optJSONObject(key) ?: JSONObject().also { root.put(key, it) }
    }

    fun string(dark: Boolean, key: String, def: String = ""): String =
        variant(dark).optString(key, def)

    fun int(dark: Boolean, key: String, def: Int = 0): Int =
        variant(dark).optInt(key, def)

    fun bool(dark: Boolean, key: String, def: Boolean = false): Boolean =
        variant(dark).optBoolean(key, def)

    fun double(dark: Boolean, key: String, def: Double = 0.0): Double =
        variant(dark).optDouble(key, def)

    fun put(dark: Boolean, key: String, value: Any) {
        variant(dark).put(key, value)
        touch()
    }

    fun putBoth(key: String, value: Any) {
        variant(false).put(key, value)
        variant(true).put(key, value)
        touch()
    }

    fun rootString(key: String, def: String = ""): String = root.optString(key, def)

    fun rootInt(key: String, def: Int = 0): Int = root.optInt(key, def)

    fun rootBool(key: String, def: Boolean = false): Boolean = root.optBoolean(key, def)

    fun rootStringList(key: String): List<String> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    fun putRoot(key: String, value: Any) {
        root.put(key, value)
        touch()
    }

    fun removeRoot(key: String) {
        root.remove(key)
        touch()
    }

    fun setVersion(value: Int) {
        root.put("version", value.coerceIn(ThemeSchema.MIN_VERSION, ThemeSchema.MAX_VERSION))
        touch()
    }

    fun hasAppearance(): Boolean = root.optJSONObject("appearance") != null

    fun setAppearance(enabled: Boolean) {
        if (enabled) {
            root.put("appearance", ThemeSchema.defaultAppearance())
        } else {
            root.remove("appearance")
        }
        touch()
    }

    fun requiredVersion(): Int {
        var result = ThemeSchema.MIN_VERSION
        if (root.optJSONObject("appearance") != null) result = 4
        if (images.keys.any { ThemeSlots.isBottomBarIcon(it) }) result = ThemeSchema.MAX_VERSION
        if (rootStringList("bottomBarIcons").isNotEmpty()) result = ThemeSchema.MAX_VERSION
        for (key in listOf("light", "dark")) {
            val v = root.optJSONObject(key) ?: continue
            if (v.optString("homeLayout", "classic") == "immersive") result = ThemeSchema.MAX_VERSION
        }
        return result
    }

    fun effectiveVersion(): Int = maxOf(version, requiredVersion())

    fun toPrettyString(): String = root.toString(2)

    // ---- images -------------------------------------------------------------

    fun putImage(slot: String, asset: ImageAsset) {
        images[slot] = asset
        syncMeta()
    }

    fun removeImage(slot: String) {
        images.remove(slot)
        syncMeta()
    }

    fun clearModeImages(dark: Boolean) {
        val mode = ThemeSlots.mode(dark)
        images.keys
            .filter { it.endsWith("_$mode") || it.contains("_${mode}_") }
            .forEach { images.remove(it) }
        syncMeta()
    }

    fun clearBottomBarIcons() {
        images.keys.filter { ThemeSlots.isBottomBarIcon(it) }.forEach { images.remove(it) }
        syncMeta()
    }

    fun syncMeta() {
        for (dark in listOf(false, true)) {
            val v = variant(dark)
            v.put("hasBackground", images.containsKey(ThemeSlots.background(dark)))
            v.put("hasCardBackground", images.containsKey(ThemeSlots.card(dark)))
            syncStickerTransforms(v, dark)
        }
        syncBottomBarIcons()
        touch()
    }

    private fun presentStickerIndices(dark: Boolean): List<Int> =
        (0 until ThemeSchema.STICKER_COUNT).filter {
            images.containsKey(ThemeSlots.sticker(dark, it))
        }

    private fun syncStickerTransforms(v: JSONObject, dark: Boolean) {
        val present = presentStickerIndices(dark)
        if (present.isEmpty()) {
            v.remove("homeCardStickerTransforms")
            return
        }
        val existing = v.optJSONArray("homeCardStickerTransforms")
        if (existing != null && existing.length() == present.size) return
        val array = JSONArray()
        present.forEach { array.put(ThemeSchema.defaultTransform(it)) }
        v.put("homeCardStickerTransforms", array)
    }

    private fun syncBottomBarIcons() {
        val slots = ThemeSchema.BOTTOM_BAR_SLOTS
            .map { it.key }
            .filter { images.containsKey(ThemeSlots.bottomBarIcon(it)) }
        if (slots.isEmpty()) {
            root.remove("bottomBarIcons")
        } else {
            val array = JSONArray()
            slots.forEach { array.put(it) }
            root.put("bottomBarIcons", array)
        }
    }

    fun presentStickers(dark: Boolean): List<Int> = presentStickerIndices(dark)

    /** Read-only transform for the sticker at [index]; falls back to the default layout. */
    fun stickerTransform(dark: Boolean, index: Int): JSONObject {
        val position = presentStickerIndices(dark).indexOf(index)
        if (position >= 0) {
            val array = variant(dark).optJSONArray("homeCardStickerTransforms")
            array?.optJSONObject(position)?.let { return it }
        }
        return ThemeSchema.defaultTransform(index)
    }

    fun setStickerTransform(dark: Boolean, index: Int, key: String, value: Double) {
        val present = presentStickerIndices(dark)
        val position = present.indexOf(index)
        if (position < 0) return
        val v = variant(dark)
        val array = v.optJSONArray("homeCardStickerTransforms") ?: JSONArray()
        while (array.length() < present.size) {
            array.put(ThemeSchema.defaultTransform(present[array.length()]))
        }
        val item = array.optJSONObject(position) ?: ThemeSchema.defaultTransform(index)
        item.put(key, value)
        array.put(position, item)
        v.put("homeCardStickerTransforms", array)
        touch()
    }

    // ---- custom texts -------------------------------------------------------

    fun customTexts(): JSONObject = root.optJSONObject("customTexts") ?: JSONObject()

    fun customText(key: String): String = customTexts().optString(key, "")

    fun customTextCount(): Int = customTexts().length()

    fun putCustomText(key: String, value: String) {
        val obj = root.optJSONObject("customTexts") ?: JSONObject().also { root.put("customTexts", it) }
        if (value.isBlank()) obj.remove(key) else obj.put(key, value)
        if (obj.length() == 0) root.remove("customTexts")
        touch()
    }

    // ---- appearance overlay -------------------------------------------------

    fun appearanceColor(mode: String, token: String): String =
        root.optJSONObject("appearance")
            ?.optJSONObject("colors")
            ?.optJSONObject(mode)
            ?.optString(token, "")
            .orEmpty()

    fun setAppearanceColor(mode: String, token: String, hex: String) {
        val overlay = root.optJSONObject("appearance")
            ?: ThemeSchema.defaultAppearance().also { root.put("appearance", it) }
        val colors = overlay.optJSONObject("colors") ?: JSONObject().also { overlay.put("colors", it) }
        val section = colors.optJSONObject(mode) ?: JSONObject().also { colors.put(mode, it) }
        section.put(token, hex)
        touch()
    }

    fun appearanceEffect(key: String): Double =
        root.optJSONObject("appearance")
            ?.optJSONObject("image")
            ?.optJSONObject("background")
            ?.optDouble(key, 0.0)
            ?: 0.0

    fun setAppearanceEffect(key: String, value: Double) {
        val overlay = root.optJSONObject("appearance")
            ?: ThemeSchema.defaultAppearance().also { root.put("appearance", it) }
        val image = overlay.optJSONObject("image") ?: JSONObject().also { overlay.put("image", it) }
        val background = image.optJSONObject("background")
            ?: JSONObject().also { image.put("background", it) }
        background.put(key, value)
        touch()
    }

    // ---- whole-document -----------------------------------------------------

    fun replaceRoot(newRoot: JSONObject) {
        val newVersion = newRoot.optInt("version", 0)
        require(newVersion in ThemeSchema.MIN_VERSION..ThemeSchema.MAX_VERSION) {
            "仅支持 v3 - v5 主题包（当前 v$newVersion）"
        }
        require(newRoot.optBoolean("adaptive", false)) { "仅支持 adaptive 自适应主题包" }
        val oldKeys = ArrayList<String>()
        val iterator = root.keys()
        while (iterator.hasNext()) oldKeys.add(iterator.next())
        oldKeys.forEach { root.remove(it) }
        val newKeys = newRoot.keys()
        while (newKeys.hasNext()) {
            val key = newKeys.next()
            root.put(key, newRoot.get(key))
        }
        syncMeta()
    }

    companion object {
        fun blank(): ThemeDocument {
            val root = JSONObject().apply {
                put("version", ThemeSchema.MAX_VERSION)
                put("adaptive", true)
                put("appIconId", "default")
                put("customHomeTitle", "SukiSU")
                put("light", ThemeSchema.variantTemplate(false).apply {
                    put("homeCardStickerTransforms", JSONArray())
                })
                put("dark", ThemeSchema.variantTemplate(true).apply {
                    put("homeCardStickerTransforms", JSONArray())
                })
            }
            root.optJSONObject("light")?.remove("homeCardStickerTransforms")
            root.optJSONObject("dark")?.remove("homeCardStickerTransforms")
            return ThemeDocument(root, mutableStateMapOf<String, ImageAsset>())
        }

        fun from(root: JSONObject, images: SnapshotStateMap<String, ImageAsset>): ThemeDocument =
            ThemeDocument(root, images)
    }
}
