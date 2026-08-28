package com.evchargebook.ui.vehicle

import android.content.Context
import com.evchargebook.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small, dependency-free manifest loader for vehicle Hero artwork.
 *
 * The manifest is cached in SharedPreferences so Coil can continue resolving the same versioned
 * image URL while offline. Image bytes themselves are handled by Coil's memory/disk cache.
 */
object HeroArtworkManifestRepository {
    data class RemoteArtwork(
        val version: Int,
        val url: String
    )

    private const val PREFS = "hero_artwork_manifest"
    private const val KEY_MANIFEST_JSON = "manifest_json"
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 5_000

    private val loadMutex = Mutex()
    @Volatile private var loaded = false
    @Volatile private var artworks: Map<String, RemoteArtwork> = emptyMap()

    suspend fun resolve(context: Context, artworkKey: String): RemoteArtwork? {
        ensureLoaded(context.applicationContext)
        return artworks[artworkKey]
    }

    private suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return

            val cachedJson = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MANIFEST_JSON, null)
            val cached = cachedJson?.let(::parseManifest).orEmpty()

            val remoteJson = fetchManifest(BuildConfig.HERO_ARTWORK_MANIFEST_URL)
            val remote = remoteJson?.let(::parseManifest).orEmpty()

            artworks = if (remote.isNotEmpty()) remote else cached
            if (remote.isNotEmpty() && remoteJson != null) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_MANIFEST_JSON, remoteJson)
                    .apply()
            }
            loaded = true
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
                    put(key, RemoteArtwork(version = version, url = url))
                }
            }
        }
    }.getOrDefault(emptyMap())
}
