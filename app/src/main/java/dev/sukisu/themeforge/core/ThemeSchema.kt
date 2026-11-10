package dev.sukisu.themeforge.core

import org.json.JSONArray
import org.json.JSONObject

/** A key of the optional `customTexts` map, with a Chinese label for the editor. */
data class CustomTextDef(val key: String, val group: String, val label: String)

data class BottomBarSlotDef(val key: String, val label: String)

/**
 * All constants and defaults that mirror the SukiSU Ultra theme schema.
 *
 * Keep this file in sync with the manager source (`ThemePackageStorage.kt`,
 * `AppearanceOverlay.kt`, `CustomTextConfig.kt`, `BottomBarIcons.kt`).
 */
object ThemeSchema {

    const val STICKER_COUNT = 5
    const val MIN_VERSION = 3
    const val MAX_VERSION = 5

    /** v1 / v2 are the legacy single-variant formats kept for import and compatibility export. */
    const val LEGACY_MIN_VERSION = 1
    const val LEGACY_MAX_VERSION = 2

    /** ZIP entries used by v1 / v2 packages (one image set, no light/dark split). */
    val LEGACY_ENTRIES: List<String> = buildList {
        add("background")
        add("card_background")
        for (i in 0 until STICKER_COUNT) add("card_sticker_$i")
    }

    /** Top-level keys that are shared by both variants (or describe the package itself). */
    val GLOBAL_KEYS = setOf(
        "version", "adaptive", "light", "dark",
        "appIconId", "customHomeTitle", "customTexts", "bottomBarIcons", "appearance",
        "moduleSortEnabledFirst", "moduleSortActionFirst", "moduleSortWebUiFirst",
        "moduleSortZygiskFirst", "moduleSortMetamoduleFirst", "moduleSortNameOrder",
        "moduleSortManual"
    )

    fun isLegacy(root: JSONObject): Boolean =
        root.optInt("version", LEGACY_MIN_VERSION) < 3 || !root.optBoolean("adaptive", false)

