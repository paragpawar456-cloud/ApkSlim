package com.parag.apkslim.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parag.apkslim.core.Category
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ElevatedSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** App icon loaded off the main thread. */
@Composable
fun AppIcon(key: Any, size: Dp = 44.dp, loader: () -> Bitmap?) {
    val bmp by produceState<Bitmap?>(null, key) { value = withContext(Dispatchers.IO) { loader() } }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size))
        else Icon(Icons.Outlined.Android, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.55f))
    }
}

/** Animated donut of category sizes. */
@Composable
fun SizeDonut(byCategory: Map<Category, Long>, total: Long, modifier: Modifier = Modifier, diameter: Dp = 168.dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(byCategory) { progress.snapTo(0f); progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val sum = byCategory.values.sum().coerceAtLeast(1)
            var start = -90f
            val sweepTotal = 360f * progress.value
            byCategory.entries.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (cat, v) ->
                val sweep = v.toFloat() / sum * sweepTotal
                if (sweep > 0.4f) {
                    drawArc(cat.color(), start + 0.6f, sweep - 1.2f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatBytes(total), style = MaterialTheme.typography.titleLarge)
            Text("total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LegendRow(category: Category, bytes: Long, total: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(category.color()))
        Spacer(Modifier.width(10.dp))
        Text(category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            " · ${if (total > 0) (bytes * 100 / total) else 0}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
    }
}

/** Big animated ring showing % saved. */
@Composable
fun SavingsRing(percent: Float, modifier: Modifier = Modifier, diameter: Dp = 200.dp, label: String = "smaller") {
    val anim by animateFloatAsState(percent.coerceIn(0f, 100f), tween(1400, easing = FastOutSlowInEasing), label = "ring")
    val track = Color.White.copy(alpha = 0.18f)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val s = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(Color(0xFF63E6BE), Color.White, Color(0xFF63E6BE))),
                -90f, 360f * anim / 100f, false, Offset(inset, inset), s, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(java.util.Locale.US, "%.1f%%", anim), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Horizontal before/after bar for one value. */
@Composable
fun CompareBar(label: String, before: Long, after: Long, max: Long, color: Color) {
    val fracBefore = if (max > 0) before.toFloat() / max else 0f
    val fracAfter by animateFloatAsState(if (max > 0) after.toFloat() / max else 0f, tween(1000), label = "after")
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(formatBytes(before), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("  →  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatBytes(after), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(fracBefore.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.28f)))
            Box(Modifier.fillMaxWidth(fracAfter.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

enum class BannerTone { INFO, WARNING, SUCCESS, ERROR }

@Composable
fun InfoBanner(icon: ImageVector, title: String, body: String, tone: BannerTone = BannerTone.INFO, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val (bg, fg) = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        BannerTone.WARNING -> Color(0xFFFFF3BF).let { if (MaterialTheme.colorScheme.background.luminanceIsDark()) Color(0xFF4A3B00) else it } to
            (if (MaterialTheme.colorScheme.background.luminanceIsDark()) Color(0xFFFFEC99) else Color(0xFF5C4400))
        BannerTone.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = bg, contentColor = fg) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
                action?.let { Spacer(Modifier.height(4.dp)); it() }
            }
        }
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
