package com.evchargebook.domain

object TripNotificationPermissionPolicy {
    const val NOTIFICATION_RUNTIME_PERMISSION_API = 33

    fun shouldRequest(
        apiLevel: Int,
        permissionGranted: Boolean,
        alreadyRequestedForTrip: Boolean
    ): Boolean =
        apiLevel >= NOTIFICATION_RUNTIME_PERMISSION_API &&
            !permissionGranted &&
            !alreadyRequestedForTrip
}
