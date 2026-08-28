package com.evchargebook.update

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.evchargebook.BuildConfig

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
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        runCatching { manager.checkForUpdate() }
            .onSuccess { update = it }
            // Update discovery must never block Local First app usage, but keep an adb-visible diagnostic.
            .onFailure { error ->
                Log.w(TAG, "Update discovery failed for ${BuildConfig.UPDATE_MANIFEST_URL}", error)
            }
    }

    val info = update ?: return

    LaunchedEffect(info.versionCode, retryToken) {
        phase = UpdatePhase.DOWNLOADING
        errorMessage = null
        downloadedUri = null
        runCatching { manager.downloadAndVerify(info) }
            .onSuccess { uri ->
                downloadedUri = uri
                phase = UpdatePhase.READY
            }
            .onFailure { error ->
                Log.w(TAG, "Background update download failed for ${info.versionName}", error)
                errorMessage = error.message ?: "更新包下载失败，请稍后重试"
                phase = UpdatePhase.FAILED
            }
    }

    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(focusable = false, clippingEnabled = true)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 96.dp)
                .widthIn(min = 320.dp, max = 520.dp)
        ) {
            BackgroundUpdateCard(
                versionName = info.versionName,
                phase = phase,
                errorMessage = errorMessage,
                mandatory = info.mandatory,
                onInstall = {
                    val uri = downloadedUri ?: return@BackgroundUpdateCard
                    if (!manager.canRequestPackageInstalls()) {
                        phase = UpdatePhase.PERMISSION_REQUIRED
                        manager.openInstallPermissionSettings(activity)
                    } else {
                        manager.launchInstaller(activity, uri)
                    }
                },
                onRetry = { retryToken += 1 },
                onLater = {
                    if (!info.mandatory && phase != UpdatePhase.DOWNLOADING) {
                        update = null
                    }
                }
            )
        }
    }
}

@Composable
private fun BackgroundUpdateCard(
    versionName: String,
    phase: UpdatePhase,
    errorMessage: String?,
    mandatory: Boolean,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val (icon, title, subtitle, accent) = when (phase) {
        UpdatePhase.IDLE,
        UpdatePhase.DOWNLOADING -> UpdateCardCopy(
            icon = Icons.Rounded.Download,
            title = "正在后台更新 $versionName",
            subtitle = "可以继续使用应用，下载并校验完成后再安装",
            accent = primary
        )
        UpdatePhase.READY -> UpdateCardCopy(
            icon = Icons.Rounded.CheckCircle,
            title = "$versionName 已准备好",
            subtitle = "更新包已下载并完成校验",
            accent = primary
        )
        UpdatePhase.PERMISSION_REQUIRED -> UpdateCardCopy(
            icon = Icons.Rounded.CheckCircle,
            title = "允许安装后即可更新",
            subtitle = "返回应用后点击“继续安装”",
            accent = primary
        )
        UpdatePhase.FAILED -> UpdateCardCopy(
            icon = Icons.Rounded.ErrorOutline,
            title = "后台更新失败",
            subtitle = errorMessage ?: "网络恢复后可以重试",
            accent = MaterialTheme.colorScheme.error
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.12f),
                    contentColor = accent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.size(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (phase) {
                    UpdatePhase.READY -> {
                        FilledTonalButton(onClick = onInstall) { Text("安装") }
                    }
                    UpdatePhase.PERMISSION_REQUIRED -> {
                        FilledTonalButton(onClick = onInstall) { Text("继续") }
                    }
                    UpdatePhase.FAILED -> {
                        TextButton(onClick = onRetry) { Text("重试") }
                    }
                    else -> Unit
                }

                if (!mandatory && phase != UpdatePhase.DOWNLOADING && phase != UpdatePhase.IDLE) {
                    TextButton(onClick = onLater) { Text("稍后") }
                }
            }

            if (phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.IDLE) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

private data class UpdateCardCopy(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val accent: Color
)

private enum class UpdatePhase {
    IDLE,
    DOWNLOADING,
    READY,
    PERMISSION_REQUIRED,
    FAILED
}

private const val TAG = "AppUpdatePrompt"
