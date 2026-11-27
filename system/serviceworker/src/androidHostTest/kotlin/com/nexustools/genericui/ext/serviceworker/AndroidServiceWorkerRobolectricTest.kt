package com.nexustools.genericui.ext.serviceworker

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.nexustools.genericui.android.AndroidExtensionRuntimeBridge
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
import com.nexustools.genericui.ext.ipc.AndroidServiceIpcBinder
import com.nexustools.genericui.ext.ipc.AndroidServiceIpcBundleCodec
import com.nexustools.genericui.ext.ipc.AndroidServiceIpcClient
import com.nexustools.genericui.ext.ipc.AndroidServiceIpcProtocol
import com.nexustools.genericui.ext.ipc.IpcPacket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidServiceWorkerRobolectricTest {

    data class EchoReq(val value: String)
    data class EchoResp(val echoed: String)

    private class RoboMissingActivityFragment : Fragment()

    private class RoboSampleWorker : ServiceWorker() {
        val started = CompletableDeferred<Unit>()
        val actionTriggered = CompletableDeferred<String>()

        override fun onStart() {
            notificationTitle = "Initial Worker"
            notificationText = "Starting"
            extendNotificationActions(
                ServiceWorkerNotificationAction(id = "refresh", label = "Refresh") {
                    if (!actionTriggered.isCompleted) {
                        actionTriggered.complete("refresh")
                    }
                }
            )
            onRequest<EchoReq> { _, req, reply ->
                reply.send(EchoResp(req.value.uppercase()))
            }
            if (!started.isCompleted) {
                started.complete(Unit)
            }
        }
    }

    private class TestAndroidSwService : Service() {
        override fun onCreate() {
            super.onCreate()
            val worker = activeTestWorker ?: RoboSampleWorker()
            AndroidServiceWorkerController.onServiceCreated(this) { worker }
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
            AndroidServiceWorkerController.onServiceStartCommand(this, intent, flags, startId)

        override fun onBind(intent: Intent?): IBinder =
            AndroidServiceWorkerController.onServiceBind(this, intent)

        override fun onDestroy() {
            AndroidServiceWorkerController.onServiceDestroyed(this)
            super.onDestroy()
        }

        companion object {
            var activeTestWorker: RoboSampleWorker? = null
        }
    }

    @BeforeTest
    fun setUp() {
        AndroidServiceWorkerController.resetForTests()
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<EchoReq> {
                override val typeId: String = "androidtest.EchoReq"
                override val messageClass = EchoReq::class
                override fun encode(value: EchoReq): PreferenceElement =
                    PreferenceElement.ofObject(mapOf("value" to PreferenceElement.ofString(value.value)))

                override fun decode(element: PreferenceElement): EchoReq =
                    EchoReq(element.mapEntries["value"]?.asString().orEmpty())
            }
        )
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<EchoResp> {
                override val typeId: String = "androidtest.EchoResp"
                override val messageClass = EchoResp::class
                override fun encode(value: EchoResp): PreferenceElement =
                    PreferenceElement.ofObject(mapOf("echoed" to PreferenceElement.ofString(value.echoed)))

                override fun decode(element: PreferenceElement): EchoResp =
                    EchoResp(element.mapEntries["echoed"]?.asString().orEmpty())
            }
        )
    }

    @AfterTest
    fun tearDown() {
        TestAndroidSwService.activeTestWorker = null
        AndroidServiceWorkerController.resetForTests()
        FragmentRegistry.clear()
        ServiceWorkerCodecRegistry.unregister("androidtest.EchoReq")
        ServiceWorkerCodecRegistry.unregister("androidtest.EchoResp")
    }

    @Test
    fun androidForegroundServiceCreatesChannelPostsActionsAndHandlesBinderIpcAndStopIntent() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        AndroidExtensionRuntimeBridge.notifyApplicationCreate(app)
        val options = ServiceWorkerOptions(
            appId = app.packageName,
            appName = "RoboWorker",
            autostart = true,
            androidForegroundServiceType = "dataSync",
            hasWorker = true
        )
        ServiceWorkerExtension.install(options)
        val worker = RoboSampleWorker()
        TestAndroidSwService.activeTestWorker = worker
        ServiceWorkerExtension.registerWorkerFactory { worker }

        assertTrue(ServiceWorkerExtension.isSupported)
        assertTrue(ServiceWorkerExtension.supportsAutostart)
        assertTrue(ServiceWorkerExtension.supportsOngoingNotification)
        assertTrue(ServiceWorkerExtension.supportsFragmentLaunch)
        assertTrue(ServiceWorkerExtension.supportsDialogFragments)
        assertFalse(ServiceWorkerExtension.supportsSystemTray)
        assertFalse(ServiceWorkerExtension.supportsMultipleWindows)

        val controller = Robolectric.buildService(TestAndroidSwService::class.java)
        val service = controller.create().get()
        try {
            withTimeout(5_000) { worker.started.await() }

            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = nm.getNotificationChannel(AndroidServiceWorkerController.resolveChannelId(service))
            assertNotNull(channel, "Foreground service notification channel must be registered")

            val fgNotif = shadowOf(service).lastForegroundNotification
            assertNotNull(fgNotif, "Service must call startForeground")
            assertEquals("RoboWorker", fgNotif.extras.getString("android.title"))

            val startedNotif = AndroidServiceWorkerController.lastPostedNotification()
            assertNotNull(startedNotif)
            assertEquals("Initial Worker", startedNotif.extras.getString("android.title"))
            assertEquals("Starting", startedNotif.extras.getString("android.text"))
            assertEquals(2, startedNotif.actions?.size)

            // Live title/text updates refresh notification cleanly
            worker.setNotificationTitle("Updated Title")
            worker.setNotificationText("Processing step 2")
            val updatedNotif = AndroidServiceWorkerController.lastPostedNotification()
            assertNotNull(updatedNotif)
            assertEquals("Updated Title", updatedNotif.extras.getString("android.title"))
            assertEquals("Processing step 2", updatedNotif.extras.getString("android.text"))
            assertEquals(2, updatedNotif.actions?.size)

            // Binder IPC round-trip via ServiceWorkerExtension.channel
            val binder = service.onBind(Intent(app, TestAndroidSwService::class.java))
            assertIs<AndroidServiceWorkerBinder>(binder)
            assertIs<AndroidServiceIpcBinder>(binder)
            assertTrue(ServiceWorkerExtension.channel.connect())
            val resp = ServiceWorkerExtension.channel.request<EchoResp>(EchoReq("hello android"), timeoutMs = 5_000)
            assertEquals("HELLO ANDROID", resp.echoed)
            assertTrue(binder.callbackCount() >= 1)

            // Notification button intent dispatches custom worker action
            val customActionIntent = Intent(app, TestAndroidSwService::class.java).apply {
                action = AndroidServiceWorkerController.ACTION_NOTIFICATION_BUTTON
                putExtra(AndroidServiceWorkerController.EXTRA_ACTION_ID, "refresh")
            }
            service.onStartCommand(customActionIntent, 0, 1)
            assertEquals("refresh", withTimeout(5_000) { worker.actionTriggered.await() })

            // Default Stop action stops the service cleanly
            val stopIntent = Intent(app, TestAndroidSwService::class.java).apply {
                action = AndroidServiceWorkerController.ACTION_STOP_SERVICE
                putExtra(
                    AndroidServiceWorkerController.EXTRA_ACTION_ID,
                    ServiceWorkerNotificationAction.DEFAULT_STOP_ACTION_ID
                )
            }
            service.onStartCommand(stopIntent, 0, 2)
            withTimeout(5_000) {
                while (!shadowOf(service).isStoppedBySelf) {
                    kotlinx.coroutines.delay(10)
                }
            }
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
        assertNull(AndroidServiceWorkerController.activeWorker())
    }

    @Test
    fun androidAutostartBootReceiverRespectsUserPreferenceToggle() {
        val app = RuntimeEnvironment.getApplication()
        AndroidExtensionRuntimeBridge.notifyApplicationCreate(app)
        val options = ServiceWorkerOptions(
            appId = app.packageName,
            appName = "RoboWorker",
            autostart = false,
            hasWorker = true
        )
        ServiceWorkerExtension.install(options)
        assertFalse(ServiceWorkerExtension.isAutostartEnabled())

        shadowOf(app).clearStartedServices()
        AndroidServiceWorkerController.onBootCompleted(app, TestAndroidSwService::class.java)
        assertNull(shadowOf(app).nextStartedService, "Boot completed must NOT start service when autostart is disabled")

        assertTrue(ServiceWorkerExtension.setAutostartEnabled(true))
        assertTrue(ServiceWorkerExtension.isAutostartEnabled())
        AndroidServiceWorkerController.onBootCompleted(app, TestAndroidSwService::class.java)
        val startedIntent = shadowOf(app).nextStartedService
        assertNotNull(startedIntent, "Boot completed must start foreground service when autostart is enabled")
        assertEquals(TestAndroidSwService::class.java.name, startedIntent.component?.className)
    }

    @Test
    fun androidWorkerRejectsSystemTrayAndUnbackedFragmentLaunch() {
        val app = RuntimeEnvironment.getApplication()
        AndroidExtensionRuntimeBridge.notifyApplicationCreate(app)
        FragmentRegistry.registerMetadata(
            fragmentClass = RoboMissingActivityFragment::class,
            fragmentId = "com.test.RoboMissingActivityFragment",
            route = "/missing"
        )
        val worker = RoboSampleWorker()
        worker.attachAndStart(AndroidServiceWorkerController.runtimeHost, ServiceWorkerOptions(appId = app.packageName))
        try {
            assertFailsWith<SystemTrayUnsupportedException> {
                worker.setTrayTooltip("Unsupported on Android")
            }
            assertFailsWith<ServiceWorkerFragmentLaunchUnsupportedException> {
                worker.startFragment<RoboMissingActivityFragment>()
            }
        } finally {
            worker.performStop()
        }
    }

    @Test
    fun androidBinderChunkedLargePacketAndBundleCodecRoundTripCleanly() {
        val app = RuntimeEnvironment.getApplication()
        val binder = AndroidServiceWorkerBinder(controller = AndroidServiceWorkerController) { incoming ->
            IpcPacket(
                id = incoming.id,
                type = incoming.type,
                channel = incoming.channel,
                payload = incoming.payload,
                replyToId = incoming.id
            )
        }
        val ipcClient = AndroidServiceIpcClient(app)
        try {
            assertTrue(ipcClient.attachBinder(binder))
            assertTrue(ipcClient.ping())

            val largeString = "B".repeat(AndroidServiceIpcProtocol.MAX_DIRECT_PARCEL_BYTES + 64 * 1024)
            val largePacket = IpcPacket(
                id = 77L,
                type = "androidtest.LargeChunk",
                channel = "android-test",
                payload = PreferenceElement.ofString(largeString)
            )
            val replyPacket = ipcClient.requestPacket(largePacket)
            assertNotNull(replyPacket)
            assertEquals(77L, replyPacket.replyToId)
            assertEquals(largeString, replyPacket.payload.asString())

            val bundle = Bundle()
            AndroidServiceIpcBundleCodec.writeToBundle(bundle, largePacket)
            val decodedFromBundle = AndroidServiceIpcBundleCodec.readFromBundle(bundle)
            assertNotNull(decodedFromBundle)
            assertEquals(largePacket.id, decodedFromBundle.id)
            assertEquals(largePacket.type, decodedFromBundle.type)
            assertEquals(largeString, decodedFromBundle.payload.asString())
        } finally {
            ipcClient.unbind()
        }
    }
}
