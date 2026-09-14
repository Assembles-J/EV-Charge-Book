package com.evchargebook.location

/** Owns one restartable resource and guarantees that release is idempotent. */
internal class RestartableResourceOwner<T>(
    private val create: () -> T,
    private val start: (T) -> Unit,
    private val stop: (T) -> Unit,
) {
    private var resource: T? = null

    fun acquire(): T {
        resource?.let { return it }
        return create().also { created ->
            start(created)
            resource = created
        }
    }

    fun release() {
        val current = resource ?: return
        resource = null
        stop(current)
    }
}
