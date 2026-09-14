package com.evchargebook.ui.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripBasemapDiagnosticsV08Test {
    @Test
    fun `provider failure remains visible after truthful fallback`() {
        val diagnostics = TripBasemapDiagnosticsV08(
            providerLabel = "OpenFreeMap Liberty",
            styleLoaded = true,
            tileLoaded = false,
            tileErrorCount = 1,
            fullyRendered = false,
            mapFailed = true,
            fallbackActive = true,
        )

        assertEquals(
            "底图 OpenFreeMap Liberty · 地图加载失败 · 已回退无底图",
            diagnostics.statusText(),
        )
    }

    @Test
    fun `tile lifecycle reports the most useful current state`() {
        assertEquals(
            "底图 OpenFreeMap Liberty · 加载中",
            TripBasemapDiagnosticsV08().statusText(),
        )
        assertEquals(
            "底图 OpenFreeMap Liberty · 等待瓦片",
            TripBasemapDiagnosticsV08(styleLoaded = true).statusText(),
        )
        assertEquals(
            "底图 OpenFreeMap Liberty · 瓦片错误 2",
            TripBasemapDiagnosticsV08(styleLoaded = true, tileErrorCount = 2).statusText(),
        )
        assertEquals(
            "底图 OpenFreeMap Liberty · 正在渲染",
            TripBasemapDiagnosticsV08(styleLoaded = true, tileLoaded = true).statusText(),
        )
        assertEquals(
            "底图 OpenFreeMap Liberty · 瓦片已加载",
            TripBasemapDiagnosticsV08(
                styleLoaded = true,
                tileLoaded = true,
                fullyRendered = true,
            ).statusText(),
        )
    }
}
