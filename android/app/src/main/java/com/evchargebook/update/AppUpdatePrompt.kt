package com.evchargebook.update

import android.app.Activity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.evchargebook.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun AppUpdatePrompt() {
    if (BuildConfig.BUILD_TYPE != "release") return

    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val manager = remember(context) { AppUpdateManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { manager.checkForUpdate() }
            .onSuccess { update = it }
            // Update discovery must never block Local First app usage.
            .onFailure { }
    }

    val info = update ?: return
    AlertDialog(
        onDismissRequest = { if (!info.mandatory && !busy) update = null },
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Text(
                when {
                    busy -> message ?: "正在准备更新…"
                    message != null -> message!!
                    else -> "当前版本 ${BuildConfig.VERSION_NAME}。更新包会先进行 SHA-256 校验，再交给 Android 系统安装器确认安装。"
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (!manager.canRequestPackageInstalls()) {
                        message = "请先允许 EV Charge Book 安装来自此来源的应用，然后返回并再次点击升级。"
                        manager.openInstallPermissionSettings(activity)
                    } else {
                        scope.launch {
                            busy = true
                            message = "正在下载并校验更新包…"
                            runCatching { manager.downloadAndVerify(info) }
                                .onSuccess { uri ->
                                    message = "校验完成，正在打开系统安装器…"
                                    manager.launchInstaller(activity, uri)
                                }
                                .onFailure { error ->
                                    message = error.message ?: "更新失败，请稍后重试"
                                }
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "处理中" else "升级") }
        },
        dismissButton = if (info.mandatory) null else {
            {
                TextButton(enabled = !busy, onClick = { update = null }) { Text("稍后") }
            }
        },
        icon = if (busy) ({ CircularProgressIndicator() }) else null
    )
}
