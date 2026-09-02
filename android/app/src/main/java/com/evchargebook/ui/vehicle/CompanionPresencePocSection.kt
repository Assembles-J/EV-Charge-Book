package com.evchargebook.ui.vehicle

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.evchargebook.ui.theme.spacing
import com.evchargebook.vehicle.presence.CompanionDevicePresenceController
import com.evchargebook.vehicle.presence.CompanionPresenceSupport

@Composable
fun CompanionPresencePocSection(
    deviceAddress: String?,
    deviceName: String?,
) {
    val context = LocalContext.current
    val controller = remember(context) {
        CompanionDevicePresenceController(context.applicationContext)
    }
    var status by remember(deviceAddress) {
        mutableStateOf(controller.status(deviceAddress))
    }
    var resultText by remember(deviceAddress) { mutableStateOf<String?>(null) }

    val approvalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val address = deviceAddress
        if (!address.isNullOrBlank()) {
            if (result.resultCode == Activity.RESULT_OK) {
                val observationResult = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    controller.ensureObservation(address)
                } else {
                    Result.success(Unit)
                }
                status = controller.status(address)
                resultText = observationResult.fold(
                    onSuccess = { "系统关联完成；等待真实连接/断开事件进行验收。" },
                    onFailure = { it.message ?: "系统关联完成，但连接观察启用失败。" },
                )
            } else {
                status = controller.status(address)
                resultText = "系统关联已取消，原 ACL 蓝牙路径继续生效。"
            }
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
            ) {
                Text(
                    "系统级连接观察 · 实验",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Companion Device 只增强 Android 对已绑定经典蓝牙连接事件的系统投递；不读取 SOC、续航、里程，也不代表已识别正在驾驶。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when (status.support) {
                        CompanionPresenceSupport.ANDROID_TOO_OLD -> "需要 Android 12 或更高版本；当前继续使用 ACL 检测。"
                        CompanionPresenceSupport.FEATURE_MISSING -> "此设备没有 Companion Device Setup 能力；当前继续使用 ACL 检测。"
                        CompanionPresenceSupport.SUPPORTED -> when {
                            deviceAddress.isNullOrBlank() -> "先在上方选择一个已配对的车辆蓝牙。"
                            status.associated -> "${deviceName ?: "当前车辆蓝牙"} 已完成系统关联。"
                            else -> "可为 ${deviceName ?: "当前车辆蓝牙"} 建立系统关联，用于 #315 真机可靠性测试。"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (status.support == CompanionPresenceSupport.SUPPORTED) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (status.associated && !deviceAddress.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                controller.removeAssociation(deviceAddress)
                                    .onSuccess {
                                        status = controller.status(deviceAddress)
                                        resultText = "系统关联已撤销；ACL 蓝牙路径仍保留。"
                                    }
                                    .onFailure {
                                        resultText = it.message ?: "撤销系统关联失败。"
                                    }
                            },
                        ) {
                            Text("撤销系统关联")
                        }
                    } else {
                        Button(
                            enabled = !deviceAddress.isNullOrBlank(),
                            onClick = {
                                val address = deviceAddress ?: return@Button
                                controller.requestAssociation(
                                    deviceAddress = address,
                                    onPendingUserApproval = { intentSender ->
                                        approvalLauncher.launch(
                                            IntentSenderRequest.Builder(intentSender).build()
                                        )
                                    },
                                    onAssociated = {
                                        status = controller.status(address)
                                        resultText = "系统关联与连接观察已启用。"
                                    },
                                    onFailure = { message ->
                                        status = controller.status(address)
                                        resultText = message
                                    },
                                )
                            },
                        ) {
                            Text("启用系统观察")
                        }
                    }
                    Spacer(Modifier.width(MaterialTheme.spacing.sm))
                    Text(
                        "ACL 保底始终保留",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            resultText?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
