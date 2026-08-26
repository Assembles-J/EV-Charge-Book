package com.evchargebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.repository.ChargingRepository
import com.evchargebook.ui.dashboard.DashboardScreen
import com.evchargebook.ui.records.*
import com.evchargebook.ui.stats.StatsScreen
import com.evchargebook.ui.vehicle.VehicleScreen
import com.evchargebook.ui.vehicle.VehicleEditScreen
import com.evchargebook.viewmodel.MainViewModel
import com.evchargebook.ui.records.AddRecordScreen
import com.evchargebook.ui.theme.EvChargeTheme

class MainActivity: ComponentActivity(){
 private val vm:MainViewModel by viewModels {
  val db=AppDatabase.getInstance(applicationContext)
  MainViewModel.Factory(ChargingRepository(db.vehicleDao(),db.chargingRecordDao()))
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{EvChargeTheme { MainApp(vm) }}}
}

@Composable
fun MainApp(viewModel:MainViewModel){
 val state by viewModel.uiState.collectAsState()
 val snackbarHostState = remember { SnackbarHostState() }
 var tab by remember{mutableIntStateOf(0)}
 var editVehicle by remember{mutableStateOf(false)}
 var addRecord by remember{mutableStateOf(false)}
 var editingRecord by remember{mutableStateOf<com.evchargebook.data.entity.ChargingRecordEntity?>(null)}
 val titles=listOf("总览","记录","统计","车辆")
 LaunchedEffect(state.successMessage) { state.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() } }
  Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, bottomBar={ if (!editVehicle && !addRecord && editingRecord == null) NavigationBar{titles.forEachIndexed{i,t->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(listOf(Icons.Default.Home,Icons.Default.History,Icons.Default.BarChart,Icons.Default.DirectionsCar)[i],t)},label={Text(t)})}}}){p->
   Surface(Modifier.padding(p)){
    if(addRecord) {
     val vehicleId = state.vehicle?.id ?: 0L
     AddRecordScreen(
      vehicleId = vehicleId,
      records = state.chargingRecords,
      onBack = { addRecord=false },
      onSave = { location,start,end,energy,cost,type,remark,time,odometer ->
       viewModel.addChargingRecord(start,end,energy,cost,location,type,remark,time,odometer)
       addRecord=false
      }
     )
    } else if(editingRecord != null) {
     RecordEditScreen(
      record = editingRecord!!,
      records = state.chargingRecords,
      onSave = { viewModel.updateChargingRecord(it); editingRecord = null },
      onBack = { editingRecord = null }
     )
    } else if(editVehicle){
     val v=state.vehicle
     if(v!=null) VehicleEditScreen(v.brand,v.model,v.batteryCapacityKwh.toString(),v.rangeKm.toString(),{b,m,c,r->viewModel.saveVehicle(b,m,c,r);editVehicle=false},{editVehicle=false})
    }else when(tab){
     0->DashboardScreen(state,{addRecord=true})
     1->RecordsScreen(state.chargingRecords,viewModel::deleteChargingRecord,{addRecord=true},{editingRecord=it})
     2->StatsScreen(state)
     3->VehicleScreen(state.vehicle,{editVehicle=true})
    }
   }
  }
}
