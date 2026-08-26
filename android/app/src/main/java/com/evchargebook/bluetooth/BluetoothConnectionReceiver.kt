package com.evchargebook.bluetooth

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.evchargebook.MainActivity
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
                if (settings.enabled && settings.deviceAddress.equals(device.address, ignoreCase = true)) {
                    showNotification(context, settings.deviceName ?: device.name ?: "车辆")
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, name: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "vehicle_connection"
        manager.createNotificationChannel(NotificationChannel(channelId, "车辆连接提示", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val openTripIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TRIP_CONFIRMATION, true)
        val pendingIntent = PendingIntent.getActivity(
            context,
            2101,
            openTripIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        manager.notify(
            2101,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("已连接 $name")
                .setContentText("点击打开行程页，确认后开始记录。")
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_media_play, "打开并确认", pendingIntent)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun hasBluetoothPermission(context: Context) =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
