package com.evchargebook.bluetooth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class VehicleBluetoothBinding(
    val vehicleId: Long,
    val enabled: Boolean,
    val deviceAddress: String,
    val deviceName: String?,
)

private val Context.vehicleBluetoothBindingDataStore by preferencesDataStore("vehicle_bluetooth_bindings")
private val bindingsJsonKey = stringPreferencesKey("bindings_json_v1")

class VehicleBluetoothBindingPreferences(private val context: Context) {
    val bindings: Flow<List<VehicleBluetoothBinding>> =
        context.vehicleBluetoothBindingDataStore.data.map { prefs ->
            decode(prefs[bindingsJsonKey])
        }

    suspend fun save(binding: VehicleBluetoothBinding) {
        context.vehicleBluetoothBindingDataStore.edit { prefs ->
            val normalizedAddress = normalizeAddress(binding.deviceAddress)
            val current = decode(prefs[bindingsJsonKey]).toMutableList()

            // Selecting a device while editing the current vehicle is an explicit rebind action.
            // Keep one owner per physical Bluetooth address so connection events stay unambiguous.
            current.removeAll {
                it.vehicleId == binding.vehicleId ||
                    it.deviceAddress.equals(normalizedAddress, ignoreCase = true)
            }
            current += binding.copy(deviceAddress = normalizedAddress)
            prefs[bindingsJsonKey] = encode(current)
        }
    }

    suspend fun remove(vehicleId: Long) {
        context.vehicleBluetoothBindingDataStore.edit { prefs ->
            val current = decode(prefs[bindingsJsonKey]).filterNot { it.vehicleId == vehicleId }
            prefs[bindingsJsonKey] = encode(current)
        }
    }

    suspend fun migrateLegacyIfUnambiguous(
        legacy: BluetoothPromptSettings,
        activeVehicleIds: List<Long>,
    ) {
        if (activeVehicleIds.size != 1 || legacy.deviceAddress.isNullOrBlank()) return
        context.vehicleBluetoothBindingDataStore.edit { prefs ->
            if (decode(prefs[bindingsJsonKey]).isNotEmpty()) return@edit
            prefs[bindingsJsonKey] = encode(
                listOf(
                    VehicleBluetoothBinding(
                        vehicleId = activeVehicleIds.single(),
                        enabled = legacy.enabled,
                        deviceAddress = normalizeAddress(legacy.deviceAddress),
                        deviceName = legacy.deviceName,
                    )
                )
            )
        }
    }

    companion object {
        fun normalizeAddress(address: String): String = address.trim().uppercase()

        private fun decode(raw: String?): List<VehicleBluetoothBinding> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        val address = item.optString("deviceAddress").takeIf { it.isNotBlank() } ?: continue
                        val deviceName = if (item.isNull("deviceName")) {
                            null
                        } else {
                            item.optString("deviceName").takeIf { it.isNotBlank() }
                        }
                        add(
                            VehicleBluetoothBinding(
                                vehicleId = item.getLong("vehicleId"),
                                enabled = item.optBoolean("enabled", false),
                                deviceAddress = normalizeAddress(address),
                                deviceName = deviceName,
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        private fun encode(bindings: List<VehicleBluetoothBinding>): String {
            val array = JSONArray()
            bindings.sortedBy { it.vehicleId }.forEach { binding ->
                array.put(
                    JSONObject()
                        .put("vehicleId", binding.vehicleId)
                        .put("enabled", binding.enabled)
                        .put("deviceAddress", normalizeAddress(binding.deviceAddress))
                        .put("deviceName", binding.deviceName ?: JSONObject.NULL)
                )
            }
            return array.toString()
        }
    }
}
