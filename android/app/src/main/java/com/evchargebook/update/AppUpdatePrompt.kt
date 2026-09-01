package com.evchargebook.update

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.BuildConfig
import com.evchargebook.ui.theme.EVDesignTokens

/**
 * Release-only update flow.
 *
 * The app shows exactly one update-information decision dialog per discovered version. After the
 * user confirms, DownloadManager owns the background transfer and the verified APK is handed
 * directly to Android's package installer. There is deliberately no second app-level "ready to
 * install" dialog: Android's installer is already the authoritative final confirmation surface.
 *
 * A separate permission explanation is only shown when Android blocks package installation from
 * this source. Download progress stays in the system notification so normal app navigation remains
 * fully interactive while the APK is being downloaded and verified.
 */
@Composable
fun AppUpdatePrompt() {
    if (BuildConfig.BUILD_TYPE != "release") return

    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val manager = remember(context) { AppUpdateManager(context.applicationContext) }
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var downloadedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadToken by remember { mutableIntStateOf(0) }
    var resumeDownloadId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        val restored = runCatching { manager.restorePendingDownload() }
            .onFailure { error -> Log.w(TAG, "Failed to restore pending update download", error) }
            .getOrNull()

        when (restored) {
            is RestoredUpdateDownload.Ready -> {
                // A verified package is already durable. Skip any second update-information dialog
                // and continue directly to the Android installer/permission handoff.
                update = restored.info
                downloadedUri = restored.uri
                phase = UpdatePhase.READY
            }

            is RestoredUpdateDownload.InProgress -> {
                // Keep following the same DownloadManager row. Do not enqueue a second APK.
                update = restored.info
                phase = UpdatePhase.DOWNLOADING
                resumeDownloadId = restored.downloadId
                downloadToken += 1
            }

            null -> {
                runCatching { manager.checkForUpdate() }
                    .onSuccess { info ->
                        if (info != null && processUpdatePromptSessionGate.tryClaim(info.versionCode)) {
                            phase = UpdatePhase.DISCOVERED
                            update = info
                        } else if (info != null) {
                            Log.d(TAG, "Skip duplicate update prompt for versionCode=${info.versionCode}")
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Update discovery failed for ${BuildConfig.UPDATE_MANIFEST_URL}", error)
                    }
            }
        }
    }

    LaunchedEffect(downloadToken) {
        if (downloadToken <= 0) return@LaunchedEffect
        val info = update ?: return@LaunchedEffect
        val existingDownloadId = resumeDownloadId
        errorMessage = null
        downloadedUri = null
        runCatching {
            if (existingDownloadId != null) {
                manager.resumeDownloadAndVerify(existingDownloadId, info)
            } else {
                manager.downloadAndVerify(info)
            }
        }
            .onSuccess { uri ->
                resumeDownloadId = null
                downloadedUri = uri
                phase = UpdatePhase.READY
            }
            .onFailure { error ->
                resumeDownloadId = null
                Log.w(TAG, "Background update download failed for ${info.versionName}", error)
                errorMessage = error.message ?: "更新包下载失败，请稍后重试"
                phase = UpdatePhase.FAILED
            }
    }

    // READY is a transient handoff state, not another information dialog. Move to INSTALLING before
    // launching any external Activity so recomposition/resume cannot launch the installer twice.
    LaunchedEffect(phase, downloadedUri) {
        if (phase != UpdatePhase.READY) return@LaunchedEffect
        val uri = downloadedUri ?: return@LaunchedEffect
        if (!manager.canRequestPackageInstalls()) {
            phase = UpdatePhase.PERMISSION_REQUIRED
            manager.openInstallPermissionSettings(activity)
        } else {
            phase = UpdatePhase.INSTALLING
            runCatching { manager.launchInstaller(activity, uri) }
                .onFailure { error ->
                    Log.w(TAG, "Failed to launch package installer", error)
                    errorMessage = error.message ?: "无法打开 Android 系统安装界面"
                    phase = UpdatePhase.FAILED
                }
        }
    }

    val info = update

    fun deferOptionalUpdate() {
        if (info?.mandatory != true) {
            phase = UpdatePhase.IDLE
            update = null
        }
    }

    fun startDownload() {
        if (info == null) return
        // Hide the only update-information decision dialog synchronously before background work.
        phase = UpdatePhase.DOWNLOADING
        resumeDownloadId = null
        downloadToken += 1
    }

    fun continueInstall() {
        val uri = downloadedUri ?: return
        if (!manager.canRequestPackageInstalls()) {
            phase = UpdatePhase.PERMISSION_REQUIRED
            manager.openInstallPermissionSettings(activity)
        } else {
            phase = UpdatePhase.INSTALLING
            runCatching { manager.launchInstaller(activity, uri) }
                .onFailure { error ->
                    Log.w(TAG, "Failed to launch package installer", error)
                    errorMessage = error.message ?: "无法打开 Android 系统安装界面"
                    phase = UpdatePhase.FAILED
                }
        }
    }

    when (phase) {
        UpdatePhase.IDLE,
        UpdatePhase.DOWNLOADING,
        UpdatePhase.READY,
        UpdatePhase.INSTALLING -> Unit

        UpdatePhase.DISCOVERED -> {
            val current = info ?: return
            UpdateDecisionDialog(
                icon = Icons.Rounded.SystemUpdateAlt,
                accent = EVDesignTokens.Energy.green,
                title = "发现新版本 ${current.versionName}",
                lines = listOf(
                    "是否现在下载更新？",
                    "确认后由 Android 下载管理器在后台下载并校验，进度会显示在系统通知栏。",
                    "校验成功后会直接进入 Android 系统安装界面，不再重复显示更新信息。"
                ),
                confirmText = "更新",
                dismissText = if (current.mandatory) null else "稍后",
                onConfirm = ::startDownload,
                onDismiss = ::deferOptionalUpdate
            )
        }

        UpdatePhase.PERMISSION_REQUIRED -> {
            val current = info ?: return
            UpdateDecisionDialog(
                icon = Icons.Rounded.SystemUpdateAlt,
                accent = EVDesignTokens.Energy.warning,
                title = "允许安装此来源应用",
                lines = listOf(
                    "Android 需要先允许 EV Charge Book 安装已经下载并校验通过的更新包。",
                    "完成系统授权后返回这里，再点击继续安装。"
                ),
                confirmText = "继续安装",
                dismissText = if (current.mandatory) null else "稍后",
                onConfirm = ::continueInstall,
                onDismiss = ::deferOptionalUpdate
            )
        }

        UpdatePhase.FAILED -> {
            val current = info ?: return
            UpdateDecisionDialog(
                icon = Icons.Rounded.ErrorOutline,
                accent = EVDesignTokens.Energy.danger,
                title = "更新失败",
                lines = listOf(errorMessage ?: "更新包下载失败，请稍后重试"),
                confirmText = if (downloadedUri == null) "重试" else "重试安装",
                dismissText = if (current.mandatory) null else "稍后",
                onConfirm = if (downloadedUri == null) ::startDownload else ::continueInstall,
                onDismiss = ::deferOptionalUpdate
            )
        }
    }
}

