@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.wakelock

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.wcstr
import platform.windows.CRITICAL_SECTION
import platform.windows.CloseHandle
import platform.windows.CreateEventW
import platform.windows.CreateThread
import platform.windows.ES_CONTINUOUS
import platform.windows.ES_DISPLAY_REQUIRED
import platform.windows.ES_SYSTEM_REQUIRED
import platform.windows.EnterCriticalSection
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.InitializeCriticalSection
import platform.windows.LeaveCriticalSection
import platform.windows.POWER_REQUEST_CONTEXT_SIMPLE_STRING
import platform.windows.POWER_REQUEST_CONTEXT_VERSION
import platform.windows.PowerClearRequest
import platform.windows.PowerCreateRequest
import platform.windows.PowerSetRequest
import platform.windows.REASON_CONTEXT
import platform.windows.SetEvent
import platform.windows.SetThreadExecutionState
import platform.windows.WAIT_TIMEOUT
import platform.windows.WaitForSingleObject
import platform.windows._POWER_REQUEST_TYPE

private val win32WakeKeeperThreadProc = staticCFunction { param: COpaquePointer? ->
    val stopEvent: HANDLE? = param
    val activeFlags = (ES_CONTINUOUS or ES_DISPLAY_REQUIRED or ES_SYSTEM_REQUIRED).toUInt()
    val resetIdleFlags = (ES_DISPLAY_REQUIRED or ES_SYSTEM_REQUIRED).toUInt()
    SetThreadExecutionState(activeFlags)
    if (stopEvent != null) {
        while (WaitForSingleObject(stopEvent, 30_000u) == WAIT_TIMEOUT.toUInt()) {
            SetThreadExecutionState(activeFlags)
            SetThreadExecutionState(resetIdleFlags)
        }
    }
    SetThreadExecutionState(ES_CONTINUOUS.toUInt())
    0u
}

internal actual object DesktopScreenWakePlatformBridge {

    private val lockPtr: CPointer<CRITICAL_SECTION> = nativeHeap.alloc<CRITICAL_SECTION>().also {
        InitializeCriticalSection(it.ptr)
    }.ptr

    private var powerRequestHandle: HANDLE? = null
    private var displayRequestActive: Boolean = false
    private var systemRequestActive: Boolean = false
    private var keeperStopEvent: HANDLE? = null
    private var keeperThreadHandle: HANDLE? = null

    @kotlin.concurrent.Volatile
    private var heldState: Boolean = false

    actual val isHeld: Boolean
        get() = heldState

    private inline fun <T> withLock(block: () -> T): T {
        EnterCriticalSection(lockPtr)
        try {
            return block()
        } finally {
            LeaveCriticalSection(lockPtr)
        }
    }

    private fun acquirePowerRequest(reason: String) = memScoped {
        if (powerRequestHandle != null) return@memScoped
        val ctx = alloc<REASON_CONTEXT>()
        ctx.Version = POWER_REQUEST_CONTEXT_VERSION.toUInt()
        ctx.Flags = POWER_REQUEST_CONTEXT_SIMPLE_STRING.toUInt()
        ctx.Reason.SimpleReasonString = reason.wcstr.ptr
        val handle = PowerCreateRequest(ctx.ptr)
        if (handle != null && handle != INVALID_HANDLE_VALUE) {
            powerRequestHandle = handle
            displayRequestActive = PowerSetRequest(handle, _POWER_REQUEST_TYPE.PowerRequestDisplayRequired) != 0
            systemRequestActive = PowerSetRequest(handle, _POWER_REQUEST_TYPE.PowerRequestSystemRequired) != 0
        }
    }

    private fun releasePowerRequest() {
        val handle = powerRequestHandle ?: return
        powerRequestHandle = null
        if (displayRequestActive) {
            PowerClearRequest(handle, _POWER_REQUEST_TYPE.PowerRequestDisplayRequired)
            displayRequestActive = false
        }
        if (systemRequestActive) {
            PowerClearRequest(handle, _POWER_REQUEST_TYPE.PowerRequestSystemRequired)
            systemRequestActive = false
        }
        CloseHandle(handle)
    }

    private fun startKeeperThread() {
        if (keeperThreadHandle != null) return
        val evt = CreateEventW(null, 1, 0, null as String?)
        if (evt == null || evt == INVALID_HANDLE_VALUE) {
            SetThreadExecutionState((ES_DISPLAY_REQUIRED or ES_SYSTEM_REQUIRED).toUInt())
            return
        }
        val thread = CreateThread(null, 0uL, win32WakeKeeperThreadProc, evt, 0u, null)
        if (thread == null || thread == INVALID_HANDLE_VALUE) {
            CloseHandle(evt)
            SetThreadExecutionState((ES_DISPLAY_REQUIRED or ES_SYSTEM_REQUIRED).toUInt())
            return
        }
        keeperStopEvent = evt
        keeperThreadHandle = thread
    }

    private fun stopKeeperThread() {
        val evt = keeperStopEvent
        val thread = keeperThreadHandle
        keeperStopEvent = null
        keeperThreadHandle = null
        if (evt != null) {
            SetEvent(evt)
        }
        if (thread != null) {
            WaitForSingleObject(thread, 2_000u)
            CloseHandle(thread)
        }
        if (evt != null) {
            CloseHandle(evt)
        }
    }

    actual fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        withLock {
            val safeReason = reason.ifBlank { "GenericUI Media Playback" }
            if (enabled) {
                acquirePowerRequest(safeReason)
                startKeeperThread()
                heldState = (powerRequestHandle != null || keeperThreadHandle != null)
            } else {
                stopKeeperThread()
                releasePowerRequest()
                heldState = false
            }
        }
    }
}
