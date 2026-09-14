package com.evchargebook.ui.trip

/**
 * Debug-only state snapshot for the replaceable Trip basemap POC.
 *
 * The state lives above MapLibre so a provider failure can remain visible after the UI switches to
 * the truthful no-basemap renderer.
 */
internal data class TripBasemapDiagnosticsV08(
    val providerLabel: String = "OpenFreeMap Liberty",
    val styleLoaded: Boolean = false,
    val tileLoaded: Boolean = false,
    val tileErrorCount: Int = 0,
    val fullyRendered: Boolean = false,
    val mapFailed: Boolean = false,
    val fallbackActive: Boolean = false,
) {
    fun statusText(): String = when {
        mapFailed && fallbackActive -> "底图 $providerLabel · 地图加载失败 · 已回退无底图"
        mapFailed -> "底图 $providerLabel · 地图加载失败"
        tileLoaded && fullyRendered -> "底图 $providerLabel · 瓦片已加载"
        tileLoaded -> "底图 $providerLabel · 正在渲染"
        tileErrorCount > 0 -> "底图 $providerLabel · 瓦片错误 $tileErrorCount"
        styleLoaded -> "底图 $providerLabel · 等待瓦片"
        else -> "底图 $providerLabel · 加载中"
    }
}
