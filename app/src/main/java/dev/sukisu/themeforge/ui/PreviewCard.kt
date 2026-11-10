package dev.sukisu.themeforge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sukisu.themeforge.core.ImageInfo
import dev.sukisu.themeforge.core.ThemeDocument
import dev.sukisu.themeforge.core.ThemeSchema
import dev.sukisu.themeforge.core.ThemeSlots

private val DOCK_LABEL_KEYS = mapOf(
    "home" to "page.home",
    "superuser" to "page.superuser",
    "module" to "page.modules",
    "setting" to "page.settings"
)

private val DOCK_LABEL_DEFAULTS = mapOf(
    "home" to "首页",
    "superuser" to "超级用户",
    "module" to "模块",
    "setting" to "设置"
)

@Composable
fun PreviewCard(doc: ThemeDocument, dark: Boolean, modifier: Modifier = Modifier) {
    val title = doc.rootString("customHomeTitle", "SukiSU").ifBlank { "SukiSU" }
    val cardImage = doc.images[ThemeSlots.card(dark)]
    val cardBitmap = remember(cardImage) { cardImage?.let { ImageInfo.preview(it.bytes, 1600) } }
    val accent = Color(doc.int(dark, "keyColor", 0xFF007AFF.toInt()))

    Box(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.62f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    if (cardBitmap == null) {
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
        ) {
            val width = maxWidth
            val height = maxHeight
            if (cardBitmap != null) {
                Image(
                    bitmap = cardBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(18.dp)
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "LKM · v3.2.0",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Tag("已授权", accent)
                Tag("2 个应用", Color.White.copy(alpha = 0.16f))
            }
            for (index in doc.presentStickers(dark)) {
                val asset = doc.images[ThemeSlots.sticker(dark, index)] ?: continue
                val bitmap = remember(asset) { ImageInfo.preview(asset.bytes, 640) } ?: continue
                val transform = doc.stickerTransform(dark, index)
                val stickerSize = (transform.optDouble("size", 0.16).toFloat()).coerceIn(0.05f, 0.5f) * width
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(
                            x = width * transform.optDouble("offsetX", 0.0).toFloat(),
                            y = height * transform.optDouble("offsetY", 0.0).toFloat()
                        )
                        .rotate(transform.optDouble("rotation", 0.0).toFloat())
                        .size(stickerSize)
                )
            }
        }
    }
}

@Composable
fun DockPreview(doc: ThemeDocument, dark: Boolean, modifier: Modifier = Modifier) {
    val floating = doc.bool(dark, "enableFloatingBottomBar", true)
    val accent = Color(doc.int(dark, "keyColor", 0xFF007AFF.toInt()))
    val barColor = if (dark) Color(0xFF111B24) else Color(0xFFF3F6F8)
    val labelColor = if (dark) Color.White.copy(alpha = 0.72f) else Color(0xFF33404A)

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (floating) 20.dp else 0.dp)
                .clip(RoundedCornerShape(if (floating) 28.dp else 0.dp))
                .background(barColor)
                .border(
                    1.dp,
                    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
                    RoundedCornerShape(if (floating) 28.dp else 0.dp)
                )
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (slot in ThemeSchema.BOTTOM_BAR_SLOTS) {
                val entry = doc.images[ThemeSlots.bottomBarIcon(slot.key)]
                val bitmap = remember(entry) { entry?.let { ImageInfo.preview(it.bytes, 160) } }
                val label = doc.customText(DOCK_LABEL_KEYS[slot.key].orEmpty())
                    .ifBlank { DOCK_LABEL_DEFAULTS[slot.key].orEmpty() }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label.take(1),
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        color = labelColor,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.42f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
