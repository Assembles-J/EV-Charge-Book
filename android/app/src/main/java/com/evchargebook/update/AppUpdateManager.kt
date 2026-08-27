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

class AppUpdateManager(private val context: Context) {
    suspend fun checkForUpdate(currentVersionCode: Int = BuildConfig.VERSION_CODE): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
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
                apkUrl = URL(URL(manifestUrl), apkPath).toString(),
                sha256 = sha256,
                publishedAt = root.optString("publishedAt"),
                mandatory = root.optBoolean("mandatory", false)
            )
        } finally {
            connection.disconnect()
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
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "ev-charge-book-${info.versionName}.apk"
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("EV Charge Book ${info.versionName}")
            .setDescription("正在下载应用更新")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadId = downloadManager.enqueue(request)

        while (true) {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                require(cursor.moveToFirst()) { "找不到更新下载任务" }
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uri = downloadManager.getUriForDownloadedFile(downloadId)
                            ?: error("更新包下载完成但无法读取")
                        val actual = sha256(uri)
                        require(actual.equals(info.sha256, ignoreCase = true)) { "更新包 SHA-256 校验失败" }
                        return@withContext uri
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        error("更新包下载失败（$reason）")
                    }
                }
            }
            delay(500)
        }
        error("unreachable")
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

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
