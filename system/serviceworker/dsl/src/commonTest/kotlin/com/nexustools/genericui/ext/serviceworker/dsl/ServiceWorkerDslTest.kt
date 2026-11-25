package com.nexustools.genericui.ext.serviceworker.dsl

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.exception.SystemTrayUnsupportedException
import com.nexustools.genericui.ext.serviceworker.ServiceWorker
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerCodecRegistry
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerEnvelope
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerFragmentLaunchRequest
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessageCodec
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerNotificationAction
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerOngoingNotificationUnsupportedException
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerOptions
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerRuntimeHost
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServiceWorkerDslTest {

    data class PingPayload(val value: String)
    data class AddRequest(val a: Int, val b: Int)
    data class AddResponse(val sum: Int)

    private class DslHost(
        override val backendName: String,
        override val supportsSystemTray: Boolean,
        override val supportsOngoingNotification: Boolean
    ) : ServiceWorkerRuntimeHost {
        override val supportsMultipleWindows: Boolean = supportsSystemTray
        override val supportsFragmentLaunch: Boolean = true
        override val supportsDialogFragments: Boolean = true
        override val supportsAutostart: Boolean = false
        override fun updateNotification(
            title: String,
            text: String,
            subText: String?,
            icon: ResourceID?,
            actions: List<ServiceWorkerNotificationAction>
        ) = Unit

        override fun updateTray(
            iconPathOrUrl: String?,
            iconResource: ResourceID?,
            tooltip: String?,
            badgeText: String?,
            menu: ContextMenu?,
            onClick: (() -> Unit)?,
            macosIconPathOrUrl: String?
        ) = Unit

        override fun openMainWindow(route: String?) = Unit
        override fun closeMainWindow() = Unit
        override fun requestLaunchFragment(request: ServiceWorkerFragmentLaunchRequest) = Unit
        override fun requestCloseFragment(fragmentId: String, instanceId: String?) = Unit
        override fun stopSelf() = Unit
        override fun exitApplication(exitCode: Int) = Unit
        override fun isAutostartEnabled(): Boolean = false
        override fun setAutostartEnabled(enabled: Boolean): Boolean = false
        override suspend fun sendToClient(clientId: String, envelope: ServiceWorkerEnvelope): Boolean = false
        override suspend fun broadcastEnvelope(envelope: ServiceWorkerEnvelope): Int = 0
        override fun disconnectClient(clientId: String) = Unit
    }

    private class TestWorker : ServiceWorker() {
        val receivedPings = mutableListOf<String>()
        var customActionClicks = 0
        var stopActionClicks = 0

        override fun onStart() {
            if (supportsOngoingNotification) {
                notificationTitle = "Sync Active"
                notificationText = "Idle"
                notificationActions {
                    action("sync_now", "Sync Now") {
                        customActionClicks++
                    }
                    stopAction("Stop Sync") {
                        stopActionClicks++
                    }
                }
            }

            if (supportsSystemTray) {
                trayMenu {
                    item("Open App") {}
                    separator()
                    item("Status") {}
                }
            }

            onMessage<PingPayload> { _, msg ->
                receivedPings.add(msg.value)
            }

            onRequest<AddRequest> { _, req, reply ->
                reply.send(AddResponse(req.a + req.b))
            }
        }
    }

    private fun registerCodecs() {
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<PingPayload> {
                override val typeId: String = "dsltest.PingPayload"
                override val messageClass = PingPayload::class
                override fun encode(value: PingPayload): PreferenceElement =
                    PreferenceElement.ofObject(mapOf("value" to PreferenceElement.ofString(value.value)))

                override fun decode(element: PreferenceElement): PingPayload =
                    PingPayload(element.mapEntries["value"]?.asString().orEmpty())
            }
        )
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<AddRequest> {
                override val typeId: String = "dsltest.AddRequest"
                override val messageClass = AddRequest::class
                override fun encode(value: AddRequest): PreferenceElement =
                    PreferenceElement.ofObject(
                        mapOf(
                            "a" to PreferenceElement.ofInt(value.a),
                            "b" to PreferenceElement.ofInt(value.b)
                        )
                    )

                override fun decode(element: PreferenceElement): AddRequest =
                    AddRequest(
                        element.mapEntries["a"]?.asInt() ?: 0,
                        element.mapEntries["b"]?.asInt() ?: 0
                    )
            }
        )
        ServiceWorkerCodecRegistry.register(
            object : ServiceWorkerMessageCodec<AddResponse> {
                override val typeId: String = "dsltest.AddResponse"
                override val messageClass = AddResponse::class
                override fun encode(value: AddResponse): PreferenceElement =
                    PreferenceElement.ofObject(mapOf("sum" to PreferenceElement.ofInt(value.sum)))

                override fun decode(element: PreferenceElement): AddResponse =
                    AddResponse(element.mapEntries["sum"]?.asInt() ?: 0)
            }
        )
    }

    @AfterTest
    fun tearDown() {
        ServiceWorkerCodecRegistry.unregister("dsltest.PingPayload")
        ServiceWorkerCodecRegistry.unregister("dsltest.AddRequest")
        ServiceWorkerCodecRegistry.unregister("dsltest.AddResponse")
    }

    @Test
    fun dslTrayMenuAndNotificationActionsConfigureWorkerCleanly() {
        registerCodecs()
        val trayWorker = TestWorker()
        trayWorker.attachAndStart(
            DslHost(backendName = "Swing", supportsSystemTray = true, supportsOngoingNotification = false),
            ServiceWorkerOptions()
        )
        try {
            assertFailsWith<ServiceWorkerOngoingNotificationUnsupportedException> {
                trayWorker.setNotificationTitle("Sync Active")
            }

            val iconRes = ResourceID(0x7f010001, ResourceType.DRAWABLE, "ic_open", "app/drawable/ic_open.png")
            val menu: ContextMenu = trayWorker.trayMenu {
                item("Open App", iconRes) {}
                separator()
                item("Status") {}
            }
            assertEquals(3, menu.children.size)
            assertSame(menu, trayWorker.getEffectiveTrayMenu())
        } finally {
            trayWorker.performStop()
        }

        val androidWorker = TestWorker()
        androidWorker.attachAndStart(
            DslHost(backendName = "Android", supportsSystemTray = false, supportsOngoingNotification = true),
            ServiceWorkerOptions()
        )
        try {
            assertFailsWith<SystemTrayUnsupportedException> {
                androidWorker.trayMenu {
                    item("Unsupported") {}
                }
            }

            androidWorker.setNotificationTitle("Sync Active")
            androidWorker.setNotificationText("Idle")
            assertEquals("Sync Active", androidWorker.notificationTitle)
            assertEquals("Idle", androidWorker.notificationText)

            val effective = androidWorker.notificationActions(replaceDefaultStop = true) {
                action("sync_now", "Sync Now") { androidWorker.customActionClicks++ }
                stopAction("Stop Sync") { androidWorker.stopActionClicks++ }
            }
            assertEquals(2, effective.size)
            assertEquals("sync_now", effective[0].id)
            assertEquals("Sync Now", effective[0].label)
            assertFalse(effective[0].triggersDefaultStop)
            assertEquals(ServiceWorkerNotificationAction.DEFAULT_STOP_ACTION_ID, effective[1].id)
            assertEquals("Stop Sync", effective[1].label)
            assertTrue(effective[1].triggersDefaultStop)

            effective[0].onClick.invoke()
            effective[1].onClick.invoke()
            assertEquals(1, androidWorker.customActionClicks)
            assertEquals(1, androidWorker.stopActionClicks)
        } finally {
            androidWorker.performStop()
        }
    }
}