@Composable
private fun UpdateDecisionDialog(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    lines: List<String>,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EVDesignTokens.Dark.surfaceElevated,
        titleContentColor = EVDesignTokens.Dark.primaryText,
        textContentColor = EVDesignTokens.Dark.secondaryText,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEachIndexed { index, line ->
                    if (index > 0) Spacer(Modifier.height(1.dp))
                    Text(
                        text = line,
                        color = if (index == lines.lastIndex && lines.size > 1) {
                            EVDesignTokens.Dark.secondaryText
                        } else {
                            EVDesignTokens.Dark.primaryText.copy(alpha = 0.92f)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = dismissText?.let { label ->
            {
                TextButton(onClick = onDismiss) {
                    Text(label, color = EVDesignTokens.Dark.secondaryText)
                }
            }
        }
    )
}

internal class UpdatePromptSessionGate {
    private val claimedVersionCodes = mutableSetOf<Int>()

    fun tryClaim(versionCode: Int): Boolean = synchronized(claimedVersionCodes) {
        claimedVersionCodes.add(versionCode)
    }
}

private val processUpdatePromptSessionGate = UpdatePromptSessionGate()

private enum class UpdatePhase {
    IDLE,
    DISCOVERED,
    DOWNLOADING,
    READY,
    INSTALLING,
    PERMISSION_REQUIRED,
    FAILED
}

private const val TAG = "AppUpdatePrompt"
