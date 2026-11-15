@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.media

import com.nexustools.genericui.nativeimpl.system.ScreenWakeLockNativeImpl
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference

/**
 * Reference-counted coordinator that holds the platform screen/sleep wake lock whenever at least
 * one active [com.nexustools.genericui.component.media.Player] (`VideoPlayer` or `AudioPlayer`)
 * is playing with `keepScreenOn = true`, and releases it as soon as all active players pause,
 * stop, finish, disable `keepScreenOn`, or dispose.
 */
object MediaScreenWakeController {
    private const val DEFAULT_WAKE_REASON: String = "GenericUI Media Playback"

    private val nextTokenId = AtomicLong(1L)
    private val activeTokensRef = AtomicReference<Set<Long>>(emptySet())
    private val syncLock = AtomicInt(0)

    private var activeWakeLockImpl: ScreenWakeLockNativeImpl? = null
    private var lastAppliedEnabled: Boolean = false

    val activePlayerCount: Int
        get() = activeTokensRef.load().size

    val isKeepScreenOnActive: Boolean
        get() = activeTokensRef.load().isNotEmpty()

    val isNativeWakeLockHeld: Boolean
        get() = activeWakeLockImpl?.isWakeLockHeld == true

    internal fun allocateToken(): Long = nextTokenId.fetchAndAdd(1L)

    internal fun setTokenActive(token: Long, active: Boolean) {
        if (token <= 0L) return
        while (true) {
            val current = activeTokensRef.load()
            val alreadyActive = token in current
            if (alreadyActive == active) {
                break
            }
            val updated = if (active) {
                current + token
            } else {
                current - token
            }
            if (activeTokensRef.compareAndSet(current, updated)) {
                break
            }
        }
        syncNativeWakeLock()
    }

    internal fun releaseToken(token: Long) {
        setTokenActive(token, false)
    }

    internal fun onWakeLockProviderUpdated() {
        withSyncLock {
            val previous = activeWakeLockImpl
            activeWakeLockImpl = null
            lastAppliedEnabled = false
            previous?.dispose()
        }
        syncNativeWakeLock()
    }

    fun releaseAll() {
        activeTokensRef.store(emptySet())
        syncNativeWakeLock()
    }

    private inline fun withSyncLock(block: () -> Unit) {
        while (!syncLock.compareAndSet(0, 1)) {
            // Short spin while applying idempotent wake lock transition
        }
        try {
            block()
        } finally {
            syncLock.store(0)
        }
    }

    private fun syncNativeWakeLock() {
        withSyncLock {
            val shouldHold = activeTokensRef.load().isNotEmpty()
            var impl = activeWakeLockImpl
            if (impl == null && shouldHold) {
                impl = MediaExtension.createScreenWakeLockNative()
                activeWakeLockImpl = impl
            }
            if (impl != null && (shouldHold != lastAppliedEnabled || shouldHold != impl.isWakeLockHeld)) {
                lastAppliedEnabled = shouldHold
                impl.setWakeLockEnabled(shouldHold, DEFAULT_WAKE_REASON)
            } else if (impl == null) {
                lastAppliedEnabled = false
            }
        }
    }
}
