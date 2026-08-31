package com.evchargebook.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import com.evchargebook.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Update metadata is published only after the immutable signed APK has been activated on the server.
 * This manager never performs a silent install: Android's system package installer remains the final gate.
 */
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val publishedAt: String,
    val mandatory: Boolean
)

sealed class RestoredUpdateDownload {
    data class Ready(val info: AppUpdateInfo, val uri: Uri) : RestoredUpdateDownload()
    data class InProgress(val info: AppUpdateInfo, val downloadId: Long) : RestoredUpdateDownload()
}

class AppUpdateManager(private val context: Context) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val updatePrefs = context.getSharedPreferences(UPDATE_DOWNLOAD_PREFS, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(currentVersionCode: Int = BuildConfig.VERSION_CODE): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val manifestBaseUrl = BuildConfig.UPDATE_MANIFEST_URL
        val manifestUrl = cacheBustedUrl(manifestBaseUrl)
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
            setRequestProperty("Pragma", "no-cache")
        }
        try {
            require(connection.responseCode in 200..299) { "更新服务暂时不可用（${connection.responseCode}）" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            require(root.getInt("schemaVersion") == 1) { "不支持的更新清单版本" }
            val versionCode = root.getInt("versionCode")
            if (versionCode <= currentVersionCode) return@withContext null
            val apkPath = root.getString("apkPath")
            val sha256 = root.getString("sha256").lowercase(Locale.US)
            require(sha256.matches(Regex("[0-9a-f]{64}"))) { "更新包校验信息无效" }
            AppUpdateInfo(
                versionCode = versionCode,
                versionName = root.getString("versionName"),
                apkUrl = URL(URL(manifestBaseUrl), apkPath).toString(),
                sha256 = sha256,
                publishedAt = root.optString("publishedAt"),
                mandatory = root.optBoolean("mandatory", false)
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Recover an update DownloadManager task after the Activity/process was recreated.
     *
     * A successful task is SHA-256 verified again before being exposed as installable. An active
     * task is returned so the UI can keep observing the same download instead of enqueueing a
     * duplicate. Stale, failed, missing, already-installed, or corrupt tasks are forgotten.
     */
    suspend fun restorePendingDownload(
        currentVersionCode: Int = BuildConfig.VERSION_CODE
    ): RestoredUpdateDownload? = withContext(Dispatchers.IO) {
        val pending = readPendingDownload() ?: return@withContext null
        val (downloadId, info) = pending

        if (info.versionCode <= currentVersionCode) {
            clearPendingDownload()
            return@withContext null
        }

        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) {
                clearPendingDownload()
                return@withContext null
            }

            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri == null || !verifySha256(uri, info.sha256)) {
                        downloadManager.remove(downloadId)
                        clearPendingDownload()
                        return@withContext null
                    }
                    RestoredUpdateDownload.Ready(info, uri)
                }

                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED -> RestoredUpdateDownload.InProgress(info, downloadId)

                DownloadManager.STATUS_FAILED -> {
                    clearPendingDownload()
                    null
                }

                else -> {
                    clearPendingDownload()
                    null
                }
            }
        }
    }

    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(activity: Activity) {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    suspend fun downloadAndVerify(info: AppUpdateInfo): Uri = withContext(Dispatchers.IO) {
        val fileName = "ev-charge-book-${info.versionName}.apk"
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("EV Charge Book ${info.versionName}")
            .setDescription("正在下载应用更新")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadId = downloadManager.enqueue(request)

        // This record is the only bridge between DownloadManager and the next app process. Commit
        // it synchronously on the IO dispatcher before returning to the polling loop. If durable
        // persistence fails, cancel the just-created task rather than leave an unrecoverable orphan.
        try {
            savePendingDownload(downloadId, info)
        } catch (error: Throwable) {
            downloadManager.remove(downloadId)
            throw error
        }
        awaitDownloadAndVerify(downloadId, info)
    }

    suspend fun resumeDownloadAndVerify(downloadId: Long, info: AppUpdateInfo): Uri = withContext(Dispatchers.IO) {
        // Refresh persisted metadata before resuming observation so another process recreation can
        // still recover the same task.
        savePendingDownload(downloadId, info)
        awaitDownloadAndVerify(downloadId, info)
    }

    fun launchInstaller(activity: Activity, apkUri: Uri) {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private suspend fun awaitDownloadAndVerify(downloadId: Long, info: AppUpdateInfo): Uri {
        while (true) {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    clearPendingDownload()
                    error("找不到更新下载任务")
                }
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uri = downloadManager.getUriForDownloadedFile(downloadId)
                            ?: run {
                                clearPendingDownload()
                                error("更新包下载完成但无法读取")
                            }
                        if (!verifySha256(uri, info.sha256)) {
                            downloadManager.remove(downloadId)
                            clearPendingDownload()
                            error("更新包 SHA-256 校验失败")
                        }
                        // Deliberately keep the persisted task. If the user closes the app before
                        // installing, the next launch must recover this verified APK as READY.
                        return uri
                    }

                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        clearPendingDownload()
                        error("更新包下载失败（$reason）")
                    }
                }
            }
            delay(500)
        }
    }

    private fun savePendingDownload(downloadId: Long, info: AppUpdateInfo) {
        val persisted = updatePrefs.edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putInt(KEY_VERSION_CODE, info.versionCode)
            .putString(KEY_VERSION_NAME, info.versionName)
            .putString(KEY_APK_URL, info.apkUrl)
            .putString(KEY_SHA256, info.sha256)
            .putString(KEY_PUBLISHED_AT, info.publishedAt)
            .putBoolean(KEY_MANDATORY, info.mandatory)
            .commit()
        check(persisted) { "无法保存更新下载状态" }
    }

    private fun readPendingDownload(): Pair<Long, AppUpdateInfo>? {
        val downloadId = updatePrefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val versionCode = updatePrefs.getInt(KEY_VERSION_CODE, -1)
        val versionName = updatePrefs.getString(KEY_VERSION_NAME, null)
        val apkUrl = updatePrefs.getString(KEY_APK_URL, null)
        val sha256 = updatePrefs.getString(KEY_SHA256, null)?.lowercase(Locale.US)
        if (
            downloadId <= 0L ||
            versionCode <= 0 ||
            versionName.isNullOrBlank() ||
            apkUrl.isNullOrBlank() ||
            sha256 == null ||
            !sha256.matches(Regex("[0-9a-f]{64}"))
        ) {
            if (updatePrefs.contains(KEY_DOWNLOAD_ID)) clearPendingDownload()
            return null
        }

        return downloadId to AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            publishedAt = updatePrefs.getString(KEY_PUBLISHED_AT, "").orEmpty(),
            mandatory = updatePrefs.getBoolean(KEY_MANDATORY, false)
        )
    }

    private fun clearPendingDownload() {
        updatePrefs.edit().clear().apply()
    }

    private fun verifySha256(uri: Uri, expected: String): Boolean = runCatching {
        sha256(uri).equals(expected, ignoreCase = true)
    }.getOrDefault(false)

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法读取更新包" }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun cacheBustedUrl(baseUrl: String): String {
        val separator = if ('?' in baseUrl) '&' else '?'
        return "$baseUrl${separator}_updateCheck=${System.currentTimeMillis()}"
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val UPDATE_DOWNLOAD_PREFS = "app_update_download"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_APK_URL = "apk_url"
        private const val KEY_SHA256 = "sha256"
        private const val KEY_PUBLISHED_AT = "published_at"
        private const val KEY_MANDATORY = "mandatory"
    }
}
