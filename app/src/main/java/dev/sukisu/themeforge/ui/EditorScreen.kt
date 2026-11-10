package dev.sukisu.themeforge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sukisu.themeforge.core.CustomTextDef
import dev.sukisu.themeforge.core.FileIo
import dev.sukisu.themeforge.core.ThemeDocument
import dev.sukisu.themeforge.core.ThemeSchema
import dev.sukisu.themeforge.core.ThemeSlots
import dev.sukisu.themeforge.core.formatBytes
import dev.sukisu.themeforge.vm.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAB_TITLES = listOf("基础", "外观", "首页", "底栏", "图片", "文案", "JSON", "预览")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val doc = vm.doc ?: return
    @Suppress("UNUSED_VARIABLE") val revision = doc.revision
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = vm.buildPackage() ?: error("没有可导出的主题")
                    FileIo.write(context, uri, bytes)
                    bytes
                }
            }
            result.onSuccess {
                vm.notify("已导出 ${vm.exportName()}（${formatBytes(it.size.toLong())}）")
            }.onFailure { vm.reportError(it.message ?: "导出失败") }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ThemeForge", fontWeight = FontWeight.SemiBold)
                        Text(
                            vm.projectName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.close() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { createLauncher.launch(vm.exportName()) }) {
                        Text("导出")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 8.dp,
                containerColor = Color.Transparent
            ) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) }
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> BasicTab(doc, vm)
                    1 -> AppearanceTab(doc, vm, dark) { dark = it }
                    2 -> HomeTab(doc, vm, dark) { dark = it }
                    3 -> DockTab(doc, vm, dark) { dark = it }
                    4 -> ImagesTab(doc, vm, dark) { dark = it }
                    5 -> TextsTab(doc, vm)
                    6 -> JsonTab(doc, vm)
                    else -> PreviewTab(doc, vm, dark, { dark = it }) {
                        createLauncher.launch(vm.exportName())
                    }
                }
            }
        }
    }

    if (busy) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("正在打包") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("写入 .sukisu…")
                }
            }
        )
    }

    vm.error?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.dismissError() },
            title = { Text("出错了") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { vm.dismissError() }) { Text("知道了") }
            }
        )
    }
}

// ---------------------------------------------------------------- basic tab

