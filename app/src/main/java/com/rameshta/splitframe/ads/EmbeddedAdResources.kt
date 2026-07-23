package com.rameshta.splitframe.ads

/** Owns one asynchronously loaded SDK resource and rejects callbacks from stale loads. */
internal class ReplaceableAdOwner<T>(
    private val destroy: (T) -> Unit,
) {
    private var generation = 0
    private var current: T? = null
    private var disposed = false

    fun beginLoad(): Int {
        check(!disposed)
        return ++generation
    }

    fun accept(loadGeneration: Int, resource: T): Boolean {
        if (disposed || loadGeneration != generation) {
            destroy(resource)
            return false
        }
        current?.let(destroy)
        current = resource
        return true
    }

    fun failed(loadGeneration: Int) {
        if (!disposed && loadGeneration == generation) {
            current?.let(destroy)
            current = null
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        current?.let(destroy)
        current = null
    }
}

/** Keeps an AdView attached to the host lifecycle without retaining an Activity. */
internal class EmbeddedAdViewLifecycleController<T>(
    private val load: (T) -> Unit,
    private val resume: (T) -> Unit,
    private val pause: (T) -> Unit,
    private val destroy: (T) -> Unit,
) {
    private var current: T? = null
    private var resumed = false

    fun attach(resource: T) {
        if (current === resource) return
        current?.let(destroy)
        current = resource
        load(resource)
        if (resumed) resume(resource) else pause(resource)
    }

    fun onResume() {
        if (resumed) return
        resumed = true
        current?.let(resume)
    }

    fun onPause() {
        if (!resumed) return
        resumed = false
        current?.let(pause)
    }

    fun release(resource: T) {
        if (current !== resource) return
        current = null
        destroy(resource)
    }
}
