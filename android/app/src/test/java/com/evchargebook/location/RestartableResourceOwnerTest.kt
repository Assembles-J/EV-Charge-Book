package com.evchargebook.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RestartableResourceOwnerTest {
    @Test
    fun acquireIsStableUntilReleaseAndReleaseStopsOwnedResource() {
        val started = mutableListOf<FakeResource>()
        val stopped = mutableListOf<FakeResource>()
        var nextId = 0
        val owner = RestartableResourceOwner(
            create = { FakeResource(++nextId) },
            start = { started += it },
            stop = { stopped += it },
        )

        val first = owner.acquire()
        val same = owner.acquire()

        assertSame(first, same)
        assertEquals(listOf(first), started)
        assertEquals(emptyList<FakeResource>(), stopped)

        owner.release()
        owner.release()

        assertEquals(listOf(first), stopped)

        val second = owner.acquire()
        assertNotSame(first, second)
        assertEquals(listOf(first, second), started)
    }

    private data class FakeResource(val id: Int)
}
