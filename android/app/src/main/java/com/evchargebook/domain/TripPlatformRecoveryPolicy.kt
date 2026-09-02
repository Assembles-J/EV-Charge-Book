package com.evchargebook.domain

/**
 * Pure policy for the bounded platform-location recovery path.
 *
 * GPS is the preferred health authority whenever it was actually registered. Network callbacks
 * may still be forwarded to Trip tracking, but they must not keep a dead GPS registration alive.
 * Recovery attempts are budgeted for the whole Trip source session and are never reset by a single
 * successful callback.
 */
object TripPlatformRecoveryPolicy {
    const val GPS_PROVIDER = "gps"
    const val NETWORK_PROVIDER = "network"

    private val recoveryDelaysMillis = longArrayOf(30_000L, 60_000L)

    fun monitoredProvider(registeredProviders: Set<String>): String? {
        val normalized = registeredProviders.map { it.lowercase() }.toSet()
        return when {
            GPS_PROVIDER in normalized -> GPS_PROVIDER
            NETWORK_PROVIDER in normalized -> NETWORK_PROVIDER
            else -> null
        }
    }

    fun callbackRefreshesWatchdog(
        monitoredProvider: String?,
        callbackProvider: String?,
    ): Boolean =
        monitoredProvider != null &&
            callbackProvider != null &&
            monitoredProvider.equals(callbackProvider, ignoreCase = true)

    fun recoveryDelayMillis(completedAttempts: Int): Long? =
        recoveryDelaysMillis.getOrNull(completedAttempts)
}
