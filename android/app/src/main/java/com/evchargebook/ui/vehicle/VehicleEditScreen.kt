package com.evchargebook.ui.vehicle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditScreen(
    initialBrand: String,
    initialModel: String,
    initialBatteryCapacity: String,
    initialRange: String,
    title: String = "编辑车辆",
    onSave: (String, String, Double, Int) -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var brand by remember { mutableStateOf(initialBrand) }
    var model by remember { mutableStateOf(initialModel) }
    var battery by remember { mutableStateOf(initialBatteryCapacity) }
    var range by remember { mutableStateOf(initialRange) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val isDirty = brand != initialBrand || model != initialModel || battery != initialBatteryCapacity || range != initialRange

    fun requestBack() {
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    BackHandler(enabled = isDirty) { showDiscardConfirm = true }

    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = ::requestBack) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            VehiclePreviewCockpit(brand, model, battery, range, title == "添加车辆")

            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text("车辆信息", style = MaterialTheme.typography.titleMedium)
                Text("用于区分账本数据，并参与电池容量与续航相关的展示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("品牌") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("车型") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Text("电池与续航", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = battery,
                        onValueChange = { battery = it },
                        label = { Text("电池容量") },
                        suffix = { Text("kWh") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = range,
                        onValueChange = { range = it.filter(Char::isDigit) },
                        label = { Text("标称续航") },
                        suffix = { Text("km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                focusManager.clearFocus()
                val capacity = battery.toDoubleOrNull(); val rangeKm = range.toIntOrNull()
                error = when {
                    brand.isBlank() -> "请输入车辆品牌"
                    model.isBlank() -> "请输入车型"
                    capacity == null || capacity <= 0 -> "电池容量需要大于 0"
                    rangeKm == null || rangeKm <= 0 -> "标称续航需要大于 0"
                    else -> null
                }
                if (error == null) onSave(brand.trim(), model.trim(), capacity!!, rangeKm!!)
            }, modifier = Modifier.fillMaxWidth()) { Text(if (title == "添加车辆") "添加车辆" else "保存车辆") }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃未保存修改？") },
            text = { Text(if (title == "添加车辆") "当前车辆信息还没有保存，返回后这些内容会丢失。" else "车辆信息已经被修改，返回后未保存的修改会丢失。") },
            confirmButton = { TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") } }
        )
    }
}

@Composable
private fun VehiclePreviewCockpit(brand: String, model: String, battery: String, range: String, adding: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(7.dp), color = MaterialTheme.colorScheme.inversePrimary, shape = MaterialTheme.shapes.extraSmall) {}
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(if (adding) "VEHICLE / NEW" else "VEHICLE / EDIT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .58f))
            }
            Text(
                listOf(brand.trim(), model.trim()).filter { it.isNotEmpty() }.joinToString(" ").ifBlank { "车辆预览" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)) {
                PreviewMetric("电池", battery.toDoubleOrNull()?.let { "${formatOne(it)} kWh" } ?: "--", Modifier.weight(1f))
                PreviewMetric("续航", range.toIntOrNull()?.let { "$it km" } ?: "--", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = .48f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

private fun formatOne(value: Double) = String.format(java.util.Locale.US, "%.1f", value)
