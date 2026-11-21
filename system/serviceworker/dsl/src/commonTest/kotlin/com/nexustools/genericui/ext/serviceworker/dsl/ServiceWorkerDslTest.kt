package com.nexustools.genericui.ext.serviceworker.dsl

import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.ext.serviceworker.ServiceWorker
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerCodecRegistry
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerMessageCodec
import com.nexustools.genericui.ext.serviceworker.ServiceWorkerNotificationAction
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServiceWorkerDslTest {

    data class PingPayload(val value: String)
    data class AddRequest(val a: Int, val b: Int)
    data class AddResponse(val sum: Int)

    private class TestWorker : ServiceWorker() {
        val receivedPings = mutableListOf<String>()
        var customActionClicks = 0
        var stopActionClicks = 0

        override fun onStart() {
            notificationTitle = "Sync Active"
            notificationText = "Idle"

            trayMenu {
                item("Open App") {}
                separator()
                item("Status") {}
            }

            notificationActions {
                action("sync_now", "Sync Now") {
                    customActionClicks++
                }
                stopAction("Stop Sync") {
                    stopActionClicks++
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
        val worker = TestWorker()

        worker.setNotificationTitle("Sync Active")
        worker.setNotificationText("Idle")
        assertEquals("Sync Active", worker.notificationTitle)
        assertEquals("Idle", worker.notificationText)

        val iconRes = ResourceID(0x7f010001, ResourceType.DRAWABLE, "ic_open", "app/drawable/ic_open.png")
        val menu: ContextMenu = worker.trayMenu {
            item("Open App", iconRes) {}
            separator()
            item("Status") {}
        }
        assertEquals(3, menu.children.size)
        assertSame(menu, worker.getEffectiveTrayMenu())

        val effective = worker.notificationActions {
            action("sync_now", "Sync Now") { worker.customActionClicks++ }
            stopAction("Stop Sync") { worker.stopActionClicks++ }
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
        assertEquals(1, worker.customActionClicks)
        assertEquals(1, worker.stopActionClicks)
    }
}
