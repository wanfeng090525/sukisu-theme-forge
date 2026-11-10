package dev.sukisu.themeforge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sukisu.themeforge.core.ImageInfo
import dev.sukisu.themeforge.core.Slots
import dev.sukisu.themeforge.core.ThemeDocument
import kotlin.math.max

private data class StickerTransform(val x: Float, val y: Float, val size: Float)

@Composable
fun PreviewCard(doc: ThemeDocument, dark: Boolean) {
    val variant = doc.variant(dark)
    val keyColor = Color(variant.optInt("keyColor", 0xFF0F766E.toInt()))
    val cardAsset = doc.images[Slots.card(dark)]
    val cardBitmap = remember(cardAsset) {
        cardAsset?.let { ImageInfo.preview(it.bytes, 1600) }
    }
    val transforms = remember(doc.revision, dark) { loadTransforms(doc, dark) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(26.dp))
            .background(if (dark) Color(0xFF0A1422) else Color(0xFFDDEBFF))
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.18f else 0.65f), RoundedCornerShape(26.dp))
    ) {
        val cardWidth = maxWidth
        val cardHeight = maxHeight

        if (cardBitmap != null) {
            Image(
                bitmap = cardBitmap,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            if (dark) Color.Black.copy(alpha = 0.50f)
                            else Color.White.copy(alpha = 0.60f)
                        )
                    )
                )
        )

        for ((index, transform) in transforms.withIndex()) {
            val asset = doc.images[Slots.sticker(dark, index)] ?: continue
            val bitmap = remember(asset) { ImageInfo.preview(asset.bytes, 512) } ?: continue
            val size = cardWidth * transform.size
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = cardWidth * (0.5f + transform.x) - size / 2,
                        y = cardHeight * (0.5f + transform.y) - size / 2
                    )
                    .size(size)
            )
        }

        Tag(
            text = "LKM",
            color = keyColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                "越狱模式",
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                color = if (dark) Color.White else Color(0xFF0A1220)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "v4.3.0-r2 (41168-6)",
                fontSize = 9.sp,
                color = if (dark) Color.White.copy(alpha = 0.72f)
                else Color(0xFF3A4A46).copy(alpha = 0.75f)
            )
        }

        if (cardBitmap == null) {
            Text(
                "未选择首页卡片图",
                style = MaterialTheme.typography.labelMedium,
                color = if (dark) Color.White.copy(alpha = 0.7f) else Color(0xFF445752),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

private fun loadTransforms(doc: ThemeDocument, dark: Boolean): List<StickerTransform> {
    val array = doc.variant(dark).optJSONArray("homeCardStickerTransforms")
    if (array != null && array.length() > 0) {
        return (0 until array.length()).map { index ->
            val item = array.optJSONObject(index)
            StickerTransform(
                x = item?.optDouble("offsetX", 0.0)?.toFloat() ?: 0f,
                y = item?.optDouble("offsetY", 0.0)?.toFloat() ?: 0f,
                size = max(0.06f, item?.optDouble("size", 0.20)?.toFloat() ?: 0.20f)
            )
        }
    }
    val present = (0 until 3).filter { doc.images.containsKey(Slots.sticker(dark, it)) }
    return present.map { defaultTransform(it) }
}

private fun defaultTransform(index: Int) = StickerTransform(
    x = when (index) { 0 -> 0.27f; 1 -> -0.32f; else -> 0f },
    y = when (index) { 0 -> -0.28f; 1 -> -0.28f; else -> 0.30f },
    size = when (index) { 0 -> 0.20f; 1 -> 0.14f; else -> 0.10f }
)
