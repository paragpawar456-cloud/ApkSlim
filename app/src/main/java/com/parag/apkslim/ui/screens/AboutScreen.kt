package com.parag.apkslim.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.parag.apkslim.ui.components.ElevatedSection
import com.parag.apkslim.ui.components.SectionTitle

private val DOES = listOf(
    "Removes native libraries for CPU types you don't need" to
        "Many APKs ship the same C/C++ code for arm64, armv7, x86 and x86_64. A phone only ever loads one set, so the rest is dead weight. This is usually the biggest win (often 30–60% of a universal APK).",
    "Re-encodes PNG/JPEG images as WebP" to
        "Lossless mode decodes the WebP again and compares every pixel with the original; if anything differs, the original is kept. Lossy mode trades a little quality for size. 9-patch, 16-bit, colour-profiled and animated PNGs are skipped.",
    "Recompresses at maximum deflate level" to
        "Lossless. Files that Android needs uncompressed (resources.arsc, page-aligned .so files, raw media) stay uncompressed so startup speed and memory use don't change.",
    "Strips build leftovers" to
        "Files such as META-INF/*.version and Maven descriptors that only build tools read. Service registrations and licence notices are kept.",
    "Aligns and re-signs" to
        "Uncompressed files are aligned (4 bytes, 16 KB for native libraries, ready for Android 15 16 KB devices), then the APK is signed with APK Signature Scheme v2/v3 (plus v1 for Android < 7) and verified.",
)

private val DOESNT = listOf(
    "Change app code (DEX)" to "Shrinking code needs the original project and R8. Editing compiled code could break the app, so ApkSlim never touches it. Performance and behaviour stay the same.",
    "Remove 'unused' resources or languages" to "Resources are referenced from code in ways that can't be detected reliably from an APK. Guessing wrong crashes the app.",
    "Promise a fixed 50–70%" to "APKs are already zip-compressed. Big savings come from unused CPU code; an APK without it may only shrink 2–15%. Apps installed from Google Play are usually already trimmed per device.",
    "Keep Play Store updates" to "A re-signed APK can't update, or be updated by, the developer-signed version. Use it for sideloading, archiving or saving space, not as a replacement for store installs.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How it works") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle("What ApkSlim does") }
            items(DOES) { (t, b) -> Point(true, t, b) }
            item { SectionTitle("What it deliberately doesn't do") }
            items(DOESNT) { (t, b) -> Point(false, t, b) }
        }
    }
}

@Composable
private fun Point(ok: Boolean, title: String, body: String) {
    ElevatedSection {
        Row {
            Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                null,
                tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
