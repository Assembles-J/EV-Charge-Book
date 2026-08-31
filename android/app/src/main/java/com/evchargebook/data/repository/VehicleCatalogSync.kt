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
    private val keyPattern = Regex("^[a-z0-9][a-z0-9-]{1,100}$")

    private data class ManagedBrand(
        val brandId: String,
        val name: String,
        val logoLightUrl: String?,
        val logoLightVersion: Int,
        val logoDarkUrl: String?,
        val logoDarkVersion: Int,
        val isActive: Boolean,
    )

    fun parse(json: String): List<VehicleCatalogEntity> {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", -1) == 1) { "unsupported vehicle catalog schema" }
        val brands = parseBrands(root)
        val array = root.optJSONArray("vehicles") ?: error("vehicle catalog has no vehicles")
        require(array.length() > 0) { "vehicle catalog must not be empty" }

        val seen = mutableSetOf<String>()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val catalogId = item.getString("catalogId").trim().lowercase()
            require(catalogId.matches(keyPattern)) { "invalid catalogId" }
            require(seen.add(catalogId)) { "duplicate catalogId" }

            val brandId = item.optString("brandId").trim().lowercase()
            if (brandId.isNotEmpty()) require(brandId.matches(keyPattern)) { "invalid brandId" }
            val managedBrand = brandId.takeIf { it.isNotEmpty() }?.let { id ->
                val brand = brands[id]
                if (brands.isNotEmpty()) requireNotNull(brand) { "unknown brandId: $id" }
                brand
            }

            val legacyBrand = item.optString("brand").trim()
            val brand = managedBrand?.name ?: legacyBrand
            val series = item.getString("series").trim()
            val modelName = item.getString("modelName").trim()
            val powertrainType = item.getString("powertrainType").trim().uppercase()
            require(brand.isNotEmpty() && series.isNotEmpty() && modelName.isNotEmpty()) { "blank catalog text" }
            require(powertrainType in powertrainTypes) { "invalid powertrain type" }

            val modelYear = optionalPositiveInt(item, "modelYear")
            if (modelYear != null) require(modelYear in 1990..2100) { "invalid model year" }
            val battery = optionalPositiveDouble(item, "batteryCapacityKwh")
            val range = optionalPositiveInt(item, "rangeKm")
            val rangeStandard = item.optString("rangeStandard").trim().takeIf { it.isNotEmpty() }
            require(rangeStandard == null || rangeStandard.length <= 32) { "invalid range standard" }
            val heroKey = item.optString("heroArtworkKey")
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
            if (heroKey != null) require(heroKey.matches(keyPattern)) { "invalid Hero key" }

            val itemActive = if (item.has("isActive")) item.getBoolean("isActive") else true
            VehicleCatalogEntity(
                catalogId = catalogId,
                source = "managed-v1",
                brandId = brandId,
                brand = brand,
                series = series,
                modelName = modelName,
                modelYear = modelYear,
                trimName = item.optString("trimName").trim().takeIf { it.isNotEmpty() },
                powertrainType = powertrainType,
                batteryCapacityKwh = battery,
                rangeKm = range,
                rangeStandard = rangeStandard,
                heroArtworkKey = heroKey,
                brandLogoLightUrl = managedBrand?.logoLightUrl,
                brandLogoLightVersion = managedBrand?.logoLightVersion ?: 0,
                brandLogoDarkUrl = managedBrand?.logoDarkUrl,
                brandLogoDarkVersion = managedBrand?.logoDarkVersion ?: 0,
                isActive = itemActive && (managedBrand?.isActive != false),
                sourceUpdatedAtEpochMillis = maxOf(
                    item.optLong("sourceUpdatedAtEpochMillis", 0L),
                    managedBrand?.let { root.optLong("updatedAtEpochMillis", 0L) } ?: 0L,
                )
            )
        }
    }

    private fun parseBrands(root: JSONObject): Map<String, ManagedBrand> {
        val array = root.optJSONArray("brands") ?: return emptyMap()
        val result = linkedMapOf<String, ManagedBrand>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val brandId = item.getString("brandId").trim().lowercase()
            val name = item.getString("name").trim()
            require(brandId.matches(keyPattern)) { "invalid brandId" }
            require(name.isNotEmpty()) { "blank brand name" }
            require(!result.containsKey(brandId)) { "duplicate brandId" }

            val lightUrl = optionalHttpsUrl(item, "logoLightUrl")
            val darkUrl = optionalHttpsUrl(item, "logoDarkUrl")
            val lightVersion = item.optInt("logoLightVersion", 0)
            val darkVersion = item.optInt("logoDarkVersion", 0)
            require(lightVersion >= 0 && darkVersion >= 0) { "invalid brand logo version" }
            require(lightUrl != null || lightVersion == 0) { "light logo version without URL" }
            require(darkUrl != null || darkVersion == 0) { "dark logo version without URL" }

            result[brandId] = ManagedBrand(
                brandId = brandId,
                name = name,
                logoLightUrl = lightUrl,
                logoLightVersion = lightVersion,
                logoDarkUrl = darkUrl,
                logoDarkVersion = darkVersion,
                isActive = if (item.has("isActive")) item.getBoolean("isActive") else true,
            )
        }
        return result
    }

    private fun optionalHttpsUrl(item: JSONObject, key: String): String? {
        if (!item.has(key) || item.isNull(key)) return null
        return item.getString(key).trim().takeIf { it.isNotEmpty() }?.also {
            require(it.startsWith("https://")) { "$key must use https" }
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
            // Only a complete, valid remote document may change the local catalog.
            // Previously managed rows missing from the new document become inactive, never deleted.
            dao.deactivateManagedEntries()
            dao.upsertManaged(items)
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
