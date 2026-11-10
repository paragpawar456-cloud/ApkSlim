package com.parag.apkslim.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parag.apkslim.data.InstalledApp
import com.parag.apkslim.ui.components.AppIcon
import com.parag.apkslim.ui.components.PillButton
import com.parag.apkslim.ui.components.Tag
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.theme.AppTheme

private enum class AppSort(val label: String) { SIZE("Largest"), NAME("A–Z"), RECENT("Recently updated") }

@Composable
fun AppsScreen(
    apps: List<InstalledApp>?,
    load: () -> Unit,
    icon: (String) -> Bitmap?,
    initialCategory: Int?,
    focusSearch: Boolean,
    onBack: (() -> Unit)?,
    onPick: (InstalledApp) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    LaunchedEffect(Unit) { load() }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable(initialCategory) { mutableStateOf(initialCategory ?: -100) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(AppSort.SIZE) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        // Search header
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } else Spacer(Modifier.width(8.dp))
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = AppTheme.extra.muted, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Outlined.Close, "Clear", Modifier.clip(RoundedCornerShape(50)).clickable { query = "" }, tint = AppTheme.extra.muted)
            }
        }
        // Filters
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppSort.entries) { s -> Chip(s.label, sort == s) { sort = s } }
            item { Box(Modifier.width(1.dp).height(32.dp).background(AppTheme.extra.divider)) }
            items(APP_CATEGORIES) { c -> Chip(c.label, (c.code ?: -100) == category) { category = c.code ?: -100 } }
            item { Chip("System apps", showSystem) { showSystem = !showSystem } }
        }
        HorizontalDivider(color = AppTheme.extra.divider)

        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val filtered = remember(apps, query, showSystem, sort, category) {
            apps.asSequence()
                .filter { showSystem || !it.isSystem }
                .filter { category == -100 || it.category == category }
                .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                .let { seq ->
                    when (sort) {
                        AppSort.SIZE -> seq.sortedByDescending { it.totalSize }
                        AppSort.NAME -> seq.sortedBy { it.label.lowercase() }
                        AppSort.RECENT -> seq.sortedByDescending { it.lastUpdate }
                    }
                }.toList()
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 24.dp)) {
            item {
                Text(
                    "${filtered.size} apps · ${formatBytes(filtered.sumOf { it.totalSize })}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.extra.muted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(app) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.packageName, 56.dp) { icon(app.packageName) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append(formatBytes(app.totalSize))
                                if (app.versionName.isNotBlank()) append(" · v").append(app.versionName.take(14))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.extra.muted,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (app.splitCount > 0) Tag("${app.splitCount + 1} parts", AppTheme.extra.accentSoft, MaterialTheme.colorScheme.primary)
                        else Tag("Single APK")
                    }
                    Spacer(Modifier.width(10.dp))
                    PillButton("Compress") { onPick(app) }
                }
                HorizontalDivider(color = AppTheme.extra.divider)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppTheme.extra.accentSoft,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
