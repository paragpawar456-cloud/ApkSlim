package com.parag.apkslim.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parag.apkslim.R
import com.parag.apkslim.core.Category
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.theme.AppTheme
import com.parag.apkslim.ui.theme.Brand
import com.parag.apkslim.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The ApkSlim mark on its orange tile. */
@Composable
fun BrandMark(size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brand.Orange), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size * 1.5f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        trailing?.invoke()
    }
}

/** Small ALL-CAPS overline used above sections, like food apps' "RECOMMENDED FOR YOU". */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(AppTheme.extra.divider))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AppTheme.extra.muted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(AppTheme.extra.divider))
    }
}

/** White card with a hairline border; the basic building block. */
@Composable
fun ElevatedSection(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = AppTheme.extra.card,
        border = BorderStroke(1.dp, AppTheme.extra.divider),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun AppIcon(key: Any, size: Dp = 44.dp, loader: () -> Bitmap?) {
    // Icons already seen are drawn on the first frame (no placeholder flash when returning to a tab).
    val bmp by produceState(IconMemory.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { loader() }?.also { IconMemory.put(key, it) }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size))
        else Icon(Icons.Outlined.Android, null, tint = AppTheme.extra.muted, modifier = Modifier.size(size * 0.5f))
    }
}

private object IconMemory : android.util.LruCache<Any, Bitmap>(16 shl 20) {
    override fun sizeOf(key: Any, value: Bitmap) = value.allocationByteCount
}

/** The "ADD"-style button of delivery apps: white, orange outline, bold caps. */
@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (filled) MaterialTheme.colorScheme.primary else AppTheme.extra.card)
            .border(1.dp, if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) Color.White else MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/** Little coloured badge, e.g. "BEST SAVINGS" or "16 PARTS". */
@Composable
fun Tag(text: String, bg: Color = AppTheme.extra.successSoft, fg: Color = AppTheme.extra.success) {
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

/** A list row with a leading icon tile and a chevron. */
@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String? = null, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppTheme.extra.muted)
    }
}

/** Receipt-style line: label on the left, amount on the right. */
@Composable
fun BillRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Canvas(modifier.fillMaxWidth().height(1.dp).padding(vertical = 0.dp)) {
        drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
    }
}

/** One horizontal bar split into category colours, with a compact legend under it. */
@Composable
fun StackedSizeBar(byCategory: Map<Category, Long>, total: Long) {
    val entries = byCategory.entries.filter { it.value > 0 }.sortedByDescending { it.value }
    val sum = entries.sumOf { it.value }.coerceAtLeast(1)
    val grow by animateFloatAsState(1f, tween(700), label = "grow")
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        entries.forEach { (c, v) ->
            val w = (v.toFloat() / sum * grow).coerceAtLeast(0.004f)
            Box(Modifier.weight(w).height(12.dp).background(c.color()))
        }
    }
    Spacer(Modifier.height(12.dp))
    entries.forEach { (c, v) -> LegendRow(c, v, total) }
}

@Composable
fun LegendRow(category: Category, bytes: Long, total: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(category.color()))
        Spacer(Modifier.width(10.dp))
        Text(category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${if (total > 0) (bytes * 100 / total) else 0}%",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.extra.muted,
            modifier = Modifier.width(44.dp).padding(start = 8.dp),
        )
    }
}

@Composable
fun CompareBar(label: String, before: Long, after: Long, max: Long, color: Color) {
    val fracBefore = if (max > 0) before.toFloat() / max else 0f
    val fracAfter by animateFloatAsState(if (max > 0) after.toFloat() / max else 0f, tween(900), label = "after")
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(formatBytes(before), style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            Text("  →  ", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            Text(formatBytes(after), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(fracBefore.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(fracAfter.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted, maxLines = 2)
        }
    }
}

enum class BannerTone { INFO, WARNING, SUCCESS, ERROR }

/** Flat notice row with a coloured left accent. */
@Composable
fun InfoBanner(icon: ImageVector, title: String, body: String, tone: BannerTone = BannerTone.INFO, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val accent = when (tone) {
        BannerTone.INFO -> Brand.Blue
        BannerTone.WARNING -> Brand.Amber
        BannerTone.SUCCESS -> AppTheme.extra.success
        BannerTone.ERROR -> MaterialTheme.colorScheme.error
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = accent.copy(alpha = 0.08f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                action?.let { Spacer(Modifier.height(2.dp)); it() }
            }
        }
    }
}
