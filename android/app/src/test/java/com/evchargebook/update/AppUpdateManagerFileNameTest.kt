package com.evchargebook.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerFileNameTest {
    @Test
    fun updaterOwnedApkNamesAreRecognized() {
        assertTrue(isManagedUpdateApkFileName("ev-charge-book-1.2.3.apk"))
        assertTrue(isManagedUpdateApkFileName("ev-charge-book-2026.08.31.APK"))
    }

    @Test
    fun unrelatedFilesAreNeverMatchedForCleanup() {
        assertFalse(isManagedUpdateApkFileName("another-app.apk"))
        assertFalse(isManagedUpdateApkFileName("ev-charge-book-notes.txt"))
        assertFalse(isManagedUpdateApkFileName("backup-ev-charge-book-1.2.3.apk"))
    }
}
