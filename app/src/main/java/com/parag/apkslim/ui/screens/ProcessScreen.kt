package com.parag.apkslim.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parag.apkslim.ProcessState
import com.parag.apkslim.core.Stage
import com.parag.apkslim.ui.components.BannerTone
import com.parag.apkslim.ui.components.BrandMark
import com.parag.apkslim.ui.components.InfoBanner
import com.parag.apkslim.ui.theme.AppTheme

val APK_STEPS = listOf(Stage.READING, Stage.IMAGES, Stage.REPACKING, Stage.SIGNING, Stage.VERIFYING)
val ARCHIVE_STEPS = listOf(Stage.READING, Stage.IMAGES, Stage.REPACKING, Stage.ARCHIVING, Stage.VERIFYING)
val RESTORE_STEPS = listOf(Stage.READING, Stage.SIGNING)

private fun Stage.friendly(): Pair<String, String> = when (this) {
    Stage.READING -> "Reading the app" to "Opening the package and checking every file"
    Stage.IMAGES -> "Slimming images" to "Re-encoding pictures as WebP"
    Stage.REPACKING -> "Repacking" to "Removing unused parts and recompressing"
    Stage.ARCHIVING -> "Compressing" to "Packing everything with XZ, the strongest step"
    Stage.SIGNING -> "Signing" to "Adding a fresh signature so Android trusts it"
    Stage.VERIFYING -> "Final check" to "Making sure nothing is broken"
    Stage.DONE -> "Done" to ""
}

@Composable
fun ProcessScreen(
    state: ProcessState,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    steps: List<Stage> = APK_STEPS,
    title: String = "Compressing your app",
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        when (state) {
            is ProcessState.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                InfoBanner(Icons.Outlined.ErrorOutline, "Something went wrong", state.message, BannerTone.ERROR)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
            ProcessState.Cancelled -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cancelled", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text("Nothing was saved.", color = AppTheme.extra.muted)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack) { Text("Back") }
            }
            else -> Tracking(state as? ProcessState.Running, steps, title, onCancel)
        }
    }
}

/** Order-tracking style: header, live progress bar, vertical timeline. */
@Composable
private fun Tracking(r: ProcessState.Running?, steps: List<Stage>, title: String, onCancel: () -> Unit) {
    val stage = r?.stage ?: Stage.READING
    val frac by animateFloatAsState(r?.fraction ?: 0f, label = "frac")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dot by pulse.animateFloat(0.75f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    val currentIndex = steps.indexOfFirst { it == stage }.let { if (it < 0) steps.indexOfLast { s -> s.ordinal < stage.ordinal } + 1 else it }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(44.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text("Keep ApkSlim open, this usually takes under a minute", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            }
        }
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { ((currentIndex.coerceAtLeast(0) + frac.coerceIn(0f, 1f)) / steps.size).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(28.dp))

        steps.forEachIndexed { i, s ->
            val done = i < currentIndex
            val active = i == currentIndex
            val (name, desc) = s.friendly()
            Row {
                // timeline rail
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                    Box(
                        Modifier.size(24.dp).scale(if (active) dot else 1f).clip(CircleShape).background(
                            when {
                                done -> AppTheme.extra.success
                                active -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Outlined.Check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                        else if (active) Box(Modifier.size(8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White))
                    }
                    if (i < steps.lastIndex) {
                        Box(Modifier.width(2.dp).height(44.dp).background(if (done) AppTheme.extra.success else MaterialTheme.colorScheme.surfaceContainerHighest))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).alpha(if (done || active) 1f else 0.5f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (active && !r?.detail.isNullOrBlank()) r!!.detail else desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (active) Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
    }
}
