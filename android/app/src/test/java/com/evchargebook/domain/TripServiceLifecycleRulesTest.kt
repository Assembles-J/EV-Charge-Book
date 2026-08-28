package com.evchargebook.domain

import com.evchargebook.data.entity.TripStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripServiceLifecycleRulesTest {
    @Test
    fun `recording trip destruction is unexpected`() {
        assertTrue(TripServiceLifecycleRules.shouldRecordUnexpectedDestroy(TripStatus.RECORDING))
    }

    @Test
    fun `interrupted trip destruction is unexpected`() {
        assertTrue(TripServiceLifecycleRules.shouldRecordUnexpectedDestroy(TripStatus.INTERRUPTED))
    }

    @Test
    fun `completed trip normal service stop is not an anomaly`() {
        assertFalse(TripServiceLifecycleRules.shouldRecordUnexpectedDestroy(TripStatus.COMPLETED))
    }

    @Test
    fun `missing trip is not reported as active destruction`() {
        assertFalse(TripServiceLifecycleRules.shouldRecordUnexpectedDestroy(null))
    }
}
