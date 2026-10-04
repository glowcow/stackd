package dev.glowcow.stackd.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import dev.glowcow.stackd.MainActivity
import dev.glowcow.stackd.R
import dev.glowcow.stackd.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** A published release newer than the running app, with the APK for this device. */
class AppRelease(val version: String, val notes: String, val url: String, val size: Long)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data object Failed : AppUpdateState
    class Available(val release: AppRelease) : AppUpdateState
    class Downloading(val release: AppRelease, val progress: Float) : AppUpdateState
}

/**
 * Updates the app from its GitHub releases: asks for the latest one, downloads the APK and hands it to the
 * system installer, which checks the signature and asks the user.
 */
class AppUpdater(private val context: Context, private val scope: CoroutineScope) {
    val current: String = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** A build from a branch carries a commit hash instead of a version and cannot tell what is newer. */
    val supported = parse(current) != null

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state

    /** Asks GitHub for the latest release; returns it when it is newer than this build. */
    suspend fun check(): AppRelease? {
        if (!supported || _state.value is AppUpdateState.Downloading) return null
        _state.value = AppUpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatching { latest() } }
        val release = result.getOrNull()
        _state.value = when {
            result.isFailure -> AppUpdateState.Failed
            release != null -> AppUpdateState.Available(release)
            else -> AppUpdateState.UpToDate
        }
        return release
    }

    fun checkNow() {
        scope.launch { check() }
    }

    /** Downloads [release] and opens the system installer. */
    fun install(release: AppRelease) {
        if (_state.value is AppUpdateState.Downloading) return
        _state.value = AppUpdateState.Downloading(release, 0f)
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { download(release) { _state.value = AppUpdateState.Downloading(release, it) } }.getOrNull()
            }
            if (file == null) {
                _state.value = AppUpdateState.Failed
                return@launch
            }
            _state.value = AppUpdateState.Available(release)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun latest(): AppRelease? {
        val c = URI(LATEST_URL).toURL().openConnection() as HttpsURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "stackd/$current")
            check(c.responseCode == HttpsURLConnection.HTTP_OK) { "GitHub answered ${c.responseCode}" }
            val root = Json.parseToJsonElement(c.inputStream.use { it.readNBytes(MAX_JSON_BYTES).decodeToString() }).jsonObject
            val version = root.getValue("tag_name").jsonPrimitive.content.removePrefix("v")
            if (!isNewer(version, current)) return null
            val name = "stackd-$version-${Build.SUPPORTED_ABIS.first()}-release.apk"
            val asset = root.getValue("assets").jsonArray.map { it.jsonObject }.firstOrNull { it["name"]?.jsonPrimitive?.content == name } ?: return null
            val url = asset.getValue("browser_download_url").jsonPrimitive.content
            // Only an APK of our own releases is ever handed to the installer.
            if (!url.startsWith(DOWNLOAD_PREFIX)) return null
            return AppRelease(version, plain(root["body"]?.jsonPrimitive?.content.orEmpty()), url, asset.getValue("size").jsonPrimitive.long)
        } finally {
            c.disconnect()
        }
    }

    private fun download(release: AppRelease, onProgress: (Float) -> Unit): File {
        require(release.size in 1..MAX_APK_BYTES) { "Unexpected APK size" }
        val dir = File(context.cacheDir, "update")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "stackd-${release.version}.apk")
        val c = URI(release.url).toURL().openConnection() as HttpsURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", "stackd/$current")
            check(c.responseCode == HttpsURLConnection.HTTP_OK) { "Download answered ${c.responseCode}" }
            var done = 0L
            c.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        done += n
                        check(done <= release.size) { "The APK is larger than announced" }
                        output.write(buffer, 0, n)
                        onProgress(done.toFloat() / release.size)
                    }
                }
            }
            check(done == release.size) { "The APK is incomplete" }
            return file
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/glowcow/stackd/releases/latest"
        private const val DOWNLOAD_PREFIX = "https://github.com/glowcow/stackd/releases/download/"
        private const val APK_TYPE = "application/vnd.android.package-archive"
        private const val TIMEOUT_MS = 20_000
        private const val MAX_JSON_BYTES = 1024 * 1024
        private const val MAX_APK_BYTES = 200L * 1024 * 1024

        /** `X.Y.Z` as numbers, or null for anything else. */
        internal fun parse(version: String): List<Int>? =
            version.split(".").map { it.toIntOrNull() ?: return null }.takeIf { it.size == 3 && it.all { n -> n >= 0 } }

        /** Release notes are Markdown; shown as text, they lose the heading marks and code ticks. */
        internal fun plain(notes: String): String =
            notes.lines().joinToString("\n") { it.replace(HEADING, "").replace("`", "") }.trim()

        private val HEADING = Regex("^#+\\s*")

        internal fun isNewer(candidate: String, current: String): Boolean {
            val a = parse(candidate) ?: return false
            val b = parse(current) ?: return false
            for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
            return false
        }
    }
}

/** Keeps the weekly check for a new app version in line with the setting. */
object AppUpdateScheduler {
    private const val JOB_ID = 2
    private const val WEEK_MS = 7 * 24 * 3_600_000L

    fun apply(context: Context, enabled: Boolean) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        when {
            !enabled -> scheduler.cancel(JOB_ID)
            // Rescheduling restarts the period, so a pending job is left alone.
            scheduler.getPendingJob(JOB_ID) != null -> Unit
            else -> scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, AppUpdateService::class.java))
                    .setPeriodic(WEEK_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}

/** Background run: one notification per new version; a tap opens the settings, where it is installed. */
class AppUpdateService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = container.scope.launch {
            val release = container.appUpdater.check()
            if (release != null && container.settings.firstRun("app_update_${release.version}")) notify(release)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    private fun notify(release: AppRelease) {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_update_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_APP_UPDATE, release.version),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_update_available, release.version))
                .setContentText(getString(R.string.app_update_tap))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL = "app_updates"
        const val NOTIFICATION_ID = 0x5743
    }
}
