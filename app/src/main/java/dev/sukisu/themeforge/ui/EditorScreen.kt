@file:OptIn(ExperimentalMaterial3Api::class)

package dev.sukisu.themeforge.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sukisu.themeforge.core.ImageAsset
import dev.sukisu.themeforge.core.ImageInfo
import dev.sukisu.themeforge.core.Slots
import dev.sukisu.themeforge.core.ThemeDocument
import dev.sukisu.themeforge.core.FileIo
import dev.sukisu.themeforge.core.formatBytes
import dev.sukisu.themeforge.vm.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val TAB_TITLES = listOf("基础", "外观", "图片", "JSON", "预览")

@Composable
fun EditorScreen(vm: EditorViewModel, doc: ThemeDocument) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }
    var dark by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    BackHandler { vm.close() }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        exporting = false
        if (uri != null && bytes != null) {
            runCatching { FileIo.write(context, uri, bytes) }
                .onSuccess { vm.notify("已导出 ${formatBytes(bytes.size.toLong())}") }
                .onFailure { vm.setError(it.message ?: "写入文件失败") }
        }
    }

    val onExport: () -> Unit = {
        if (!exporting) {
            exporting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { vm.buildPackage() }
                }
                result.onSuccess { bytes ->
                    if (bytes != null) {
                        pendingBytes = bytes
                        createLauncher.launch(vm.exportName())
                    } else {
                        exporting = false
                    }
                }.onFailure {
                    exporting = false
                    vm.setError(it.message ?: "导出失败")
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = { vm.close() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(
                            vm.projectName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "v${doc.effectiveVersion()} · ${doc.images.size} 张图片",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExport, enabled = !exporting) {
                        if (exporting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "导出")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title, fontSize = 13.sp) }
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> BasicTab(doc, dark, { dark = it })
                    1 -> AppearanceTab(doc, dark, { dark = it })
                    2 -> ImagesTab(doc, dark, { dark = it }, vm)
                    3 -> JsonTab(doc, vm)
                    else -> PreviewTab(doc, dark, { dark = it }, onExport)
                }
            }
        }
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

