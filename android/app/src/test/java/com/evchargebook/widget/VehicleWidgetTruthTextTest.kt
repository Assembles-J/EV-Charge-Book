package com.evchargebook.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleWidgetTruthTextTest {
    @Test
    fun `unknown local state stays unavailable instead of inventing vehicle facts`() {
        val snapshot = VehicleWidgetSnapshot(
            vehicleId = 7L,
            displayName = "C16",
        )

        assertEquals("--", VehicleWidgetTruthText.soc(snapshot))
        assertEquals(
            "尚无车辆状态记录",
            VehicleWidgetTruthText.stateLabel(snapshot) { "should-not-be-used" },
        )
    }

    @Test
    fun `known state is explicitly labeled as last local record`() {
        val snapshot = VehicleWidgetSnapshot(
            vehicleId = 7L,
            displayName = "C16",
            currentSoc = 74,
            stateUpdatedAtEpochMillis = 1234L,
        )

        assertEquals("74%", VehicleWidgetTruthText.soc(snapshot))
        val label = VehicleWidgetTruthText.stateLabel(snapshot) { "16:06" }
        assertEquals("上次记录 · 16:06", label)
        assertFalse(label.contains("实时"))
        assertFalse(label.contains("车辆同步"))
    }

    @Test
    fun `bluetooth configuration is shown as a rule not a live connection claim`() {
        val promptOnly = VehicleWidgetSnapshot(
            vehicleId = 7L,
            bluetoothDetectionEnabled = true,
            bluetoothDeviceName = "C16",
        )
        val autoStart = promptOnly.copy(autoStartOnConnect = true)

        val promptHeadline = VehicleWidgetTruthText.statusHeadline(promptOnly)
        assertEquals("● 蓝牙检测已开启", promptHeadline)
        assertFalse(promptHeadline.contains("车辆已连接"))
        assertEquals("连接C16后提醒确认行程", VehicleWidgetTruthText.statusDetail(promptOnly))

        assertEquals("● 自动行程已就绪", VehicleWidgetTruthText.statusHeadline(autoStart))
        assertEquals("连接C16后尝试自动开始行程", VehicleWidgetTruthText.statusDetail(autoStart))
    }

    @Test
    fun `active trip and charging labels describe app bookkeeping only`() {
        val trip = VehicleWidgetSnapshot(vehicleId = 7L, activeTrip = true)
        val charging = VehicleWidgetSnapshot(vehicleId = 7L, activeCharging = true)

        assertEquals("● 行程记录中", VehicleWidgetTruthText.statusHeadline(trip))
        assertEquals("行程记录中 · 本地状态", VehicleWidgetTruthText.stateLabel(trip) { "16:06" })
        assertEquals("打开行程", VehicleWidgetTruthText.tripAction(trip))
        assertEquals("● 充电记录中", VehicleWidgetTruthText.statusHeadline(charging))
        assertEquals("充电记录中 · 本地状态", VehicleWidgetTruthText.stateLabel(charging) { "16:06" })
        assertEquals("充电记录中", VehicleWidgetTruthText.appAction(charging))
    }

    @Test
    fun `empty app state asks user to add vehicle`() {
        val snapshot = VehicleWidgetSnapshot()

        assertEquals("尚未添加车辆", VehicleWidgetTruthText.statusHeadline(snapshot))
        assertEquals("等待添加车辆", VehicleWidgetTruthText.stateLabel(snapshot) { "16:06" })
    }

    @Test
    fun `widget size policy keeps compact layout until a real expanded width exists`() {
        assertTrue(VehicleWidgetSizePolicy.isCompact(0))
        assertTrue(VehicleWidgetSizePolicy.isCompact(299))
        assertFalse(VehicleWidgetSizePolicy.isCompact(300))
    }
}
