package com.evchargebook.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeocodeMemoryCacheTest {
    @Test
    fun `nearby coordinates reuse the same successful address`() {
        val cache = GeocodeMemoryCache()
        cache.put(31.23040, 121.47370, "上海市黄浦区")

        assertEquals("上海市黄浦区", cache.get(31.23041, 121.47371))
    }

    @Test
    fun `failed or blank geocode is not cached so later retry remains possible`() {
        val cache = GeocodeMemoryCache()
        cache.put(31.23040, 121.47370, null)
        cache.put(31.23040, 121.47370, "   ")

        assertNull(cache.get(31.23040, 121.47370))
        assertEquals(0, cache.size())
    }

    @Test
    fun `cache stays bounded`() {
        val cache = GeocodeMemoryCache(maxEntries = 2)
        cache.put(31.0000, 121.0000, "A")
        cache.put(31.1000, 121.1000, "B")
        cache.put(31.2000, 121.2000, "C")

        assertEquals(2, cache.size())
        assertNull(cache.get(31.0000, 121.0000))
        assertEquals("B", cache.get(31.1000, 121.1000))
        assertEquals("C", cache.get(31.2000, 121.2000))
    }
}
