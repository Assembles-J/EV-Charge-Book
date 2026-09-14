package com.evchargebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisibilityCounterTest {
    @Test
    fun `no started activity is background`() {
        assertFalse(ActivityVisibilityCounter().hasVisibleActivity)
    }

    @Test
    fun `one started activity is visible`() {
        val counter = ActivityVisibilityCounter()

        counter.onActivityStarted()

        assertTrue(counter.hasVisibleActivity)
    }

    @Test
    fun `multiple activities remain visible until last one stops`() {
        val counter = ActivityVisibilityCounter()
        counter.onActivityStarted()
        counter.onActivityStarted()

        counter.onActivityStopped()

        assertTrue(counter.hasVisibleActivity)
        counter.onActivityStopped()
        assertFalse(counter.hasVisibleActivity)
    }

    @Test
    fun `extra stop never makes visibility counter negative`() {
        val counter = ActivityVisibilityCounter()

        counter.onActivityStopped()
        counter.onActivityStarted()

        assertTrue(counter.hasVisibleActivity)
    }
}
