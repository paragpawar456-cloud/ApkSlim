package com.parag.apkslim.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parag.apkslim.AnalyzeState
import com.parag.apkslim.Preset
import com.parag.apkslim.core.ImageMode
import com.parag.apkslim.core.OptimizeOptions
import com.parag.apkslim.core.SavingsEstimate
import com.parag.apkslim.data.SigningInfo
import com.parag.apkslim.ui.components.AppIcon
import com.parag.apkslim.ui.components.BannerTone
import com.parag.apkslim.ui.components.ElevatedSection
import com.parag.apkslim.ui.components.InfoBanner
import com.parag.apkslim.ui.components.LegendRow
import com.parag.apkslim.ui.components.SizeDonut
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.formatPercent
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
    onOptimize: () -> Unit,
    onSigningSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (state is AnalyzeState.Ready && estimate != null) {
                EstimateBar(state, estimate, onOptimize)
            }
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
                InfoBanner(Icons.Outlined.ErrorOutline, "Couldn't read this APK", state.message, BannerTone.ERROR)
            }
            is AnalyzeState.Ready -> ReadyContent(state, options, preset, signing, iconFor, onPreset, onToggleAbi, onOptions, onSigningSettings, padding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    s: AnalyzeState.Ready,
    options: OptimizeOptions,
    preset: Preset,
    signing: SigningInfo?,
    iconFor: (java.io.File) -> Bitmap?,
    onPreset: (Preset) -> Unit,
    onToggleAbi: (String) -> Unit,
    onOptions: ((OptimizeOptions) -> OptimizeOptions) -> Unit,
    onSigningSettings: () -> Unit,
    padding: PaddingValues,
) {
    val a = s.analysis
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- Header ----
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(s.info.file.absolutePath, 60.dp) { iconFor(s.info.file) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.info.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.info.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "v${s.info.versionName} · ${formatBytes(a.fileSize)} · ${a.entryCount} files · Android ${sdkName(s.info.minSdk)}+",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        s.blockedReason?.let { reason ->
            item { InfoBanner(Icons.Outlined.Block, "Can't optimize this one", reason, BannerTone.ERROR) }
        }

        // ---- Breakdown ----
        item {
            ElevatedSection {
                Text("What's inside", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SizeDonut(a.byCategory, a.fileSize) }
                Spacer(Modifier.height(14.dp))
                a.byCategory.entries.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (c, v) -> LegendRow(c, v, a.fileSize) }
            }
        }

        if (s.blockedReason == null) {
            // ---- Presets ----
            item {
                ElevatedSection {
                    Text("Optimization level", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    val presets = listOf(Preset.SAFE, Preset.BALANCED, Preset.MAXIMUM)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        presets.forEachIndexed { i, p ->
                            SegmentedButton(
                                selected = preset == p,
                                onClick = { onPreset(p) },
                                shape = SegmentedButtonDefaults.itemShape(i, presets.size),
                            ) { Text(p.title) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(preset.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- ABIs ----
            if (a.abis.isNotEmpty()) item {
                ElevatedSection {
                    OptionHeader(Icons.Outlined.Memory, "Native libraries (CPU code)", "Keep only the CPU types you need. This phone uses ${s.deviceAbi ?: "none of these"}.")
                    Spacer(Modifier.height(8.dp))
                    val keep = options.keepAbis ?: a.abis.toSet()
                    a.abis.forEach { abi ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = abi in keep, onCheckedChange = { onToggleAbi(abi) })
                            Text(abi, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (abi == s.deviceAbi) {
                                Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("This phone", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(formatBytes(a.abiSizes.getValue(abi)), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    AnimatedVisibility(s.deviceAbi != null && s.deviceAbi !in keep) {
                        InfoBanner(Icons.Outlined.WarningAmber, "This phone's CPU type is unchecked", "The optimized app would crash on this phone. Keep ${s.deviceAbi} to run it here.", BannerTone.ERROR, Modifier.padding(top = 8.dp))
                    }
                    AnimatedVisibility(keep.size < a.abis.size) {
                        Text(
                            "The result is tailored to devices with the checked CPU types. Keep the original if you plan to share it with other phones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // ---- Images ----
            item {
                val imgCount = a.entries.count { com.parag.apkslim.core.Rules.isOptimizableImage(it.name, options.includeAssetImages) }
                ElevatedSection(Modifier.animateContentSize()) {
                    OptionHeader(Icons.Outlined.Image, "Images", "$imgCount PNG/JPEG files can be re-encoded as WebP. 9-patch images are always left alone.")
                    Spacer(Modifier.height(12.dp))
                    val modes = ImageMode.entries
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = options.imageMode == m,
                                onClick = { onOptions { it.copy(imageMode = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(m.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (options.imageMode) {
                            ImageMode.OFF -> "Images are copied unchanged."
                            ImageMode.LOSSLESS -> "Every pixel is checked after re-encoding; if anything differs the original is kept."
                            ImageMode.LOSSY -> "Smaller files, slight quality loss. 80+ is hard to tell apart on a phone screen."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (options.imageMode == ImageMode.LOSSY) {
                        Spacer(Modifier.height(8.dp))
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
                }
            }

            // ---- Compression ----
            item {
                ElevatedSection {
                    OptionHeader(Icons.Outlined.Compress, "Zip compression", "Re-deflates compressed files at a higher level. Lossless. Stored files (resource table, aligned libraries) stay uncompressed so the app loads just as fast.")
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Level", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = options.compressionLevel.toFloat(),
                            onValueChange = { v -> onOptions { it.copy(compressionLevel = v.toInt()) } },
                            valueRange = 1f..9f,
                            steps = 7,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text("${options.compressionLevel}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(20.dp))
                    }
                    if (options.compressionLevel < 6) {
                        Text("Levels below 6 usually make the APK bigger.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ---- Cleanup ----
            item {
                ElevatedSection {
                    OptionHeader(Icons.Outlined.CleaningServices, "Cleanup", "Removes build-tool leftovers Android never reads. Service registrations and licence files are kept.")
                    SwitchRow("Strip build metadata", "META-INF/*.version, Maven descriptors, debug probes", options.stripBuildMetadata) { v ->
                        onOptions { it.copy(stripBuildMetadata = v) }
                    }
                }
            }

            // ---- Signing ----
            item {
                InfoBanner(
                    Icons.Outlined.Key,
                    "The APK will be re-signed",
                    (if (signing?.usingCustom == true) "Using your imported key. " else "Using the ApkSlim key. ") +
                        "Android only updates an app with an APK signed by the same key. " +
                        (if (s.info.isInstalled) "${s.info.label} is installed, so you'll need to uninstall it first (its data will be lost) unless you sign with its original key." else ""),
                    BannerTone.WARNING,
                ) { TextButton(onClick = onSigningSettings, contentPadding = PaddingValues(0.dp)) { Text("Signing options") } }
            }
        }

        // ---- Largest files ----
        item { LargestFiles(s) }
    }
}

@Composable
private fun LargestFiles(s: AnalyzeState.Ready) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ElevatedSection(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Largest files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
        }
        val list = s.analysis.largest(if (expanded) 25 else 5)
        list.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.extraSmall, color = e.category.color().copy(alpha = 0.18f)) {
                    Text(e.category.label.substringBefore(' '), style = MaterialTheme.typography.labelSmall, color = e.category.color(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(e.name, style = MonoStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatBytes(e.compressedSize), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EstimateBar(s: AnalyzeState.Ready, e: SavingsEstimate, onOptimize: () -> Unit) {
    val total = e.total.coerceAtLeast(0)
    val pct = if (s.analysis.fileSize > 0) total * 100f / s.analysis.fileSize else 0f
    val animated by animateIntAsState((pct * 10).toInt(), label = "pct")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Estimated saving", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("≈ ${formatPercent(animated / 10f)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(formatBytes(-total, signed = true), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Button(onClick = onOptimize, enabled = s.blockedReason == null, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)) {
                Icon(Icons.Outlined.Bolt, null)
                Spacer(Modifier.width(6.dp))
                Text("Optimize")
            }
        }
    }
}

@Composable
private fun OptionHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
