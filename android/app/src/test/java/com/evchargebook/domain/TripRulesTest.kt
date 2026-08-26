package com.evchargebook.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TripRulesTest {
    @Test
    fun elapsedSeconds_calculatesWholeSeconds() {
        assertEquals(90L, TripRules.elapsedSeconds(1_000L, 91_999L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun elapsedSeconds_rejectsEndBeforeStart() {
        TripRules.elapsedSeconds(2_000L, 1_999L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireCanStart_rejectsConcurrentTrip() {
        TripRules.requireCanStart(true)
    }
}
