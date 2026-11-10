package com.parag.apkslim.ui.screens

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parag.apkslim.data.HistoryItem
import com.parag.apkslim.data.InstalledApp
import com.parag.apkslim.data.StorageInfo
import com.parag.apkslim.ui.components.AppIcon
import com.parag.apkslim.ui.components.BrandMark
import com.parag.apkslim.ui.components.PillButton
import com.parag.apkslim.ui.components.SectionTitle
import com.parag.apkslim.ui.components.Tag
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.theme.AppTheme
import com.parag.apkslim.ui.theme.Brand
import kotlinx.coroutines.delay

data class AppCategory(val label: String, val icon: ImageVector, val code: Int?)

val APP_CATEGORIES = listOf(
    AppCategory("All apps", Icons.Outlined.Apps, null),
    AppCategory("Games", Icons.Outlined.SportsEsports, ApplicationInfo.CATEGORY_GAME),
    AppCategory("Social", Icons.Outlined.Forum, ApplicationInfo.CATEGORY_SOCIAL),
    AppCategory("Video", Icons.Outlined.Movie, ApplicationInfo.CATEGORY_VIDEO),
    AppCategory("Photos", Icons.Outlined.PhotoCamera, ApplicationInfo.CATEGORY_IMAGE),
    AppCategory("Music", Icons.Outlined.MusicNote, ApplicationInfo.CATEGORY_AUDIO),
    AppCategory("Work", Icons.Outlined.WorkOutline, ApplicationInfo.CATEGORY_PRODUCTIVITY),
)

@Composable
fun HomeScreen(
    apps: List<InstalledApp>?,
    storage: StorageInfo,
    history: List<HistoryItem>,
    icon: (String) -> Bitmap?,
    onSearch: () -> Unit,
    onCategory: (Int?) -> Unit,
    onCompressApp: (InstalledApp) -> Unit,
    onPickFile: () -> Unit,
    onHistory: () -> Unit,
    onAbout: () -> Unit,
    onOpenHistoryItem: (HistoryItem) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    crashReport: String? = null,
    onShareCrash: (String) -> Unit = {},
    onDismissCrash: () -> Unit = {},
) {
    val userApps = remember(apps) { apps.orEmpty().filter { !it.isSystem } }
    val biggest = remember(userApps) { userApps.sortedByDescending { it.totalSize }.take(10) }
    val appsBytes = remember(userApps) { userApps.sumOf { it.totalSize } }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
    ) {
        // ---- Header: brand + storage ----
        item {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ApkSlim", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (storage.total > 0) "${formatBytes(storage.free)} free of ${formatBytes(storage.total)}" else "App size reducer",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.extra.muted,
                        )
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onAbout)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Info, "How it works", modifier = Modifier.size(20.dp)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                StorageMeter(storage, appsBytes)
                Spacer(Modifier.height(16.dp))
                // Search
                Row(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onSearch).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Search an app to compress", style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.muted)
                }
            }
        }

        // ---- Last run closed unexpectedly ----
        if (crashReport != null) {
            item {
                com.parag.apkslim.ui.components.InfoBanner(
                    Icons.Outlined.Info,
                    "ApkSlim closed unexpectedly last time",
                    "A report was saved on this phone. Share it so the problem can be fixed. Nothing is sent unless you choose to.",
                    com.parag.apkslim.ui.components.BannerTone.WARNING,
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                ) {
                    Row {
                        TextButton(onClick = onDismissCrash) { Text("Dismiss") }
                        TextButton(onClick = { onShareCrash(crashReport) }) { Text("Share report") }
                    }
                }
            }
        }

        // ---- Offers carousel ----
        item { Spacer(Modifier.height(18.dp)); OfferCarousel(onAbout) }

        // ---- Categories (round tiles) ----
        item {
            Column(Modifier.padding(top = 22.dp)) {
                Text("Browse by type", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(APP_CATEGORIES) { c ->
                        Column(
                            Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable { onCategory(c.code) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(56.dp).clip(CircleShape).background(AppTheme.extra.accentSoft), contentAlignment = Alignment.Center) {
                                Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(c.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }

        // ---- Biggest apps carousel ----
        item {
            Column(Modifier.padding(top = 18.dp)) {
                SectionTitle(
                    "Biggest apps on your phone",
                    Modifier.padding(horizontal = 20.dp),
                    subtitle = "Tap Compress to see how much you can save",
                ) { TextButton(onClick = { onCategory(null) }) { Text("See all") } }
                if (apps == null) {
                    Text("Loading your apps…", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(biggest, key = { it.packageName }) { app -> BigAppCard(app, icon) { onCompressApp(app) } }
                    }
                }
            }
        }

        // ---- Quick actions ----
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
                Text("Have a file?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(Icons.Outlined.FolderOpen, "Open APK", ".apk or .apks", Brand.Blue, Modifier.weight(1f), onPickFile)
                    QuickTile(Icons.Outlined.Restore, "Restore", ".slim archive", Brand.Green, Modifier.weight(1f), onPickFile)
                }
            }
        }

        // ---- Recent ----
        if (history.isNotEmpty()) {
            item {
                SectionTitle("Recently compressed", Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                    TextButton(onClick = onHistory) { Text("See all") }
                }
            }
            items(history.take(3), key = { it.id }) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) { HistoryRow(it) { onOpenHistoryItem(it) } }
            }
        }
    }
}

