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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.parag.apkslim.ProcessState
import com.parag.apkslim.core.Category
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
fun ResultScreen(done: ProcessState.Done, onClose: () -> Unit) {
    val context = LocalContext.current
    val r = done.result
    val file = r.output
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
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
                if (r.savedPercent < 3f) {
                    InfoBanner(
                        Icons.Outlined.ThumbUp,
                        "Already well optimized",
                        "This APK had little left to remove: it was probably built with R8, resource shrinking and per-device splits. That's a good sign for the developer, not a failure.",
                        BannerTone.INFO,
                    )
                }

                // ---- Actions ----
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { ApkActions.install(context, file) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.InstallMobile, null); Spacer(Modifier.width(6.dp)); Text("Install")
                    }
                    FilledTonalButton(onClick = { ApkActions.share(context, file) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text("Share")
                    }
                    OutlinedButton(onClick = { saveLauncher.launch(file.name) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(6.dp)); Text("Save")
                    }
                }

                if (done.info.isInstalled) {
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
                ElevatedSection {
                    Text("Before vs after", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    val cats = Category.entries.filter { (r.before[it] ?: 0) > 0 || (r.after[it] ?: 0) > 0 }
                        .sortedByDescending { r.before[it] ?: 0 }
                    val max = cats.maxOfOrNull { maxOf(r.before[it] ?: 0, r.after[it] ?: 0) } ?: 1
                    cats.forEach { c -> CompareBar(c.label, r.before[c] ?: 0, r.after[c] ?: 0, max, c.color()) }
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
