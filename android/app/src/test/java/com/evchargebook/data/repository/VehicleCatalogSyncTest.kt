package com.evchargebook.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VehicleCatalogSyncTest {
    @Test
    fun `valid managed catalog parses local offline fields`() {
        val json = """
            {
              "schemaVersion": 1,
              "catalogVersion": 8,
              "vehicles": [
                {
                  "catalogId": "example-ev-2026-long-range",
                  "brand": "Example",
                  "series": "EV",
                  "modelName": "EV 2026",
                  "modelYear": 2026,
                  "trimName": "Long Range",
                  "powertrainType": "BEV",
                  "batteryCapacityKwh": 88.8,
                  "rangeKm": 688,
                  "heroArtworkKey": "example-ev-2026",
                  "isActive": false,
                  "sourceUpdatedAtEpochMillis": 123456
                }
              ]
            }
        """.trimIndent()

        val item = VehicleCatalogRemoteParser.parse(json).single()

        assertEquals("example-ev-2026-long-range", item.catalogId)
        assertEquals("managed-v1", item.source)
        assertEquals("example-ev-2026", item.heroArtworkKey)
        assertFalse(item.isActive)
        assertEquals(88.8, item.batteryCapacityKwh!!, 0.001)
        assertEquals(688, item.rangeKm)
        assertEquals(123456L, item.sourceUpdatedAtEpochMillis)
    }

    @Test
    fun `missing optional Hero key stays null`() {
        val json = """
            {
              "schemaVersion": 1,
              "vehicles": [
                {
                  "catalogId": "example-ev-2026",
                  "brand": "Example",
                  "series": "EV",
                  "modelName": "EV 2026",
                  "powertrainType": "BEV",
                  "isActive": true
                }
              ]
            }
        """.trimIndent()

        assertNull(VehicleCatalogRemoteParser.parse(json).single().heroArtworkKey)
    }

    @Test
    fun `duplicate ids reject the whole remote document`() {
        val json = """
            {
              "schemaVersion": 1,
              "vehicles": [
                {"catalogId":"same-id","brand":"A","series":"A","modelName":"A","powertrainType":"BEV"},
                {"catalogId":"same-id","brand":"B","series":"B","modelName":"B","powertrainType":"BEV"}
              ]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            VehicleCatalogRemoteParser.parse(json)
        }
    }

    @Test
    fun `empty remote catalog is rejected so local Room data is not replaced`() {
        val json = """{"schemaVersion":1,"vehicles":[]}"""

        assertThrows(IllegalArgumentException::class.java) {
            VehicleCatalogRemoteParser.parse(json)
        }
    }
}
