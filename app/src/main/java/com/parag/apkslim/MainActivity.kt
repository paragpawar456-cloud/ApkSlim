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
    const val RESTORE = "restore"
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
    val installing by vm.installing.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val openedArchive by vm.openedArchive.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(toast) {
        toast?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); vm.toastShown() }
    }
    // A .slim archive was opened: restore it instead of analysing.
    LaunchedEffect(openedArchive) {
        openedArchive?.let { file ->
            vm.consumeArchive()
            vm.restore(file)
            nav.navigate(Routes.RESTORE) { popUpTo(Routes.HOME) }
        }
    }

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
                onPickFile = { pickApk.launch(arrayOf("*/*")) },
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
                onFormat = vm::setFormat,
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
                steps = if (options.format == com.parag.apkslim.core.OutputFormat.ARCHIVE) com.parag.apkslim.ui.screens.ARCHIVE_STEPS else com.parag.apkslim.ui.screens.APK_STEPS,
            )
        }
        composable(Routes.RESULT) {
            val done = process as? ProcessState.Done
            val close = { vm.resetProcess(); nav.popBackStack(Routes.HOME, inclusive = false); Unit }
            BackHandler { close() }
            if (done != null) ResultScreen(done, installing, onInstall = { vm.install(it) }, onClose = close)
            else LaunchedEffect(Unit) { nav.popBackStack(Routes.HOME, inclusive = false) }
        }
        composable(Routes.RESTORE) {
            val close = { vm.resetProcess(); nav.popBackStack(Routes.HOME, inclusive = false); Unit }
            BackHandler { if (process is ProcessState.Running) vm.cancel() else close() }
            when (val p = process) {
                is ProcessState.Restored -> com.parag.apkslim.ui.screens.RestoredScreen(p, installing, onInstall = { vm.installFiles(p.files) }, onClose = close)
                else -> ProcessScreen(
                    state = p,
                    onCancel = vm::cancel,
                    onBack = close,
                    onRetry = close,
                    steps = com.parag.apkslim.ui.screens.RESTORE_STEPS,
                )
            }
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
                onInstall = { vm.install(it) },
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
