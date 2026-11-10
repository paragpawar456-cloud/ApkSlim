package com.parag.apkslim.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parag.apkslim.AnalyzeState
import com.parag.apkslim.Preset
import com.parag.apkslim.core.ApkAnalyzer
import com.parag.apkslim.core.ImageMode
import com.parag.apkslim.core.OptimizeOptions
import com.parag.apkslim.core.OutputFormat
import com.parag.apkslim.core.Rules
import com.parag.apkslim.core.SavingsEstimate
import com.parag.apkslim.data.SigningInfo
import com.parag.apkslim.ui.components.AppIcon
import com.parag.apkslim.ui.components.BannerTone
import com.parag.apkslim.ui.components.BillRow
import com.parag.apkslim.ui.components.DashedDivider
import com.parag.apkslim.ui.components.ElevatedSection
import com.parag.apkslim.ui.components.InfoBanner
import com.parag.apkslim.ui.components.StackedSizeBar
import com.parag.apkslim.ui.components.Tag
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.formatPercent
import com.parag.apkslim.ui.theme.AppTheme
import com.parag.apkslim.ui.theme.MonoStyle
import com.parag.apkslim.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(
    state: AnalyzeState,
    options: OptimizeOptions,
    preset: Preset,
    estimate: SavingsEstimate?,
    signing: SigningInfo?,
    iconFor: (java.io.File) -> Bitmap?,
    onPreset: (Preset) -> Unit,
    onToggleAbi: (String) -> Unit,
    onOptions: ((OptimizeOptions) -> OptimizeOptions) -> Unit,
    onFormat: (OutputFormat) -> Unit,
    onOptimize: () -> Unit,
    onSigningSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text("Compress app", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (state is AnalyzeState.Ready && estimate != null) CheckoutBar(state, estimate, options.format, onOptimize)
        },
    ) { padding ->
        when (state) {
            is AnalyzeState.Loading, AnalyzeState.Idle -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text((state as? AnalyzeState.Loading)?.message ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is AnalyzeState.Error -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                InfoBanner(Icons.Outlined.ErrorOutline, "Couldn't read this file", state.message, BannerTone.ERROR)
            }
            is AnalyzeState.Ready -> ReadyContent(state, options, preset, estimate, signing, iconFor, onPreset, onToggleAbi, onOptions, onFormat, onSigningSettings, padding)
        }
    }
}

