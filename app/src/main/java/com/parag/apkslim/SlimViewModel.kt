package com.parag.apkslim

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parag.apkslim.core.ApkAnalysis
import com.parag.apkslim.core.ApkAnalyzer
import com.parag.apkslim.core.CancelledException
import com.parag.apkslim.core.ImageMode
import com.parag.apkslim.core.OptimizeOptions
import com.parag.apkslim.core.OptimizeResult
import com.parag.apkslim.core.SavingsEstimate
import com.parag.apkslim.core.SlimPipeline
import com.parag.apkslim.core.Stage
import com.parag.apkslim.data.AndroidImageProcessor
import com.parag.apkslim.data.ApkInfo
import com.parag.apkslim.data.AppRepository
import com.parag.apkslim.data.HistoryItem
import com.parag.apkslim.data.HistoryStore
import com.parag.apkslim.data.InstalledApp
import com.parag.apkslim.data.SigningInfo
import com.parag.apkslim.data.SigningKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class Preset(val title: String, val blurb: String) {
    SAFE("Safe", "Only removes CPU code this phone can't use. Every other file stays byte-identical."),
    BALANCED("Balanced", "Safe + pixel-perfect (lossless) image re-encoding + build metadata cleanup."),
    MAXIMUM("Maximum", "Balanced + lossy image compression. Smallest result; images may look slightly softer."),
    CUSTOM("Custom", "Your own combination of options."),
}

sealed interface AnalyzeState {
    data object Idle : AnalyzeState
    data class Loading(val message: String) : AnalyzeState
    data class Ready(val info: ApkInfo, val analysis: ApkAnalysis, val deviceAbi: String?) : AnalyzeState {
        /** Optimizing only base.apk of a Play split install would produce an app that can't start. */
        val blockedReason: String? get() = when {
            analysis.isSplit -> "This file looks like a split APK (no app code inside). Split APKs can't be optimized on their own."
            info.fromInstalledApp && info.installedSplitCount > 0 ->
                "This app was installed from an App Bundle as ${info.installedSplitCount + 1} separate APKs. " +
                    "Google Play already delivered only the parts your phone needs, so there's little to remove, " +
                    "and rebuilding just the base APK would break the app."
            else -> null
        }
    }
    data class Error(val message: String) : AnalyzeState
}

sealed interface ProcessState {
    data object Idle : ProcessState
    data class Running(val stage: Stage, val fraction: Float, val detail: String) : ProcessState
    data class Done(val result: OptimizeResult, val history: HistoryItem, val info: ApkInfo) : ProcessState
    data class Failed(val message: String) : ProcessState
    data object Cancelled : ProcessState
}

class SlimViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val historyStore = HistoryStore(app)
    val signing = SigningKeyManager(app)

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    private val _analyze = MutableStateFlow<AnalyzeState>(AnalyzeState.Idle)
    val analyze: StateFlow<AnalyzeState> = _analyze.asStateFlow()

    private val _options = MutableStateFlow(OptimizeOptions())
    val options: StateFlow<OptimizeOptions> = _options.asStateFlow()

    private val _preset = MutableStateFlow(Preset.BALANCED)
    val preset: StateFlow<Preset> = _preset.asStateFlow()

    private val _estimate = MutableStateFlow<SavingsEstimate?>(null)
    val estimate: StateFlow<SavingsEstimate?> = _estimate.asStateFlow()

    private val _process = MutableStateFlow<ProcessState>(ProcessState.Idle)
    val process: StateFlow<ProcessState> = _process.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val _signingInfo = MutableStateFlow<SigningInfo?>(null)
    val signingInfo: StateFlow<SigningInfo?> = _signingInfo.asStateFlow()

    private var processJob: Job? = null
    private val cancelFlag = AtomicBoolean(false)

    init {
        refreshHistory()
        refreshSigning()
    }

    fun icon(pkg: String) = repo.icon(pkg)
    fun iconFor(file: File) = repo.iconForArchive(file)

    fun loadApps(force: Boolean = false) {
        if (_apps.value != null && !force) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { repo.installedApps() }
        }
    }

    fun openUri(uri: Uri) = startAnalysis("Copying APK…") { repo.importUri(uri) to false }

    fun openInstalled(app: InstalledApp) = startAnalysis("Reading ${app.label}…") { File(app.sourceDir) to true }

    private fun startAnalysis(message: String, source: suspend () -> Pair<File, Boolean>) {
        _analyze.value = AnalyzeState.Loading(message)
        _process.value = ProcessState.Idle
        viewModelScope.launch {
            _analyze.value = withContext(Dispatchers.IO) {
                try {
                    val (file, installed) = source()
                    val info = repo.describe(file, installed)
                    val analysis = ApkAnalyzer.analyze(file)
                    val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull { it in analysis.abiSizes.keys }
                    AnalyzeState.Ready(info, analysis, deviceAbi)
                } catch (t: Throwable) {
                    AnalyzeState.Error(t.message ?: "Could not read this APK")
                }
            }
            (analyze.value as? AnalyzeState.Ready)?.let { applyPreset(Preset.BALANCED) }
        }
    }

    fun applyPreset(p: Preset) {
        val ready = analyze.value as? AnalyzeState.Ready ?: return
        _preset.value = p
        if (p == Preset.CUSTOM) return
        val keep = ready.deviceAbi?.let { setOf(it) }
        _options.value = OptimizeOptions(
            keepAbis = keep,
            imageMode = when (p) { Preset.SAFE -> ImageMode.OFF; Preset.MAXIMUM -> ImageMode.LOSSY; else -> ImageMode.LOSSLESS },
            imageQuality = 80,
            compressionLevel = 9,
            stripBuildMetadata = p != Preset.SAFE,
            minSdk = ready.info.minSdk,
        )
        recompute()
    }

    fun updateOptions(transform: (OptimizeOptions) -> OptimizeOptions) {
        _options.update(transform)
        _preset.value = Preset.CUSTOM
        recompute()
    }

    fun toggleAbi(abi: String) = updateOptions { o ->
        val ready = analyze.value as AnalyzeState.Ready
        val current = o.keepAbis ?: ready.analysis.abis.toSet()
        val next = if (abi in current) current - abi else current + abi
        o.copy(keepAbis = if (next.size == ready.analysis.abis.size) null else next)
    }

    private fun recompute() {
        val ready = analyze.value as? AnalyzeState.Ready ?: return
        _estimate.value = ApkAnalyzer.estimate(ready.analysis, _options.value)
    }

    fun optimize() {
        val ready = analyze.value as? AnalyzeState.Ready ?: return
        if (ready.blockedReason != null) return
        cancelFlag.set(false)
        _process.value = ProcessState.Running(Stage.READING, 0f, "")
        processJob = viewModelScope.launch(Dispatchers.Default) {
            val ctx = getApplication<Application>()
            val outDir = File(ctx.filesDir, "outputs").apply { mkdirs() }
            val safeName = ready.info.label.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "app" }
            val output = File(outDir, "${safeName}_${ready.info.versionName.replace(Regex("[^A-Za-z0-9.]+"), "")}_slim_${System.currentTimeMillis()}.apk")
            try {
                val key = withContext(Dispatchers.IO) { signing.currentKey() }
                var lastEmit = 0L
                val result = SlimPipeline(AndroidImageProcessor()).run(
                    input = ready.info.file,
                    output = output,
                    workDir = File(ctx.cacheDir, "work"),
                    options = _options.value.copy(minSdk = ready.info.minSdk),
                    key = key,
                    progress = { stage, fraction, detail ->
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 60 || fraction >= 1f || stage != (process.value as? ProcessState.Running)?.stage) {
                            lastEmit = now
                            _process.value = ProcessState.Running(stage, fraction, detail)
                        }
                    },
                    isCancelled = { cancelFlag.get() },
                )
                val item = HistoryItem(
                    id = System.currentTimeMillis(),
                    appName = ready.info.label,
                    packageName = ready.info.packageName,
                    versionName = ready.info.versionName,
                    originalSize = result.originalSize,
                    optimizedSize = result.optimizedSize,
                    timestamp = System.currentTimeMillis(),
                    outputPath = output.absolutePath,
                    summary = summarize(),
                )
                historyStore.add(item)
                refreshHistory()
                _process.value = ProcessState.Done(result, item, ready.info)
            } catch (c: CancelledException) {
                output.delete()
                _process.value = ProcessState.Cancelled
            } catch (oom: OutOfMemoryError) {
                output.delete()
                _process.value = ProcessState.Failed("Ran out of memory. Try turning image optimization off for this APK.")
            } catch (t: Throwable) {
                output.delete()
                _process.value = ProcessState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun cancel() = cancelFlag.set(true)

    fun resetProcess() { _process.value = ProcessState.Idle }

    private fun summarize(): String {
        val o = _options.value
        return buildList {
            o.keepAbis?.let { add("ABI: ${it.joinToString()}") }
            when (o.imageMode) {
                ImageMode.LOSSLESS -> add("Lossless images")
                ImageMode.LOSSY -> add("Lossy images q${o.imageQuality}")
                ImageMode.OFF -> {}
            }
            add("Deflate ${o.compressionLevel}")
            if (o.stripBuildMetadata) add("Metadata stripped")
        }.joinToString(" · ")
    }

    fun refreshHistory() {
        viewModelScope.launch { _history.value = withContext(Dispatchers.IO) { historyStore.load() } }
    }

    fun deleteHistory(id: Long) = viewModelScope.launch(Dispatchers.IO) { historyStore.delete(id); refreshHistory() }
    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { historyStore.clear(); refreshHistory() }

    fun refreshSigning() {
        viewModelScope.launch {
            _signingInfo.value = withContext(Dispatchers.IO) { runCatching { signing.info() }.getOrNull() }
        }
    }

    fun setUseCustomKey(v: Boolean) { signing.useCustom = v; refreshSigning() }

    suspend fun importKeystore(uri: Uri, name: String, storePw: String, alias: String?, keyPw: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { signing.importCustom(uri, name, storePw, alias, keyPw) }.map { }
        }.also { refreshSigning() }

    suspend fun exportKey(uri: Uri, password: String): Result<Unit> =
        withContext(Dispatchers.IO) { runCatching { signing.exportDefault(uri, password) } }

    fun removeCustomKey() { signing.removeCustom(); refreshSigning() }
}
