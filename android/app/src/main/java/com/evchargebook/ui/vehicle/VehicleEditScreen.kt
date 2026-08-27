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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evchargebook.ui.theme.spacing

private val VehicleEditHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0B2117), Color(0xFF07120D))
)

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
    val adding = title == "添加车辆"
    val isDirty = brand != initialBrand || model != initialModel || battery != initialBatteryCapacity || range != initialRange

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
                        Text(if (adding) "VEHICLE SETUP" else "VEHICLE PROFILE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = ::requestBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.xs))
            VehiclePreviewCockpit(brand, model, battery, range, adding)

            FormSection("车辆身份", "VEHICLE IDENTITY", "用于区分账本数据，并作为首页和车辆页的主标题。") {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("品牌") },
                    placeholder = { Text("例如：零跑") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("车型") },
                    placeholder = { Text("例如：C16 2026款") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                )
            }

            FormSection("电池与续航", "EV SPECS", "用于首页车辆指标与后续能耗统计。") {
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

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val capacity = battery.toDoubleOrNull()
                    val rangeKm = range.toIntOrNull()
                    error = when {
                        brand.isBlank() -> "请输入车辆品牌"
                        model.isBlank() -> "请输入车型"
                        capacity == null || capacity <= 0 -> "电池容量需要大于 0"
                        rangeKm == null || rangeKm <= 0 -> "标称续航需要大于 0"
                        else -> null
                    }
                    if (error == null) onSave(brand.trim(), model.trim(), capacity!!, rangeKm!!)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (adding) "添加车辆" else "保存车辆") }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃未保存修改？") },
            text = { Text(if (adding) "当前车辆信息还没有保存，返回后这些内容会丢失。" else "车辆信息已经被修改，返回后未保存的修改会丢失。") },
            confirmButton = { TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("继续编辑") } }
        )
    }
}

@Composable
private fun VehiclePreviewCockpit(brand: String, model: String, battery: String, range: String, adding: Boolean) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.Transparent, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.background(VehicleEditHeroBrush).padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(MaterialTheme.spacing.xs))
                Text(if (adding) "VEHICLE / NEW" else "VEHICLE / EDIT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOf(brand.trim(), model.trim()).filter { it.isNotEmpty() }.joinToString(" ").ifBlank { "车辆预览" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FormSection(title: String, eyebrow: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

private fun formatOne(value: Double) = String.format(java.util.Locale.US, "%.1f", value)
