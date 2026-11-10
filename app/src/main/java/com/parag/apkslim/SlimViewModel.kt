package com.parag.apkslim

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parag.apkslim.core.ApkAnalysis
import com.parag.apkslim.core.ApkAnalyzer
import com.parag.apkslim.core.CancelledException
import com.parag.apkslim.core.ArchiveMeta
import com.parag.apkslim.core.ImageMode
import com.parag.apkslim.core.NamedApk
import com.parag.apkslim.core.OutputFormat
import com.parag.apkslim.core.SlimArchive
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
import com.parag.apkslim.data.Installer
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
        /** A lone config split (no code) can't become a working app by itself. */
        val blockedReason: String? get() = when {
            analysis.isSplit && info.parts.size == 1 ->
                "This file is one part (split) of an app, without the app's code. Pick the installed app instead, or open the full .apks bundle, so all parts are processed together."
            else -> null
        }
        val isMultiPart: Boolean get() = info.parts.size > 1
    }
    data class Error(val message: String) : AnalyzeState
}

sealed interface ProcessState {
    data object Idle : ProcessState
    data class Running(val stage: Stage, val fraction: Float, val detail: String) : ProcessState
    data class Done(val result: OptimizeResult, val history: HistoryItem, val info: ApkInfo) : ProcessState
    data class Restored(val meta: ArchiveMeta, val files: List<File>, val archive: File) : ProcessState
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
        refreshStorage()
        refreshHistory()
        refreshSigning()
    }

    fun icon(pkg: String) = repo.icon(pkg)

    private val _storage = MutableStateFlow(com.parag.apkslim.data.StorageInfo(0, 0))
    val storage: StateFlow<com.parag.apkslim.data.StorageInfo> = _storage.asStateFlow()
    fun refreshStorage() { viewModelScope.launch { _storage.value = withContext(Dispatchers.IO) { repo.storage() } } }
    fun iconFor(file: File) = repo.iconForArchive(file)

    fun loadApps(force: Boolean = false) {
        if (_apps.value != null && !force) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { repo.installedApps() }
        }
    }

    private val _openedArchive = MutableStateFlow<File?>(null)
    /** Set when the user opened a .slim file: the UI routes to restore instead of analysis. */
    val openedArchive: StateFlow<File?> = _openedArchive.asStateFlow()

    /** Opens a picked .apk, .apks bundle or .slim archive. Returns false for archives (restore flow). */
    fun openUri(uri: Uri) = startAnalysis("Copying file…") {
        val file = repo.importUri(uri)
        when {
            SlimArchive.isArchive(file) -> { _openedArchive.value = file; null }
            file.name.endsWith(".apks", true) || isBundle(file) -> {
                val parts = SlimPipeline.unbundle(file, File(file.parentFile, "parts")).map { NamedApk(it.name, it) }
                val base = parts.firstOrNull { it.name == "base.apk" } ?: parts.first { f -> runCatching { !ApkAnalyzer.analyze(f.file).isSplit }.getOrDefault(false) }
                Triple(base.file, false, listOf(base) + (parts - base))
            }
            else -> Triple(file, false, listOf(NamedApk("base.apk", file)))
        }
    }

    fun openInstalled(app: InstalledApp) = startAnalysis("Reading ${app.label}…") {
        Triple(File(app.sourceDir), true, repo.partsOf(app))
    }

    private fun isBundle(f: File) = runCatching {
        java.util.zip.ZipFile(f).use { z -> z.entries().asSequence().any { it.name.endsWith(".apk") } && z.getEntry("AndroidManifest.xml") == null }
    }.getOrDefault(false)

    fun consumeArchive() { _openedArchive.value = null }

    private fun startAnalysis(message: String, source: suspend () -> Triple<File, Boolean, List<NamedApk>>?) {
        _analyze.value = AnalyzeState.Loading(message)
        _process.value = ProcessState.Idle
        viewModelScope.launch {
            _analyze.value = withContext(Dispatchers.IO) {
                try {
                    val (file, installed, parts) = source() ?: return@withContext AnalyzeState.Idle
                    val info = repo.describe(file, installed, parts)
                    val analysis = ApkAnalyzer.analyzeAll(parts.map { it.file })
                    val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull { it in analysis.abiSizes.keys }
                    AnalyzeState.Ready(info, analysis, deviceAbi)
                } catch (t: Throwable) {
                    AnalyzeState.Error(t.message ?: "Could not read this APK")
                }
            }
            (analyze.value as? AnalyzeState.Ready)?.let { ready ->
                applyPreset(Preset.BALANCED)
                // Pick the format that actually saves space: if an installable APK would save
                // under 10 %, default to the compressed .slim archive.
                val apkPct = ApkAnalyzer.estimate(ready.analysis, _options.value.copy(format = OutputFormat.APK)).total * 100.0 / ready.analysis.fileSize.coerceAtLeast(1)
                setFormat(if (apkPct < 10) OutputFormat.ARCHIVE else OutputFormat.APK)
            }
        }
    }

    fun applyPreset(p: Preset) {
        val ready = analyze.value as? AnalyzeState.Ready ?: return
        _preset.value = p
        if (p == Preset.CUSTOM) return
        val keep = ready.deviceAbi?.let { setOf(it) }
        val format = _options.value.format
        _options.value = OptimizeOptions(
            format = format,
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

    /** Output format isn't part of a preset, so switching it keeps the preset selection. */
    fun setFormat(format: OutputFormat) {
        _options.update { it.copy(format = format) }
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
            val opts = _options.value.copy(minSdk = ready.info.minSdk)
            val ext = when {
                opts.format == OutputFormat.ARCHIVE -> SlimArchive.EXTENSION
                ready.isMultiPart -> "apks"
                else -> "apk"
            }
            val output = File(outDir, "${safeName}_${ready.info.versionName.replace(Regex("[^A-Za-z0-9.]+"), "")}_slim_${System.currentTimeMillis()}.$ext")
            try {
                val key = if (opts.format == OutputFormat.APK) withContext(Dispatchers.IO) { signing.currentKey() } else null
                var lastEmit = 0L
                val result = SlimPipeline(AndroidImageProcessor()).runAll(
                    inputs = ready.info.parts,
                    meta = ArchiveMeta(ready.info.label, ready.info.packageName, ready.info.versionName, ready.info.minSdk),
                    output = output,
                    workDir = File(ctx.cacheDir, "work"),
                    options = opts,
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

    /** Rebuilds installable APK(s) from a .slim archive and signs them with the current key. */
    fun restore(archive: File) {
        cancelFlag.set(false)
        _process.value = ProcessState.Running(Stage.VERIFYING, 0f, "")
        viewModelScope.launch(Dispatchers.Default) {
            val ctx = getApplication<Application>()
            val outDir = File(ctx.cacheDir, "restored").apply { deleteRecursively(); mkdirs() }
            try {
                val key = signing.currentKey()
                var lastEmit = 0L
                val (meta, files) = SlimPipeline(null).restore(
                    archive, outDir, File(ctx.cacheDir, "work"), key,
                    progress = { stage, fraction, detail ->
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 60 || fraction >= 1f) {
                            lastEmit = now
                            _process.value = ProcessState.Running(if (stage == Stage.VERIFYING) Stage.READING else stage, fraction, detail)
                        }
                    },
                    isCancelled = { cancelFlag.get() },
                )
                _process.value = ProcessState.Restored(meta, files, archive)
            } catch (c: CancelledException) {
                _process.value = ProcessState.Cancelled
            } catch (t: Throwable) {
                _process.value = ProcessState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun toastShown() { _toast.value = null }

    /** Installs an output file: .apk directly, .apks as one multi-part session, .slim after restoring. */
    fun install(file: File) {
        if (_installing.value) return
        _installing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            try {
                val apks = when {
                    file.name.endsWith(".apks") -> SlimPipeline.unbundle(file, File(ctx.cacheDir, "install").apply { deleteRecursively() })
                    SlimArchive.isArchive(file) -> SlimPipeline(null).restore(
                        file, File(ctx.cacheDir, "install").apply { deleteRecursively() }, File(ctx.cacheDir, "work"), signing.currentKey(),
                    ).second
                    else -> listOf(file)
                }
                Installer.install(ctx, apks)
            } catch (t: Throwable) {
                _toast.value = "Install failed: ${t.message}"
            } finally {
                _installing.value = false
            }
        }
    }

    fun installFiles(files: List<File>) {
        if (_installing.value) return
        _installing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { Installer.install(getApplication<Application>(), files) }
            catch (t: Throwable) { _toast.value = "Install failed: ${t.message}" }
            finally { _installing.value = false }
        }
    }

    fun resetProcess() { _process.value = ProcessState.Idle }

    private fun summarize(): String {
        val o = _options.value
        return buildList {
            if (o.format == OutputFormat.ARCHIVE) add(".slim archive")
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
