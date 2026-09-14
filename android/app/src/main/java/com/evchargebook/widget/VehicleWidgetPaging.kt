package com.evchargebook.widget

enum class VehicleWidgetStackPage {
    VEHICLE,
    TRIP,
    CHARGING,
}

/**
 * Pure contract for the three cards hosted by the widget StackView.
 *
 * The launcher owns swipe navigation. The widget deliberately keeps the interaction surface
 * simple: tapping a card opens the app, while only pages with a meaningful contextual action
 * expose an extra action button.
 */
object VehicleWidgetStackSpec {
    private val pages = listOf(
        VehicleWidgetStackPage.VEHICLE,
        VehicleWidgetStackPage.TRIP,
        VehicleWidgetStackPage.CHARGING,
    )

    fun pageCount(): Int = pages.size

    fun pageKey(position: Int): String = page(position).name

    fun actionLabel(position: Int): String = when (page(position)) {
        VehicleWidgetStackPage.VEHICLE -> ""
        VehicleWidgetStackPage.TRIP -> "行程"
        VehicleWidgetStackPage.CHARGING -> "充电记录"
    }

    fun tapDestination(position: Int): String = "APP"

    fun actionDestination(position: Int): String = when (page(position)) {
        VehicleWidgetStackPage.VEHICLE -> "NONE"
        VehicleWidgetStackPage.TRIP -> "TRIP"
        VehicleWidgetStackPage.CHARGING -> "APP"
    }

    internal fun page(position: Int): VehicleWidgetStackPage {
        val count = pages.size
        val remainder = position % count
        val normalized = if (remainder < 0) remainder + count else remainder
        return pages[normalized]
    }
}
