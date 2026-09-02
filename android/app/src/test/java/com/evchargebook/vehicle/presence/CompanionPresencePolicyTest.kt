package com.evchargebook.vehicle.presence

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionPresencePolicyTest {
    @Test
    fun `presence observation requires Android 12`() {
        assertEquals(
            CompanionPresenceSupport.ANDROID_TOO_OLD,
            CompanionPresencePolicy.support(
                sdkInt = 30,
                hasCompanionSetupFeature = true,
            ),
        )
    }

    @Test
    fun `missing system feature fails closed`() {
        assertEquals(
            CompanionPresenceSupport.FEATURE_MISSING,
            CompanionPresencePolicy.support(
                sdkInt = 31,
                hasCompanionSetupFeature = false,
            ),
        )
    }

    @Test
    fun `supported platform is capability gated`() {
        assertEquals(
            CompanionPresenceSupport.SUPPORTED,
            CompanionPresencePolicy.support(
                sdkInt = 31,
                hasCompanionSetupFeature = true,
            ),
        )
    }

    @Test
    fun `classic companion callback is connection semantics`() {
        assertEquals(
            VehiclePresenceState.CONNECTED,
            CompanionPresencePolicy.classicCallbackState(appeared = true),
        )
        assertEquals(
            VehiclePresenceState.DISCONNECTED,
            CompanionPresencePolicy.classicCallbackState(appeared = false),
        )
    }
}
