package com.parag.apkslim.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parag.apkslim.ProcessState
import com.parag.apkslim.core.Category
import com.parag.apkslim.core.OutputFormat
import com.parag.apkslim.ui.ApkActions
import com.parag.apkslim.ui.components.BannerTone
import com.parag.apkslim.ui.components.BillRow
import com.parag.apkslim.ui.components.CompareBar
import com.parag.apkslim.ui.components.DashedDivider
import com.parag.apkslim.ui.components.ElevatedSection
import com.parag.apkslim.ui.components.InfoBanner
import com.parag.apkslim.ui.components.StatTile
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.formatPercent
import com.parag.apkslim.ui.theme.AppTheme
import com.parag.apkslim.ui.theme.Brand
import com.parag.apkslim.ui.theme.color

@Composable
private fun SuccessBadge(ok: Boolean) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Box(
        Modifier.size(84.dp).scale(pop.value).clip(CircleShape).background(if (ok) AppTheme.extra.success else MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(44.dp))
    }
}

@Composable
fun ResultScreen(done: ProcessState.Done, installing: Boolean, onInstall: (java.io.File) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val isArchive = done.result.format == OutputFormat.ARCHIVE
    val r = done.result
    val file = r.output
    val saved = r.savedBytes.coerceAtLeast(0)
    val pct = r.savedPercent.coerceAtLeast(0f)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ApkActions.mimeFor(file))) { uri ->
        if (uri != null) {
            val ok = ApkActions.copyTo(context, file, uri)
            Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }
    val bar by animateFloatAsState(if (r.originalSize > 0) r.optimizedSize.toFloat() / r.originalSize else 1f, tween(1200), label = "bar")

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") }
                }
                SuccessBadge(saved > 0)
                Spacer(Modifier.height(18.dp))
                Text(
                    if (saved > 0) "You saved ${formatBytes(saved)}" else "Already as small as it gets",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${done.info.label} is ${formatPercent(pct)} smaller" + if (isArchive) " as a .slim file" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.extra.muted,
                )
            }
        }

        // Size comparison card
        item {
            ElevatedSection {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Before", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted)
                        Text(formatBytes(r.originalSize), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("After", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted)
                        Text(formatBytes(r.optimizedSize), style = MaterialTheme.typography.titleLarge, color = AppTheme.extra.success)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).height(14.dp).clip(RoundedCornerShape(7.dp)).background(AppTheme.extra.success))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isArchive) "Saved as ${file.name}" else "Signature: ${if (r.verify.verified) "valid (" + r.verify.schemes.joinToString("+") + ")" else "check failed"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.extra.muted,
                )
            }
        }

        item {
            ResultActions(
                installLabel = if (isArchive) "Restore & install" else "Install",
                installing = installing,
                onInstall = { if (ApkActions.ensureInstallPermission(context)) onInstall(file) },
                onShare = { ApkActions.share(context, file) },
                onSave = { saveLauncher.launch(file.name) },
            )
        }

        if (saved == 0L && !isArchive) item {
            InfoBanner(
                Icons.Outlined.Lightbulb,
                "Tip: try the Compressed file option",
                "This app is mostly code that's already compressed inside the APK. The .slim format uses a stronger compressor and usually saves 20–35% on apps like this.",
                BannerTone.INFO,
            )
        }
        if (isArchive) item {
            InfoBanner(
                Icons.Outlined.Inventory2,
                "How to use a .slim file",
                "Keep it, back it up or send it. To install, open it in ApkSlim and tap Restore & install. ApkSlim rebuilds a normal APK from it.",
                BannerTone.INFO,
            )
        }
        if (done.info.isInstalled && !isArchive) item {
            InfoBanner(
                Icons.Outlined.WarningAmber,
                "Uninstall the original first",
                "Android won't install this copy over the Play Store version because the signatures differ. Uninstalling removes the app's data.",
                BannerTone.WARNING,
            ) { TextButton(onClick = { ApkActions.uninstall(context, done.info.packageName) }, contentPadding = PaddingValues(0.dp)) { Text("Uninstall original") } }
        }

        // Receipt
        item {
            ElevatedSection {
                Text("Where the space came from", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val minus = AppTheme.extra.success
                if (r.stats.removedAbiBytes > 0) BillRow("Unused CPU code", "− ${formatBytes(r.stats.removedAbiBytes)}", minus)
                if (r.stats.imageBytesSaved > 0) BillRow("Images (${r.stats.imagesOptimized} re-encoded)", "− ${formatBytes(r.stats.imageBytesSaved)}", minus)
                if (r.stats.removedMetadataBytes > 0) BillRow("Build leftovers", "− ${formatBytes(r.stats.removedMetadataBytes)}", minus)
                val other = saved - r.stats.removedAbiBytes - r.stats.imageBytesSaved - r.stats.removedMetadataBytes
                if (other > 0) BillRow(if (isArchive) "XZ compression & repacking" else "Recompression & repacking", "− ${formatBytes(other)}", minus)
                Spacer(Modifier.height(6.dp)); DashedDivider(); Spacer(Modifier.height(6.dp))
                BillRow("Total saved", formatBytes(saved), AppTheme.extra.success, bold = true)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(String.format(java.util.Locale.US, "%.1f s", r.durationMs / 1000f), "time taken", Modifier.weight(1f), Icons.Outlined.Timer, Brand.Violet)
                StatTile(if (r.apkCount > 1) "${r.apkCount}" else "1", if (r.apkCount > 1) "parts processed" else "APK processed", Modifier.weight(1f), Icons.Outlined.Memory, Brand.Blue)
                StatTile(if (r.verify.verified) "OK" else "!", if (isArchive) "archive check" else "signature", Modifier.weight(1f), Icons.Outlined.Verified, if (r.verify.verified) Brand.Green else Brand.Coral)
            }
        }

        val after = r.after
        if (after != null) item {
            ElevatedSection {
                Text("Before vs after", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val cats = Category.entries.filter { (r.before[it] ?: 0) > 0 || (after[it] ?: 0) > 0 }.sortedByDescending { r.before[it] ?: 0 }
                val max = cats.maxOfOrNull { maxOf(r.before[it] ?: 0, after[it] ?: 0) } ?: 1
                cats.forEach { c -> CompareBar(c.label, r.before[c] ?: 0, after[c] ?: 0, max, c.color()) }
            }
        }
    }
}

