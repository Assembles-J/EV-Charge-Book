package com.evchargebook.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptSessionGateTest {
    @Test
    fun sameVersionCanOnlyBeClaimedOnce() {
        val gate = UpdatePromptSessionGate()

        assertTrue(gate.tryClaim(42))
        assertFalse(gate.tryClaim(42))
    }

    @Test
    fun newerVersionCanStillBeClaimed() {
        val gate = UpdatePromptSessionGate()

        assertTrue(gate.tryClaim(42))
        assertTrue(gate.tryClaim(43))
    }

    @Test
    fun independentGateCanDeduplicateInstallReadyPrompt() {
        val readyGate = UpdatePromptSessionGate()

        assertTrue(readyGate.tryClaim(108))
        assertFalse(readyGate.tryClaim(108))
    }
}
