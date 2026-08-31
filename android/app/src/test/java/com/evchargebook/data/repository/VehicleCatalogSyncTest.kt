package com.evchargebook.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VehicleCatalogSyncTest {
    @Test
    fun `valid managed catalog resolves brand logo metadata into local rows`() {
        val json = """
            {
              "schemaVersion": 1,
              "catalogVersion": 8,
              "updatedAtEpochMillis": 999,
              "brands": [
                {
                  "brandId": "example",
                  "name": "Example",
                  "logoLightUrl": "https://cdn.example.com/brand_example_light_v2.webp",
                  "logoLightVersion": 2,
                  "logoDarkUrl": "https://cdn.example.com/brand_example_dark_v3.webp",
                  "logoDarkVersion": 3,
                  "isActive": true
                }
              ],
              "vehicles": [
                {
                  "catalogId": "example-ev-2026-long-range",
                  "brandId": "example",
                  "brand": "stale-client-snapshot",
                  "series": "EV",
                  "modelName": "EV 2026",
                  "modelYear": 2026,
                  "trimName": "Long Range",
                  "powertrainType": "BEV",
                  "batteryCapacityKwh": 88.8,
                  "rangeKm": 688,
                  "rangeStandard": "CLTC",
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
        assertEquals("example", item.brandId)
        assertEquals("Example", item.brand)
        assertEquals("example-ev-2026", item.heroArtworkKey)
        assertEquals("CLTC", item.rangeStandard)
        assertEquals("https://cdn.example.com/brand_example_light_v2.webp", item.brandLogoLightUrl)
        assertEquals(2, item.brandLogoLightVersion)
        assertEquals("https://cdn.example.com/brand_example_dark_v3.webp", item.brandLogoDarkUrl)
        assertEquals(3, item.brandLogoDarkVersion)
        assertFalse(item.isActive)
        assertEquals(88.8, item.batteryCapacityKwh!!, 0.001)
        assertEquals(688, item.rangeKm)
        assertEquals(123456L, item.sourceUpdatedAtEpochMillis)
    }

    @Test
    fun `legacy catalog without brands remains readable during rollout`() {
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

        val item = VehicleCatalogRemoteParser.parse(json).single()
        assertEquals("", item.brandId)
        assertEquals("Example", item.brand)
        assertNull(item.heroArtworkKey)
        assertNull(item.brandLogoLightUrl)
    }

    @Test
    fun `brand retirement removes its vehicles from new selection`() {
        val json = """
            {
              "schemaVersion": 1,
              "brands": [
                {"brandId":"example","name":"Example","logoLightVersion":0,"logoDarkVersion":0,"isActive":false}
              ],
              "vehicles": [
                {"catalogId":"example-ev","brandId":"example","brand":"Example","series":"EV","modelName":"EV","powertrainType":"BEV","isActive":true}
              ]
            }
        """.trimIndent()

        assertFalse(VehicleCatalogRemoteParser.parse(json).single().isActive)
    }

    @Test
    fun `unknown brand id rejects the whole managed document`() {
        val json = """
            {
              "schemaVersion": 1,
              "brands": [{"brandId":"known","name":"Known","logoLightVersion":0,"logoDarkVersion":0}],
              "vehicles": [{"catalogId":"example-ev","brandId":"missing","brand":"Example","series":"EV","modelName":"EV","powertrainType":"BEV"}]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            VehicleCatalogRemoteParser.parse(json)
        }
    }

    @Test
    fun `non https logo url rejects the whole remote document`() {
        val json = """
            {
              "schemaVersion": 1,
              "brands": [{"brandId":"example","name":"Example","logoLightUrl":"http://example.com/logo.webp","logoLightVersion":1,"logoDarkVersion":0}],
              "vehicles": [{"catalogId":"example-ev","brandId":"example","brand":"Example","series":"EV","modelName":"EV","powertrainType":"BEV"}]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            VehicleCatalogRemoteParser.parse(json)
        }
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