@Composable
private fun StorageMeter(storage: StorageInfo, appsBytes: Long) {
    if (storage.total <= 0) return
    val usedFrac = storage.used.toFloat() / storage.total
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${(usedFrac * 100).toInt()}% used", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (appsBytes > 0) Text("Apps take ${formatBytes(appsBytes)}", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { usedFrac },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (usedFrac > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
        }
    }
}

private data class Offer(val kicker: String, val title: String, val body: String, val icon: ImageVector, val bg: @Composable () -> Color, val accent: Color)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OfferCarousel(onClick: () -> Unit) {
    val offers = listOf(
        Offer("Compressed archive", "Save 20–35%", "Pack any app into a .slim file. Restore it whenever you need it.", Icons.Outlined.Inventory2, { AppTheme.extra.bannerPeach }, Brand.Orange),
        Offer("Universal APKs", "Save up to 70%", "Drop CPU code your phone never runs, like x86 and armv7 copies.", Icons.Outlined.Memory, { AppTheme.extra.bannerSky }, Brand.Blue),
        Offer("Pixel-perfect", "Lighter images", "PNGs re-encoded losslessly. Every pixel is checked.", Icons.Outlined.Image, { AppTheme.extra.bannerSage }, Brand.Green),
    )
    val pager = rememberPagerState { offers.size }
    LaunchedEffect(pager) {
        while (true) {
            delay(4000)
            pager.animateScrollToPage((pager.currentPage + 1) % offers.size)
        }
    }
    Column {
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { i ->
            val o = offers[i]
            Row(
                Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(20.dp)).background(o.bg()).clickable(onClick = onClick).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(o.kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = o.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(o.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text(o.body, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(64.dp).clip(CircleShape).background(o.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(o.icon, null, tint = o.accent, modifier = Modifier.size(32.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(offers.size) { i ->
                val active = pager.currentPage == i
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (active) 18.dp else 6.dp).clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun BigAppCard(app: InstalledApp, icon: (String) -> Bitmap?, onCompress: () -> Unit) {
    Surface(
        Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onCompress),
        shape = RoundedCornerShape(18.dp),
        color = AppTheme.extra.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.extra.divider),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box {
                AppIcon(app.packageName, 56.dp) { icon(app.packageName) }
            }
            Spacer(Modifier.height(10.dp))
            Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(app.totalSize), style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            Spacer(Modifier.height(4.dp))
            if (app.splitCount > 0) Tag("${app.splitCount + 1} parts", AppTheme.extra.accentSoft, MaterialTheme.colorScheme.primary)
            else Tag("Single APK")
            Spacer(Modifier.height(10.dp))
            PillButton("Compress", Modifier.fillMaxWidth(), onClick = onCompress)
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, subtitle: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = AppTheme.extra.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.extra.divider),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun HistoryRow(item: HistoryItem, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val saved = item.savedPercent.coerceAtLeast(0f)
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        color = AppTheme.extra.card,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.extra.divider),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (saved > 0f) AppTheme.extra.successSoft else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (saved > 0f) "−${saved.toInt()}%" else "0%",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (saved > 0f) AppTheme.extra.success else AppTheme.extra.muted,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.appName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.outputPath.endsWith(".slim")) { Spacer(Modifier.width(6.dp)); Tag(".slim", AppTheme.extra.accentSoft, MaterialTheme.colorScheme.primary) }
                }
                Text(
                    "${formatBytes(item.originalSize)} → ${formatBytes(item.optimizedSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.extra.muted,
                )
                Text(com.parag.apkslim.ui.formatDate(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = AppTheme.extra.muted)
        }
    }
}
