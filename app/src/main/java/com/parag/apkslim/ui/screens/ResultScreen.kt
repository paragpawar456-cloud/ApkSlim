package com.parag.apkslim.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.parag.apkslim.ProcessState
import com.parag.apkslim.core.Category
import com.parag.apkslim.core.OutputFormat
import com.parag.apkslim.ui.ApkActions
import com.parag.apkslim.ui.components.BannerTone
import com.parag.apkslim.ui.components.CompareBar
import com.parag.apkslim.ui.components.ElevatedSection
import com.parag.apkslim.ui.components.InfoBanner
import com.parag.apkslim.ui.components.SavingsRing
import com.parag.apkslim.ui.components.StatTile
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.theme.Brand
import com.parag.apkslim.ui.theme.color

@Composable
fun ResultScreen(done: ProcessState.Done, installing: Boolean, onInstall: (java.io.File) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val isArchive = done.result.format == OutputFormat.ARCHIVE
    val r = done.result
    val file = r.output
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ApkActions.mimeFor(file))) { uri ->
        if (uri != null) {
            val ok = ApkActions.copyTo(context, file, uri)
            Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- Hero ----
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .background(Brand.success)
                    .statusBarsPadding()
                    .padding(bottom = 28.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                }
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(done.info.label, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(16.dp))
                    SavingsRing(r.savedPercent.coerceAtLeast(0f))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${formatBytes(r.originalSize)}  →  ${formatBytes(r.optimizedSize)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text("${formatBytes(r.savedBytes)} saved", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (r.savedPercent < 3f && !isArchive) {
                    InfoBanner(
                        Icons.Outlined.ThumbUp,
                        "Already well optimized",
                        "This APK had little left to remove: most of it is app code that is already compressed. For a much smaller file, go back and choose Output › Smallest .slim.",
                        BannerTone.INFO,
                    )
                }

                // ---- Actions ----
                ResultActions(
                    installLabel = if (isArchive) "Restore & install" else "Install",
                    installing = installing,
                    onInstall = { if (ApkActions.ensureInstallPermission(context)) onInstall(file) },
                    onShare = { ApkActions.share(context, file) },
                    onSave = { saveLauncher.launch(file.name) },
                )

                if (isArchive) {
                    InfoBanner(
                        Icons.Outlined.Inventory2,
                        "Saved as a .slim archive",
                        "${file.name} is the smallest form of this app, for storage or sending. It can't be installed directly: open it in ApkSlim (or tap Restore & install) to rebuild the APK.",
                        BannerTone.INFO,
                    )
                }

                if (done.info.isInstalled && !isArchive) {
                    InfoBanner(
                        Icons.Outlined.WarningAmber,
                        "Uninstall the original first",
                        "${done.info.label} is installed with the developer's signature. Android will refuse to install this re-signed copy over it. Uninstalling removes the app's data, and the slim copy won't receive Play Store updates.",
                        BannerTone.WARNING,
                    ) { TextButton(onClick = { ApkActions.uninstall(context, done.info.packageName) }, contentPadding = PaddingValues(0.dp)) { Text("Uninstall original") } }
                }

                // ---- Stats ----
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(formatBytes(r.stats.removedAbiBytes), "unused CPU code removed", Modifier.weight(1f), Icons.Outlined.Memory)
                    StatTile("${r.stats.imagesOptimized}/${r.stats.imagesConsidered}", "images re-encoded (${formatBytes(r.stats.imageBytesSaved)})", Modifier.weight(1f), Icons.Outlined.Image, Brand.Amber)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        if (r.verify.verified) "Valid" else "Invalid",
                        if (r.verify.verified) "signature (${r.verify.schemes.joinToString("+")})" else r.verify.errors.firstOrNull() ?: "signature check failed",
                        Modifier.weight(1f),
                        Icons.Outlined.Verified,
                        if (r.verify.verified) Brand.Mint else Brand.Coral,
                    )
                    StatTile(String.format(java.util.Locale.US, "%.1f s", r.durationMs / 1000f), "processing time", Modifier.weight(1f), Icons.Outlined.Timer, Brand.Violet)
                }

                // ---- Before / after ----
                val after = r.after
                if (after != null) {
                    ElevatedSection {
                        Text("Before vs after", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        val cats = Category.entries.filter { (r.before[it] ?: 0) > 0 || (after[it] ?: 0) > 0 }
                            .sortedByDescending { r.before[it] ?: 0 }
                        val max = cats.maxOfOrNull { maxOf(r.before[it] ?: 0, after[it] ?: 0) } ?: 1
                        cats.forEach { c -> CompareBar(c.label, r.before[c] ?: 0, after[c] ?: 0, max, c.color()) }
                    }
                }

                Text(
                    "Saved in ApkSlim › History. App code (DEX) was not modified, so performance and behaviour are unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
fun ResultActions(installLabel: String, installing: Boolean, onInstall: () -> Unit, onShare: () -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onInstall, enabled = !installing, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (installing) androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp).width(20.dp))
            else Icon(Icons.Outlined.InstallMobile, null)
            Spacer(Modifier.width(8.dp))
            Text(if (installing) "Preparing…" else installLabel, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text("Share", maxLines = 1)
            }
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(6.dp)); Text("Save", maxLines = 1)
            }
        }
    }
}

/** Shown after a .slim archive was restored into installable APK(s). */
@Composable
fun RestoredScreen(state: ProcessState.Restored, installing: Boolean, onInstall: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val total = state.files.sumOf { it.length() }
    // Several parts are saved/shared as one .apks bundle; a single APK as itself.
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
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    .background(Brand.hero)
                    .statusBarsPadding()
                    .padding(bottom = 28.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(Icons.Outlined.Close, "Close", tint = Color.White)
                }
                Column(Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Verified, null, tint = Color.White, modifier = Modifier.height(56.dp).width(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Restored", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        "${state.meta.label.ifEmpty { "App" }} ${state.meta.versionName}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${formatBytes(state.archive.length())} archive → ${formatBytes(total)} in ${state.files.size} APK${if (state.files.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ResultActions(
                    installLabel = "Install",
                    installing = installing,
                    onInstall = { if (ApkActions.ensureInstallPermission(context)) onInstall() },
                    onShare = { ApkActions.share(context, shareable) },
                    onSave = { saveLauncher.launch(shareable.name) },
                )
                InfoBanner(
                    Icons.Outlined.WarningAmber,
                    "Signed with your ApkSlim key",
                    "If ${state.meta.label.ifEmpty { "this app" }} is installed from the Play Store, uninstall it first: Android won't replace an app signed with a different key.",
                    BannerTone.WARNING,
                )
            }
        }
    }
}