    /**
     * Converts a v1 / v2 (or non-adaptive) theme.json into the v3 adaptive shape.
     *
     * The legacy variant is shared by light and dark; images are duplicated by the packager.
     * Fields the legacy format cannot express (bottom bar icons, appearance) are dropped and
     * reported by the caller.
     */
    fun upgradeLegacy(root: JSONObject): JSONObject {
        if (!isLegacy(root)) return JSONObject(root.toString())
        val out = JSONObject()
        out.put("version", MIN_VERSION)
        out.put("adaptive", true)
        for (key in GLOBAL_KEYS) {
            if (key == "version" || key == "adaptive" || key == "light" || key == "dark") continue
            if (key == "bottomBarIcons") continue
            val value = root.opt(key) ?: continue
            if (value == JSONObject.NULL) continue
            out.put(key, value)
        }
        val variant = JSONObject()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in GLOBAL_KEYS) continue
            val value = root.opt(key) ?: continue
            if (value == JSONObject.NULL) continue
            variant.put(key, value)
        }
        out.put("light", JSONObject(variant.toString()))
        out.put("dark", JSONObject(variant.toString()))
        return out
    }

    val APP_ICONS = listOf("default", "kernelsu", "sukisu_logo", "zako")

    val COLOR_STYLES = listOf(
        "TonalSpot", "Neutral", "Vibrant", "Expressive", "Fidelity",
        "Content", "Monochrome", "Rainbow", "FruitSalad"
    )

    val COLOR_SPECS = listOf("SPEC_2021", "SPEC_2025")

    val THEME_MODES = listOf(
        0 to "跟随系统",
        1 to "浅色",
        2 to "深色",
        3 to "Monet 跟随系统",
        4 to "Monet 浅色",
        5 to "Monet 深色",
        6 to "深色 AMOLED"
    )

    val UI_MODES = listOf("miuix", "material")
    val HOME_LAYOUTS = listOf("classic", "immersive")
    val STATUS_ALIGNMENTS = listOf("start", "end")
    val CARD_IMAGE_STYLES = listOf("banner", "off")
    val MODULE_NAME_ORDERS = listOf("DEFAULT", "ASCENDING", "DESCENDING")

    val BOTTOM_BAR_SLOTS = listOf(
        BottomBarSlotDef("home", "首页"),
        BottomBarSlotDef("superuser", "超级用户"),
        BottomBarSlotDef("module", "模块"),
        BottomBarSlotDef("setting", "设置")
    )

    data class AppearanceTokenDef(val key: String, val label: String)

    val COLOR_TOKENS = listOf(
        AppearanceTokenDef("accent", "强调色"),
        AppearanceTokenDef("onAccent", "强调色文字"),
        AppearanceTokenDef("textPrimary", "主要文字"),
        AppearanceTokenDef("textSecondary", "次要文字"),
        AppearanceTokenDef("textDisabled", "禁用文字"),
        AppearanceTokenDef("surface", "页面背景"),
        AppearanceTokenDef("card", "卡片背景"),
        AppearanceTokenDef("outline", "描边"),
        AppearanceTokenDef("error", "错误色")
    )

    val APPEARANCE_MODES = listOf(
        "shared" to "通用",
        "light" to "浅色",
        "dark" to "深色"
    )

    val IMAGE_EFFECTS = listOf(
        "sharpen" to "锐化",
        "saturation" to "饱和度",
        "contrast" to "对比度",
        "brightness" to "亮度"
    )

    val CUSTOM_TEXTS: List<CustomTextDef> = listOf(
        CustomTextDef("home.title", "首页", "首页标题"),
        CustomTextDef("home.status_working", "首页", "运行状态文字"),
        CustomTextDef("home.status_install_hint", "首页", "点击安装提示"),
        CustomTextDef("home.btn_install", "首页", "安装按钮"),
        CustomTextDef("home.tag_lkm", "首页", "LKM 徽标"),
        CustomTextDef("home.tag_safemode", "首页", "安全模式徽标"),
        CustomTextDef("home.stats_superuser", "首页", "授权统计标题"),
        CustomTextDef("home.stats_module", "首页", "模块统计标题"),
        CustomTextDef("home.info_kernel", "首页", "内核版本标签"),
        CustomTextDef("home.info_kernel_full", "首页", "完整内核版本标签"),
        CustomTextDef("home.info_manager_version", "首页", "管理器版本标签"),
        CustomTextDef("home.info_selinux", "首页", "SELinux 状态标签"),
        CustomTextDef("home.info_seccomp", "首页", "Seccomp 状态标签"),
        CustomTextDef("home.info_zygisk", "首页", "Zygisk 实现标签"),
        CustomTextDef("home.info_hook_type", "首页", "Hook 方式标签"),
        CustomTextDef("home.info_device_model", "首页", "设备型号标签"),
        CustomTextDef("home.info_fingerprint", "首页", "设备指纹标签"),
        CustomTextDef("home.warn_version_spoof", "首页", "版本不匹配警告"),
        CustomTextDef("home.update_new_version", "首页", "新版本提示（可含 %s）"),
        CustomTextDef("home.ci_update_available", "首页", "CI 更新提示（可含 %s）"),
        CustomTextDef("home.ci_update_busy", "首页", "CI 检查中提示"),
        CustomTextDef("home.warn_pr_build", "首页", "PR 构建警告"),
        CustomTextDef("home.warn_pr_kernel", "首页", "内核 PR 警告"),
        CustomTextDef("home.warn_require_kernel", "首页", "需要新内核提示"),
        CustomTextDef("home.warn_require_kernel_gki", "首页", "需要新内核提示（GKI）"),
        CustomTextDef("home.warn_require_manager", "首页", "需要新管理器提示"),
        CustomTextDef("home.warn_lkm_update", "首页", "LKM 更新提示"),
        CustomTextDef("home.warn_root_failed", "首页", "Root 失败警告"),
        CustomTextDef("home.warn_preview_mode", "首页", "预览模式警告"),

        CustomTextDef("page.home", "页面", "首页"),
        CustomTextDef("page.superuser", "页面", "超级用户"),
        CustomTextDef("page.modules", "页面", "模块"),
        CustomTextDef("page.settings", "页面", "设置"),

        CustomTextDef("page.appearance", "页面", "外观"),
        CustomTextDef("page.theme_store", "页面", "主题商店"),
        CustomTextDef("page.custom_texts", "页面", "自定义文案"),
        CustomTextDef("page.app_icon", "页面", "应用图标"),
        CustomTextDef("page.app_profile", "页面", "应用配置"),
        CustomTextDef("page.template", "页面", "配置模板"),
        CustomTextDef("page.about", "页面", "关于"),
        CustomTextDef("page.umount", "页面", "卸载路径"),
        CustomTextDef("page.extensions", "页面", "扩展"),
        CustomTextDef("page.tools", "页面", "工具箱"),
        CustomTextDef("page.sulog", "页面", "SuLog"),
        CustomTextDef("page.kernel_modules", "页面", "内核模块"),
        CustomTextDef("page.dynamic_managers", "页面", "动态管理器"),
        CustomTextDef("page.module_repo", "页面", "模块仓库"),
        CustomTextDef("page.install", "页面", "安装"),
        CustomTextDef("extension.kpm", "页面", "KPM 扩展"),
        CustomTextDef("extension.cpu_spoof", "页面", "CPU 伪装"),
        CustomTextDef("extension.susfs", "页面", "SUSFS 扩展"),
        CustomTextDef("extension.version_spoof", "页面", "版本伪装"),

        CustomTextDef("settings.ui_mode", "设置", "界面模式"),
        CustomTextDef("settings.language", "设置", "语言"),
        CustomTextDef("settings.sucompat", "设置", "SU 兼容模式"),
        CustomTextDef("settings.kernel_umount", "设置", "内核卸载"),
        CustomTextDef("settings.selinux_hide", "设置", "SELinux 隐藏"),
        CustomTextDef("settings.avc_spoof", "设置", "AVC 伪装"),
        CustomTextDef("settings.mount_hide", "设置", "挂载隐藏"),
        CustomTextDef("settings.sulog", "设置", "SuLog"),
        CustomTextDef("settings.adb_root", "设置", "ADB Root"),
        CustomTextDef("settings.shell_su", "设置", "Shell SU"),
        CustomTextDef("settings.soft_reboot", "设置", "软重启"),
        CustomTextDef("settings.umount_modules_default", "设置", "默认卸载模块"),
        CustomTextDef("settings.auto_delete_module_zip", "设置", "自动删除模块包"),
        CustomTextDef("settings.auto_delete_update_apk", "设置", "自动删除更新包"),
        CustomTextDef("settings.web_debugging", "设置", "Web 调试"),
        CustomTextDef("settings.auto_jailbreak", "设置", "自动越狱"),
        CustomTextDef("settings.temporary_root_duration", "设置", "临时 Root 时长"),
        CustomTextDef("settings.uninstall", "设置", "卸载"),
        CustomTextDef("settings.send_log", "设置", "发送日志")
    )

    val CUSTOM_TEXT_GROUPS = listOf("首页", "页面", "设置")

    fun customTextDef(key: String): CustomTextDef? = CUSTOM_TEXTS.firstOrNull { it.key == key }

    fun bottomBarLabel(key: String): String =
        BOTTOM_BAR_SLOTS.firstOrNull { it.key == key }?.label ?: key

    fun variantTemplate(dark: Boolean): JSONObject = JSONObject().apply {
        put("themeMode", if (dark) 5 else 4)
        put("miuixMonet", true)
        put("keyColor", if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt())
        put("colorStyle", "TonalSpot")
        put("colorSpec", "SPEC_2025")
        put("cardBackgroundAlphaPercent", if (dark) 62 else 76)
        put("customBackgroundBlurPercent", if (dark) 24 else 20)
        put("customBackgroundScrimPercent", if (dark) -22 else -8)
        put("hasBackground", false)
        put("hasCardBackground", false)
        put("hideHomeCardStatusText", false)
        put("hideHomeCardDecoration", false)
        put("homeCardStickerTransforms", JSONArray())
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

    fun defaultAppearance(): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("colors", JSONObject().apply {
            put("shared", tokens("#FFB3261E", null, null, null, null, null, null, null, null))
            put("light", tokens(
                "#FF0F766E", "#FFFFFFFF", "#FF0E1F1B", "#FF4C625D", "#FF93A7A2",
                "#FFF8FAF9", "#FFFFFFFF", "#FFD9E6E2", "#FFB3261E"
            ))
            put("dark", tokens(
                "#FF5EEAD4", "#FF04231E", "#FFF1FBF8", "#FFADC7C1", "#FF6F8781",
                "#FF070F0D", "#FF0F1B18", "#FF233530", "#FFFF8A80"
            ))
        })
        put("image", JSONObject().apply {
            put("background", JSONObject().apply {
                put("sharpen", 12)
                put("saturation", 112)
                put("contrast", 103)
                put("brightness", 0)
            })
        })
    }

    private fun tokens(
        accent: String?,
        onAccent: String?,
        textPrimary: String?,
        textSecondary: String?,
        textDisabled: String?,
        surface: String?,
        card: String?,
        outline: String?,
        error: String?
    ): JSONObject = JSONObject().apply {
        accent?.let { put("accent", it) }
        onAccent?.let { put("onAccent", it) }
        textPrimary?.let { put("textPrimary", it) }
        textSecondary?.let { put("textSecondary", it) }
        textDisabled?.let { put("textDisabled", it) }
        surface?.let { put("surface", it) }
        card?.let { put("card", it) }
        outline?.let { put("outline", it) }
        error?.let { put("error", it) }
    }

    /** A theme.json that contains every field the loader understands, with valid defaults. */
    fun fullTemplate(): JSONObject = JSONObject().apply {
        put("version", MAX_VERSION)
        put("adaptive", true)
        put("appIconId", "default")
        put("customHomeTitle", "SukiSU")
        put("themeMode", 0)
        put("miuixMonet", true)
        put("customTexts", JSONObject())
        put("bottomBarIcons", JSONArray())
        put("moduleSortEnabledFirst", false)
        put("moduleSortActionFirst", false)
        put("moduleSortWebUiFirst", false)
        put("moduleSortZygiskFirst", false)
        put("moduleSortMetamoduleFirst", false)
        put("moduleSortNameOrder", "DEFAULT")
        put("moduleSortManual", false)
        put("appearance", defaultAppearance())
        put("light", variantTemplate(false))
        put("dark", variantTemplate(true))
    }

    fun defaultTransform(index: Int): JSONObject = JSONObject().apply {
        put("offsetX", when (index) { 0 -> 0.30; 1 -> -0.28; 2 -> 0.28; else -> -0.26 })
        put("offsetY", when (index) { 0 -> -0.28; 1 -> -0.28; 2 -> 0.28; else -> 0.28 })
        put("rotation", when (index) { 1 -> 8; 2 -> -6; 3 -> 10; 4 -> -10; else -> 0 })
        put("size", when (index) { 0 -> 0.20; 1 -> 0.16; 2 -> 0.12; else -> 0.10 })
        put("offsetYPct", when (index) { 0 -> -0.24; 1 -> -0.24; 2 -> 0.24; else -> 0.24 })
    }
}
