package com.evchargebook.trip

import com.evchargebook.domain.TripGpsHealthSnapshot
import com.evchargebook.domain.TripGpsHealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripGpsHealthFeedTest {
    @Test
    fun publishAndClearExposeCurrentInTripHealth() {
        TripGpsHealthFeed.clear()
        assertNull(TripGpsHealthFeed.snapshot.value)

        val snapshot = TripGpsHealthSnapshot(
            status = TripGpsHealthStatus.LOST,
            secondsSinceLastCallback = 31,
            secondsSinceLastAcceptedPoint = 31,
            message = "GPS 暂时中断",
        )
        TripGpsHealthFeed.publish(snapshot)

        assertEquals(snapshot, TripGpsHealthFeed.snapshot.value)

        TripGpsHealthFeed.clear()
        assertNull(TripGpsHealthFeed.snapshot.value)
    }
}
