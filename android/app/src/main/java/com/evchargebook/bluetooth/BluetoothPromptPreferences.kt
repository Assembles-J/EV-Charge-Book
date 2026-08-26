package com.evchargebook.bluetooth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class BluetoothPromptSettings(val enabled: Boolean = false, val deviceAddress: String? = null, val deviceName: String? = null)
data class PairedBluetoothDevice(val address: String, val name: String)

private val Context.bluetoothPromptDataStore by preferencesDataStore("bluetooth_prompt")
private val enabledKey = booleanPreferencesKey("enabled")
private val addressKey = stringPreferencesKey("device_address")
private val nameKey = stringPreferencesKey("device_name")

class BluetoothPromptPreferences(private val context: Context) {
    val settings: Flow<BluetoothPromptSettings> = context.bluetoothPromptDataStore.data.map { prefs ->
        BluetoothPromptSettings(prefs[enabledKey] == true, prefs[addressKey], prefs[nameKey])
    }

    suspend fun save(enabled: Boolean, address: String?, name: String?) {
        context.bluetoothPromptDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
            if (address == null) { prefs.remove(addressKey); prefs.remove(nameKey) }
            else { prefs[addressKey] = address; prefs[nameKey] = name.orEmpty() }
        }
    }
}
