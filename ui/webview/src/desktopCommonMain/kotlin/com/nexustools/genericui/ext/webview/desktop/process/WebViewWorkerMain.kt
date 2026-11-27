@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.process

import com.nexustools.genericui.ext.alien.AlienDirtyTile
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.canvas2d.alien.Canvas2DAlienExtension
import com.nexustools.genericui.ext.canvas3d.alien.Canvas3DAlienExtension
import com.nexustools.genericui.ext.ipc.IpcChannelAddress
import com.nexustools.genericui.ext.ipc.IpcClient
import com.nexustools.genericui.ext.ipc.IpcConfig
import com.nexustools.genericui.ext.ipc.IpcConnection
import com.nexustools.genericui.ext.ipc.IpcExtension
import com.nexustools.genericui.ext.ipc.IpcPacket
import com.nexustools.genericui.ext.media.alien.MediaAlienExtension
import com.nexustools.genericui.ext.svg.alien.SvgAlienExtension
import com.nexustools.genericui.ext.webview.desktop.NjsPlatformCompilerBootstrap
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewIpcPacketTypes
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewResolvedCapabilities
import com.nexustools.genericui.ext.webview.desktop.ipc.WebViewWorkerLauncher
import com.nexustools.genericui.ext.webview.desktop.njs.WebViewWorkerRuntime
import com.nexustools.genericui.component.system.PreferenceElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.jvm.JvmStatic

/**
 * Isolated OS worker process entry point shared by Native Desktop (`webviewProcess` executable)
 * and JVM Swing (`java -cp <classpath> WebViewWorkerMain`).
 *
 * Registers IPC + Alien + companion extensions (`svg`, `canvas2d`, `canvas3d`, `media`), connects
 * over `:extensions:system:ipc` BinaryJson, executes commands single-threadedly inside a dedicated
 * [WebViewWorkerRuntime], and streams dirty RGBA8888 viewport tiles directly as raw binary chunks.
 */
object WebViewWorkerMain {
    private const val IPC_CONNECT_TIMEOUT_MS: Int = 15_000
    private const val IPC_MAX_PACKET_BYTES: Int = 32 * 1024 * 1024
    private const val BRIDGE_RPC_TIMEOUT_MS: Long = 10_000L

    @JvmStatic
    fun main(args: Array<String>) {
        var ipcKind = ""
        var ipcEndpoint = ""
        var webViewId = ""
        for (arg in args) {
            when {
                arg.startsWith("--ipc-kind=") -> ipcKind = arg.substringAfter("--ipc-kind=")
                arg.startsWith("--ipc-endpoint=") -> ipcEndpoint = arg.substringAfter("--ipc-endpoint=")
                arg.startsWith("--webview-id=") -> webViewId = arg.substringAfter("--webview-id=")
            }
        }

        if (ipcKind.isBlank() || ipcEndpoint.isBlank()) {
            throw IllegalArgumentException(
                "WebViewWorkerMain requires --ipc-kind and --ipc-endpoint CLI arguments"
            )
        }

        IpcExtension.registerProviders()
        SvgAlienExtension.register()
        Canvas2DAlienExtension.register()
        Canvas3DAlienExtension.register()
        MediaAlienExtension.register()
        AlienExtension.useSystemCanvasRenderer()
        NjsPlatformCompilerBootstrap.ensureInitialized()

        val address = IpcChannelAddress(ipcKind, ipcEndpoint)
        val connection = IpcClient.connect(
            address = address,
            timeoutMs = IPC_CONNECT_TIMEOUT_MS,
            config = IpcConfig(
                connectTimeoutMs = IPC_CONNECT_TIMEOUT_MS,
                maxPacketBytes = IPC_MAX_PACKET_BYTES,
                pipeBufferSizeBytes = 65_536
            )
        ) ?: throw IllegalStateException(
            "WebView worker '$webViewId' failed to connect to host IPC endpoint $address"
        )

        val workerDaemon = WorkerSessionDaemon(connection, webViewId)
        workerDaemon.runUntilShutdown()
    }

