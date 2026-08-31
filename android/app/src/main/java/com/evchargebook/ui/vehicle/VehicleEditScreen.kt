package com.evchargebook.ui.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.spacing
import java.util.Locale

private val VehicleEditHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0B2117), Color(0xFF07120D))
)

private const val MAX_VEHICLE_NICKNAME_LENGTH = 24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(
    initialNickname: String,
    brand: String,
    model: String,
    batteryCapacityKwh: Double?,
    rangeKm: Int?,
    title: String = "编辑车辆名称",
    onSave: (String?) -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val adding = title == "添加车辆"
    val normalizedInitial = initialNickname.trim()
    val normalizedNickname = nickname.trim()
    val isDirty = normalizedNickname != normalizedInitial
    val fallbackName = model.ifBlank { brand }

    fun requestBack() {
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    BackHandler(enabled = isDirty) { showDiscardConfirm = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (adding) "CATALOG VEHICLE" else "PERSONAL VEHICLE NAME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = ::requestBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    Modifier.background(VehicleEditHeroBrush).padding(MaterialTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Spacer(Modifier.width(MaterialTheme.spacing.xs))
                        Text(
                            if (adding) "VEHICLE / ADD" else "VEHICLE / RENAME",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        normalizedNickname.ifBlank { fallbackName.ifBlank { "车辆" } },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        listOf(brand.trim(), model.trim()).filter { it.isNotEmpty() }.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text("车辆名称", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "这是你的个人名称，可以修改；留空时显示后台车型名称。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(MAX_VEHICLE_NICKNAME_LENGTH) },
                    label = { Text("车辆名称（可选）") },
                    placeholder = { Text(fallbackName.ifBlank { "例如：通勤车" }) },
                    supportingText = {
                        Text("${nickname.length}/$MAX_VEHICLE_NICKNAME_LENGTH · 只影响显示名称，不修改车型资料")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text("标准车型资料", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "以下信息由车型库统一维护，在 App 中仅展示，不允许修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ReadOnlyFact("品牌", brand.ifBlank { "--" })
                ReadOnlyFact("车型", model.ifBlank { "--" })
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    ReadOnlyFact(
                        "电池容量",
                        batteryCapacityKwh?.let { "${String.format(Locale.US, "%.1f", it)} kWh" } ?: "--",
                        Modifier.weight(1f)
                    )
                    ReadOnlyFact(
                        "标称续航",
                        rangeKm?.let { "$it km" } ?: "--",
                        Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onSave(normalizedNickname.takeIf { it.isNotEmpty() })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (adding) "确认添加" else "保存名称")
            }

            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃未保存修改？") },
            text = { Text("车辆名称已经修改，返回后未保存的内容会丢失。") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") } }
        )
    }
}

@Composable
private fun ReadOnlyFact(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
