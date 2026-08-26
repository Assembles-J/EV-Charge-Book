package com.evchargebook.data.backup

import com.evchargebook.data.entity.ChargingRecordEntity
import com.evchargebook.data.entity.TripPointEntity
import com.evchargebook.data.entity.TripSessionEntity
import com.evchargebook.data.entity.TripStatus
import com.evchargebook.data.entity.VehicleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val vehicles: List<VehicleEntity>,
    val chargingRecords: List<ChargingRecordEntity>,
    val tripSessions: List<TripSessionEntity> = emptyList(),
    val tripPoints: List<TripPointEntity> = emptyList()
)

object BackupCodec {
    const val CURRENT_SCHEMA_VERSION = 6

    fun encode(payload: BackupPayload): String = JSONObject().apply {
        put("schemaVersion", payload.schemaVersion)
        put("exportedAt", payload.exportedAt)
        put("appVersion", payload.appVersion)
        put("vehicles", JSONArray().apply {
            payload.vehicles.forEach { vehicle ->
                put(JSONObject().apply {
                    put("id", vehicle.id)
                    putNullable("catalogVehicleId", vehicle.catalogVehicleId)
                    put("brand", vehicle.brand)
                    put("model", vehicle.model)
                    put("batteryCapacityKwh", vehicle.batteryCapacityKwh)
                    put("rangeKm", vehicle.rangeKm)
                    put("isDefault", vehicle.isDefault)
                    put("isArchived", vehicle.isArchived)
                    put("createdAtEpochMillis", vehicle.createdAtEpochMillis)
                    put("syncId", vehicle.syncId)
                    put("updatedAtEpochMillis", vehicle.updatedAtEpochMillis)
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
                    putNullable("latitude", record.latitude)
                    putNullable("longitude", record.longitude)
                    putNullable("locationAccuracyMeters", record.locationAccuracyMeters)
                    put("syncId", record.syncId)
                    put("updatedAtEpochMillis", record.updatedAtEpochMillis)
                    put("isDeleted", record.isDeleted)
                })
            }
        })
        put("tripSessions", JSONArray().apply {
            payload.tripSessions.forEach { trip ->
                put(JSONObject().apply {
                    put("id", trip.id)
                    put("vehicleId", trip.vehicleId)
                    put("startedAtEpochMillis", trip.startedAtEpochMillis)
                    putNullable("endedAtEpochMillis", trip.endedAtEpochMillis)
                    put("distanceMeters", trip.distanceMeters)
                    put("elapsedSeconds", trip.elapsedSeconds)
                    putNullable("movingSeconds", trip.movingSeconds)
                    putNullable("stoppedSeconds", trip.stoppedSeconds)
                    putNullable("averageSpeedMps", trip.averageSpeedMps)
                    putNullable("maxSpeedMps", trip.maxSpeedMps)
                    putNullable("startLatitude", trip.startLatitude)
                    putNullable("startLongitude", trip.startLongitude)
                    putNullable("endLatitude", trip.endLatitude)
                    putNullable("endLongitude", trip.endLongitude)
                    putNullable("startAltitudeMeters", trip.startAltitudeMeters)
                    putNullable("endAltitudeMeters", trip.endAltitudeMeters)
                    putNullable("minAltitudeMeters", trip.minAltitudeMeters)
                    putNullable("maxAltitudeMeters", trip.maxAltitudeMeters)
                    put("status", trip.status)
                })
            }
        })
        put("tripPoints", JSONArray().apply {
            payload.tripPoints.forEach { point ->
                put(JSONObject().apply {
                    put("id", point.id)
                    put("tripId", point.tripId)
                    put("capturedAtEpochMillis", point.capturedAtEpochMillis)
                    put("latitude", point.latitude)
                    put("longitude", point.longitude)
                    putNullable("altitudeMeters", point.altitudeMeters)
                    putNullable("speedMps", point.speedMps)
                    putNullable("bearingDegrees", point.bearingDegrees)
                    putNullable("horizontalAccuracyMeters", point.horizontalAccuracyMeters)
                    putNullable("verticalAccuracyMeters", point.verticalAccuracyMeters)
                    putNullable("speedAccuracyMps", point.speedAccuracyMps)
                    putNullable("provider", point.provider)
                })
            }
        })
    }.toString(2)

    fun decode(text: String): BackupPayload {
        val root = JSONObject(text)
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion in 2..CURRENT_SCHEMA_VERSION) {
            "不支持的备份版本：$schemaVersion，当前支持 2 至 ${CURRENT_SCHEMA_VERSION}"
        }

        val vehiclesJson = root.getJSONArray("vehicles")
        val vehicles = buildList {
            for (index in 0 until vehiclesJson.length()) {
                val item = vehiclesJson.getJSONObject(index)
                val id = item.getLong("id")
                val createdAt = item.optLong("createdAtEpochMillis", id)
                add(
                    VehicleEntity(
                        id = id,
                        catalogVehicleId = item.optNullableString("catalogVehicleId"),
                        brand = item.getString("brand"),
                        model = item.getString("model"),
                        batteryCapacityKwh = item.getDouble("batteryCapacityKwh"),
                        rangeKm = item.getInt("rangeKm"),
                        isDefault = item.optBoolean("isDefault", false),
                        isArchived = item.optBoolean("isArchived", false),
                        createdAtEpochMillis = createdAt,
                        syncId = item.optString("syncId").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                        updatedAtEpochMillis = item.optLong("updatedAtEpochMillis", createdAt)
                    )
                )
            }
        }

