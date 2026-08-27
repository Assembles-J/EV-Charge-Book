package com.evchargebook.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDiagnosticSamplingRulesTest {
    @Test
    fun persistsFirstAndEveryTwentyFifthRejectedPoint() {
        assertTrue(TripDiagnosticSamplingRules.shouldPersistRejectedPoint(1))
        assertFalse(TripDiagnosticSamplingRules.shouldPersistRejectedPoint(2))
        assertFalse(TripDiagnosticSamplingRules.shouldPersistRejectedPoint(24))
        assertTrue(TripDiagnosticSamplingRules.shouldPersistRejectedPoint(25))
        assertFalse(TripDiagnosticSamplingRules.shouldPersistRejectedPoint(26))
        assertTrue(TripDiagnosticSamplingRules.shouldPersistRejectedPoint(50))
    }
}
