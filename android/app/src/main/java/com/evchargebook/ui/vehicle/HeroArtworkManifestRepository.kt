package com.evchargebook.ui.vehicle

import android.content.Context
import android.content.res.Configuration
import com.evchargebook.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small, dependency-free manifest loader for vehicle Hero artwork.
 *
 * Hero resolution is deliberately local-first:
 * 1. read the last successful manifest from SharedPreferences and return it immediately;
 * 2. let Coil resolve the versioned image from memory/disk cache before network;
 * 3. refresh the manifest once in the background for the next Hero resolve.
 *
 * A network failure must never delay or clear an already cached Hero mapping.
 *
 * VehicleCatalog stores one stable semantic base key. The manifest may publish:
 * - <base>-dark
 * - <base>-light
 *
 * Existing <base> entries remain a supported legacy fallback.
 */
object HeroArtworkManifestRepository {
    data class RemoteArtwork(
        val version: Int,
        val url: String,
        val manifestVersion: Int = version,
        val resolvedKey: String? = null,
    )

    private const val PREFS = "hero_artwork_manifest"
    private const val KEY_MANIFEST_JSON = "manifest_json"
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 5_000

    private val loadMutex = Mutex()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var cacheLoaded = false
    @Volatile private var refreshStarted = false
    @Volatile private var artworks: Map<String, RemoteArtwork> = emptyMap()

    /**
     * Resolve the best Hero for the current Android UI mode.
     *
     * Dark: <base>-dark -> legacy <base>
     * Light: <base>-light -> <base>-dark -> legacy <base>
     */
    suspend fun resolve(context: Context, artworkKey: String): RemoteArtwork? {
        val appContext = context.applicationContext
        ensureCachedManifestLoaded(appContext)
        startRemoteRefresh(appContext)
        val preferLight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        return resolveCached(artworkKey, preferLight)
    }

    internal fun candidateKeys(artworkKey: String, preferLight: Boolean): List<String> {
        val base = artworkKey
            .trim()
            .removeSuffix("-dark")
            .removeSuffix("-light")
        if (base.isBlank()) return emptyList()
        return if (preferLight) {
            listOf("$base-light", "$base-dark", base)
        } else {
            listOf("$base-dark", base)
        }
    }

    internal fun resolveFrom(
        entries: Map<String, RemoteArtwork>,
        artworkKey: String,
        preferLight: Boolean,
    ): RemoteArtwork? {
        val base = artworkKey.trim().removeSuffix("-dark").removeSuffix("-light")
        return candidateKeys(base, preferLight).firstNotNullOfOrNull { key ->
            entries[key]?.let { artwork ->
                // HeroVehicleCard historically uses RemoteArtwork.version as an explicit Coil cache key.
                // Encode only the resolved semantic variant into that cache version so light/dark v1
                // cannot collide in memory/disk cache. manifestVersion remains the authoritative vN.
                val marker = when {
                    key.endsWith("-light") -> 2
                    key.endsWith("-dark") -> 1
                    else -> 0
                }
                artwork.copy(
                    version = artwork.manifestVersion * 10 + marker,
                    resolvedKey = key,
                )
            }
        }
    }

    private fun resolveCached(artworkKey: String, preferLight: Boolean): RemoteArtwork? =
        resolveFrom(artworks, artworkKey, preferLight)

    private suspend fun ensureCachedManifestLoaded(context: Context) {
        if (cacheLoaded) return
        loadMutex.withLock {
            if (cacheLoaded) return

            val cachedJson = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MANIFEST_JSON, null)
            artworks = cachedJson?.let(::parseManifest).orEmpty()
            cacheLoaded = true
        }
    }

    /**
     * Refresh once per app process without blocking the current Hero render.
     *
     * The refreshed mapping is persisted and becomes available on the next Hero resolve
     * (for example when returning to Dashboard or on the next app launch).
     */
    private fun startRemoteRefresh(context: Context) {
        if (refreshStarted) return
        synchronized(this) {
            if (refreshStarted) return
            refreshStarted = true
        }

        refreshScope.launch {
            val remoteJson = fetchManifest(BuildConfig.HERO_ARTWORK_MANIFEST_URL)
            val remote = remoteJson?.let(::parseManifest).orEmpty()
            if (remote.isEmpty() || remoteJson == null) return@launch

            artworks = remote
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MANIFEST_JSON, remoteJson)
                .apply()
        }
    }

    private suspend fun fetchManifest(url: String): String? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
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

    internal fun parseManifest(json: String): Map<String, RemoteArtwork> = runCatching {
        val root = JSONObject(json)
        if (root.optInt("schemaVersion", -1) != 1) return@runCatching emptyMap()
        val entries = root.optJSONObject("artworks") ?: return@runCatching emptyMap()
        buildMap {
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = entries.optJSONObject(key) ?: continue
                val version = item.optInt("version", 0)
                val url = item.optString("url", "")
                if (version > 0 && url.startsWith("https://")) {
                    put(
                        key,
                        RemoteArtwork(
                            version = version,
                            manifestVersion = version,
                            resolvedKey = key,
                            url = url,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyMap())
}