        val recordsJson = root.getJSONArray("chargingRecords")
        val records = buildList {
            for (index in 0 until recordsJson.length()) {
                val item = recordsJson.getJSONObject(index)
                val chargeTime = item.getLong("chargeTimeEpochMillis")
                add(
                    ChargingRecordEntity(
                        id = item.getLong("id"),
                        vehicleId = item.getLong("vehicleId"),
                        chargeTimeEpochMillis = chargeTime,
                        energyKwh = item.getDouble("energyKwh"),
                        cost = item.getDouble("cost"),
                        startSoc = item.getInt("startSoc"),
                        endSoc = item.getInt("endSoc"),
                        chargerType = item.optNullableString("chargerType"),
                        location = item.optNullableString("location"),
                        remark = item.optNullableString("remark"),
                        odometerKm = item.optNullableDouble("odometerKm"),
                        latitude = item.optNullableDouble("latitude"),
                        longitude = item.optNullableDouble("longitude"),
                        locationAccuracyMeters = item.optNullableDouble("locationAccuracyMeters"),
                        syncId = item.optString("syncId").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                        updatedAtEpochMillis = item.optLong("updatedAtEpochMillis", chargeTime),
                        isDeleted = item.optBoolean("isDeleted", false)
                    )
                )
            }
        }

        val sessionsJson = root.optJSONArray("tripSessions") ?: JSONArray()
        val sessions = buildList {
            for (index in 0 until sessionsJson.length()) {
                val item = sessionsJson.getJSONObject(index)
                add(
                    TripSessionEntity(
                        id = item.getLong("id"),
                        vehicleId = item.getLong("vehicleId"),
                        startedAtEpochMillis = item.getLong("startedAtEpochMillis"),
                        endedAtEpochMillis = item.optNullableLong("endedAtEpochMillis"),
                        distanceMeters = item.optDouble("distanceMeters", 0.0),
                        elapsedSeconds = item.optLong("elapsedSeconds", 0),
                        movingSeconds = item.optNullableLong("movingSeconds"),
                        stoppedSeconds = item.optNullableLong("stoppedSeconds"),
                        averageSpeedMps = item.optNullableDouble("averageSpeedMps"),
                        maxSpeedMps = item.optNullableDouble("maxSpeedMps"),
                        startLatitude = item.optNullableDouble("startLatitude"),
                        startLongitude = item.optNullableDouble("startLongitude"),
                        endLatitude = item.optNullableDouble("endLatitude"),
                        endLongitude = item.optNullableDouble("endLongitude"),
                        startAltitudeMeters = item.optNullableDouble("startAltitudeMeters"),
                        endAltitudeMeters = item.optNullableDouble("endAltitudeMeters"),
                        minAltitudeMeters = item.optNullableDouble("minAltitudeMeters"),
                        maxAltitudeMeters = item.optNullableDouble("maxAltitudeMeters"),
                        status = item.optString("status", TripStatus.COMPLETED)
                    )
                )
            }
        }

        val pointsJson = root.optJSONArray("tripPoints") ?: JSONArray()
        val points = buildList {
            for (index in 0 until pointsJson.length()) {
                val item = pointsJson.getJSONObject(index)
                add(
                    TripPointEntity(
                        id = item.getLong("id"),
                        tripId = item.getLong("tripId"),
                        capturedAtEpochMillis = item.getLong("capturedAtEpochMillis"),
                        latitude = item.getDouble("latitude"),
                        longitude = item.getDouble("longitude"),
                        altitudeMeters = item.optNullableDouble("altitudeMeters"),
                        speedMps = item.optNullableDouble("speedMps"),
                        bearingDegrees = item.optNullableDouble("bearingDegrees"),
                        horizontalAccuracyMeters = item.optNullableDouble("horizontalAccuracyMeters"),
                        verticalAccuracyMeters = item.optNullableDouble("verticalAccuracyMeters"),
                        speedAccuracyMps = item.optNullableDouble("speedAccuracyMps"),
                        provider = item.optNullableString("provider")
                    )
                )
            }
        }

        val vehicleIds = vehicles.map { it.id }.toSet()
        val tripIds = sessions.map { it.id }.toSet()
        require(vehicles.isNotEmpty()) { "备份中没有车辆数据" }
        require(vehicles.all { it.syncId.isNotBlank() }) { "备份中存在缺失同步身份的车辆" }
        require(vehicles.map { it.syncId }.distinct().size == vehicles.size) { "备份中存在重复车辆同步身份" }
        require(records.all { it.vehicleId in vehicleIds }) { "备份中存在无法关联车辆的充电记录" }
        require(records.all { it.syncId.isNotBlank() }) { "备份中存在缺失同步身份的充电记录" }
        require(records.map { it.syncId }.distinct().size == records.size) { "备份中存在重复充电记录同步身份" }
        require(records.all { (it.latitude == null) == (it.longitude == null) }) { "备份中存在不完整的定位坐标" }
        require(sessions.all { it.vehicleId in vehicleIds }) { "备份中存在无法关联车辆的行程" }
        require(sessions.all { it.status in setOf(TripStatus.RECORDING, TripStatus.INTERRUPTED, TripStatus.COMPLETED) }) { "备份中存在未知行程状态" }
        require(sessions.all { (it.startLatitude == null) == (it.startLongitude == null) && (it.endLatitude == null) == (it.endLongitude == null) }) { "备份中存在不完整的行程坐标" }
        require(points.all { it.tripId in tripIds }) { "备份中存在无法关联行程的轨迹点" }

        return BackupPayload(
            schemaVersion = schemaVersion,
            exportedAt = root.getLong("exportedAt"),
            appVersion = root.optString("appVersion", "unknown"),
            vehicles = vehicles,
            chargingRecords = records,
            tripSessions = sessions,
            tripPoints = points
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (!has(name) || isNull(name)) null else getDouble(name)

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)
}
