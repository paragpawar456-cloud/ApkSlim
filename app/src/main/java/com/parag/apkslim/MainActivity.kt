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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
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
    const val APPS = "apps?cat={cat}&focus={focus}"
    const val APPS_TAB = "apps?cat=-100&focus=false"
    const val HISTORY_TAB = "history?id=-1"
    fun apps(category: Int? = null, focus: Boolean = false) = "apps?cat=${category ?: -100}&focus=$focus"
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
    val storage by vm.storage.collectAsStateWithLifecycle()
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

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in setOf(Routes.HOME, Routes.APPS, Routes.HISTORY, Routes.SETTINGS)
    androidx.compose.material3.Scaffold(
        bottomBar = { if (showBar) BottomBar(route, nav) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { scaffoldPadding ->
    val bottomPadding = scaffoldPadding.calculateBottomPadding()
    NavHost(
        nav,
        startDestination = Routes.HOME,
        modifier = androidx.compose.ui.Modifier,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable(Routes.HOME) {
            LaunchedEffect(Unit) { vm.loadApps(); vm.refreshStorage() }
            HomeScreen(
                apps = apps,
                storage = storage,
                history = history,
                icon = vm::icon,
                onSearch = { nav.navigate(Routes.apps(focus = true)) },
                onCategory = { nav.navigate(Routes.apps(category = it)) },
                onCompressApp = { vm.openInstalled(it); nav.navigate(Routes.ANALYZE) },
                onPickFile = { pickApk.launch(arrayOf("*/*")) },
                onHistory = { nav.navigateTab(Routes.HISTORY_TAB) },
                onAbout = { nav.navigate(Routes.ABOUT) },
                onOpenHistoryItem = { nav.navigate(Routes.history(it.id)) },
                bottomPadding = bottomPadding,
            )
        }
        composable(
            Routes.APPS,
            arguments = listOf(
                navArgument("cat") { type = NavType.IntType; defaultValue = -100 },
                navArgument("focus") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val cat = entry.arguments?.getInt("cat")?.takeIf { it != -100 }
            AppsScreen(
                apps = apps,
                load = { vm.loadApps() },
                icon = vm::icon,
                initialCategory = cat,
                focusSearch = entry.arguments?.getBoolean("focus") == true,
                onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack(); Unit }) else null,
                onPick = { vm.openInstalled(it); nav.navigate(Routes.ANALYZE) },
                bottomPadding = bottomPadding,
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
                    title = "Restoring your app",
                )
            }
        }
        composable(
            Routes.HISTORY,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(bottom = bottomPadding)) { HistoryScreen(
                items = history,
                initialSelectedId = id,
                onDelete = { vm.deleteHistory(it) },
                onInstall = { vm.install(it) },
                onClear = { vm.clearHistory() },
                onBack = { nav.popBackStack() },
            ) }
        }
        composable(Routes.SETTINGS) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(bottom = bottomPadding)) {
                SettingsScreen(vm, signing, onAbout = { nav.navigate(Routes.ABOUT) }, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
    }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@androidx.compose.runtime.Composable
private fun BottomBar(route: String?, nav: NavHostController) {
    data class Tab(val route: String, val target: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val tabs = listOf(
        Tab(Routes.HOME, Routes.HOME, "Home", androidx.compose.material.icons.Icons.Outlined.Home),
        Tab(Routes.APPS, Routes.APPS_TAB, "Apps", androidx.compose.material.icons.Icons.Outlined.Apps),
        Tab(Routes.HISTORY, Routes.HISTORY_TAB, "History", androidx.compose.material.icons.Icons.Outlined.History),
        Tab(Routes.SETTINGS, Routes.SETTINGS, "Settings", androidx.compose.material.icons.Icons.Outlined.Settings),
    )
    androidx.compose.material3.NavigationBar(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { t ->
            androidx.compose.material3.NavigationBarItem(
                selected = route == t.route,
                onClick = { if (route != t.route) nav.navigateTab(t.target) },
                icon = { androidx.compose.material3.Icon(t.icon, null) },
                label = { androidx.compose.material3.Text(t.label) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    indicatorColor = com.parag.apkslim.ui.theme.AppTheme.extra.accentSoft,
                ),
            )
        }
    }
}
