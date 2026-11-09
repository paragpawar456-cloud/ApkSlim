package com.parag.apkslim.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.parag.apkslim.SlimViewModel
import com.parag.apkslim.ui.components.ElevatedSection
import com.parag.apkslim.ui.components.SectionTitle
import com.parag.apkslim.ui.theme.MonoStyle
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SlimViewModel, info: com.parag.apkslim.data.SigningInfo?, onAbout: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importUri by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }

    val pickKeystore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "keystore.p12"
            importUri = uri to name
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pkcs12")) { uri ->
        if (uri != null) exportUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Signing key") }
            item {
                ElevatedSection {
                    Text(
                        "Every optimized APK must be signed again. Android installs an update only if it's signed with the same key as the installed app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyChoice("ApkSlim key", "Generated on this phone. Export it to keep signing future versions the same way.", selected = info?.usingCustom != true) {
                        vm.setUseCustomKey(false)
                    }
                    KeyChoice(
                        "My own keystore",
                        if (vm.signing.hasCustom) "Imported .p12 / .pfx / .bks. Use the app's original release key to keep updates working." else "Import a .p12 / .pfx / .bks keystore first.",
                        selected = info?.usingCustom == true,
                        enabled = vm.signing.hasCustom,
                    ) { vm.setUseCustomKey(true) }

                    info?.let {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Text("Active certificate", style = MaterialTheme.typography.labelLarge)
                        Text(it.subject, style = MaterialTheme.typography.bodySmall)
                        Text("Valid until ${DateFormat.getDateInstance().format(Date(it.validUntil))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("SHA-256", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it.sha256, style = MonoStyle)
                    }
                }
            }
            item {
                ElevatedSection {
                    ActionRow(Icons.Outlined.Upload, "Import keystore", "Use your own release key") { pickKeystore.launch(arrayOf("*/*")) }
                    ActionRow(Icons.Outlined.Download, "Export ApkSlim key (.p12)", "Back it up so you can reuse it on a new phone or with apksigner") {
                        exportLauncher.launch("apkslim-key.p12")
                    }
                    if (vm.signing.hasCustom) {
                        ActionRow(Icons.Outlined.Key, "Remove imported keystore", "Switches back to the ApkSlim key", tint = MaterialTheme.colorScheme.error) { vm.removeCustomKey() }
                    }
                }
            }
            item { SectionTitle("About") }
            item {
                ElevatedSection {
                    ActionRow(Icons.AutoMirrored.Outlined.HelpOutline, "How ApkSlim works", "What it changes, what it never touches, realistic savings") { onAbout() }
                    Text("ApkSlim 1.0 · signing powered by apksig (AOSP)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    importUri?.let { (uri, name) ->
        ImportDialog(name, onDismiss = { importUri = null }) { storePw, alias, keyPw ->
            scope.launch {
                val r = vm.importKeystore(uri, name, storePw, alias, keyPw)
                Toast.makeText(context, r.fold({ "Keystore imported" }, { "Import failed: ${it.message}" }), Toast.LENGTH_LONG).show()
                if (r.isSuccess) importUri = null
            }
        }
    }

    exportUri?.let { uri ->
        PasswordDialog(
            title = "Protect the exported key",
            text = "Choose a password (at least 6 characters). You'll need it to use the key with apksigner or to import it again.",
            onDismiss = { exportUri = null },
        ) { pw ->
            scope.launch {
                val r = vm.exportKey(uri, pw)
                Toast.makeText(context, r.fold({ "Key exported. Keep it somewhere safe." }, { "Export failed: ${it.message}" }), Toast.LENGTH_LONG).show()
                exportUri = null
            }
        }
    }
}

@Composable
private fun KeyChoice(title: String, subtitle: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.outline)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ImportDialog(fileName: String, onDismiss: () -> Unit, onImport: (String, String?, String?) -> Unit) {
    var storePw by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var keyPw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import $fileName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(storePw, { storePw = it }, label = { Text("Keystore password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                OutlinedTextField(alias, { alias = it }, label = { Text("Alias (optional)") }, singleLine = true)
                OutlinedTextField(keyPw, { keyPw = it }, label = { Text("Key password (if different)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Text("JKS files from Android Studio must be converted to PKCS12 first:\nkeytool -importkeystore -srckeystore my.jks -destkeystore my.p12 -deststoretype PKCS12",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onImport(storePw, alias.ifBlank { null }, keyPw.ifBlank { null }) }, enabled = storePw.isNotEmpty()) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(pw2, { pw2 = it }, label = { Text("Repeat password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    isError = pw2.isNotEmpty() && pw != pw2)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = pw.length >= 6 && pw == pw2) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
