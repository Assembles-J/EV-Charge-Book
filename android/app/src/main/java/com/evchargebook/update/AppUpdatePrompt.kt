package com.evchargebook.update

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.evchargebook.BuildConfig
import com.evchargebook.ui.theme.EVDesignTokens

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
    var showInstallConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { manager.checkForUpdate() }
            .onSuccess { info ->
                update = info
                if (info != null) phase = UpdatePhase.DISCOVERED
            }
            .onFailure { error ->
                Log.w(TAG, "Update discovery failed for ${BuildConfig.UPDATE_MANIFEST_URL}", error)
            }
    }

    val info = update ?: return

    LaunchedEffect(downloadToken) {
        if (downloadToken <= 0) return@LaunchedEffect
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

    fun continueInstall() {
        val uri = downloadedUri ?: return
        if (!manager.canRequestPackageInstalls()) {
            phase = UpdatePhase.PERMISSION_REQUIRED
            manager.openInstallPermissionSettings(activity)
        } else {
            manager.launchInstaller(activity, uri)
        }
    }

    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = true
        )
    ) {
        // Important: use offset instead of bottom/vertical padding here. Padding enlarges the
        // Popup window's touchable bounds and can cover the bottom navigation even though the
        // visible card itself sits above it. Offset keeps the popup hit region equal to the card.
        Box(
            modifier = Modifier
                .offset(y = (-82).dp)
                .padding(horizontal = 8.dp)
                .widthIn(min = 320.dp, max = 520.dp)
        ) {
            BackgroundUpdateCard(
                versionName = info.versionName,
                phase = phase,
                errorMessage = errorMessage,
                mandatory = info.mandatory,
                onUpdate = { downloadToken += 1 },
                onInstall = { showInstallConfirmation = true },
                onContinueInstall = ::continueInstall,
                onRetry = { downloadToken += 1 },
                onLater = {
                    if (!info.mandatory && phase != UpdatePhase.DOWNLOADING) {
                        update = null
                    }
                }
            )
        }
    }

    if (showInstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showInstallConfirmation = false },
            containerColor = EVDesignTokens.Dark.surfaceElevated,
            titleContentColor = EVDesignTokens.Dark.primaryText,
            textContentColor = EVDesignTokens.Dark.secondaryText,
            title = {
                Text(
                    text = "确认安装更新 ${info.versionName}",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("更新包已下载并完成完整性校验。")
                    Text("安装时 Android 会打开系统安装界面，安装期间应用会暂时退出；完成后重新打开即可。")
                    Text(
                        text = "建议在停车且网络、电量状态良好时安装。",
                        color = EVDesignTokens.Energy.green
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInstallConfirmation = false
                        continueInstall()
                    }
                ) {
                    Text(
                        text = "确认安装",
                        color = EVDesignTokens.Energy.green,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallConfirmation = false }) {
                    Text("取消", color = EVDesignTokens.Dark.secondaryText)
                }
            }
        )
    }
}

@Composable
private fun BackgroundUpdateCard(
    versionName: String,
    phase: UpdatePhase,
    errorMessage: String?,
    mandatory: Boolean,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onContinueInstall: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit
) {
    val copy = when (phase) {
        UpdatePhase.IDLE,
        UpdatePhase.DISCOVERED -> UpdateCardCopy(
            icon = Icons.Rounded.SystemUpdateAlt,
            title = "发现新版本 $versionName",
            subtitle = "确认后将在后台下载，期间可以继续使用应用",
            accent = EVDesignTokens.Energy.green,
            action = "更新"
        )
        UpdatePhase.DOWNLOADING -> UpdateCardCopy(
            icon = Icons.Rounded.Download,
            title = "正在后台更新 $versionName",
            subtitle = "可以继续使用应用，下载并校验完成后再安装",
            accent = EVDesignTokens.Energy.green,
            action = null
        )
        UpdatePhase.READY -> UpdateCardCopy(
            icon = Icons.Rounded.CheckCircle,
            title = "$versionName 已准备好",
            subtitle = "更新已下载完成，可随时安装",
            accent = EVDesignTokens.Energy.success,
            action = "立即安装"
        )
        UpdatePhase.PERMISSION_REQUIRED -> UpdateCardCopy(
            icon = Icons.Rounded.CheckCircle,
            title = "$versionName 已准备好",
            subtitle = "允许安装此来源应用后，返回继续安装",
            accent = EVDesignTokens.Energy.warning,
            action = "继续安装"
        )
        UpdatePhase.FAILED -> UpdateCardCopy(
            icon = Icons.Rounded.ErrorOutline,
            title = "更新失败",
            subtitle = errorMessage ?: "网络恢复后可以重试",
            accent = EVDesignTokens.Energy.danger,
            action = "重试"
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EVDesignTokens.Radius.large.dp),
        color = EVDesignTokens.Dark.surfaceElevated.copy(alpha = 0.98f),
        contentColor = EVDesignTokens.Dark.primaryText,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, EVDesignTokens.Dark.outline)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = copy.accent.copy(alpha = 0.12f),
                    contentColor = copy.accent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = copy.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = EVDesignTokens.Dark.primaryText
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = copy.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = EVDesignTokens.Dark.secondaryText
                    )
                }

                copy.action?.let { label ->
                    TextButton(
                        onClick = when (phase) {
                            UpdatePhase.IDLE,
                            UpdatePhase.DISCOVERED -> onUpdate
                            UpdatePhase.READY -> onInstall
                            UpdatePhase.PERMISSION_REQUIRED -> onContinueInstall
                            UpdatePhase.FAILED -> onRetry
                            UpdatePhase.DOWNLOADING -> ({})
                        }
                    ) {
                        Text(
                            text = label,
                            color = copy.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                val canDefer = !mandatory && phase in setOf(
                    UpdatePhase.IDLE,
                    UpdatePhase.DISCOVERED,
                    UpdatePhase.READY,
                    UpdatePhase.PERMISSION_REQUIRED,
                    UpdatePhase.FAILED
                )
                if (canDefer) {
                    TextButton(onClick = onLater) {
                        Text("稍后", color = EVDesignTokens.Dark.secondaryText)
                    }
                }
            }

            if (phase == UpdatePhase.DOWNLOADING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(EVDesignTokens.Dark.outline)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = EVDesignTokens.Energy.green,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}

private data class UpdateCardCopy(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val action: String?
)

private enum class UpdatePhase {
    IDLE,
    DISCOVERED,
    DOWNLOADING,
    READY,
    PERMISSION_REQUIRED,
    FAILED
}

private const val TAG = "AppUpdatePrompt"
