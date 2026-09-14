package com.evchargebook.autotrip

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothAutoStartExecutionPolicyTest {
    @Test
    fun `auto start disabled keeps confirmation prompt behavior`() {
        val decision = BluetoothAutoStartExecutionPolicy.decide(
            autoStartEnabled = false,
            sdkInt = 36,
            appInForeground = false,
        )

        assertEquals(BluetoothAutoStartExecution.CONFIRMATION_PROMPT, decision)
    }

    @Test
    fun `android 11 background can use direct auto start`() {
        val decision = BluetoothAutoStartExecutionPolicy.decide(
            autoStartEnabled = true,
            sdkInt = 30,
            appInForeground = false,
        )

        assertEquals(BluetoothAutoStartExecution.DIRECT_START, decision)
    }

    @Test
    fun `android 12 plus visible app can use direct auto start`() {
        val decision = BluetoothAutoStartExecutionPolicy.decide(
            autoStartEnabled = true,
            sdkInt = 36,
            appInForeground = true,
        )

        assertEquals(BluetoothAutoStartExecution.DIRECT_START, decision)
    }

    @Test
    fun `android 12 plus background falls back to user action`() {
        val decision = BluetoothAutoStartExecutionPolicy.decide(
            autoStartEnabled = true,
            sdkInt = 31,
            appInForeground = false,
        )

        assertEquals(BluetoothAutoStartExecution.USER_ACTION_REQUIRED, decision)
    }
}
