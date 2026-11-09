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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.parag.apkslim.ProcessState
import com.parag.apkslim.core.Stage
import com.parag.apkslim.ui.components.BannerTone
import com.parag.apkslim.ui.components.InfoBanner
import com.parag.apkslim.ui.theme.Brand

@Composable
fun ProcessScreen(state: ProcessState, onCancel: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit, steps: List<Stage> = APK_STEPS) {
    // Keep the screen on while working; big APKs can take a minute.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp)) {
        when (state) {
            is ProcessState.Running, ProcessState.Idle, is ProcessState.Done, is ProcessState.Restored -> Running(state as? ProcessState.Running, onCancel, steps)
            is ProcessState.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                InfoBanner(Icons.Outlined.ErrorOutline, "Optimization failed", state.message, BannerTone.ERROR)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
            ProcessState.Cancelled -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cancelled", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Nothing was saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack) { Text("Back to options") }
            }
        }
    }
}



val APK_STEPS = listOf(Stage.READING, Stage.IMAGES, Stage.REPACKING, Stage.SIGNING, Stage.VERIFYING)
val ARCHIVE_STEPS = listOf(Stage.READING, Stage.IMAGES, Stage.REPACKING, Stage.ARCHIVING, Stage.VERIFYING)
val RESTORE_STEPS = listOf(Stage.READING, Stage.SIGNING)

@Composable
private fun Running(r: ProcessState.Running?, onCancel: () -> Unit, steps: List<Stage> = APK_STEPS) {
    val stage = r?.stage ?: Stage.READING

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale")
    val frac by animateFloatAsState(r?.fraction ?: 0f, label = "frac")

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(180.dp).scale(scale).clip(CircleShape).background(Brand.hero), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, trackColor = Color.White.copy(alpha = 0.2f), strokeWidth = 6.dp, modifier = Modifier.size(120.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(stage.label, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            r?.detail.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).clip(CircleShape))
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(0.8f)) {
            steps.forEach { s ->
                val done = s.ordinal < stage.ordinal
                val active = s == stage
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        done -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        active -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        else -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        s.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}
