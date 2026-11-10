package dev.sukisu.themeforge.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.json.JSONArray
import org.json.JSONObject

object Slots {
    fun mode(dark: Boolean) = if (dark) "dark" else "light"
    fun background(dark: Boolean) = "background_${mode(dark)}"
    fun card(dark: Boolean) = "card_background_${mode(dark)}"
    fun sticker(dark: Boolean, index: Int) = "card_sticker_${mode(dark)}_$index"

    val all: List<String> = buildList {
        for (m in listOf("light", "dark")) {
            add("background_$m")
            add("card_background_$m")
            for (i in 0 until 3) add("card_sticker_${m}_$i")
        }
    }
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
 * Images are kept in memory and written to ZIP entries on export.
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
    val version: Int get() = root.optInt("version", 5)

    fun variant(dark: Boolean): JSONObject {
        val key = Slots.mode(dark)
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

    fun putRoot(key: String, value: Any) {
        root.put(key, value)
        touch()
    }

    fun setVersion(value: Int) {
        root.put("version", value.coerceIn(3, 5))
        touch()
    }

    fun hasAppearance(): Boolean = root.optJSONObject("appearance") != null

    fun setAppearance(enabled: Boolean) {
        if (enabled) {
            root.put("appearance", JSONObject(DEFAULT_APPEARANCE))
        } else {
            root.remove("appearance")
        }
        touch()
    }

    fun requiredVersion(): Int {
        var result = 3
        if (root.optJSONObject("appearance") != null) result = 4
        for (key in listOf("light", "dark")) {
            val v = root.optJSONObject(key) ?: continue
            if (v.optString("homeLayout", "classic") == "immersive") result = 5
            if (v.has("bottomBarIcons") && !v.isNull("bottomBarIcons")) result = 5
        }
        return result
    }

    fun effectiveVersion(): Int = maxOf(version, requiredVersion())

    fun toPrettyString(): String = root.toString(2)

    fun putImage(slot: String, asset: ImageAsset) {
        images[slot] = asset
        syncMeta()
    }

    fun removeImage(slot: String) {
        images.remove(slot)
        syncMeta()
    }

    fun clearModeImages(dark: Boolean) {
        val prefix = Slots.mode(dark)
        images.keys
            .filter { it.endsWith("_$prefix") || it.contains("_${prefix}_") }
            .forEach { images.remove(it) }
        syncMeta()
    }

    fun syncMeta() {
        for (dark in listOf(false, true)) {
            val v = variant(dark)
            v.put("hasBackground", images.containsKey(Slots.background(dark)))
            v.put("hasCardBackground", images.containsKey(Slots.card(dark)))
            syncStickerTransforms(v, dark)
        }
        touch()
    }

    private fun syncStickerTransforms(v: JSONObject, dark: Boolean) {
        val present = (0 until 3).filter { images.containsKey(Slots.sticker(dark, it)) }
        if (present.isEmpty()) {
            v.remove("homeCardStickerTransforms")
            return
        }
        val existing = v.optJSONArray("homeCardStickerTransforms")
        if (existing != null && existing.length() == present.size) return
        val array = JSONArray()
        present.forEach { array.put(defaultTransform(it)) }
        v.put("homeCardStickerTransforms", array)
    }

    fun replaceRoot(newRoot: JSONObject) {
        val newVersion = newRoot.optInt("version", 0)
        require(newVersion in 3..5) { "仅支持 v3 - v5 主题包（当前 v$newVersion）" }
        require(newRoot.optBoolean("adaptive", false)) { "仅支持 adaptive 自适应主题包" }
        val keys = root.keys()
        while (keys.hasNext()) root.remove(keys.next())
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
                put("version", 5)
                put("appIconId", "sukisu_logo")
                put("customHomeTitle", "SukiSU")
                put("adaptive", true)
                put("light", defaultVariant(0xFF0F766E.toInt(), 76, 20, -8))
                put("dark", defaultVariant(0xFF2DD4BF.toInt(), 62, 24, -22))
            }
            return ThemeDocument(root, mutableStateMapOf<String, ImageAsset>())
        }

        fun from(root: JSONObject, images: SnapshotStateMap<String, ImageAsset>): ThemeDocument =
            ThemeDocument(root, images)

        private fun defaultVariant(keyColor: Int, cardAlpha: Int, blur: Int, scrim: Int) =
            JSONObject().apply {
                put("themeMode", 0)
                put("miuixMonet", false)
                put("keyColor", keyColor)
                put("colorStyle", "TonalSpot")
                put("colorSpec", "SPEC_2025")
                put("cardBackgroundAlphaPercent", cardAlpha)
                put("customBackgroundBlurPercent", blur)
                put("customBackgroundScrimPercent", scrim)
                put("hasBackground", false)
                put("hasCardBackground", false)
                put("hideHomeCardStatusText", false)
                put("hideHomeCardDecoration", true)
                put("homeLayout", "classic")
                put("homeStatusAlignment", "start")
                put("homeStatusScale", 1.0)
                put("homePillScale", 1.0)
                put("homeStatusWidthFraction", 0.94)
                put("homeCardImageStyle", "banner")
                put("showHomeCardImageWithoutBackground", true)
                put("uiMode", "miuix")
                put("enableFloatingBottomBar", true)
                put("enableFloatingBottomBarBlur", true)
                put("enableNavigationBadge", true)
                put("enablePredictiveBack", true)
                put("enableSwipeDismiss", true)
                put("enableBlur", true)
                put("enableProgressiveBlur", true)
                put("pagerInterceptionMode", 1)
                put("pageScale", 1.0)
                put("moduleDescriptionMaxLines", 3)
            }

        private fun defaultTransform(index: Int) = JSONObject().apply {
            put("offsetX", when (index) { 0 -> 0.27; 1 -> -0.32; else -> 0.0 })
            put("offsetY", when (index) { 0 -> -0.28; 1 -> -0.28; else -> 0.30 })
            put("rotation", when (index) { 1 -> 8; 2 -> -6; else -> 0 })
            put("size", when (index) { 0 -> 0.20; 1 -> 0.14; else -> 0.10 })
            put("offsetYPct", when (index) { 1 -> -0.05; 2 -> 0.08; else -> 0.0 })
        }

        private val DEFAULT_APPEARANCE = """
            {
              "schema": 1,
              "colors": {
                "shared": { "error": "#FFB3261E" },
                "light": {
                  "accent": "#FF0F766E",
                  "onAccent": "#FFFFFFFF",
                  "textPrimary": "#FF0E1F1B",
                  "textSecondary": "#FF4C625D",
                  "textDisabled": "#FF93A7A2",
                  "surface": "#FFF8FAF9",
                  "card": "#FFFFFFFF",
                  "outline": "#FFD9E6E2"
                },
                "dark": {
                  "accent": "#FF5EEAD4",
                  "onAccent": "#FF04231E",
                  "textPrimary": "#FFF1FBF8",
                  "textSecondary": "#FFADC7C1",
                  "textDisabled": "#FF6F8781",
                  "surface": "#FF070F0D",
                  "card": "#FF0F1B18",
                  "outline": "#FF233530",
                  "error": "#FFFF8A80"
                }
              },
              "image": {
                "background": { "sharpen": 12, "saturation": 112, "contrast": 103, "brightness": 0 }
              }
            }
        """.trimIndent()
    }
}
