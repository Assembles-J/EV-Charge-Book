package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlatformRecoveryPolicyTest {
    @Test
    fun `gps is monitored when gps and network are both registered`() {
        assertEquals(
            TripPlatformRecoveryPolicy.GPS_PROVIDER,
            TripPlatformRecoveryPolicy.monitoredProvider(setOf("gps", "network"))
        )
    }

    @Test
    fun `network is monitored only when gps was not registered`() {
        assertEquals(
            TripPlatformRecoveryPolicy.NETWORK_PROVIDER,
            TripPlatformRecoveryPolicy.monitoredProvider(setOf("network"))
        )
    }

    @Test
    fun `network callback cannot refresh a gps watchdog`() {
        assertFalse(
            TripPlatformRecoveryPolicy.callbackRefreshesWatchdog(
                monitoredProvider = "gps",
                callbackProvider = "network"
            )
        )
        assertTrue(
            TripPlatformRecoveryPolicy.callbackRefreshesWatchdog(
                monitoredProvider = "gps",
                callbackProvider = "gps"
            )
        )
    }

    @Test
    fun `recovery budget is exhausted after two attempts`() {
        assertEquals(30_000L, TripPlatformRecoveryPolicy.recoveryDelayMillis(0))
        assertEquals(60_000L, TripPlatformRecoveryPolicy.recoveryDelayMillis(1))
        assertNull(TripPlatformRecoveryPolicy.recoveryDelayMillis(2))
        assertNull(TripPlatformRecoveryPolicy.recoveryDelayMillis(3))
    }
}
