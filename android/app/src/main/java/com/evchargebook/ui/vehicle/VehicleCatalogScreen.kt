package com.evchargebook.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.VehicleCatalogEntity
import com.evchargebook.ui.theme.spacing

private val CatalogHeroBrush = Brush.linearGradient(
    listOf(Color(0xFF06100B), Color(0xFF0B2117), Color(0xFF07120D))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleCatalogScreen(
    items: List<VehicleCatalogEntity>,
    onSelect: (VehicleCatalogEntity) -> Unit,
    onCustom: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) items else items.filter {
            "${it.brand} ${it.series} ${it.modelName} ${it.trimName.orEmpty()}".contains(keyword, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择车型", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("VEHICLE CATALOG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                OutlinedButton(
                    onClick = onCustom,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm)
                ) { Text("没有找到？自定义添加车辆") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = Color.Transparent) {
                    Column(
                        Modifier.background(CatalogHeroBrush).padding(MaterialTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Spacer(Modifier.width(MaterialTheme.spacing.xs))
                            Text("VEHICLE / FIND", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("找到你的车型", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("优先选择车型库，电池容量和标称续航会自动带入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            placeholder = { Text("搜索品牌、车系或配置") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            if (query.isBlank()) "车型库 ${items.size} 条" else "找到 ${filtered.size} 条结果",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.lg)) {
                        Text("没有匹配车型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("修改关键词，或者使用底部的自定义添加。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(filtered, key = { it.catalogId }) { item ->
                CatalogVehicleRow(item, onSelect)
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CatalogVehicleRow(item: VehicleCatalogEntity, onSelect: (VehicleCatalogEntity) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(item) },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Row(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.brand.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${item.brand} ${item.modelName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.trimName ?: item.series, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.powertrainType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "${item.batteryCapacityKwh?.let { "$it kWh" } ?: "电池 --"}  ·  ${item.rangeKm?.let { "$it km" } ?: "续航 --"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
