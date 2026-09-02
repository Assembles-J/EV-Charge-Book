package com.evchargebook.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class CockpitThemeContractTest {
    @Test
    fun lightCockpitColorsFollowActiveMaterialScheme() {
        val scheme = lightColorScheme()
        val cockpit = cockpitColorsFor(scheme)

        assertEquals(scheme.background, cockpit.background)
        assertEquals(scheme.surfaceContainerLow, cockpit.surface)
        assertEquals(scheme.surfaceContainerHigh, cockpit.surfaceElevated)
        assertEquals(scheme.onSurface, cockpit.primaryText)
        assertEquals(scheme.onSurfaceVariant, cockpit.secondaryText)
        assertEquals(scheme.outlineVariant, cockpit.outline)
        assertEquals(scheme.primary, cockpit.accent)
        assertEquals(scheme.error, cockpit.danger)
    }

    @Test
    fun darkCockpitColorsFollowActiveMaterialScheme() {
        val scheme = darkColorScheme()
        val cockpit = cockpitColorsFor(scheme)

        assertEquals(scheme.background, cockpit.background)
        assertEquals(scheme.surfaceContainerLow, cockpit.surface)
        assertEquals(scheme.surfaceContainerHigh, cockpit.surfaceElevated)
        assertEquals(scheme.onSurface, cockpit.primaryText)
        assertEquals(scheme.onSurfaceVariant, cockpit.secondaryText)
        assertEquals(scheme.outlineVariant, cockpit.outline)
        assertEquals(scheme.primary, cockpit.accent)
        assertEquals(scheme.error, cockpit.danger)
    }
}
