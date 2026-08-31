package com.evchargebook.update

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
 * There is deliberately no floating Popup while an APK is downloading. A Popup creates a
 * separate Android window and physical devices can report a touchable region that does not
 * exactly match its translated visual bounds. That made normal Dashboard/navigation controls
 * untappable even though the updater looked non-modal.
 *
 * The updater is modal only at explicit decision points:
 * 1. ask before download;
 * 2. ask after the verified APK is ready to install.
 *
 * DownloadManager owns progress in between, so every app screen remains fully interactive.
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
                // A verified APK survives process death. On a fresh app process, surface the
                // install action immediately instead of asking the user to download it again.
                if (processReadyPromptSessionGate.tryClaim(restored.info.versionCode)) {
                    update = restored.info
                    downloadedUri = restored.uri
                    phase = UpdatePhase.READY
                }
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
                processReadyPromptSessionGate.tryClaim(info.versionCode)
                phase = UpdatePhase.READY
            }
            .onFailure { error ->
                resumeDownloadId = null
                Log.w(TAG, "Background update download failed for ${info.versionName}", error)
                errorMessage = error.message ?: "更新包下载失败，请稍后重试"
                phase = UpdatePhase.FAILED
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
        // Hide the decision dialog synchronously before starting background work.
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
            manager.launchInstaller(activity, uri)
        }
    }

    when (phase) {
        UpdatePhase.IDLE,
        UpdatePhase.DOWNLOADING -> Unit

        UpdatePhase.DISCOVERED -> {
            val current = info ?: return
            UpdateDecisionDialog(
                icon = Icons.Rounded.SystemUpdateAlt,
                accent = EVDesignTokens.Energy.green,
                title = "发现新版本 ${current.versionName}",
                lines = listOf(
                    "是否现在下载更新？",
                    "确认后由 Android 下载管理器在后台下载并校验，进度会显示在系统通知栏。",
                    "下载期间总览、记录、统计、行程和车辆页面都可以正常使用。"
                ),
                confirmText = "更新",
                dismissText = if (current.mandatory) null else "稍后",
                onConfirm = ::startDownload,
                onDismiss = ::deferOptionalUpdate
            )
        }

        UpdatePhase.READY -> {
            val current = info ?: return
            UpdateDecisionDialog(
                icon = Icons.Rounded.CheckCircle,
                accent = EVDesignTokens.Energy.success,
                title = "${current.versionName} 已准备好",
                lines = listOf(
                    "更新包已下载并通过 SHA-256 完整性校验。",
                    "点击安装后会进入 Android 系统安装界面；即使关闭并重新打开 App，也不会重复下载。"
                ),
                confirmText = "安装",
                dismissText = if (current.mandatory) null else "稍后",
                onConfirm = ::continueInstall,
                onDismiss = ::deferOptionalUpdate
            )
        }

        UpdatePhase.PERMISSION_REQUIRED -> {
            val current = info ?: return
            UpdateDecisionDialog(
                icon = Icons.Rounded.CheckCircle,
                accent = EVDesignTokens.Energy.warning,
                title = "允许安装此来源应用",
                lines = listOf(
                    "Android 需要先允许 EV Charge Book 安装下载的更新包。",
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
                confirmText = "重试",
                dismissText = if (current.mandatory) null else "稍后",
                onConfirm = ::startDownload,
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
private val processReadyPromptSessionGate = UpdatePromptSessionGate()

private enum class UpdatePhase {
    IDLE,
    DISCOVERED,
    DOWNLOADING,
    READY,
    PERMISSION_REQUIRED,
    FAILED
}

private const val TAG = "AppUpdatePrompt"