@Composable
private fun ReadyContent(
    s: AnalyzeState.Ready,
    options: OptimizeOptions,
    preset: Preset,
    estimate: SavingsEstimate?,
    signing: SigningInfo?,
    iconFor: (java.io.File) -> Bitmap?,
    onPreset: (Preset) -> Unit,
    onToggleAbi: (String) -> Unit,
    onOptions: ((OptimizeOptions) -> OptimizeOptions) -> Unit,
    onFormat: (OutputFormat) -> Unit,
    onSigningSettings: () -> Unit,
    padding: PaddingValues,
) {
    val a = s.analysis
    // Both formats are estimated up front so the user can compare the two choices directly.
    val apkEstimate = remember(a, options) { ApkAnalyzer.estimate(a, options.copy(format = OutputFormat.APK)) }
    val slimEstimate = remember(a, options) { ApkAnalyzer.estimate(a, options.copy(format = OutputFormat.ARCHIVE)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- App header ----
        item {
            ElevatedSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(s.info.file.absolutePath, 60.dp) { iconFor(s.info.file) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.info.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "v${s.info.versionName.take(18)} · Android ${sdkName(s.info.minSdk)}+",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.extra.muted,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(formatBytes(a.fileSize), MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
                            if (s.isMultiPart) Tag("${s.info.parts.size} parts", AppTheme.extra.accentSoft, MaterialTheme.colorScheme.primary)
                            if (s.info.isInstalled) Tag("Installed")
                        }
                    }
                }
            }
        }

        s.blockedReason?.let { reason ->
            item { InfoBanner(Icons.Outlined.Block, "This file can't be compressed alone", reason, BannerTone.ERROR) }
        }

        if (s.blockedReason == null) {
            // ---- The main choice ----
            item { Text("How do you want to save space?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            item {
                val best = slimEstimate.total > apkEstimate.total
                FormatCard(
                    selected = options.format == OutputFormat.ARCHIVE,
                    icon = Icons.Outlined.Inventory2,
                    title = "Compressed file (.slim)",
                    subtitle = "Smallest size, for storage and sharing. ApkSlim restores it to install.",
                    saving = slimEstimate.total,
                    total = a.fileSize,
                    badge = if (best) "Best savings" else null,
                ) { onFormat(OutputFormat.ARCHIVE) }
            }
            item {
                FormatCard(
                    selected = options.format == OutputFormat.APK,
                    icon = Icons.Outlined.InstallMobile,
                    title = if (s.isMultiPart) "Install-ready bundle (.apks)" else "Install-ready APK",
                    subtitle = "Installs directly. Smaller only where unused code or images can be removed.",
                    saving = apkEstimate.total,
                    total = a.fileSize,
                    badge = null,
                ) { onFormat(OutputFormat.APK) }
            }

            // ---- Bill details ----
            item { SavingsBill(a.fileSize, estimate ?: apkEstimate, options.format) }

            // ---- Level chips + advanced ----
            item { LevelAndAdvanced(s, options, preset, onPreset, onToggleAbi, onOptions) }

            if (s.isMultiPart) item {
                InfoBanner(
                    Icons.Outlined.Layers,
                    "Split app: ${s.info.parts.size} parts",
                    "Installed from an App Bundle. All parts are processed together and installed back as one app.",
                    BannerTone.INFO,
                )
            }

            if (options.format == OutputFormat.APK) item {
                InfoBanner(
                    Icons.Outlined.Key,
                    "The new APK gets a new signature",
                    (if (signing?.usingCustom == true) "Signed with your imported key. " else "Signed with your ApkSlim key. ") +
                        (if (s.info.isInstalled) "Uninstall the current ${s.info.label} before installing it: Android won't replace an app signed by another developer." else "Android only updates an app with an APK signed by the same key."),
                    BannerTone.WARNING,
                ) { TextButton(onClick = onSigningSettings, contentPadding = PaddingValues(0.dp)) { Text("Signing options") } }
            }
        }

        // ---- What's inside ----
        item {
            ElevatedSection {
                Text("What's inside", style = MaterialTheme.typography.titleMedium)
                Text("${a.entryCount} files", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                Spacer(Modifier.height(14.dp))
                StackedSizeBar(a.byCategory, a.fileSize)
            }
        }
        item { LargestFiles(s) }
    }
}

@Composable
private fun FormatCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
    saving: Long,
    total: Long,
    badge: String?,
    onClick: () -> Unit,
) {
    val pct = if (total > 0) saving.coerceAtLeast(0) * 100f / total else 0f
    val border = if (selected) MaterialTheme.colorScheme.primary else AppTheme.extra.divider
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) AppTheme.extra.accentSoft.copy(alpha = 0.55f) else AppTheme.extra.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    if (badge != null) { Spacer(Modifier.width(6.dp)); Tag(badge) }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (saving > 0) "Save ≈ ${formatBytes(saving)} (${formatPercent(pct)})" else "Little to save",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (saving > 0) AppTheme.extra.success else AppTheme.extra.muted,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Receipt-style breakdown of where the saving comes from. */
@Composable
private fun SavingsBill(fileSize: Long, e: SavingsEstimate, format: OutputFormat) {
    val minus = AppTheme.extra.success
    ElevatedSection {
        Text("Savings details", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        BillRow("Current size", formatBytes(fileSize))
        if (e.abiBytes > 0) BillRow("Unused CPU code", "− ${formatBytes(e.abiBytes)}", minus, hint = "Code for processors your phone doesn't have")
        if (e.imageBytes > 0) BillRow("Images", "− ${formatBytes(e.imageBytes)}", minus, hint = "Re-encoded as WebP")
        if (e.compressionBytes > 0) BillRow("Better zip compression", "− ${formatBytes(e.compressionBytes)}", minus)
        if (e.metadataBytes > 0) BillRow("Build leftovers & old signature", "− ${formatBytes(e.metadataBytes)}", minus)
        if (format == OutputFormat.ARCHIVE && e.archiveBytes > 0) BillRow("XZ archive compression", "− ${formatBytes(e.archiveBytes)}", minus, hint = "Stronger compression for app code and resources")
        Spacer(Modifier.height(8.dp))
        DashedDivider()
        Spacer(Modifier.height(8.dp))
        val after = (fileSize - e.total).coerceAtLeast(0)
        BillRow("Estimated new size", formatBytes(after), bold = true)
        val pct = if (fileSize > 0) e.total.coerceAtLeast(0) * 100f / fileSize else 0f
        Surface(shape = RoundedCornerShape(10.dp), color = AppTheme.extra.successSoft, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                if (e.total > 0) "You save about ${formatBytes(e.total)} (${formatPercent(pct)})" else "This app is already as small as it gets in this format",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.extra.success,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LevelAndAdvanced(
    s: AnalyzeState.Ready,
    options: OptimizeOptions,
    preset: Preset,
    onPreset: (Preset) -> Unit,
    onToggleAbi: (String) -> Unit,
    onOptions: ((OptimizeOptions) -> OptimizeOptions) -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    val a = s.analysis
    ElevatedSection(Modifier.animateContentSize()) {
        Text("Compression level", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Preset.SAFE, Preset.BALANCED, Preset.MAXIMUM).forEach { p ->
                FilterChip(
                    selected = preset == p,
                    onClick = { onPreset(p) },
                    label = { Text(p.title) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppTheme.extra.accentSoft, selectedLabelColor = MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(preset.blurb, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = AppTheme.extra.divider)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { advanced = !advanced }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Tune, null, tint = AppTheme.extra.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Advanced options", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = AppTheme.extra.muted)
        }
        AnimatedVisibility(advanced) {
            Column {
                // ABIs
                if (a.abis.isNotEmpty()) {
                    OptionHeader(Icons.Outlined.Memory, "CPU code (native libraries)", "This phone uses ${s.deviceAbi ?: "none of these"}.")
                    val keep = options.keepAbis ?: a.abis.toSet()
                    a.abis.forEach { abi ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = abi in keep, onCheckedChange = { onToggleAbi(abi) })
                            Text(abi, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (abi == s.deviceAbi) { Tag("This phone", AppTheme.extra.accentSoft, MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                            Text(formatBytes(a.abiSizes.getValue(abi)), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (s.deviceAbi != null && s.deviceAbi !in keep) {
                        InfoBanner(Icons.Outlined.WarningAmber, "This phone's CPU type is unchecked", "The app would crash on this phone. Keep ${s.deviceAbi}.", BannerTone.ERROR)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                // Images
                val imgCount = a.entries.count { Rules.isOptimizableImage(it.name, options.includeAssetImages) }
                OptionHeader(Icons.Outlined.Image, "Images", "$imgCount PNG/JPEG files can be re-encoded as WebP.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageMode.entries.forEach { m ->
                        FilterChip(
                            selected = options.imageMode == m,
                            onClick = { onOptions { it.copy(imageMode = m) } },
                            label = { Text(when (m) { ImageMode.OFF -> "Keep"; ImageMode.LOSSLESS -> "Lossless"; ImageMode.LOSSY -> "Lossy" }) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppTheme.extra.accentSoft, selectedLabelColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                if (options.imageMode == ImageMode.LOSSY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quality", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = options.imageQuality.toFloat(),
                            onValueChange = { v -> onOptions { it.copy(imageQuality = v.toInt()) } },
                            valueRange = 50f..95f,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text("${options.imageQuality}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                    }
                }
                if (options.imageMode != ImageMode.OFF) {
                    SwitchRow("Include images in assets/", "Off by default: games often read these files directly.", options.includeAssetImages) { v ->
                        onOptions { it.copy(includeAssetImages = v) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Zip level
                OptionHeader(Icons.Outlined.Compress, "Zip level", "Applies to the installable APK. Lossless.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = options.compressionLevel.toFloat(),
                        onValueChange = { v -> onOptions { it.copy(compressionLevel = v.toInt()) } },
                        valueRange = 1f..9f,
                        steps = 7,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Text("${options.compressionLevel}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(20.dp))
                }
                Spacer(Modifier.height(4.dp))
                OptionHeader(Icons.Outlined.CleaningServices, "Cleanup", "Build-tool leftovers Android never reads.")
                SwitchRow("Strip build metadata", "META-INF/*.version, Maven files, debug probes", options.stripBuildMetadata) { v ->
                    onOptions { it.copy(stripBuildMetadata = v) }
                }
            }
        }
    }
}

@Composable
private fun LargestFiles(s: AnalyzeState.Ready) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ElevatedSection(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Largest files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show more") }
        }
        val list = s.analysis.largest(if (expanded) 25 else 5)
        list.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = AppTheme.extra.divider)
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(e.category.color()))
                Spacer(Modifier.width(10.dp))
                Text(e.name, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatBytes(e.compressedSize), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Sticky "checkout" bar: total saving on the left, big orange button on the right. */
@Composable
private fun CheckoutBar(s: AnalyzeState.Ready, e: SavingsEstimate, format: OutputFormat, onOptimize: () -> Unit) {
    val total = e.total.coerceAtLeast(0)
    val pct = if (s.analysis.fileSize > 0) total * 100f / s.analysis.fileSize else 0f
    Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 16.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatBytes((s.analysis.fileSize - total).coerceAtLeast(0)), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (total > 0) "saves ${formatBytes(total)} · ${formatPercent(pct)}" else "estimated size",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.extra.success,
                )
            }
            Button(
                onClick = onOptimize,
                enabled = s.blockedReason == null,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (format == OutputFormat.ARCHIVE) "Compress now" else "Build APK", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OptionHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

fun sdkName(sdk: Int): String = when (sdk) {
    in 0..20 -> "4.4"
    21, 22 -> "5"
    23 -> "6"
    24, 25 -> "7"
    26, 27 -> "8"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31, 32 -> "12"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    else -> "16"
}
