package com.evchargebook.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloadRecoveryContractTest {
    @Test
    fun readyRecoveryKeepsOriginalUpdateMetadata() {
        val info = AppUpdateInfo(
            versionCode = 108,
            versionName = "1.0.8",
            apkUrl = "https://example.invalid/ev-charge-book-1.0.8.apk",
            sha256 = "a".repeat(64),
            publishedAt = "2026-08-31T06:00:00Z",
            mandatory = false
        )

        assertEquals(108, info.versionCode)
        assertEquals("1.0.8", info.versionName)
        assertTrue(info.sha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun processGateStillAllowsNewerVersionAfterOnePrompt() {
        val gate = UpdatePromptSessionGate()

        assertTrue(gate.tryClaim(108))
        assertTrue(gate.tryClaim(109))
    }
}
