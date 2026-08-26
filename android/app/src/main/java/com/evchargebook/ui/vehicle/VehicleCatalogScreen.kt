package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleCatalogScreen(items: List<VehicleCatalogEntity>, onSelect: (VehicleCatalogEntity) -> Unit, onCustom: () -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) { items.filter { "${it.brand} ${it.series} ${it.modelName} ${it.trimName.orEmpty()}".contains(query.trim(), ignoreCase = true) } }
    Scaffold(topBar = { TopAppBar(title = { Text("选择车型") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }) }, bottomBar = { Surface { OutlinedButton(onClick = onCustom, modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md)) { Text("没有找到？自定义添加车辆") } } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索品牌、车系或配置") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            if (filtered.isEmpty()) item { Text("没有匹配车型，可使用下方自定义添加。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(filtered, key = { it.catalogId }) { item -> ListItem(headlineContent = { Text("${item.brand} ${item.modelName}") }, supportingContent = { Text("${item.trimName ?: item.series} · ${item.powertrainType} · ${item.batteryCapacityKwh ?: "-"} kWh / ${item.rangeKm ?: "-"} km") }, modifier = Modifier.fillMaxWidth(), trailingContent = { TextButton(onClick = { onSelect(item) }) { Text("选择") } }) }
        }
    }
}
