@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.media.desktop.wakelock

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSActivityIdleDisplaySleepDisabled
import platform.Foundation.NSActivityIdleSystemSleepDisabled
import platform.Foundation.NSActivityUserInitiated
import platform.Foundation.NSProcessInfo
import platform.Foundation.beginActivityWithOptions
import platform.Foundation.endActivity
import platform.IOKit.IOPMAssertionCreateWithName
import platform.IOKit.IOPMAssertionRelease
import platform.IOKit.kIOPMAssertionLevelOn
import platform.darwin.NSObjectProtocol
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

internal actual object DesktopScreenWakePlatformBridge {

    private val mutex: CPointer<pthread_mutex_t> = nativeHeap.alloc<pthread_mutex_t>().also {
        pthread_mutex_init(it.ptr, null)
    }.ptr

    private var displayAssertionId: UInt = 0u
    private var systemAssertionId: UInt = 0u
    private var processActivityToken: NSObjectProtocol? = null

    @kotlin.concurrent.Volatile
    private var heldState: Boolean = false

    actual val isHeld: Boolean
        get() = heldState

    internal val activeDisplayAssertionId: UInt
        get() = withMutex { displayAssertionId }

    private inline fun <T> withMutex(block: () -> T): T {
        pthread_mutex_lock(mutex)
        try {
            return block()
        } finally {
            pthread_mutex_unlock(mutex)
        }
    }

    private fun createAssertion(typeString: String, reason: String): UInt = memScoped {
        val typeCf = CFStringCreateWithCString(null, typeString, kCFStringEncodingUTF8)
            ?: return 0u
        val reasonCf = CFStringCreateWithCString(null, reason, kCFStringEncodingUTF8)
        if (reasonCf == null) {
            CFRelease(typeCf)
            return 0u
        }
        val assertionVar = alloc<UIntVar>()
        assertionVar.value = 0u
        val kr = IOPMAssertionCreateWithName(
            typeCf,
            kIOPMAssertionLevelOn.toUInt(),
            reasonCf,
            assertionVar.ptr
        )
        CFRelease(reasonCf)
        CFRelease(typeCf)
        if (kr == 0) assertionVar.value else 0u
    }

    actual fun setWakeLockEnabled(enabled: Boolean, reason: String) {
        withMutex {
            val safeReason = reason.ifBlank { "GenericUI Media Playback" }
            if (enabled) {
                if (displayAssertionId == 0u) {
                    displayAssertionId = createAssertion("PreventUserIdleDisplaySleep", safeReason)
                }
                if (systemAssertionId == 0u) {
                    systemAssertionId = createAssertion("PreventUserIdleSystemSleep", safeReason)
                }
                if (processActivityToken == null) {
                    processActivityToken = runCatching {
                        val options = NSActivityIdleDisplaySleepDisabled or
                            NSActivityIdleSystemSleepDisabled or
                            NSActivityUserInitiated
                        NSProcessInfo.processInfo.beginActivityWithOptions(options, reason = safeReason)
                    }.getOrNull()
                }
                heldState = (displayAssertionId != 0u || systemAssertionId != 0u || processActivityToken != null)
            } else {
                val dispId = displayAssertionId
                displayAssertionId = 0u
                if (dispId != 0u) {
                    IOPMAssertionRelease(dispId)
                }
                val sysId = systemAssertionId
                systemAssertionId = 0u
                if (sysId != 0u) {
                    IOPMAssertionRelease(sysId)
                }
                val token = processActivityToken
                processActivityToken = null
                if (token != null) {
                    runCatching {
                        NSProcessInfo.processInfo.endActivity(token)
                    }
                }
                heldState = false
            }
        }
    }
}
