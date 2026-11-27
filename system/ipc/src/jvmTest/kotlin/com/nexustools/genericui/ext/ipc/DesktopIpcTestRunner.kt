package com.nexustools.genericui.ext.ipc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

internal actual fun runIpcBlockingTest(block: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        block()
    }
}
