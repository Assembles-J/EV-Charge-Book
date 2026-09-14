package com.evchargebook.autotrip

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoTripNotificationCopyTest {
    @Test
    fun `normal bluetooth candidate asks for confirmation`() {
        val copy = AutoTripNotificationCopy.candidate(
            vehicleLabel = "零跑 C16",
            autoStartUserActionRequired = false,
        )

        assertEquals("已连接 零跑 C16", copy.title)
        assertEquals("是否开始本次行程？", copy.text)
        assertEquals("立即开始", copy.actionLabel)
    }

    @Test
    fun `background auto start fallback explains one tap requirement`() {
        val copy = AutoTripNotificationCopy.candidate(
            vehicleLabel = "零跑 C16",
            autoStartUserActionRequired = true,
        )

        assertEquals("已连接 零跑 C16 · 自动开始待确认", copy.title)
        assertEquals("系统限制后台自动启动，点击即可开始记录。", copy.text)
        assertEquals("开始行程", copy.actionLabel)
    }

    @Test
    fun `direct auto start success is explicit`() {
        val copy = AutoTripNotificationCopy.autoStarted("零跑 C16")

        assertEquals("零跑 C16 行程已自动开始", copy.title)
        assertEquals("由车辆蓝牙连接触发 · 正在记录", copy.text)
    }
}
