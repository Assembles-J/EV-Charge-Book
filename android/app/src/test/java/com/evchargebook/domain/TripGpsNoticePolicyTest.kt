package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripGpsNoticePolicyTest {
    @Test
    fun statusTextExplainsDegradedLostAndLongGapWithoutClaimingTripStopped() {
        assertEquals("GPS 信号较弱，正在恢复定位", TripGpsNoticePolicy.statusText(TripGpsHealthStatus.DEGRADED))
        assertEquals("GPS 信号已中断，行程仍在记录", TripGpsNoticePolicy.statusText(TripGpsHealthStatus.LOST))
        assertEquals("GPS 长时间中断，请检查定位/省电设置", TripGpsNoticePolicy.statusText(TripGpsHealthStatus.LONG_GAP))
    }

    @Test
    fun recoveryNoticeOnlyAppearsWhenGpsReturnsFromAProblemState() {
        assertEquals(
            "GPS 已恢复",
            TripGpsNoticePolicy.transitionNotice(TripGpsHealthStatus.LOST, TripGpsHealthStatus.GOOD)
        )
        assertEquals(
            "GPS 已恢复",
            TripGpsNoticePolicy.transitionNotice(TripGpsHealthStatus.DEGRADED, TripGpsHealthStatus.GOOD)
        )
        assertNull(TripGpsNoticePolicy.transitionNotice(TripGpsHealthStatus.WAITING, TripGpsHealthStatus.GOOD))
        assertNull(TripGpsNoticePolicy.transitionNotice(TripGpsHealthStatus.GOOD, TripGpsHealthStatus.GOOD))
    }

    @Test
    fun problemTransitionsHaveOneShotNoticeText() {
        assertEquals(
            "GPS 信号较弱，正在恢复定位",
            TripGpsNoticePolicy.transitionNotice(TripGpsHealthStatus.GOOD, TripGpsHealthStatus.DEGRADED)
        )
        assertEquals(
            "GPS 信号已中断，行程仍在记录",
            TripGpsNoticePolicy.transitionNotice(TripGpsHealthStatus.DEGRADED, TripGpsHealthStatus.LOST)
        )
        assertEquals(
            "GPS 长时间中断，请检查定位/省电设置",
            TripGpsNoticePolicy.transitionNotice(TripGpsHealthStatus.LOST, TripGpsHealthStatus.LONG_GAP)
        )
    }
}
