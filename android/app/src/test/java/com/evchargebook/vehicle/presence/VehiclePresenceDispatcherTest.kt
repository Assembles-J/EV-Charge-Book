package com.evchargebook.vehicle.presence

import com.evchargebook.autotrip.AutoTripCandidateResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiclePresenceDispatcherTest {
    @Test
    fun `connected observation is delegated with source and timestamp intact`() = runBlocking {
        val sink = FakeSink()
        val dispatcher = VehiclePresenceDispatcher(sink)
        val event = VehiclePresenceEvent(
            state = VehiclePresenceState.CONNECTED,
            source = VehiclePresenceSource.CLASSIC_ACL,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Car",
            observedAtEpochMillis = 1234L,
        )

        val result = dispatcher.dispatch(event)

        assertTrue(result is VehiclePresenceDispatchResult.Candidate)
        assertEquals(event, sink.connected.single())
        assertTrue(sink.disconnected.isEmpty())
    }

    @Test
    fun `foreground reconciliation remains a distinct presence source`() = runBlocking {
        val sink = FakeSink()
        val dispatcher = VehiclePresenceDispatcher(sink)
        val event = VehiclePresenceEvent(
            state = VehiclePresenceState.CONNECTED,
            source = VehiclePresenceSource.FOREGROUND_CONNECTION_CHECK,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            observedAtEpochMillis = 5678L,
        )

        dispatcher.dispatch(event)

        assertEquals(VehiclePresenceSource.FOREGROUND_CONNECTION_CHECK, sink.connected.single().source)
        assertEquals(5678L, sink.connected.single().observedAtEpochMillis)
    }

    @Test
    fun `present only observation never becomes a Trip candidate`() = runBlocking {
        val sink = FakeSink()
        val dispatcher = VehiclePresenceDispatcher(sink)

        val result = dispatcher.dispatch(
            VehiclePresenceEvent(
                state = VehiclePresenceState.PRESENT,
                source = VehiclePresenceSource.COMPANION_DEVICE,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
            )
        )

        assertEquals(VehiclePresenceDispatchResult.ObservedOnly, result)
        assertTrue(sink.connected.isEmpty())
        assertTrue(sink.disconnected.isEmpty())
    }

    @Test
    fun `disconnect only delegates session close path`() = runBlocking {
        val sink = FakeSink()
        val dispatcher = VehiclePresenceDispatcher(sink)
        val event = VehiclePresenceEvent(
            state = VehiclePresenceState.DISCONNECTED,
            source = VehiclePresenceSource.CLASSIC_ACL,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            observedAtEpochMillis = 9012L,
        )

        val result = dispatcher.dispatch(event)

        assertEquals(VehiclePresenceDispatchResult.Disconnected, result)
        assertEquals(event, sink.disconnected.single())
        assertTrue(sink.connected.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank device identity is rejected`() {
        VehiclePresenceEvent(
            state = VehiclePresenceState.CONNECTED,
            source = VehiclePresenceSource.CLASSIC_ACL,
            deviceAddress = "   ",
        )
    }

    private class FakeSink : VehiclePresenceCandidateSink {
        val connected = mutableListOf<VehiclePresenceEvent>()
        val disconnected = mutableListOf<VehiclePresenceEvent>()

        override suspend fun onConnected(event: VehiclePresenceEvent): AutoTripCandidateResult {
            connected += event
            return AutoTripCandidateResult.NotConfigured
        }

        override suspend fun onDisconnected(event: VehiclePresenceEvent) {
            disconnected += event
        }
    }
}