@Composable
private fun TabColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun BasicTab(doc: ThemeDocument, dark: Boolean, onDark: (Boolean) -> Unit) {
    val revision = doc.revision
    TabColumn {
        ModeChips(dark, onDark)

        SectionCard("主题标识", "写入 theme.json 的顶层字段") {
            OutlinedTextField(
                value = doc.rootString("customHomeTitle", "SukiSU"),
                onValueChange = { doc.putRoot("customHomeTitle", it) },
                label = { Text("首页标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = doc.rootString("appIconId", "sukisu_logo"),
                onValueChange = { doc.putRoot("appIconId", it) },
                label = { Text("应用图标 ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard("版本与能力", "格式版本不能低于内容所需能力") {
            DropdownField(
                label = "格式版本",
                value = "v${doc.version}",
                options = listOf("v3", "v4", "v5"),
                onSelect = { doc.setVersion(it.removePrefix("v").toInt()) }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "当前内容最低需要 v${doc.requiredVersion()}；导出时会自动提升到 v${doc.effectiveVersion()}。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = "外观覆盖 appearance",
                checked = doc.hasAppearance(),
                description = "v4+ 色板、文字颜色与图片效果，具体字段在 JSON 中编辑"
            ) { doc.setAppearance(it) }
        }

        SectionCard("首页布局", "同时应用到 light / dark") {
            DropdownField(
                label = "布局",
                value = doc.string(dark, "homeLayout", "classic"),
                options = listOf("classic", "immersive"),
                onSelect = { doc.putBoth("homeLayout", it) }
            )
            Spacer(Modifier.height(10.dp))
            DropdownField(
                label = "卡片图片样式",
                value = doc.string(dark, "homeCardImageStyle", "banner"),
                options = listOf("banner", "off"),
                onSelect = { doc.putBoth("homeCardImageStyle", it) }
            )
            Spacer(Modifier.height(10.dp))
            DropdownField(
                label = "界面模式",
                value = doc.string(dark, "uiMode", "miuix"),
                options = listOf("miuix", "material"),
                onSelect = { doc.putBoth("uiMode", it) }
            )
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                label = "隐藏状态文字",
                checked = doc.bool(dark, "hideHomeCardStatusText")
            ) { doc.put(dark, "hideHomeCardStatusText", it) }
            SwitchRow(
                label = "隐藏卡片装饰",
                checked = doc.bool(dark, "hideHomeCardDecoration", true)
            ) { doc.put(dark, "hideHomeCardDecoration", it) }
            SwitchRow(
                label = "无背景时也显示卡片图",
                checked = doc.bool(dark, "showHomeCardImageWithoutBackground", true)
            ) { doc.put(dark, "showHomeCardImageWithoutBackground", it) }
        }

        SectionCard("交互与显示") {
            SwitchRow(
                label = "浮动底栏",
                checked = doc.bool(dark, "enableFloatingBottomBar", true)
            ) { doc.put(dark, "enableFloatingBottomBar", it) }
            SwitchRow(
                label = "底栏模糊",
                checked = doc.bool(dark, "enableFloatingBottomBarBlur", true)
            ) { doc.put(dark, "enableFloatingBottomBarBlur", it) }
            SwitchRow(
                label = "导航角标",
                checked = doc.bool(dark, "enableNavigationBadge", true)
            ) { doc.put(dark, "enableNavigationBadge", it) }
            SwitchRow(
                label = "预测性返回",
                checked = doc.bool(dark, "enablePredictiveBack", true)
            ) { doc.put(dark, "enablePredictiveBack", it) }
            SwitchRow(
                label = "滑动关闭",
                checked = doc.bool(dark, "enableSwipeDismiss", true)
            ) { doc.put(dark, "enableSwipeDismiss", it) }
            SwitchRow(
                label = "全局模糊",
                checked = doc.bool(dark, "enableBlur", true)
            ) { doc.put(dark, "enableBlur", it) }
            SwitchRow(
                label = "渐进模糊",
                checked = doc.bool(dark, "enableProgressiveBlur", true)
            ) { doc.put(dark, "enableProgressiveBlur", it) }
            SliderRow(
                label = "模块描述行数",
                value = doc.int(dark, "moduleDescriptionMaxLines", 3).toDouble(),
                range = 1f..6f,
                steps = 4,
                valueText = doc.int(dark, "moduleDescriptionMaxLines", 3).toString()
            ) { doc.put(dark, "moduleDescriptionMaxLines", it.toInt()) }
        }
    }
}

@Composable
private fun AppearanceTab(doc: ThemeDocument, dark: Boolean, onDark: (Boolean) -> Unit) {
    val revision = doc.revision
    TabColumn {
        ModeChips(dark, onDark)

        SectionCard("强调色", "keyColor 是 ARGB 32 位整数") {
            ColorField(
                label = "浅色 keyColor",
                argb = argbOf(doc.int(false, "keyColor", 0xFF0F766E.toInt()))
            ) { doc.put(false, "keyColor", it.toInt()) }
            Spacer(Modifier.height(8.dp))
            ColorField(
                label = "深色 keyColor",
                argb = argbOf(doc.int(true, "keyColor", 0xFF2DD4BF.toInt()))
            ) { doc.put(true, "keyColor", it.toInt()) }
        }

        SectionCard("配色方案") {
            DropdownField(
                label = "colorStyle",
                value = doc.string(dark, "colorStyle", "TonalSpot"),
                options = listOf(
                    "TonalSpot", "Vibrant", "Expressive", "Neutral",
                    "Monochrome", "Fidelity", "Content", "Rainbow", "FruitSalad"
                ),
                onSelect = { doc.putBoth("colorStyle", it) }
            )
            Spacer(Modifier.height(10.dp))
            DropdownField(
                label = "colorSpec",
                value = doc.string(dark, "colorSpec", "SPEC_2025"),
                options = listOf("SPEC_2021", "SPEC_2025"),
                onSelect = { doc.putBoth("colorSpec", it) }
            )
            SwitchRow(
                label = "miuixMonet",
                checked = doc.bool(dark, "miuixMonet")
            ) { doc.put(dark, "miuixMonet", it) }
        }

        SectionCard("玻璃与背景") {
            SliderRow(
                label = "卡片不透明度",
                value = doc.int(dark, "cardBackgroundAlphaPercent", 76).toDouble(),
                range = 0f..100f,
                valueText = "${doc.int(dark, "cardBackgroundAlphaPercent", 76)}%"
            ) { doc.put(dark, "cardBackgroundAlphaPercent", it.toInt()) }
            SliderRow(
                label = "背景模糊",
                value = doc.int(dark, "customBackgroundBlurPercent", 20).toDouble(),
                range = 0f..100f,
                valueText = "${doc.int(dark, "customBackgroundBlurPercent", 20)}%"
            ) { doc.put(dark, "customBackgroundBlurPercent", it.toInt()) }
            SliderRow(
                label = "背景压暗",
                value = doc.int(dark, "customBackgroundScrimPercent", -8).toDouble(),
                range = -100f..100f,
                valueText = "${doc.int(dark, "customBackgroundScrimPercent", -8)}%"
            ) { doc.put(dark, "customBackgroundScrimPercent", it.toInt()) }
        }

        SectionCard("首页尺寸") {
            SliderRow(
                label = "状态胶囊宽度",
                value = doc.double(dark, "homeStatusWidthFraction", 0.94),
                range = 0.5f..1f
            ) { doc.put(dark, "homeStatusWidthFraction", it) }
            SliderRow(
                label = "状态缩放",
                value = doc.double(dark, "homeStatusScale", 1.0),
                range = 0.6f..1.6f
            ) { doc.put(dark, "homeStatusScale", it) }
            SliderRow(
                label = "胶囊缩放",
                value = doc.double(dark, "homePillScale", 1.0),
                range = 0.6f..1.6f
            ) { doc.put(dark, "homePillScale", it) }
            SliderRow(
                label = "页面缩放",
                value = doc.double(dark, "pageScale", 1.0),
                range = 0.8f..1.2f
            ) { doc.put(dark, "pageScale", it) }
            Spacer(Modifier.height(6.dp))
            DropdownField(
                label = "状态对齐",
                value = doc.string(dark, "homeStatusAlignment", "start"),
                options = listOf("start", "center", "end"),
                onSelect = { doc.putBoth("homeStatusAlignment", it) }
            )
        }
    }
}

@Composable
private fun ImagesTab(
    doc: ThemeDocument,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    vm: EditorViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSlot by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri != null && slot != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = FileIo.read(context, uri, 64L * 1024 * 1024)
                        val size = ImageInfo.decodeSize(bytes)
                            ?: error("无法解码图片")
                        ImageAsset(bytes, slot, size.first, size.second)
                    }
                }
                result.onSuccess { doc.putImage(slot, it) }
                    .onFailure { vm.setError(it.message ?: "读取图片失败") }
            }
        }
    }

    val pick: (String) -> Unit = { slot ->
        pendingSlot = slot
        picker.launch(arrayOf("image/*"))
    }

    TabColumn {
        ModeChips(dark, onDark)

        SectionCard("壁纸", "9:16 竖图，建议 1440 × 3120") {
            ImageSlotCard(
                label = "background_${Slots.mode(dark)}",
                asset = doc.images[Slots.background(dark)],
                aspect = 9f / 16f,
                onPick = { pick(Slots.background(dark)) },
                onClear = { doc.removeImage(Slots.background(dark)) }
            )
        }

        SectionCard("首页卡片", "约 1.9:1，建议 1600 × 840") {
            ImageSlotCard(
                label = "card_background_${Slots.mode(dark)}",
                asset = doc.images[Slots.card(dark)],
                aspect = 1.9f,
                onPick = { pick(Slots.card(dark)) },
                onClear = { doc.removeImage(Slots.card(dark)) }
            )
        }

        SectionCard("贴纸", "透明 PNG / WebP，最多 3 个，位置由 transforms 决定") {
            for (index in 0 until 3) {
                ImageSlotCard(
                    label = "card_sticker_${Slots.mode(dark)}_$index",
                    asset = doc.images[Slots.sticker(dark, index)],
                    aspect = 1f,
                    onPick = { pick(Slots.sticker(dark, index)) },
                    onClear = { doc.removeImage(Slots.sticker(dark, index)) }
                )
                if (index < 2) Spacer(Modifier.height(14.dp))
            }
        }

        OutlinedButton(
            onClick = { doc.clearModeImages(dark) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清空本模式图片")
        }
    }
}

