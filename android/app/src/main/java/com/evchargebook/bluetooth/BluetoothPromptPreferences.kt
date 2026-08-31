package com.evchargebook.bluetooth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.evchargebook.data.database.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class BluetoothPromptSettings(
    val enabled: Boolean = false,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
)

data class PairedBluetoothDevice(val address: String, val name: String)

private val Context.bluetoothPromptDataStore by preferencesDataStore("bluetooth_prompt")
private val enabledKey = booleanPreferencesKey("enabled")
private val addressKey = stringPreferencesKey("device_address")
private val nameKey = stringPreferencesKey("device_name")

/**
 * Compatibility facade for the existing UI/repository API.
 *
 * New settings are stored per vehicle. The old global DataStore remains only as a safe migration
 * source for installations that previously configured one vehicle Bluetooth target.
 */
class BluetoothPromptPreferences(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val bindingPreferences = VehicleBluetoothBindingPreferences(context)
    private val legacySettings: Flow<BluetoothPromptSettings> =
        context.bluetoothPromptDataStore.data.map { prefs ->
            BluetoothPromptSettings(
                enabled = prefs[enabledKey] == true,
                deviceAddress = prefs[addressKey],
                deviceName = prefs[nameKey],
            )
        }

    val settings: Flow<BluetoothPromptSettings> = combine(
        database.vehicleDao().observeActive(),
        bindingPreferences.bindings,
        legacySettings,
    ) { vehicles, bindings, legacy ->
        val selectedVehicle = vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
        val binding = selectedVehicle?.let { vehicle ->
            bindings.firstOrNull { it.vehicleId == vehicle.id }
        }

        when {
            binding != null -> BluetoothPromptSettings(
                enabled = binding.enabled,
                deviceAddress = binding.deviceAddress,
                deviceName = binding.deviceName,
            )

            // Only inherit an old global binding when vehicle ownership is unambiguous.
            vehicles.size == 1 && !legacy.deviceAddress.isNullOrBlank() -> legacy
            else -> BluetoothPromptSettings()
        }
    }

    suspend fun save(enabled: Boolean, address: String?, name: String?) {
        val vehicles = database.vehicleDao().observeActive().first()
        val selectedVehicle = vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()

        if (selectedVehicle != null) {
            if (address.isNullOrBlank()) {
                bindingPreferences.remove(selectedVehicle.id)
            } else {
                bindingPreferences.save(
                    VehicleBluetoothBinding(
                        vehicleId = selectedVehicle.id,
                        enabled = enabled,
                        deviceAddress = address,
                        deviceName = name,
                    )
                )
            }
        }

        // Keep the legacy value synchronized for downgrade compatibility. It is never used to
        // guess ownership when multiple active vehicles exist.
        context.bluetoothPromptDataStore.edit { prefs ->
            prefs[enabledKey] = enabled
            if (address.isNullOrBlank()) {
                prefs.remove(addressKey)
                prefs.remove(nameKey)
            } else {
                prefs[addressKey] = VehicleBluetoothBindingPreferences.normalizeAddress(address)
                prefs[nameKey] = name.orEmpty()
            }
        }
    }
}
