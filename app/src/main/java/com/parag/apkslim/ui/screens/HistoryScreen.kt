package com.parag.apkslim.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.parag.apkslim.data.HistoryItem
import com.parag.apkslim.ui.ApkActions
import com.parag.apkslim.ui.components.StatTile
import com.parag.apkslim.ui.formatBytes
import com.parag.apkslim.ui.formatDate
import com.parag.apkslim.ui.formatPercent
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    items: List<HistoryItem>,
    initialSelectedId: Long?,
    onDelete: (Long) -> Unit,
    onInstall: (File) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(items.firstOrNull { it.id == initialSelectedId }) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<HistoryItem?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val item = pendingSave
        if (uri != null && item != null) {
            val ok = ApkActions.copyTo(context, File(item.outputPath), uri)
            Toast.makeText(context, if (ok) "Saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
        }
        pendingSave = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (items.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear all") }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.History, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("No optimized APKs yet", style = MaterialTheme.typography.titleMedium)
                    Text("Results show up here so you can reinstall or share them later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    StatTile(formatBytes(items.sumOf { it.savedBytes.coerceAtLeast(0) }), "total saved", Modifier.weight(1f))
                    StatTile(formatPercent(items.map { it.savedPercent }.average().toFloat()), "average reduction", Modifier.weight(1f))
                    StatTile(items.size.toString(), "APKs", Modifier.weight(1f))
                }
            }
            items(items, key = { it.id }) { item ->
                HistoryRow(item) { selected = item }
            }
        }
    }

    selected?.let { item ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(item.appName, style = MaterialTheme.typography.titleLarge)
                    Text("${item.packageName} · v${item.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${formatBytes(item.originalSize)} → ${formatBytes(item.optimizedSize)}  (−${formatPercent(item.savedPercent)})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDate(item.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                }
                if (item.outputExists) {
                    val f = File(item.outputPath)
                    ListItem(
                        headlineContent = { Text(if (f.name.endsWith(".slim")) "Restore & install" else "Install") },
                        leadingContent = { Icon(Icons.Outlined.InstallMobile, null) },
                        modifier = Modifier.clickableRow { if (ApkActions.ensureInstallPermission(context)) onInstall(f); selected = null },
                    )
                    ListItem(
                        headlineContent = { Text("Share") },
                        leadingContent = { Icon(Icons.Outlined.Share, null) },
                        modifier = Modifier.clickableRow { ApkActions.share(context, f) },
                    )
                    ListItem(
                        headlineContent = { Text("Save to…") },
                        leadingContent = { Icon(Icons.Outlined.SaveAlt, null) },
                        modifier = Modifier.clickableRow { pendingSave = item; saveLauncher.launch(f.name) },
                    )
                } else {
                    Text("The output file was removed.", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error)
                }
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickableRow { onDelete(item.id); selected = null },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This deletes all ${items.size} optimized APKs stored by ApkSlim. Files you saved elsewhere are not affected.") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.fillMaxWidth().clickable(onClick = onClick)