@Composable
fun ResultActions(installLabel: String, installing: Boolean, onInstall: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onInstall, enabled = !installing, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (installing) CircularProgressIndicator(strokeWidth = 2.dp, color = Color.White, modifier = Modifier.size(20.dp))
            else Icon(Icons.Outlined.InstallMobile, null)
            Spacer(Modifier.width(8.dp))
            Text(if (installing) "Preparing…" else installLabel, maxLines = 1, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onShare, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share", maxLines = 1)
            }
            OutlinedButton(onClick = onSave, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Outlined.SaveAlt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save", maxLines = 1)
            }
        }
    }
}

/** Shown after a .slim archive was restored into installable APK(s). */
@Composable
fun RestoredScreen(state: ProcessState.Restored, installing: Boolean, onInstall: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val total = state.files.sumOf { it.length() }
    val shareable = remember(state) {
        if (state.files.size == 1) state.files[0]
        else java.io.File(state.files[0].parentFile, "${state.meta.label.ifEmpty { "app" }.replace(Regex("[^A-Za-z0-9]+"), "_")}.apks").also { out ->
            if (!out.exists()) com.parag.apkslim.core.SlimPipeline.bundle(state.files.map { com.parag.apkslim.core.NamedApk(it.name, it) }, out)
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ApkActions.mimeFor(shareable))) { uri ->
        if (uri != null) {
            val ok = ApkActions.copyTo(context, shareable, uri)
            Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") } }
                SuccessBadge(true)
                Spacer(Modifier.height(18.dp))
                Text("Ready to install", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${state.meta.label.ifEmpty { "App" }} ${state.meta.versionName}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.extra.muted,
                )
            }
        }
        item {
            ElevatedSection {
                BillRow(".slim file", formatBytes(state.archive.length()))
                BillRow("Restored app", formatBytes(total), hint = "${state.files.size} APK${if (state.files.size > 1) "s" else ""}, signed and verified")
            }
        }
        item {
            ResultActions(
                installLabel = "Install",
                installing = installing,
                onInstall = { if (ApkActions.ensureInstallPermission(context)) onInstall() },
                onShare = { ApkActions.share(context, shareable) },
                onSave = { saveLauncher.launch(shareable.name) },
            )
        }
        item {
            InfoBanner(
                Icons.Outlined.WarningAmber,
                "Signed with your ApkSlim key",
                "If ${state.meta.label.ifEmpty { "this app" }} is installed from the Play Store, uninstall it first: Android won't replace an app signed with a different key.",
                BannerTone.WARNING,
            )
        }
    }
}

