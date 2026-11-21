package com.nexustools.genericui.ext.serviceworker

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleMobileServiceWorkerTest {

    private class NeverInstantiatedWorker : ServiceWorker() {
        override fun onStart() = Unit
    }

    @Test
    fun iosNeverInstantiatesOrSimulatesServiceWorkerAndExposesUnsupportedCapabilities() {
        assertFalse(ServiceWorkerExtension.isSupported)
        assertFalse(ServiceWorkerExtension.isAutostartSupported)
        assertFalse(ServiceWorkerExtension.channel.isConnected)
        assertFalse(ServiceWorkerExtension.isAutostartEnabled())
        assertFalse(ServiceWorkerExtension.setAutostartEnabled(true))
        assertNull(ServiceWorkerExtension.activeWorker())

        var factoryInvoked = false
        ServiceWorkerExtension.registerWorkerFactory {
            factoryInvoked = true
            NeverInstantiatedWorker()
        }
        assertFalse(ServiceWorkerExtension.hasRegisteredWorkerFactory())
        assertFalse(ServiceWorkerExtension.ensureStarted())
        assertFalse(factoryInvoked)
        assertTrue(ServiceWorkerExtension.prepareDesktopMainLaunch(emptyArray()))

        var sendFailure: Throwable? = null
        val sendBlock: suspend () -> Unit = {
            ServiceWorkerExtension.channel.send("ping")
        }
        sendBlock.startCoroutine(
            Continuation(EmptyCoroutineContext) { result ->
                sendFailure = result.exceptionOrNull()
            }
        )
        assertIs<UnsupportedOperationException>(sendFailure)

        var requestOutcome: Result<String>? = null
        val reqBlock: suspend () -> Result<String> = {
            ServiceWorkerExtension.channel.tryRequest<String>("ping")
        }
        reqBlock.startCoroutine(
            Continuation(EmptyCoroutineContext) { result ->
                requestOutcome = result.getOrNull()
            }
        )
        assertTrue(requestOutcome?.isFailure == true)
        assertIs<UnsupportedOperationException>(requestOutcome?.exceptionOrNull())
    }
}
