package com.nexustools.genericui.ext.serviceworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

internal actual fun runDesktopBlockingTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        block()
    }
}
