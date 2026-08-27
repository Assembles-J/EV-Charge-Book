package com.evchargebook.domain.charge

import com.evchargebook.data.entity.ChargingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeDefaultResolverTest {
    @Test
    fun `uses current vehicle soc and latest charge defaults`() {
        val records = listOf(
            ChargingRecordEntity(
                id = 2,
                vehicleId = 1,
                chargeTimeEpochMillis = 2_000,
                energyKwh = 30.0,
                cost = 45.0,
                startSoc = 40,
                endSoc = 92,
                chargerType = "公共快充",
                location = "公司快充站"
            ),
            ChargingRecordEntity(
                id = 1,
                vehicleId = 1,
                chargeTimeEpochMillis = 1_000,
                energyKwh = 20.0,
                cost = 12.0,
                startSoc = 30,
                endSoc = 80,
                chargerType = "家充",
                location = "家"
            )
        )

        val result = ChargeDefaultResolver.resolve(currentSoc = 65, records = records)

        assertEquals(65, result.startSoc)
        assertEquals(92, result.endSoc)
        assertEquals("公共快充", result.chargerType)
        assertEquals(1.5, result.pricePerKwh!!, 0.0001)
        assertEquals("公司快充站", result.location)
    }

    @Test
    fun `falls back to 100 end soc without history`() {
        val result = ChargeDefaultResolver.resolve(currentSoc = null, records = emptyList())

        assertNull(result.startSoc)
        assertEquals(100, result.endSoc)
        assertEquals(ChargeDefaultResolver.FALLBACK_CHARGER_TYPE, result.chargerType)
        assertNull(result.pricePerKwh)
    }
}
