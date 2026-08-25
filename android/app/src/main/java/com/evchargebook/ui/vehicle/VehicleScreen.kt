package com.evchargebook.ui.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.evchargebook.data.entity.VehicleEntity

@Composable
fun VehicleScreen(vehicle: VehicleEntity?, onEdit:()->Unit){
 Scaffold(topBar={TopAppBar(title={Text("我的车辆")},actions={TextButton(onClick=onEdit){Text("编辑")}})}){p->
  Column(Modifier.padding(p).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   if(vehicle==null){Text("暂无车辆")}
   else{
    Text("${vehicle.brand} ${vehicle.model}",style=MaterialTheme.typography.headlineSmall)
    Text("电池容量 ${vehicle.batteryCapacityKwh} kWh")
    Text("标称续航 ${vehicle.rangeKm} km")
   }
  }
 }
}
