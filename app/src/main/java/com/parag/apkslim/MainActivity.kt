package com.parag.apkslim

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.parag.apkslim.ui.screens.AboutScreen
import com.parag.apkslim.ui.screens.AnalyzeScreen
import com.parag.apkslim.ui.screens.AppsScreen
import com.parag.apkslim.ui.screens.HistoryScreen
import com.parag.apkslim.ui.screens.HomeScreen
import com.parag.apkslim.ui.screens.ProcessScreen
import com.parag.apkslim.ui.screens.ResultScreen
import com.parag.apkslim.ui.screens.SettingsScreen
import com.parag.apkslim.ui.theme.ApkSlimTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val vm: SlimViewModel by viewModels()
    private val incoming = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ApkSlimTheme {
                val nav = rememberNavController()
                val pending by incoming.collectAsStateWithLifecycle()
                LaunchedEffect(pending) {
                    pending?.let {
                        vm.openUri(it)
                        nav.navigate(Routes.ANALYZE) { launchSingleTop = true }
                        incoming.value = null
                    }
                }
                AppNav(nav, vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) incoming.value = uri
    }
}

object Routes {
    const val HOME = "home"
    const val APPS = "apps"
    const val ANALYZE = "analyze"
    const val PROCESS = "process"
    const val RESULT = "result"
    const val HISTORY = "history?id={id}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    fun history(id: Long? = null) = if (id == null) "history" else "history?id=$id"
}

@Composable
private fun AppNav(nav: NavHostController, vm: SlimViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val analyze by vm.analyze.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val preset by vm.preset.collectAsStateWithLifecycle()
    val estimate by vm.estimate.collectAsStateWithLifecycle()
    val process by vm.process.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val signing by vm.signingInfo.collectAsStateWithLifecycle()

    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.openUri(uri)
            nav.navigate(Routes.ANALYZE)
        }
    }

    NavHost(
        nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                history = history,
                onPickFile = { pickApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
                onInstalled = { nav.navigate(Routes.APPS) },
                onHistory = { nav.navigate(Routes.history()) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onAbout = { nav.navigate(Routes.ABOUT) },
                onOpenHistoryItem = { nav.navigate(Routes.history(it.id)) },
            )
        }
        composable(Routes.APPS) {
            AppsScreen(
                apps = apps,
                load = { vm.loadApps() },
                icon = vm::icon,
                onBack = { nav.popBackStack() },
                onPick = { vm.openInstalled(it); nav.navigate(Routes.ANALYZE) },
            )
        }
        composable(Routes.ANALYZE) {
            AnalyzeScreen(
                state = analyze,
                options = options,
                preset = preset,
                estimate = estimate,
                signing = signing,
                iconFor = vm::iconFor,
                onPreset = vm::applyPreset,
                onToggleAbi = vm::toggleAbi,
                onOptions = vm::updateOptions,
                onOptimize = { vm.optimize(); nav.navigate(Routes.PROCESS) },
                onSigningSettings = { nav.navigate(Routes.SETTINGS) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.PROCESS) {
            LaunchedEffect(process) {
                if (process is ProcessState.Done) {
                    nav.navigate(Routes.RESULT) { popUpTo(Routes.ANALYZE) }
                }
            }
            BackHandler(enabled = process is ProcessState.Running) { vm.cancel() }
            ProcessScreen(
                state = process,
                onCancel = vm::cancel,
                onBack = { vm.resetProcess(); nav.popBackStack() },
                onRetry = { vm.optimize() },
            )
        }
        composable(Routes.RESULT) {
            val done = process as? ProcessState.Done
            val close = { vm.resetProcess(); nav.popBackStack(Routes.HOME, inclusive = false); Unit }
            BackHandler { close() }
            if (done != null) ResultScreen(done, onClose = close)
            else LaunchedEffect(Unit) { nav.popBackStack(Routes.HOME, inclusive = false) }
        }
        composable(
            Routes.HISTORY,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
            HistoryScreen(
                items = history,
                initialSelectedId = id,
                onDelete = { vm.deleteHistory(it) },
                onClear = { vm.clearHistory() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm, signing, onAbout = { nav.navigate(Routes.ABOUT) }, onBack = { nav.popBackStack() })
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