@Composable
private fun BasicTab(doc: ThemeDocument, vm: EditorViewModel) {
    TabColumn {
        SectionCard("项目") {
            TextInputRow(
                label = "导出文件名",
                value = vm.projectName,
                placeholder = "MyTheme",
                onValueChange = vm::renameProject
            )
        }
        SectionCard("主题标识", "写入 theme.json 顶部，明暗共用") {
            TextInputRow(
                label = "首页标题 customHomeTitle",
                value = doc.rootString("customHomeTitle", "SukiSU"),
                onValueChange = { doc.putRoot("customHomeTitle", it) }
            )
            Spacer(Modifier.height(4.dp))
            DropdownField(
                label = "应用图标 appIconId",
                value = doc.rootString("appIconId", "default"),
                options = ThemeSchema.APP_ICONS
            ) { doc.putRoot("appIconId", it) }
            Spacer(Modifier.height(8.dp))
            DropdownField(
                label = "格式版本 version",
                value = doc.version.toString(),
                options = listOf("3", "4", "5")
            ) { doc.setVersion(it.toIntOrNull() ?: ThemeSchema.MIN_VERSION) }
            Text(
                "当前内容需求 v${doc.requiredVersion()}，导出时自动提升到最高需求版本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SectionCard("界面模式", "写入 light / dark 两套配置") {
            ChoiceRow(
                label = "界面风格 uiMode",
                value = doc.string(false, "uiMode", "miuix"),
                choices = listOf("Miuix" to "miuix", "Material" to "material")
            ) { doc.putBoth("uiMode", it) }
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                label = "主题模式 themeMode",
                value = doc.int(false, "themeMode", 0).toString(),
                choices = ThemeSchema.THEME_MODES.map { it.second to it.first.toString() }
            ) { value ->
                value.toIntOrNull()?.let { doc.putBoth("themeMode", it) }
            }
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = "启用 Monet 取色 miuixMonet",
                checked = doc.bool(false, "miuixMonet", true)
            ) { doc.putBoth("miuixMonet", it) }
        }
        SectionCard("全局行为") {
            SliderRow(
                label = "页面缩放 pageScale",
                value = doc.double(false, "pageScale", 1.0),
                range = 0.80f..1.10f,
                valueText = String.format("%.2f", doc.double(false, "pageScale", 1.0))
            ) { doc.putBoth("pageScale", it) }
            IntSliderRow(
                label = "模块简介行数 moduleDescriptionMaxLines",
                value = doc.int(false, "moduleDescriptionMaxLines", 3),
                range = 1..5,
                unit = " 行"
            ) { doc.putBoth("moduleDescriptionMaxLines", it) }
        }
        SectionCard("模块列表排序", "写入 theme.json 顶部的全局排序规则") {
            SwitchRow("已启用优先", doc.rootBool("moduleSortEnabledFirst")) {
                doc.putRoot("moduleSortEnabledFirst", it)
            }
            SwitchRow("操作优先", doc.rootBool("moduleSortActionFirst")) {
                doc.putRoot("moduleSortActionFirst", it)
            }
            SwitchRow("WebUI 优先", doc.rootBool("moduleSortWebUiFirst")) {
                doc.putRoot("moduleSortWebUiFirst", it)
            }
            SwitchRow("Zygisk 优先", doc.rootBool("moduleSortZygiskFirst")) {
                doc.putRoot("moduleSortZygiskFirst", it)
            }
            SwitchRow("元模块优先", doc.rootBool("moduleSortMetamoduleFirst")) {
                doc.putRoot("moduleSortMetamoduleFirst", it)
            }
            Spacer(Modifier.height(4.dp))
            DropdownField(
                label = "名称排序 moduleSortNameOrder",
                value = doc.rootString("moduleSortNameOrder", "DEFAULT"),
                options = ThemeSchema.MODULE_NAME_ORDERS
            ) { doc.putRoot("moduleSortNameOrder", it) }
            Spacer(Modifier.height(4.dp))
            SwitchRow("保持手动排序", doc.rootBool("moduleSortManual")) {
                doc.putRoot("moduleSortManual", it)
            }
        }
        SectionCard("格式状态") {
            InfoRow("主题类型", if (doc.adaptive) "adaptive 自适应" else "旧版")
            InfoRow("当前版本", "v${doc.version}")
            InfoRow("导出所需版本", "v${doc.effectiveVersion()}")
            InfoRow("appearance 覆盖", if (doc.hasAppearance()) "已启用" else "未启用")
            InfoRow("图片数量", "${doc.images.size} 张")
            InfoRow("自定义文案", "${doc.customTextCount()} 项")
            if (doc.effectiveVersion() >= ThemeSchema.MAX_VERSION) {
                Text(
                    "包含沉浸式布局、底栏图标等 v5 能力",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ----------------------------------------------------------- appearance tab

@Composable
private fun AppearanceTab(
    doc: ThemeDocument,
    vm: EditorViewModel,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit
) {
    var overlayMode by remember { mutableStateOf("light") }
    TabColumn {
        SectionCard("明暗选择", "浅色与深色的颜色、图片、布局完全独立") {
            ModeChips(dark = dark, onChange = onDarkChange)
        }
        SectionCard("配色") {
            ColorField(
                label = "强调色 keyColor",
                argb = argbOf(doc.int(dark, "keyColor", 0xFF007AFF.toInt()))
            ) { doc.put(dark, "keyColor", it.toInt()) }
            ChoiceRow(
                label = "配色风格 colorStyle",
                value = doc.string(dark, "colorStyle", "TonalSpot"),
                choices = ThemeSchema.COLOR_STYLES.map { it to it }
            ) { doc.put(dark, "colorStyle", it) }
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                label = "配色规范 colorSpec",
                value = doc.string(dark, "colorSpec", "SPEC_2025"),
                choices = ThemeSchema.COLOR_SPECS.map { it to it }
            ) { doc.put(dark, "colorSpec", it) }
        }
        SectionCard("玻璃与背景") {
            IntSliderRow(
                label = "卡片不透明度 cardBackgroundAlphaPercent",
                value = doc.int(dark, "cardBackgroundAlphaPercent", 76),
                range = 10..91,
                unit = "%"
            ) { doc.put(dark, "cardBackgroundAlphaPercent", it) }
            IntSliderRow(
                label = "自定义背景模糊 customBackgroundBlurPercent",
                value = doc.int(dark, "customBackgroundBlurPercent", 20),
                range = 0..100,
                unit = "%"
            ) { doc.put(dark, "customBackgroundBlurPercent", it) }
            IntSliderRow(
                label = "背景压暗 customBackgroundScrimPercent",
                value = doc.int(dark, "customBackgroundScrimPercent", -8),
                range = -100..100,
                unit = "%"
            ) { doc.put(dark, "customBackgroundScrimPercent", it) }
            SwitchRow("启用模糊 enableBlur", doc.bool(dark, "enableBlur", true)) {
                doc.put(dark, "enableBlur", it)
            }
            SwitchRow(
                "渐进模糊 enableProgressiveBlur",
                doc.bool(dark, "enableProgressiveBlur", true)
            ) { doc.put(dark, "enableProgressiveBlur", it) }
        }
        SectionCard(
            "外观覆盖 appearance",
            "直接覆盖管理器颜色令牌，需要 v4；未启用时使用 keyColor 派生配色"
        ) {
            SwitchRow(
                "启用 appearance 覆盖",
                doc.hasAppearance()
            ) { doc.setAppearance(it) }
            if (doc.hasAppearance()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeSchema.APPEARANCE_MODES.forEach { (key, label) ->
                        FilterChipSafe(selected = overlayMode == key, label = label) {
                            overlayMode = key
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                ThemeSchema.COLOR_TOKENS.forEach { token ->
                    ColorField(
                        label = "${token.label} ${token.key}",
                        argb = parseColor(doc.appearanceColor(overlayMode, token.key)) ?: 0L
                    ) { doc.setAppearanceColor(overlayMode, token.key, String.format("#%08X", it)) }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        val defaults = ThemeSchema.defaultAppearance()
                        ThemeSchema.COLOR_TOKENS.forEach { token ->
                            val hex = defaults.optJSONObject("colors")
                                ?.optJSONObject(overlayMode)
                                ?.optString(token.key, "")
                            if (hex.isNotBlank()) doc.setAppearanceColor(overlayMode, token.key, hex)
                        }
                        vm.notify("已恢复 $overlayMode 默认外观色")
                    }
                ) { Text("恢复本模式默认色") }
                Spacer(Modifier.height(8.dp))
                ThemeSchema.IMAGE_EFFECTS.forEach { (key, label) ->
                    val value = doc.appearanceEffect(key)
                    val range = when (key) {
                        "sharpen" -> 0f..100f
                        "brightness" -> -100f..100f
                        else -> 20f..200f
                    }
                    SliderRow(
                        label = "$label image.background.$key",
                        value = value,
                        range = range,
                        valueText = String.format("%.0f", value)
                    ) { doc.setAppearanceEffect(key, it) }
                }
            }
        }
        SectionCard("提示") {
            Text(
                "appearance 的 shared 段对所有模式生效，light / dark 段分别覆盖。全部填满后即可完全不依赖 keyColor 派生。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- home tab

@Composable
private fun HomeTab(
    doc: ThemeDocument,
    vm: EditorViewModel,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit
) {
    TabColumn {
        SectionCard("明暗选择") {
            ModeChips(dark = dark, onChange = onDarkChange)
        }
        SectionCard("首页布局") {
            ChoiceRow(
                label = "布局 homeLayout",
                value = doc.string(dark, "homeLayout", "classic"),
                choices = listOf("经典 classic" to "classic", "沉浸式 immersive" to "immersive")
            ) { doc.put(dark, "homeLayout", it) }
            Spacer(Modifier.height(6.dp))
            ChoiceRow(
                label = "卡片图片样式 homeCardImageStyle",
                value = doc.string(dark, "homeCardImageStyle", "banner"),
                choices = listOf("横幅 banner" to "banner", "关闭 off" to "off")
            ) { doc.put(dark, "homeCardImageStyle", it) }
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                "无背景时也显示卡片图",
                doc.bool(dark, "showHomeCardImageWithoutBackground", true)
            ) { doc.put(dark, "showHomeCardImageWithoutBackground", it) }
        }
        SectionCard("状态区") {
            ChoiceRow(
                label = "对齐方式 homeStatusAlignment",
                value = doc.string(dark, "homeStatusAlignment", "start"),
                choices = listOf("左对齐 start" to "start", "右对齐 end" to "end")
            ) { doc.put(dark, "homeStatusAlignment", it) }
            SliderRow(
                label = "状态缩放 homeStatusScale",
                value = doc.double(dark, "homeStatusScale", 1.0),
                range = 0.5f..3.0f,
                valueText = String.format("%.2f×", doc.double(dark, "homeStatusScale", 1.0))
            ) { doc.put(dark, "homeStatusScale", it) }
            SliderRow(
                label = "胶囊缩放 homePillScale",
                value = doc.double(dark, "homePillScale", 1.0),
                range = 0.5f..1.5f,
                valueText = String.format("%.2f×", doc.double(dark, "homePillScale", 1.0))
            ) { doc.put(dark, "homePillScale", it) }
            SliderRow(
                label = "状态宽度 homeStatusWidthFraction",
                value = doc.double(dark, "homeStatusWidthFraction", 0.94),
                range = 0.45f..1.0f,
                valueText = String.format("%.0f%%", doc.double(dark, "homeStatusWidthFraction", 0.94) * 100)
            ) { doc.put(dark, "homeStatusWidthFraction", it) }
            SwitchRow(
                "隐藏状态文字 hideHomeCardStatusText",
                doc.bool(dark, "hideHomeCardStatusText")
            ) { doc.put(dark, "hideHomeCardStatusText", it) }
            SwitchRow(
                "隐藏卡片装饰 hideHomeCardDecoration",
                doc.bool(dark, "hideHomeCardDecoration")
            ) { doc.put(dark, "hideHomeCardDecoration", it) }
        }
        SectionCard("提示") {
            Text(
                "沉浸式布局和底栏图标需要 v5。卡片图、贴纸请在『图片』页设置；底栏图标在『底栏』页设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- dock tab

@Composable
private fun DockTab(
    doc: ThemeDocument,
    vm: EditorViewModel,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit
) {
    TabColumn {
        SectionCard(
            "底栏图标（全局）",
            "替换 dock 的 4 个图标，自动写入 bottom_bar_icon_* 条目；需要 v5。底栏文字在『文案』页修改 page.*"
        ) {
            ThemeSchema.BOTTOM_BAR_SLOTS.forEach { slot ->
                ImageSlotCard(
                    doc = doc,
                    slot = ThemeSlots.bottomBarIcon(slot.key),
                    title = slot.label,
                    subtitle = "建议 24×24 或 96×96 的透明 PNG / WebP",
                    aspect = 1f,
                    onError = vm::reportError
                )
            }
            if (doc.images.keys.any { ThemeSlots.isBottomBarIcon(it) }) {
                OutlinedButton(onClick = { doc.clearBottomBarIcons() }) {
                    Text("清除全部底栏图标")
                }
            }
        }
        SectionCard("明暗选择", "底栏行为开关按浅色 / 深色独立保存") {
            ModeChips(dark = dark, onChange = onDarkChange)
        }
        SectionCard("底栏行为") {
            SwitchRow(
                "悬浮底栏 enableFloatingBottomBar",
                doc.bool(dark, "enableFloatingBottomBar", true)
            ) { doc.put(dark, "enableFloatingBottomBar", it) }
            SwitchRow(
                "底栏模糊 enableFloatingBottomBarBlur",
                doc.bool(dark, "enableFloatingBottomBarBlur", true)
            ) { doc.put(dark, "enableFloatingBottomBarBlur", it) }
            SwitchRow(
                "导航角标 enableNavigationBadge",
                doc.bool(dark, "enableNavigationBadge", true)
            ) { doc.put(dark, "enableNavigationBadge", it) }
            SwitchRow(
                "预测性返回 enablePredictiveBack",
                doc.bool(dark, "enablePredictiveBack", true)
            ) { doc.put(dark, "enablePredictiveBack", it) }
            SwitchRow(
                "滑动关闭 enableSwipeDismiss",
                doc.bool(dark, "enableSwipeDismiss", true)
            ) { doc.put(dark, "enableSwipeDismiss", it) }
        }
        SectionCard("手势与尺寸") {
            ChoiceRow(
                label = "页面滑动拦截 pagerInterceptionMode",
                value = doc.int(dark, "pagerInterceptionMode", 1).toString(),
                choices = listOf(
                    "关闭" to "0",
                    "边缘拦截" to "1",
                    "全区域拦截" to "2"
                )
            ) { value -> value.toIntOrNull()?.let { doc.put(dark, "pagerInterceptionMode", it) } }
            SliderRow(
                label = "页面缩放 pageScale",
                value = doc.double(dark, "pageScale", 1.0),
                range = 0.80f..1.10f,
                valueText = String.format("%.2f", doc.double(dark, "pageScale", 1.0))
            ) { doc.put(dark, "pageScale", it) }
        }
        SectionCard("效果预览") {
            DockPreview(doc = doc, dark = dark)
        }
    }
}

// -------------------------------------------------------------- images tab

@Composable
private fun ImagesTab(
    doc: ThemeDocument,
    vm: EditorViewModel,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit
) {
    TabColumn {
        SectionCard("明暗选择", "图片按浅色 / 深色分别存储") {
            ModeChips(dark = dark, onChange = onDarkChange)
        }
        SectionCard("壁纸与首页卡片") {
            ImageSlotCard(
                doc = doc,
                slot = ThemeSlots.background(dark),
                title = "全屏壁纸 background",
                subtitle = "推荐竖屏 1080×2400，JPG / WebP",
                aspect = 0.56f,
                onError = vm::reportError
            )
            ImageSlotCard(
                doc = doc,
                slot = ThemeSlots.card(dark),
                title = "首页卡片 card_background",
                subtitle = "推荐 1600×840，含玻璃、时钟等细节",
                aspect = 1.9f,
                onError = vm::reportError
            )
        }
        SectionCard(
            "首页贴纸",
            "最多 ${ThemeSchema.STICKER_COUNT} 个，透明 PNG；位置、旋转、大小可分别调整"
        ) {
            for (index in 0 until ThemeSchema.STICKER_COUNT) {
                val slot = ThemeSlots.sticker(dark, index)
                ImageSlotCard(
                    doc = doc,
                    slot = slot,
                    title = "贴纸 ${index + 1}",
                    subtitle = "叠加在首页卡片上",
                    aspect = 1.4f,
                    onError = vm::reportError
                )
                if (doc.images.containsKey(slot)) {
                    StickerTransformEditor(doc, dark, index)
                }
            }
        }
        OutlinedButton(
            onClick = {
                doc.clearModeImages(dark)
                vm.notify("已清空${if (dark) "深色" else "浅色"}图片")
            }
        ) {
            Text("清空本模式全部图片")
        }
    }
}

@Composable
private fun StickerTransformEditor(doc: ThemeDocument, dark: Boolean, index: Int) {
    val transform = doc.stickerTransform(dark, index)
    Column(Modifier.fillMaxWidth()) {
        SliderRow(
            label = "水平位置 offsetX",
            value = transform.optDouble("offsetX", 0.0),
            range = -0.5f..0.5f,
            valueText = String.format("%.2f", transform.optDouble("offsetX", 0.0))
        ) { doc.setStickerTransform(dark, index, "offsetX", it) }
        SliderRow(
            label = "垂直位置 offsetY",
            value = transform.optDouble("offsetY", 0.0),
            range = -0.5f..0.5f,
            valueText = String.format("%.2f", transform.optDouble("offsetY", 0.0))
        ) { doc.setStickerTransform(dark, index, "offsetY", it) }
        SliderRow(
            label = "旋转 rotation",
            value = transform.optDouble("rotation", 0.0),
            range = -180f..180f,
            valueText = String.format("%.0f°", transform.optDouble("rotation", 0.0))
        ) { doc.setStickerTransform(dark, index, "rotation", it) }
        SliderRow(
            label = "大小 size",
            value = transform.optDouble("size", 0.16),
            range = 0.05f..0.40f,
            valueText = String.format("%.0f%%", transform.optDouble("size", 0.16) * 100)
        ) { doc.setStickerTransform(dark, index, "size", it) }
    }
}

// --------------------------------------------------------------- texts tab

@Composable
private fun TextsTab(doc: ThemeDocument, vm: EditorViewModel) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CustomTextDef?>(null) }
    val filtered = remember(query) {
        ThemeSchema.CUSTOM_TEXTS.filter {
            query.isBlank() ||
                it.key.contains(query, ignoreCase = true) ||
                it.label.contains(query, ignoreCase = true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(
            "自定义文案 customTexts",
            "共 ${ThemeSchema.CUSTOM_TEXTS.size} 个可覆盖文案，已自定义 ${doc.customTextCount()} 项；留空表示使用系统默认"
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索 key 或名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "支持 %s 占位符的条目会在应用时自动填充版本号等内容",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ThemeSchema.CUSTOM_TEXT_GROUPS.forEach { group ->
            val items = filtered.filter { it.group == group }
            if (items.isEmpty()) return@forEach
            SectionCard(group) {
                items.forEach { def ->
                    val value = doc.customText(def.key)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = def }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(def.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                def.key,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            value.ifBlank { "默认" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (value.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
        }
        Text(
            "来源：SukiSU Ultra CustomTextKey 全量键位。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    editing?.let { def ->
        var value by remember(def.key) { mutableStateOf(doc.customText(def.key)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(def.label) },
            text = {
                Column {
                    Text(
                        def.key,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("文案内容") },
                        modifier = Modifier.heightIn(min = 96.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        doc.putCustomText(def.key, value)
                        editing = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        doc.putCustomText(def.key, "")
                        editing = null
                        vm.notify("已恢复默认：${def.label}")
                    }
                ) { Text("恢复默认") }
            }
        )
    }
}

// ---------------------------------------------------------------- json tab

@Composable
private fun JsonTab(doc: ThemeDocument, vm: EditorViewModel) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf(doc.toPrettyString()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "完整 theme.json：包含全局字段、light / dark、appearance、排序与底栏。修改后点『应用』立即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (vm.loadFullTemplate()) text = doc.toPrettyString()
            }) { Text("载入完整模板") }
            OutlinedButton(onClick = {
                vm.formatJson(text)?.let { text = it }
            }) { Text("格式化") }
            Button(onClick = {
                if (vm.applyJson(text)) text = doc.toPrettyString()
            }) { Text("应用") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                vm.notify("JSON 已复制")
            }) { Text("复制") }
            OutlinedButton(onClick = {
                clipboard.getText()?.text?.let { text = it }
            }) { Text("粘贴") }
            OutlinedButton(onClick = { text = doc.toPrettyString() }) { Text("还原") }
        }
        vm.jsonError?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("theme.json") },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
        )
    }
}

// ------------------------------------------------------------- preview tab

@Composable
private fun PreviewTab(
    doc: ThemeDocument,
    vm: EditorViewModel,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    onExport: () -> Unit
) {
    val imageBytes = doc.images.values.sumOf { it.bytes.size.toLong() }
    TabColumn {
        SectionCard("明暗选择") {
            ModeChips(dark = dark, onChange = onDarkChange)
        }
        SectionCard("首页卡片") {
            PreviewCard(doc = doc, dark = dark)
        }
        SectionCard("底栏") {
            DockPreview(doc = doc, dark = dark)
        }
        SectionCard("包信息") {
            InfoRow("版本", "v${doc.version} → v${doc.effectiveVersion()}")
            InfoRow("应用图标", doc.rootString("appIconId", "default"))
            InfoRow("图片", "${doc.images.size} 张 · ${formatBytes(imageBytes)}")
            InfoRow("贴纸", "${doc.presentStickers(false).size} / ${doc.presentStickers(true).size}")
            InfoRow(
                "底栏图标",
                "${doc.images.keys.count { ThemeSlots.isBottomBarIcon(it) }} / 4"
            )
            InfoRow("自定义文案", "${doc.customTextCount()} 项")
            InfoRow("导出文件名", vm.exportName())
        }
        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("导出 .sukisu") }
    }
}

// ---------------------------------------------------------------- helpers

@Composable
private fun TabColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun ChoiceRow(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    val current = choices.firstOrNull { it.second == value }?.first ?: value
    DropdownField(
        label = label,
        value = current,
        options = choices.map { it.first }
    ) { picked ->
        choices.firstOrNull { it.first == picked }?.second?.let(onSelect)
    }
}

@Composable
private fun FilterChipSafe(selected: Boolean, label: String, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

private fun argbOf(value: Int): Long = value.toLong() and 0xFFFFFFFFL
