package com.evchargebook.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCaptureMigration16To17ContractTest {
    @Test
    fun `migration adds nullable monotonic capture timestamp only`() {
        assertEquals(1, TripCaptureMigration16To17.statements.size)
        val statement = TripCaptureMigration16To17.statements.single()
        assertTrue(statement.contains("ALTER TABLE trip_points"))
        assertTrue(statement.contains("capturedAtElapsedRealtimeNanos INTEGER DEFAULT NULL"))
    }
}
