package com.evchargebook.data.backup

import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.VehicleEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val vehicles: List<VehicleEntity>,
    val chargingRecords: List<ChargingRecordEntity>
)

object BackupCodec {
    const val CURRENT_SCHEMA_VERSION = 2

    fun encode(payload: BackupPayload): String = JSONObject().apply {
        put("schemaVersion", payload.schemaVersion)
        put("exportedAt", payload.exportedAt)
        put("appVersion", payload.appVersion)
        put("vehicles", JSONArray().apply {
            payload.vehicles.forEach { vehicle ->
                put(JSONObject().apply {
                    put("id", vehicle.id)
                    put("brand", vehicle.brand)
                    put("model", vehicle.model)
                    put("batteryCapacityKwh", vehicle.batteryCapacityKwh)
                    put("rangeKm", vehicle.rangeKm)
                })
            }
        })
        put("chargingRecords", JSONArray().apply {
            payload.chargingRecords.forEach { record ->
                put(JSONObject().apply {
                    put("id", record.id)
                    put("vehicleId", record.vehicleId)
                    put("chargeTimeEpochMillis", record.chargeTimeEpochMillis)
                    put("energyKwh", record.energyKwh)
                    put("cost", record.cost)
                    put("startSoc", record.startSoc)
                    put("endSoc", record.endSoc)
                    putNullable("chargerType", record.chargerType)
                    putNullable("location", record.location)
                    putNullable("remark", record.remark)
                    putNullable("odometerKm", record.odometerKm)
                })
            }
        })
    }.toString(2)

    fun decode(text: String): BackupPayload {
        val root = JSONObject(text)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "不支持的备份版本：$schemaVersion，当前支持 ${CURRENT_SCHEMA_VERSION}"
        }

        val vehiclesJson = root.getJSONArray("vehicles")
        val vehicles = buildList {
            for (index in 0 until vehiclesJson.length()) {
                val item = vehiclesJson.getJSONObject(index)
                add(
                    VehicleEntity(
                        id = item.getLong("id"),
                        brand = item.getString("brand"),
                        model = item.getString("model"),
                        batteryCapacityKwh = item.getDouble("batteryCapacityKwh"),
                        rangeKm = item.getInt("rangeKm")
                    )
                )
            }
        }

        val recordsJson = root.getJSONArray("chargingRecords")
        val records = buildList {
            for (index in 0 until recordsJson.length()) {
                val item = recordsJson.getJSONObject(index)
                add(
                    ChargingRecordEntity(
                        id = item.getLong("id"),
                        vehicleId = item.getLong("vehicleId"),
                        chargeTimeEpochMillis = item.getLong("chargeTimeEpochMillis"),
                        energyKwh = item.getDouble("energyKwh"),
                        cost = item.getDouble("cost"),
                        startSoc = item.getInt("startSoc"),
                        endSoc = item.getInt("endSoc"),
                        chargerType = item.optNullableString("chargerType"),
                        location = item.optNullableString("location"),
                        remark = item.optNullableString("remark"),
                        odometerKm = item.optNullableDouble("odometerKm")
                    )
                )
            }
        }

        val vehicleIds = vehicles.map { it.id }.toSet()
        require(vehicles.isNotEmpty()) { "备份中没有车辆数据" }
        require(records.all { it.vehicleId in vehicleIds }) { "备份中存在无法关联车辆的充电记录" }

        return BackupPayload(
            schemaVersion = schemaVersion,
            exportedAt = root.getLong("exportedAt"),
            appVersion = root.optString("appVersion", "unknown"),
            vehicles = vehicles,
            chargingRecords = records
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (!has(name) || isNull(name)) null else getDouble(name)
}
