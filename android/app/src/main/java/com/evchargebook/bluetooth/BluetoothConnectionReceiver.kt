package com.evchargebook.bluetooth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED || !hasBluetoothPermission(context)) return
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = BluetoothPromptPreferences(context).settings.first()
                if (settings.enabled && settings.deviceAddress.equals(device.address, ignoreCase = true)) showNotification(context, settings.deviceName ?: device.name ?: "车辆")
            } finally { pending.finish() }
        }
    }

    private fun showNotification(context: Context, name: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "vehicle_connection"
        manager.createNotificationChannel(NotificationChannel(channelId, "车辆连接提示", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(2101, NotificationCompat.Builder(context, channelId).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("已连接 $name").setContentText("检测到指定车载蓝牙。行程记录需要你主动确认开始。").setAutoCancel(true).build())
        }
    }

    private fun hasBluetoothPermission(context: Context) = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
