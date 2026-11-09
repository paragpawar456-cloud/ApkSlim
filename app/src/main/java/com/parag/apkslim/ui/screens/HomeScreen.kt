package com.parag.apkslim.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parag.apkslim.data.HistoryItem
import com.parag.apkslim.ui.components.ElevatedSection
import com.parag.apkslim.ui.components.SectionTitle
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.formatPercent
import com.parag.apkslim.ui.theme.Brand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    history: List<HistoryItem>,
    onPickFile: () -> Unit,
    onInstalled: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onOpenHistoryItem: (HistoryItem) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("ApkSlim", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Outlined.History, "History") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Hero(history) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard(Icons.Outlined.FolderOpen, "Open file", ".apk, .apks or .slim", Modifier.weight(1f), onPickFile)
                    ActionCard(Icons.Outlined.Apps, "Installed apps", "Extract & slim an app", Modifier.weight(1f), onInstalled)
                }
            }
            item { ExpectationsCard(onAbout) }
            if (history.isNotEmpty()) {
                item {
                    SectionTitle("Recent") { TextButton(onClick = onHistory) { Text("See all") } }
                }
                items(history.take(3), key = { it.id }) { HistoryRow(it) { onOpenHistoryItem(it) } }
            }
        }
    }
}

@Composable
private fun Hero(history: List<HistoryItem>) {
    val saved = history.sumOf { it.savedBytes.coerceAtLeast(0) }
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Brand.hero).padding(24.dp)
    ) {
        Column {
            Text("Make APKs lighter", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "Removes CPU code your phone can't run, re-encodes images pixel-perfectly, recompresses, aligns and re-signs. App code is never modified.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeroStat(formatBytes(saved), "saved so far")
                HeroStat(history.size.toString(), "APKs slimmed")
                val best = history.maxOfOrNull { it.savedPercent } ?: 0f
                HeroStat(formatPercent(best), "best result")
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpectationsCard(onAbout: () -> Unit) {
    ElevatedSection {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Insights, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(10.dp))
            Text("Realistic savings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onAbout) { Icon(Icons.Outlined.Info, "How it works") }
        }
        Spacer(Modifier.height(6.dp))
        ExpectRow("Universal APKs with native code for 3–4 CPUs", "30–70%")
        ExpectRow("Image-heavy apps without native code", "5–25%")
        ExpectRow("Play Store apps as installable APK", "0–5%")
        ExpectRow("Play Store apps as .slim archive", "20–35%")
        Spacer(Modifier.height(6.dp))
        Text(
            "APKs are already compressed, so no tool can promise a fixed 50–70% for every app. The .slim archive uses a stronger compressor for storage and sharing; ApkSlim restores it before installing. You see an estimate before anything changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpectRow(what: String, range: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(what, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Text(range, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun HistoryRow(item: HistoryItem, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Text("${item.savedPercent.toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.appName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "${formatBytes(item.originalSize)} → ${formatBytes(item.optimizedSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(com.parag.apkslim.ui.formatDate(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