    private class WorkerSessionDaemon(
        private val connection: IpcConnection,
        private val webViewId: String
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val queueLock = AtomicInt(0)
        private val pendingLock = AtomicInt(0)
        private val sendLock = AtomicInt(0)
        private val inboundCommands = ArrayList<IpcPacket>()
        private val pendingBridgeReplies = LinkedHashMap<Long, CompletableDeferred<IpcPacket>>()
        private val nextPacketId = AtomicLong(1L)
        private val shutdownFlag = AtomicBoolean(false)
        private val runtimeRef = AtomicReference<WebViewWorkerRuntime?>(null)

        private var runtime: WebViewWorkerRuntime? = null
        private var activeCommandId: Long? = null
        private var bufferedPageFinished: Triple<String, Boolean, Boolean>? = null
        private var lastReportedTitle: String = ""
        private var lastReportedFaviconUrl: String? = null
        private var hasReportedFaviconUrl: Boolean = false

        fun runUntilShutdown() {
            val engineName = "AlienRenderSession+${NjsPlatformCompilerBootstrap.activeEngineName()}"
            sendEvent(
                WebViewIpcPacketTypes.EVT_READY,
                PreferenceElement.ofObject(
                    linkedMapOf(
                        "pid" to PreferenceElement.ofLong(WebViewWorkerLauncher.currentOsProcessId()),
                        "webViewId" to PreferenceElement.ofString(webViewId),
                        "engineName" to PreferenceElement.ofString(engineName)
                    )
                )
            )

            val readerJob = scope.launch {
                try {
                    while (!shutdownFlag.load() && !connection.isClosed) {
                        val packet = connection.receivePacket(timeoutMs = 150) ?: continue
                        when (packet.type) {
                            WebViewIpcPacketTypes.RES_BRIDGE_CALL -> {
                                val replyTo = packet.replyToId ?: continue
                                val waiter = withPendingLock { pendingBridgeReplies.remove(replyTo) }
                                waiter?.complete(packet)
                            }
                            WebViewIpcPacketTypes.CMD_STOP_LOADING -> {
                                runtimeRef.load()?.stopLoading()
                                sendOk(packet, PreferenceElement.Null)
                            }
                            else -> {
                                withQueueLock { inboundCommands.add(packet) }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    if (!shutdownFlag.load()) {
                        sendEvent(
                            WebViewIpcPacketTypes.EVT_JS_ERROR,
                            PreferenceElement.ofObject(
                                mapOf("error" to PreferenceElement.ofString("Worker IPC read error: ${t.message}"))
                            )
                        )
                    }
                } finally {
                    failAllBridgeWaiters(IllegalStateException("Worker IPC reader terminated"))
                    shutdownFlag.store(true)
                }
            }

            try {
                while (!shutdownFlag.load() && !connection.isClosed) {
                    val cmd = withQueueLock {
                        if (inboundCommands.isNotEmpty()) inboundCommands.removeAt(0) else null
                    }
                    if (cmd != null) {
                        handleCommandPacket(cmd)
                        continue
                    }
                    val rt = runtime
                    if (rt != null && rt.needsPeriodicTick()) {
                        runBlocking { delay(16L) }
                        activeCommandId = 0L
                        bufferedPageFinished = null
                        val dirty = try {
                            rt.stepPeriodicTick(null)
                        } catch (t: Throwable) {
                            emitJsError("Worker tick error: ${t.message ?: t::class.simpleName}")
                            emptyList()
                        } finally {
                            activeCommandId = null
                        }
                        val pendingNav = bufferedPageFinished
                        bufferedPageFinished = null
                        if (dirty.isNotEmpty()) {
                            emitDirtyFrameTiles(rt, dirty)
                        }
                        emitTitleChangedIfNeeded(rt)
                        emitFaviconChangedIfNeeded(rt)
                        if (pendingNav != null) {
                            emitPageFinishedEvent(commandId = 0L, rt = rt, finished = pendingNav)
                        }
                    } else {
                        runBlocking { delay(10L) }
                    }
                }
            } finally {
                shutdownFlag.store(true)
                runtimeRef.store(null)
                runtime?.dispose()
                runtime = null
                connection.close()
                runBlocking { withTimeoutOrNull(1_000L) { readerJob.join() } }
                scope.cancel()
            }
        }

        private fun handleCommandPacket(cmd: IpcPacket) {
            try {
                when (cmd.type) {
                    WebViewIpcPacketTypes.CMD_INIT -> {
                        val width = (cmd.payload["width"]?.asInt(1024) ?: 1024).coerceAtLeast(1)
                        val height = (cmd.payload["height"]?.asInt(768) ?: 768).coerceAtLeast(1)
                        val enableJs = cmd.payload["enableJavaScript"]?.asBoolean(true) ?: true
                        val allowFileAccess = cmd.payload["allowFileAccess"]?.asBoolean(false) ?: false
                        val uaOverride = cmd.payload["userAgentOverride"]?.asString()
                        val caps = WebViewResolvedCapabilities.fromPreferenceElement(cmd.payload["capabilities"])

                        runtime?.dispose()
                        val rt = WebViewWorkerRuntime(
                            initialWidth = width,
                            initialHeight = height,
                            enableJavaScript = enableJs,
                            allowFileAccess = allowFileAccess,
                            capabilities = caps,
                            userAgentOverride = uaOverride,
                            onConsoleMessage = { lvl, msg, srcId, line ->
                                sendEvent(
                                    WebViewIpcPacketTypes.EVT_CONSOLE_MESSAGE,
                                    PreferenceElement.ofObject(
                                        linkedMapOf(
                                            "level" to PreferenceElement.ofString(lvl),
                                            "message" to PreferenceElement.ofString(msg),
                                            "sourceId" to PreferenceElement.ofString(srcId),
                                            "line" to PreferenceElement.ofInt(line)
                                        )
                                    )
                                )
                            },
                            onJavaScriptError = { err -> emitJsError(err) },
                            onPageFinished = { url, canBack, canFwd ->
                                if (activeCommandId != null) {
                                    bufferedPageFinished = Triple(url, canBack, canFwd)
                                } else {
                                    sendEvent(
                                        WebViewIpcPacketTypes.EVT_PAGE_FINISHED,
                                        PreferenceElement.ofObject(
                                            linkedMapOf(
                                                "url" to PreferenceElement.ofString(url),
                                                "title" to PreferenceElement.ofString(runtime?.currentTitle.orEmpty()),
                                                "faviconUrl" to faviconPreferenceElement(runtime?.currentFaviconUrl),
                                                "canGoBack" to PreferenceElement.ofBoolean(canBack),
                                                "canGoForward" to PreferenceElement.ofBoolean(canFwd)
                                            )
                                        )
                                    )
                                }
                            },
                            onWebMessageToHost = { msg ->
                                sendEvent(
                                    WebViewIpcPacketTypes.EVT_WEB_MESSAGE,
                                    PreferenceElement.ofObject(mapOf("message" to PreferenceElement.ofString(msg)))
                                )
                            },
                            onHostBridgeCall = { name, payloadJson -> callHostBridgeSynchronously(name, payloadJson) }
                        )
                        rt.onProgressiveFrame = { progTiles ->
                            if (progTiles.isNotEmpty()) {
                                emitDirtyFrameTiles(rt, progTiles)
                            }
                        }
                        rt.onFaviconChanged = { _ ->
                            if (activeCommandId == null) {
                                emitFaviconChangedIfNeeded(rt)
                            }
                        }
                        runtime = rt
                        runtimeRef.store(rt)
                        val initialTiles = rt.renderDirtyTiles()
                        sendOk(cmd, buildTilesPayload(rt, initialTiles))
                    }
                    WebViewIpcPacketTypes.CMD_LOAD_URL -> {
                        val rt = requireRuntime()
                        val url = cmd.payload["url"]?.asString().orEmpty()
                        executeNavCommand(cmd, rt) { rt.loadUrl(url) }
                    }
                    WebViewIpcPacketTypes.CMD_LOAD_HTML -> {
                        val rt = requireRuntime()
                        val html = cmd.payload["html"]?.asString().orEmpty()
                        val baseUrl = cmd.payload["baseUrl"]?.asString()
                        executeNavCommand(cmd, rt) { rt.loadHtml(html, baseUrl) }
                    }
                    WebViewIpcPacketTypes.CMD_RELOAD -> {
                        val rt = requireRuntime()
                        executeNavCommand(cmd, rt) { rt.reload() }
                    }
                    WebViewIpcPacketTypes.CMD_STOP_LOADING -> {
                        runtime?.stopLoading()
                        sendOk(cmd, PreferenceElement.Null)
                    }
                    WebViewIpcPacketTypes.CMD_GO_BACK -> {
                        val rt = requireRuntime()
                        executeNavCommand(cmd, rt) { rt.goBack() }
                    }
                    WebViewIpcPacketTypes.CMD_GO_FORWARD -> {
                        val rt = requireRuntime()
                        executeNavCommand(cmd, rt) { rt.goForward() }
                    }
                    WebViewIpcPacketTypes.CMD_EVALUATE_JS -> {
                        val rt = requireRuntime()
                        val script = cmd.payload["script"]?.asString().orEmpty()
                        val (evalResult, dirty, pendingNav) = executeCapturingNav(cmd) {
                            val res = rt.evaluateJavaScript(script)
                            val tiles = rt.renderDirtyTiles()
                            res to tiles
                        }
                        val (hasResult, resultStr) = evalResult
                        if (dirty.isNotEmpty()) emitDirtyFrameTiles(rt, dirty)
                        sendOk(
                            cmd,
                            PreferenceElement.ofObject(
                                linkedMapOf(
                                    "hasResult" to PreferenceElement.ofBoolean(hasResult),
                                    "result" to (resultStr?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null),
                                    "commandId" to PreferenceElement.ofLong(cmd.id),
                                    "url" to PreferenceElement.ofString(rt.currentUrl),
                                    "title" to PreferenceElement.ofString(rt.currentTitle),
                                    "faviconUrl" to faviconPreferenceElement(rt.currentFaviconUrl),
                                    "canGoBack" to PreferenceElement.ofBoolean(rt.canGoBack),
                                    "canGoForward" to PreferenceElement.ofBoolean(rt.canGoForward),
                                    "pageFinished" to PreferenceElement.ofBoolean(pendingNav != null),
                                    "pageFinishedUrl" to PreferenceElement.ofString(pendingNav?.first ?: rt.currentUrl)
                                )
                            )
                        )
                        lastReportedFaviconUrl = rt.currentFaviconUrl
                        hasReportedFaviconUrl = true
                        if (pendingNav != null) {
                            emitTitleChangedIfNeeded(rt)
                            emitPageFinishedEvent(cmd.id, rt, pendingNav)
                        } else {
                            emitTitleChangedIfNeeded(rt)
                        }
                    }
                    WebViewIpcPacketTypes.CMD_CALL_COMM_FN -> {
                        val rt = requireRuntime()
                        val name = cmd.payload["name"]?.asString().orEmpty()
                        val payloadJson = cmd.payload["payloadJson"]?.asString().orEmpty()
                        val (callResult, dirty, pendingNav) = executeCapturingNav(cmd) {
                            val res = rt.callGuestCommunicationFunction(name, payloadJson)
                            val tiles = rt.renderDirtyTiles()
                            res to tiles
                        }
                        val (hasResult, resultStr) = callResult
                        if (dirty.isNotEmpty()) emitDirtyFrameTiles(rt, dirty)
                        sendOk(
                            cmd,
                            PreferenceElement.ofObject(
                                linkedMapOf(
                                    "hasResult" to PreferenceElement.ofBoolean(hasResult),
                                    "result" to (resultStr?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null),
                                    "commandId" to PreferenceElement.ofLong(cmd.id),
                                    "url" to PreferenceElement.ofString(rt.currentUrl),
                                    "title" to PreferenceElement.ofString(rt.currentTitle),
                                    "faviconUrl" to faviconPreferenceElement(rt.currentFaviconUrl),
                                    "canGoBack" to PreferenceElement.ofBoolean(rt.canGoBack),
                                    "canGoForward" to PreferenceElement.ofBoolean(rt.canGoForward),
                                    "pageFinished" to PreferenceElement.ofBoolean(pendingNav != null),
                                    "pageFinishedUrl" to PreferenceElement.ofString(pendingNav?.first ?: rt.currentUrl)
                                )
                            )
                        )
                        lastReportedFaviconUrl = rt.currentFaviconUrl
                        hasReportedFaviconUrl = true
                        if (pendingNav != null) {
                            emitTitleChangedIfNeeded(rt)
                            emitPageFinishedEvent(cmd.id, rt, pendingNav)
                        } else {
                            emitTitleChangedIfNeeded(rt)
                        }
                    }
                    WebViewIpcPacketTypes.CMD_POST_MESSAGE -> {
                        val rt = requireRuntime()
                        val msg = cmd.payload["message"]?.asString().orEmpty()
                        executeActionWithOptionalNav(cmd, rt) {
                            rt.deliverHostMessage(msg)
                            rt.renderDirtyTiles()
                        }
                    }
                    WebViewIpcPacketTypes.CMD_REGISTER_BRIDGE_FN -> {
                        runtime?.registerHostBridgeName(cmd.payload["name"]?.asString().orEmpty())
                        sendOk(cmd, PreferenceElement.Null)
                    }
                    WebViewIpcPacketTypes.CMD_UNREGISTER_BRIDGE_FN -> {
                        runtime?.unregisterHostBridgeName(cmd.payload["name"]?.asString().orEmpty())
                        sendOk(cmd, PreferenceElement.Null)
                    }
                    WebViewIpcPacketTypes.CMD_RESIZE -> {
                        val rt = requireRuntime()
                        val w = cmd.payload["width"]?.asInt(rt.viewportWidth) ?: rt.viewportWidth
                        val h = cmd.payload["height"]?.asInt(rt.viewportHeight) ?: rt.viewportHeight
                        val tiles = rt.resizeViewport(w, h)
                        sendOk(cmd, buildTilesPayload(rt, tiles))
                    }
                    WebViewIpcPacketTypes.CMD_POINTER_EVENT -> {
                        val rt = requireRuntime()
                        val kind = cmd.payload["kind"]?.asString().orEmpty()
                        val x = (cmd.payload["x"]?.asDouble(0.0) ?: 0.0).toFloat()
                        val y = (cmd.payload["y"]?.asDouble(0.0) ?: 0.0).toFloat()
                        val button = cmd.payload["button"]?.asInt(0) ?: 0
                        executeActionWithOptionalNav(cmd, rt) { rt.handlePointerEvent(kind, x, y, button) }
                    }
                    WebViewIpcPacketTypes.CMD_WHEEL_EVENT -> {
                        val rt = requireRuntime()
                        val x = (cmd.payload["x"]?.asDouble(0.0) ?: 0.0).toFloat()
                        val y = (cmd.payload["y"]?.asDouble(0.0) ?: 0.0).toFloat()
                        val dx = (cmd.payload["deltaX"]?.asDouble(0.0) ?: 0.0).toFloat()
                        val dy = (cmd.payload["deltaY"]?.asDouble(0.0) ?: 0.0).toFloat()
                        executeActionWithOptionalNav(cmd, rt) { rt.handleWheelEvent(x, y, dx, dy) }
                    }
                    WebViewIpcPacketTypes.CMD_KEY_EVENT -> {
                        val rt = requireRuntime()
                        executeActionWithOptionalNav(cmd, rt) {
                            rt.handleKeyEvent(
                                kind = cmd.payload["kind"]?.asString().orEmpty(),
                                key = cmd.payload["key"]?.asString().orEmpty(),
                                code = cmd.payload["code"]?.asString().orEmpty(),
                                keyCode = cmd.payload["keyCode"]?.asInt(0) ?: 0,
                                shift = cmd.payload["shift"]?.asBoolean(false) ?: false,
                                ctrl = cmd.payload["ctrl"]?.asBoolean(false) ?: false,
                                alt = cmd.payload["alt"]?.asBoolean(false) ?: false,
                                meta = cmd.payload["meta"]?.asBoolean(false) ?: false,
                                text = cmd.payload["text"]?.asString().orEmpty()
                            )
                        }
                    }
                    WebViewIpcPacketTypes.CMD_FOCUS_EVENT -> {
                        val rt = requireRuntime()
                        val focused = cmd.payload["focused"]?.asBoolean(true) ?: true
                        executeActionWithOptionalNav(cmd, rt) { rt.handleFocusEvent(focused) }
                    }
                    WebViewIpcPacketTypes.CMD_TICK -> {
                        val rt = requireRuntime()
                        val dt = cmd.payload["deltaMs"]?.takeIf { it.isNumber }?.asDouble()?.toFloat()
                        val tiles = rt.stepPeriodicTick(dt)
                        sendOk(cmd, buildTilesPayload(rt, tiles))
                    }
                    WebViewIpcPacketTypes.CMD_REQUEST_FRAME -> {
                        val rt = requireRuntime()
                        val tiles = rt.renderDirtyTiles()
                        sendOk(cmd, buildTilesPayload(rt, tiles))
                    }
                    WebViewIpcPacketTypes.CMD_PING -> {
                        sendOk(cmd, PreferenceElement.ofObject(mapOf("alive" to PreferenceElement.ofBoolean(true))))
                    }
                    WebViewIpcPacketTypes.CMD_SHUTDOWN -> {
                        sendOk(cmd, PreferenceElement.Null)
                        shutdownFlag.store(true)
                    }
                    else -> {
                        sendError(cmd, "Unsupported WebView worker command: '${cmd.type}'")
                    }
                }
            } catch (t: Throwable) {
                sendError(cmd, t.message ?: t::class.simpleName ?: "WorkerCommandFailed")
            }
        }

        private fun callHostBridgeSynchronously(name: String, payloadJson: String): String {
            val reqId = nextPacketId.fetchAndAdd(1L)
            val deferred = CompletableDeferred<IpcPacket>()
            withPendingLock { pendingBridgeReplies[reqId] = deferred }
            val sent = withSendLock {
                connection.sendPacket(
                    IpcPacket(
                        id = reqId,
                        type = WebViewIpcPacketTypes.EVT_BRIDGE_CALL,
                        payload = PreferenceElement.ofObject(
                            linkedMapOf(
                                "name" to PreferenceElement.ofString(name),
                                "payloadJson" to PreferenceElement.ofString(payloadJson)
                            )
                        )
                    )
                )
            }
            if (!sent) {
                withPendingLock { pendingBridgeReplies.remove(reqId) }
                throw IllegalStateException("Failed to transmit host bridge call '$name'")
            }
            val reply = runBlocking { withTimeoutOrNull(BRIDGE_RPC_TIMEOUT_MS) { deferred.await() } }
            if (reply == null) {
                withPendingLock { pendingBridgeReplies.remove(reqId) }
                throw IllegalStateException("Timed out waiting for host bridge reply to '$name'")
            }
            return reply.payload["result"]?.asString().orEmpty()
        }

        private fun emitDirtyFrameTiles(rt: WebViewWorkerRuntime, tiles: List<AlienDirtyTile>) {
            if (tiles.isEmpty()) return
            sendEvent(WebViewIpcPacketTypes.EVT_FRAME_TILES, buildTilesPayload(rt, tiles))
        }

        private fun buildTilesPayload(rt: WebViewWorkerRuntime, tiles: List<AlienDirtyTile>): PreferenceElement {
            val tileElements = tiles.map { t ->
                PreferenceElement.ofObject(
                    linkedMapOf(
                        "x" to PreferenceElement.ofInt(t.x),
                        "y" to PreferenceElement.ofInt(t.y),
                        "w" to PreferenceElement.ofInt(t.width),
                        "h" to PreferenceElement.ofInt(t.height),
                        "rgba" to PreferenceElement.ofBytes(t.rgbaBytes)
                    )
                )
            }
            return PreferenceElement.ofObject(
                linkedMapOf(
                    "frameWidth" to PreferenceElement.ofInt(rt.viewportWidth),
                    "frameHeight" to PreferenceElement.ofInt(rt.viewportHeight),
                    "frameSeq" to PreferenceElement.ofLong(rt.frameSequence),
                    "tiles" to PreferenceElement.ofArray(tileElements)
                )
            )
        }

        private inline fun <T> executeCapturingNav(
            cmd: IpcPacket,
            block: () -> Pair<T, List<AlienDirtyTile>>
        ): Triple<T, List<AlienDirtyTile>, Triple<String, Boolean, Boolean>?> {
            activeCommandId = cmd.id
            bufferedPageFinished = null
            return try {
                val (res, tiles) = block()
                val finished = bufferedPageFinished
                Triple(res, tiles, finished)
            } finally {
                activeCommandId = null
                bufferedPageFinished = null
            }
        }

        private inline fun executeNavCommand(
            cmd: IpcPacket,
            rt: WebViewWorkerRuntime,
            block: () -> List<AlienDirtyTile>
        ) {
            activeCommandId = cmd.id
            bufferedPageFinished = null
            val tiles = try {
                block()
            } catch (t: Throwable) {
                activeCommandId = null
                bufferedPageFinished = null
                throw t
            }
            val finished = bufferedPageFinished
            activeCommandId = null
            bufferedPageFinished = null
            if (tiles.isNotEmpty()) emitDirtyFrameTiles(rt, tiles)
            sendOk(
                cmd,
                navStatePayload(
                    rt = rt,
                    commandId = cmd.id,
                    pageFinished = finished != null,
                    pageFinishedUrl = finished?.first
                )
            )
            // Title and favicon are already delivered in RES_OK; updating snapshots avoids duplicate events.
            lastReportedTitle = rt.currentTitle
            lastReportedFaviconUrl = rt.currentFaviconUrl
            hasReportedFaviconUrl = true
            if (finished != null) {
                emitPageFinishedEvent(cmd.id, rt, finished)
            }
        }

        private inline fun executeActionWithOptionalNav(
            cmd: IpcPacket,
            rt: WebViewWorkerRuntime,
            block: () -> List<AlienDirtyTile>
        ) {
            val (_, tiles, finished) = executeCapturingNav(cmd) { Unit to block() }
            if (tiles.isNotEmpty()) emitDirtyFrameTiles(rt, tiles)
            if (finished != null) {
                sendOk(
                    cmd,
                    navStatePayload(
                        rt = rt,
                        commandId = cmd.id,
                        pageFinished = true,
                        pageFinishedUrl = finished.first
                    )
                )
                emitTitleChangedIfNeeded(rt)
                lastReportedFaviconUrl = rt.currentFaviconUrl
                hasReportedFaviconUrl = true
                emitPageFinishedEvent(cmd.id, rt, finished)
            } else {
                emitTitleChangedIfNeeded(rt)
                emitFaviconChangedIfNeeded(rt)
                sendOk(cmd, PreferenceElement.Null)
            }
        }

        private fun navStatePayload(
            rt: WebViewWorkerRuntime,
            commandId: Long = 0L,
            pageFinished: Boolean = false,
            pageFinishedUrl: String? = null
        ): PreferenceElement =
            PreferenceElement.ofObject(
                linkedMapOf(
                    "commandId" to PreferenceElement.ofLong(commandId),
                    "url" to PreferenceElement.ofString(rt.currentUrl),
                    "title" to PreferenceElement.ofString(rt.currentTitle),
                    "faviconUrl" to faviconPreferenceElement(rt.currentFaviconUrl),
                    "canGoBack" to PreferenceElement.ofBoolean(rt.canGoBack),
                    "canGoForward" to PreferenceElement.ofBoolean(rt.canGoForward),
                    "pageFinished" to PreferenceElement.ofBoolean(pageFinished),
                    "pageFinishedUrl" to PreferenceElement.ofString(pageFinishedUrl ?: rt.currentUrl)
                )
            )

        private fun faviconPreferenceElement(url: String?): PreferenceElement =
            url?.takeIf { it.isNotBlank() }?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null

        private fun emitTitleChangedIfNeeded(rt: WebViewWorkerRuntime) {
            val current = rt.currentTitle
            if (current != lastReportedTitle) {
                lastReportedTitle = current
                sendEvent(
                    WebViewIpcPacketTypes.EVT_TITLE_CHANGED,
                    PreferenceElement.ofObject(mapOf("title" to PreferenceElement.ofString(current)))
                )
            }
        }

        private fun emitFaviconChangedIfNeeded(rt: WebViewWorkerRuntime) {
            val current = rt.currentFaviconUrl
            if (!hasReportedFaviconUrl || current != lastReportedFaviconUrl) {
                if (!hasReportedFaviconUrl && current == null) {
                    return
                }
                hasReportedFaviconUrl = true
                lastReportedFaviconUrl = current
                sendEvent(
                    WebViewIpcPacketTypes.EVT_FAVICON_CHANGED,
                    PreferenceElement.ofObject(mapOf("faviconUrl" to faviconPreferenceElement(current)))
                )
            }
        }

        private fun emitPageFinishedEvent(
            commandId: Long,
            rt: WebViewWorkerRuntime,
            finished: Triple<String, Boolean, Boolean>
        ) {
            sendEvent(
                WebViewIpcPacketTypes.EVT_PAGE_FINISHED,
                PreferenceElement.ofObject(
                    linkedMapOf(
                        "commandId" to PreferenceElement.ofLong(commandId),
                        "url" to PreferenceElement.ofString(finished.first),
                        "title" to PreferenceElement.ofString(rt.currentTitle),
                        "faviconUrl" to faviconPreferenceElement(rt.currentFaviconUrl),
                        "canGoBack" to PreferenceElement.ofBoolean(finished.second),
                        "canGoForward" to PreferenceElement.ofBoolean(finished.third)
                    )
                )
            )
        }

        private fun emitJsError(err: String) {
            sendEvent(
                WebViewIpcPacketTypes.EVT_JS_ERROR,
                PreferenceElement.ofObject(mapOf("error" to PreferenceElement.ofString(err)))
            )
        }

        private fun sendOk(req: IpcPacket, payload: PreferenceElement): Boolean {
            return withSendLock {
                connection.sendPacket(
                    IpcPacket(
                        id = nextPacketId.fetchAndAdd(1L),
                        type = WebViewIpcPacketTypes.RES_OK,
                        replyToId = req.id,
                        payload = payload
                    )
                )
            }
        }

        private fun sendError(req: IpcPacket, message: String) {
            withSendLock {
                connection.sendPacket(
                    IpcPacket(
                        id = nextPacketId.fetchAndAdd(1L),
                        type = WebViewIpcPacketTypes.RES_ERROR,
                        replyToId = req.id,
                        error = message
                    )
                )
            }
        }

        private fun sendEvent(type: String, payload: PreferenceElement) {
            withSendLock {
                connection.sendPacket(
                    IpcPacket(
                        id = nextPacketId.fetchAndAdd(1L),
                        type = type,
                        payload = payload
                    )
                )
            }
        }

        private inline fun <T> withSendLock(block: () -> T): T {
            while (!sendLock.compareAndSet(0, 1)) {
                // Brief spin protecting IPC writes between readerJob stop replies and worker loop.
            }
            return try {
                block()
            } finally {
                sendLock.store(0)
            }
        }

        private fun requireRuntime(): WebViewWorkerRuntime =
            runtime ?: throw IllegalStateException("WebView worker runtime not initialized")

        private fun failAllBridgeWaiters(error: Throwable) {
            val snap = withPendingLock {
                val list = pendingBridgeReplies.values.toList()
                pendingBridgeReplies.clear()
                list
            }
            for (d in snap) {
                d.completeExceptionally(error)
            }
        }

        private inline fun <T> withQueueLock(block: () -> T): T {
            while (!queueLock.compareAndSet(0, 1)) {
                // Brief spin lock between IPC reader coroutine and single worker execution thread.
            }
            return try {
                block()
            } finally {
                queueLock.store(0)
            }
        }

        private inline fun <T> withPendingLock(block: () -> T): T {
            while (!pendingLock.compareAndSet(0, 1)) {
                // Brief spin lock protecting pending synchronous bridge RPC map.
            }
            return try {
                block()
            } finally {
                pendingLock.store(0)
            }
        }
    }
}
