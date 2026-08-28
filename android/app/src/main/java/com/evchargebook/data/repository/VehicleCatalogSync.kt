package com.evchargebook.data.repository

import androidx.room.withTransaction
import com.evchargebook.data.database.AppDatabase
import com.evchargebook.data.entity.VehicleCatalogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object VehicleCatalogRemoteParser {
    private val powertrainTypes = setOf("BEV", "PHEV", "REEV")

    fun parse(json: String): List<VehicleCatalogEntity> {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == 1) { "unsupported vehicle catalog schema" }
        val array = root.optJSONArray("vehicles") ?: error("vehicle catalog has no vehicles")
        require(array.length() > 0) { "vehicle catalog must not be empty" }

        val seen = mutableSetOf<String>()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val catalogId = item.getString("catalogId").trim().lowercase()
            require(catalogId.matches(Regex("^[a-z0-9][a-z0-9-]{1,100}$"))) { "invalid catalogId" }
            require(seen.add(catalogId)) { "duplicate catalogId" }

            val brand = item.getString("brand").trim()
            val series = item.getString("series").trim()
            val modelName = item.getString("modelName").trim()
            val powertrainType = item.getString("powertrainType").trim().uppercase()
            require(brand.isNotEmpty() && series.isNotEmpty() && modelName.isNotEmpty()) { "blank catalog text" }
            require(powertrainType in powertrainTypes) { "invalid powertrain type" }

            val modelYear = optionalPositiveInt(item, "modelYear")
            if (modelYear != null) require(modelYear in 1990..2100) { "invalid model year" }
            val battery = optionalPositiveDouble(item, "batteryCapacityKwh")
            val range = optionalPositiveInt(item, "rangeKm")
            val heroKey = item.optString("heroArtworkKey")
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
            if (heroKey != null) {
                require(heroKey.matches(Regex("^[a-z0-9][a-z0-9-]{1,100}$"))) { "invalid Hero key" }
            }

            VehicleCatalogEntity(
                catalogId = catalogId,
                source = "managed-v1",
                brand = brand,
                series = series,
                modelName = modelName,
                modelYear = modelYear,
                trimName = item.optString("trimName").trim().takeIf { it.isNotEmpty() },
                powertrainType = powertrainType,
                batteryCapacityKwh = battery,
                rangeKm = range,
                heroArtworkKey = heroKey,
                isActive = if (item.has("isActive")) item.getBoolean("isActive") else true,
                sourceUpdatedAtEpochMillis = item.optLong("sourceUpdatedAtEpochMillis", 0L)
            )
        }
    }

    private fun optionalPositiveInt(item: JSONObject, key: String): Int? {
        if (!item.has(key) || item.isNull(key)) return null
        return item.getInt(key).also { require(it > 0) { "$key must be positive" } }
    }

    private fun optionalPositiveDouble(item: JSONObject, key: String): Double? {
        if (!item.has(key) || item.isNull(key)) return null
        return item.getDouble(key).also { require(it.isFinite() && it > 0.0) { "$key must be positive" } }
    }
}

class VehicleCatalogSync(private val database: AppDatabase) {
    private val dao = database.vehicleCatalogDao()

    suspend fun refresh(url: String): Boolean {
        val json = fetch(url) ?: return false
        val items = runCatching { VehicleCatalogRemoteParser.parse(json) }.getOrNull() ?: return false
        if (items.isEmpty()) return false

        database.withTransaction {
            // A successful, fully parsed document is authoritative only for previously managed rows.
            // Bundled/local fallback rows are never removed by a failed or partial network response.
            dao.deactivateManagedEntries()
            dao.insertAll(items)
        }
        return true
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3_000
                readTimeout = 4_000
                setRequestProperty("Accept", "application/json")
                useCaches = false
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
