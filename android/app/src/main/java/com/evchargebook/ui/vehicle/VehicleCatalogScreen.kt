package com.evchargebook.ui.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleCatalogScreen(items: List<VehicleCatalogEntity>, onSelect: (VehicleCatalogEntity) -> Unit, onCustom: () -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) { items.filter { "${it.brand} ${it.series} ${it.modelName} ${it.trimName.orEmpty()}".contains(query.trim(), ignoreCase = true) } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("选择车型") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }) },
        bottomBar = { Surface(tonalElevation = 1.dp) { TextButton(onClick = onCustom, modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.xs)) { Text("没有找到？自定义添加车辆") } } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm)) {
            item {
                OutlinedTextField(value = query, onValueChange = { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("搜索品牌、车系或配置") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
                Text(if (query.isBlank()) "车型库 ${items.size} 条" else "找到 ${filtered.size} 条结果", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(MaterialTheme.spacing.sm))
            }
            if (filtered.isEmpty()) item { Text("没有匹配车型。你可以修改关键词，或使用下方自定义添加。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = MaterialTheme.spacing.lg)) }
            items(filtered, key = { it.catalogId }) { item ->
                ListItem(
                    headlineContent = { Text("${item.brand} ${item.modelName}") },
                    supportingContent = { Text("${item.trimName ?: item.series} · ${item.powertrainType}\n${item.batteryCapacityKwh ?: "-"} kWh · ${item.rangeKm ?: "-"} km") },
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }
                )
                HorizontalDivider()
            }
        }
    }
}