@Composable
private fun ImageSlotCard(
    label: String,
    asset: ImageAsset?,
    aspect: Float,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (asset != null) {
                val bitmap = remember(asset) { ImageInfo.preview(asset.bytes, 900) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Text(
                    "空槽位",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                if (asset != null) {
                    Text(
                        "${asset.width} × ${asset.height} · ${asset.sizeKb} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onPick) { Text(if (asset == null) "选择" else "替换") }
            if (asset != null) {
                TextButton(onClick = onClear) { Text("移除") }
            }
        }
    }
}

@Composable
private fun JsonTab(doc: ThemeDocument, vm: EditorViewModel) {
    var text by remember(doc) { mutableStateOf(doc.toPrettyString()) }

    TabColumn {
        SectionCard("theme.json", "高级字段直接编辑；应用后会按图片槽位重新同步 hasBackground 等标志") {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(430.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 40
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        text = runCatching { JSONObject(text).toString(2) }.getOrElse { text }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("格式化") }
                Button(
                    onClick = {
                        if (vm.applyJson(text)) text = doc.toPrettyString()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("校验并应用") }
            }
        }

        SectionCard("提示") {
            Text(
                "version 仅支持 3、4、5。包含 appearance 至少 v4；包含 immersive 布局或 bottomBarIcons 至少 v5。导出时会自动取内容所需的最低版本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviewTab(
    doc: ThemeDocument,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    onExport: () -> Unit
) {
    val imageBytes = doc.images.values.sumOf { it.bytes.size.toLong() }
    val jsonBytes = doc.toPrettyString().toByteArray().size.toLong()

    TabColumn {
        ModeChips(dark, onDark)
        PreviewCard(doc, dark)

        SectionCard("包信息") {
            InfoRow("格式版本", "v${doc.effectiveVersion()}")
            InfoRow("内容最低要求", "v${doc.requiredVersion()}")
            InfoRow("图片数量", "${doc.images.size} 张")
            InfoRow("估算大小", formatBytes(imageBytes + jsonBytes + 28))
            InfoRow("加密", "AES-256-GCM / SKTH")
        }

        Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("导出 .sukisu")
        }

        Text(
            "预览只还原首页卡片区域，不包含系统状态栏、统计卡和底栏；贴纸位置来自 homeCardStickerTransforms。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun argbOf(value: Int): Long = value.toLong() and 0xFFFFFFFFL
