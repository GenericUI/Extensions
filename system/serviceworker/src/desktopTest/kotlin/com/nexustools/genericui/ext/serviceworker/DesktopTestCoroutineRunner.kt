package com.nexustools.genericui.ext.serviceworker

import kotlinx.coroutines.CoroutineScope

internal expect fun runDesktopBlockingTest(block: suspend CoroutineScope.() -> Unit)
