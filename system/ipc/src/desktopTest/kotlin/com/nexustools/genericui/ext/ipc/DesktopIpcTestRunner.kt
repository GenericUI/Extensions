package com.nexustools.genericui.ext.ipc

import kotlinx.coroutines.CoroutineScope

internal expect fun runIpcBlockingTest(block: suspend CoroutineScope.() -> Unit)
