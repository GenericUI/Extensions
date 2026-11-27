@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.nexustools.genericui.ext.webview.desktop.ipc

import com.nexustools.genericui.ext.ipc.IpcConfig
import com.nexustools.genericui.ext.ipc.IpcConnection
import com.nexustools.genericui.ext.ipc.IpcExtension
import com.nexustools.genericui.ext.ipc.IpcPacket
import com.nexustools.genericui.ext.ipc.IpcServer
import com.nexustools.genericui.ext.webview.WebViewConfig
import com.nexustools.genericui.ext.webview.WebViewConsoleMessage
import com.nexustools.genericui.ext.webview.system.SystemWebViewBridgeDispatcher
import com.nexustools.genericui.component.system.PreferenceElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong

/**
 * Owns the dedicated isolated OS worker process, `:extensions:system:ipc` BinaryJson transport,
 * framebuffer tile receiver, navigation/JS/bridge RPC dispatching, and automatic crash recovery
 * for a single desktop [com.nexustools.genericui.ext.webview.WebView] instance.
 */
class WebViewWorkerHostSession(
    val webViewConfig: WebViewConfig,
    initialWidth: Int = webViewConfig.initialViewportWidth.coerceAtLeast(1),
    initialHeight: Int = webViewConfig.initialViewportHeight.coerceAtLeast(1)
) {
    companion object {
        private const val IPC_MAX_PACKET_BYTES: Int = 32 * 1024 * 1024
        private const val STARTUP_CONNECT_TIMEOUT_MS: Int = 20_000
        private const val DEFAULT_RPC_TIMEOUT_MS: Long = 15_000L
        private const val NAV_RPC_TIMEOUT_MS: Long = 25_000L
        private const val SHUTDOWN_GRACE_MS: Int = 500
        private const val MAX_TRACKED_PAGE_FINISHED_IDS: Int = 128
        private val nextSessionOrdinal = AtomicLong(1L)
    }

    val resolvedRuntimeConfig: WebViewResolvedRuntimeConfig =
        WebViewRuntimeConfigResolver.resolve(webViewConfig)

    val sessionId: Long = nextSessionOrdinal.fetchAndAdd(1L)
    val webViewId: String = "wv-${WebViewWorkerLauncher.currentOsProcessId()}-$sessionId"
    val framebuffer: WebViewHostFramebuffer = WebViewHostFramebuffer(initialWidth, initialHeight)

    private val bridgeDispatcher = SystemWebViewBridgeDispatcher()
    private val lifecycleMutex = Mutex()
    private val mapLock = AtomicInt(0)
    private val pendingReplies = LinkedHashMap<Long, WebViewHostRpcWaiter>()
    private val nextRequestId = AtomicLong(1L)
    private val disposedFlag = AtomicBoolean(false)
    private val restartingFlag = AtomicBoolean(false)
    private val loadingFlag = AtomicBoolean(false)
    private val sessionBoundToUiLoop = AtomicBoolean(false)
    private val startupScheduledFlag = AtomicBoolean(false)
    private val resizeDispatchQueued = AtomicBoolean(false)
    private val pendingResizeW = AtomicInt(initialWidth.coerceAtLeast(1))
    private val pendingResizeH = AtomicInt(initialHeight.coerceAtLeast(1))
    private val syncedResizeW = AtomicInt(-1)
    private val syncedResizeH = AtomicInt(-1)
    private val moveDispatchQueued = AtomicBoolean(false)
    private val pendingMoveXBits = AtomicInt(0)
    private val pendingMoveYBits = AtomicInt(0)
    private val pendingMoveButton = AtomicInt(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val commandWorkerJob: Job = scope.launch {
        for (task in commandChannel) {
            if (disposedFlag.load()) break
            try {
                task()
            } catch (t: Throwable) {
                if (!disposedFlag.load()) {
                    val msg = t.message ?: t::class.simpleName ?: "WebViewWorkerCommandError"
                    postCallbackToUi(forceAsync = true) {
                        onJavaScriptError?.invoke("WebView command error: $msg")
                    }
                }
            }
        }
    }

    private var ipcServer: IpcServer? = null
    private var activeConnection: IpcConnection? = null
    private var workerProcess: WebViewWorkerProcessHandle? = null
    private var readerJob: Job? = null
    private var readySignal: kotlinx.coroutines.CompletableDeferred<IpcPacket>? = null
    private var workerEngineNameState: String = "AlienRenderSession+NJS"

    private var viewportWidth: Int = initialWidth.coerceAtLeast(1)
    private var viewportHeight: Int = initialHeight.coerceAtLeast(1)
    private var currentUrlState: String? = null
    private var currentTitleState: String? = null
    private var currentFaviconUrlState: String? = null
    private var lastLoadedHtml: String? = null
    private var lastLoadedBaseUrl: String? = null
    private var lastLoadedWasHtml: Boolean = false
    private var canGoBackState: Boolean = false
    private var canGoForwardState: Boolean = false

    private val registeredBridgeFunctions = LinkedHashSet<String>()
    private val handledPageFinishedCommandIds = LinkedHashSet<Long>()

    var workerCrashCount: Int = 0
        private set
    var workerRestartCount: Int = 0
        private set

    var onWebMessageReceived: ((String) -> Unit)? = null
    var onPageFinished: ((String) -> Unit)? = null
    var onTitleChanged: ((String) -> Unit)? = null
    var onFaviconChanged: ((String?) -> Unit)? = null
    var onLoadingChanged: ((Boolean) -> Unit)? = null
    var onNavStateChanged: ((canGoBack: Boolean, canGoForward: Boolean) -> Unit)? = null
    var onConsoleMessage: ((WebViewConsoleMessage) -> Unit)? = null
    var onJavaScriptError: ((String) -> Unit)? = null
    var onViewportRedraw: (() -> Unit)? = null
    var onWorkerRestarted: ((newPid: Long, restartCount: Int) -> Unit)? = null

    private data class NavReplyOutcome(
        val resolvedUrl: String,
        val title: String?,
        val titleChanged: Boolean,
        val faviconUrl: String?,
        val faviconChanged: Boolean,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val navChanged: Boolean,
        val tilesApplied: Boolean,
        val pageFinished: Boolean,
        val pageFinishedUrl: String,
        val wasLoading: Boolean,
        val errorMessage: String?
    )

    private inline fun <T> withPendingLock(block: () -> T): T {
        while (!mapLock.compareAndSet(0, 1)) {
            // brief spin protecting pending RPC map and nav snapshots
        }
        return try {
            block()
        } finally {
            mapLock.store(0)
        }
    }

    val currentWorkerPid: Long?
        get() = workerProcess?.pid

    val isWorkerAlive: Boolean
        get() = workerProcess?.isAlive() == true && activeConnection?.isClosed == false

    val engineSummary: String
        get() = workerEngineNameState

    fun getCurrentUrl(): String? = withPendingLock { currentUrlState }

    fun getCurrentTitle(): String? = withPendingLock { currentTitleState }

    fun getCurrentFaviconUrl(): String? = withPendingLock { currentFaviconUrlState }

    fun isLoading(): Boolean = loadingFlag.load()

    fun canGoBack(): Boolean = withPendingLock { canGoBackState }

    fun canGoForward(): Boolean = withPendingLock { canGoForwardState }

    private fun shouldMarshalCallbacksToUi(): Boolean =
        sessionBoundToUiLoop.load() || WebViewHostUiBridge.isUiLoopActive()

    private fun shouldUseNonBlockingUiMode(): Boolean {
        if (WebViewHostUiBridge.isUiLoopActive()) {
            sessionBoundToUiLoop.store(true)
            WebViewHostUiBridge.ensureUiThreadBoundIfActive()
            return true
        }
        return sessionBoundToUiLoop.load()
    }

    private fun postCallbackToUi(forceAsync: Boolean = true, block: () -> Unit) {
        if (disposedFlag.load()) return
        if (!shouldMarshalCallbacksToUi()) {
            block()
            return
        }
        WebViewHostUiBridge.postToUi(
            requireUiLoop = true,
            forceAsync = forceAsync,
            block = {
                if (!disposedFlag.load()) {
                    block()
                }
            }
        )
    }

    private fun dispatchBackground(block: suspend () -> Unit) {
        if (disposedFlag.load()) return
        commandChannel.trySend(block)
    }

    fun ensureStarted() {
        check(!disposedFlag.load()) { "WebViewWorkerHostSession($webViewId) is already disposed" }
        startupScheduledFlag.store(true)
        runBlocking {
            lifecycleMutex.withLock {
                ensureStartedLocked()
            }
        }
    }

    fun ensureStartedAsync() {
        if (disposedFlag.load()) return
        if (!startupScheduledFlag.compareAndSet(false, true)) return
        dispatchBackground {
            lifecycleMutex.withLock {
                ensureStartedLocked()
            }
        }
    }

    fun ensureStartedSmart() {
        if (shouldUseNonBlockingUiMode()) {
            ensureStartedAsync()
        } else {
            ensureStarted()
        }
    }

    private suspend fun ensureStartedLocked() {
        check(!disposedFlag.load()) { "WebViewWorkerHostSession($webViewId) is already disposed" }
        val conn = activeConnection
        val proc = workerProcess
        if (conn != null && !conn.isClosed && proc != null && proc.isAlive()) {
            return
        }
        cleanupTransportLocked()
        spawnAndInitializeLocked()
    }

    private suspend fun spawnAndInitializeLocked() {
        IpcExtension.registerProviders()

        val attemptOrdinal = workerRestartCount + 1
        val channelName = "$webViewId-ch-$attemptOrdinal-${nextRequestId.load()}"
        val runtimeDir = WebViewWorkerLauncher.resolveRuntimeDirectory(webViewId)
        val ipcConfig = IpcConfig(
            connectTimeoutMs = STARTUP_CONNECT_TIMEOUT_MS,
            maxPacketBytes = IPC_MAX_PACKET_BYTES,
            pipeBufferSizeBytes = 65_536
        )

        val connectedDeferred = kotlinx.coroutines.CompletableDeferred<IpcConnection>()
        val server = IpcServer()
        val started = server.start(
            channelName = channelName,
            runtimeDir = runtimeDir,
            config = ipcConfig
        ) { connection ->
            if (!connectedDeferred.complete(connection)) {
                connection.close()
            }
        }
        if (!started) {
            server.close()
            throw IllegalStateException("Failed to start IPC server for WebView worker '$channelName'")
        }
        val address = server.address
            ?: run {
                server.close()
                throw IllegalStateException("IPC server started without channel address for '$channelName'")
            }

        ipcServer = server
        readySignal = kotlinx.coroutines.CompletableDeferred()

        val proc = try {
            WebViewWorkerLauncher.spawnWorker(
                address = address,
                webViewId = webViewId,
                config = resolvedRuntimeConfig
            )
        } catch (t: Throwable) {
            server.close()
            ipcServer = null
            throw IllegalStateException("Failed to spawn isolated WebView worker OS process: ${t.message}", t)
        }
        workerProcess = proc

        val connection = withTimeoutOrNull(STARTUP_CONNECT_TIMEOUT_MS.toLong()) {
            while (!connectedDeferred.isCompleted) {
                if (!proc.isAlive()) {
                    throw IllegalStateException(
                        "WebView worker process exited before IPC connect (${proc.diagnosticsSummary()})"
                    )
                }
                kotlinx.coroutines.delay(15L)
            }
            connectedDeferred.await()
        } ?: run {
            val diag = proc.diagnosticsSummary()
            proc.killForcibly()
            proc.closeHandle()
            server.close()
            ipcServer = null
            workerProcess = null
            throw IllegalStateException(
                "Timed out after ${STARTUP_CONNECT_TIMEOUT_MS}ms waiting for worker IPC connection ($diag)"
            )
        }

        activeConnection = connection
        readerJob = scope.launch {
            runReaderLoop(connection, proc)
        }

        val readyPkt = withTimeoutOrNull(STARTUP_CONNECT_TIMEOUT_MS.toLong()) {
            readySignal?.await()
        } ?: run {
            val diag = proc.diagnosticsSummary()
            cleanupTransportLocked()
            throw IllegalStateException("Timed out waiting for worker ready handshake ($diag)")
        }
        readyPkt.payload["engineName"]?.asString()?.takeIf { it.isNotBlank() }?.let {
            workerEngineNameState = it
        }

        sendInitLocked(connection)
    }

    private suspend fun sendInitLocked(connection: IpcConnection) {
        val initPayload = PreferenceElement.ofObject(
            linkedMapOf(
                "width" to PreferenceElement.ofInt(viewportWidth),
                "height" to PreferenceElement.ofInt(viewportHeight),
                "enableJavaScript" to PreferenceElement.ofBoolean(webViewConfig.enableJavaScript),
                "domStorageEnabled" to PreferenceElement.ofBoolean(webViewConfig.domStorageEnabled),
                "enableDevTools" to PreferenceElement.ofBoolean(webViewConfig.enableDevTools),
                "allowFileAccess" to PreferenceElement.ofBoolean(webViewConfig.allowFileAccess),
                "userAgentOverride" to (webViewConfig.userAgentOverride?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null),
                "capabilities" to resolvedRuntimeConfig.capabilities.toPreferenceElement()
            )
        )
        val reply = sendRequestOnConnectionBlocking(
            connection = connection,
            type = WebViewIpcPacketTypes.CMD_INIT,
            payload = initPayload,
            timeoutMs = DEFAULT_RPC_TIMEOUT_MS
        )
        if (reply.error != null) {
            throw IllegalStateException("Worker initialization failed: ${reply.error}")
        }
        syncedResizeW.store(viewportWidth)
        syncedResizeH.store(viewportHeight)
        framebuffer.applyDirtyTilesFromPacket(reply)

        val bridgesSnapshot = withPendingLock { registeredBridgeFunctions.toList() }
        for (bridgeName in bridgesSnapshot) {
            sendRequestOnConnectionBlocking(
                connection = connection,
                type = WebViewIpcPacketTypes.CMD_REGISTER_BRIDGE_FN,
                payload = PreferenceElement.ofObject(mapOf("name" to PreferenceElement.ofString(bridgeName))),
                timeoutMs = DEFAULT_RPC_TIMEOUT_MS
            )
        }
    }

    private suspend fun runReaderLoop(connection: IpcConnection, proc: WebViewWorkerProcessHandle) {
        var unexpectedDisconnect = false
        try {
            while (!disposedFlag.load() && !connection.isClosed) {
                val packet = connection.receivePacket(timeoutMs = 200)
                if (packet == null) {
                    if (connection.isClosed || !proc.isAlive()) {
                        unexpectedDisconnect = !disposedFlag.load()
                        break
                    }
                    continue
                }
                handleIncomingPacket(connection, packet)
            }
        } catch (t: Throwable) {
            if (!disposedFlag.load()) {
                unexpectedDisconnect = true
                val errMsg = "Worker IPC channel error: ${t.message ?: t::class.simpleName}"
                postCallbackToUi(forceAsync = true) {
                    onJavaScriptError?.invoke(errMsg)
                }
            }
        } finally {
            failAllPendingWaiters(IllegalStateException("WebView worker IPC connection closed"))
            if (unexpectedDisconnect && !disposedFlag.load()) {
                handleUnexpectedWorkerCrash()
            }
        }
    }

    private fun handleIncomingPacket(connection: IpcConnection, packet: IpcPacket) {
        when (packet.type) {
            WebViewIpcPacketTypes.EVT_READY -> {
                readySignal?.complete(packet)
            }
            WebViewIpcPacketTypes.RES_OK, WebViewIpcPacketTypes.RES_ERROR -> {
                val replyTo = packet.replyToId ?: return
                val waiter = withPendingLock {
                    if (
                        packet.type == WebViewIpcPacketTypes.RES_OK &&
                        (packet.payload["pageFinished"]?.asBoolean(false) == true)
                    ) {
                        if (handledPageFinishedCommandIds.size >= MAX_TRACKED_PAGE_FINISHED_IDS) {
                            val oldest = handledPageFinishedCommandIds.firstOrNull()
                            if (oldest != null) handledPageFinishedCommandIds.remove(oldest)
                        }
                        handledPageFinishedCommandIds.add(replyTo)
                    }
                    pendingReplies.remove(replyTo)
                }
                waiter?.complete(packet)
            }
            WebViewIpcPacketTypes.EVT_FRAME_TILES -> {
                framebuffer.applyDirtyTilesFromPacket(packet)
                postCallbackToUi(forceAsync = true) {
                    onViewportRedraw?.invoke()
                }
            }
            WebViewIpcPacketTypes.EVT_TITLE_CHANGED -> {
                val title = packet.payload["title"]?.asString().orEmpty()
                val changed = withPendingLock {
                    if (currentTitleState == title) {
                        false
                    } else {
                        currentTitleState = title
                        true
                    }
                }
                if (changed) {
                    postCallbackToUi(forceAsync = true) {
                        onTitleChanged?.invoke(title)
                    }
                }
            }
            WebViewIpcPacketTypes.EVT_FAVICON_CHANGED -> {
                val favEl = packet.payload["faviconUrl"]
                val faviconUrl = favEl?.takeIf { !it.isNull }?.asString()?.takeIf { it.isNotEmpty() }
                val changed = withPendingLock {
                    if (currentFaviconUrlState == faviconUrl) {
                        false
                    } else {
                        currentFaviconUrlState = faviconUrl
                        true
                    }
                }
                if (changed) {
                    postCallbackToUi(forceAsync = true) {
                        onFaviconChanged?.invoke(faviconUrl)
                    }
                }
            }
            WebViewIpcPacketTypes.EVT_LOADING_CHANGED -> {
                val loading = packet.payload["loading"]?.asBoolean(false) ?: false
                if (loadingFlag.compareAndSet(!loading, loading)) {
                    postCallbackToUi(forceAsync = true) {
                        onLoadingChanged?.invoke(loading)
                    }
                }
            }
            WebViewIpcPacketTypes.EVT_PAGE_FINISHED -> {
                val cmdId = packet.payload["commandId"]?.asLong(-1L) ?: -1L
                val alreadyHandled = cmdId > 0L && withPendingLock {
                    handledPageFinishedCommandIds.remove(cmdId)
                }
                if (alreadyHandled) {
                    return
                }
                val url = packet.payload["url"]?.asString().orEmpty()
                val title = packet.payload["title"]?.asString()
                val favEl = packet.payload["faviconUrl"]
                val hasFavicon = favEl != null
                val faviconUrl = favEl?.takeIf { !it.isNull }?.asString()?.takeIf { it.isNotEmpty() }
                val rawBack = packet.payload["canGoBack"]
                val rawFwd = packet.payload["canGoForward"]
                var titleChanged = false
                var faviconChanged = false
                var navChanged = false
                var resolvedBack = false
                var resolvedFwd = false
                withPendingLock {
                    if (url.isNotEmpty()) {
                        currentUrlState = url
                    }
                    if (title != null && title != currentTitleState) {
                        currentTitleState = title
                        titleChanged = true
                    }
                    if (hasFavicon && faviconUrl != currentFaviconUrlState) {
                        currentFaviconUrlState = faviconUrl
                        faviconChanged = true
                    }
                    val nextBack = rawBack?.asBoolean(canGoBackState) ?: canGoBackState
                    val nextFwd = rawFwd?.asBoolean(canGoForwardState) ?: canGoForwardState
                    if (nextBack != canGoBackState || nextFwd != canGoForwardState) {
                        canGoBackState = nextBack
                        canGoForwardState = nextFwd
                        navChanged = true
                    }
                    resolvedBack = canGoBackState
                    resolvedFwd = canGoForwardState
                }
                val wasLoading = loadingFlag.compareAndSet(true, false)
                val deliver: () -> Unit = {
                    if (titleChanged && title != null) onTitleChanged?.invoke(title)
                    if (faviconChanged) onFaviconChanged?.invoke(faviconUrl)
                    if (navChanged) onNavStateChanged?.invoke(resolvedBack, resolvedFwd)
                    if (wasLoading) onLoadingChanged?.invoke(false)
                    onPageFinished?.invoke(url)
                }
                if (shouldMarshalCallbacksToUi()) {
                    postCallbackToUi(forceAsync = true, block = deliver)
                } else {
                    // Run unsolicited async pageFinished callbacks off runReaderLoop so re-entrant evaluateJavascript never deadlocks.
                    scope.launch(Dispatchers.Default) {
                        if (!disposedFlag.load()) {
                            deliver()
                        }
                    }
                }
            }
            WebViewIpcPacketTypes.EVT_WEB_MESSAGE -> {
                val message = packet.payload["message"]?.asString() ?: return
                postCallbackToUi(forceAsync = true) {
                    onWebMessageReceived?.invoke(message)
                }
            }
            WebViewIpcPacketTypes.EVT_CONSOLE_MESSAGE -> {
                val msg = WebViewConsoleMessage(
                    level = packet.payload["level"]?.asString("log") ?: "log",
                    message = packet.payload["message"]?.asString().orEmpty(),
                    sourceId = packet.payload["sourceId"]?.asString().orEmpty(),
                    line = packet.payload["line"]?.asInt(0) ?: 0
                )
                postCallbackToUi(forceAsync = true) {
                    onConsoleMessage?.invoke(msg)
                }
            }
            WebViewIpcPacketTypes.EVT_JS_ERROR -> {
                val err = packet.payload["error"]?.asString("Unknown JavaScript error") ?: "Unknown JavaScript error"
                postCallbackToUi(forceAsync = true) {
                    onJavaScriptError?.invoke(err)
                }
            }
            WebViewIpcPacketTypes.EVT_BRIDGE_CALL -> {
                val fnName = packet.payload["name"]?.asString().orEmpty()
                val payloadJson = packet.payload["payloadJson"]?.asString().orEmpty()
                scope.launch(Dispatchers.Default) {
                    val result = try {
                        bridgeDispatcher.dispatch(fnName, payloadJson)
                    } catch (t: Throwable) {
                        val msg = t.message ?: t::class.simpleName ?: "BridgeHandlerError"
                        "{\"error\":\"${escapeJsonString(msg)}\"}"
                    }
                    connection.sendPacket(
                        IpcPacket(
                            id = nextRequestId.fetchAndAdd(1L),
                            type = WebViewIpcPacketTypes.RES_BRIDGE_CALL,
                            replyToId = packet.id,
                            payload = PreferenceElement.ofObject(
                                mapOf("result" to PreferenceElement.ofString(result))
                            )
                        )
                    )
                }
            }
        }
    }

    private fun handleUnexpectedWorkerCrash() {
        if (disposedFlag.load()) return
        if (!restartingFlag.compareAndSet(false, true)) return
        workerCrashCount++
        scope.launch {
            try {
                restartWorkerInternal()
            } catch (t: Throwable) {
                val msg = "Worker auto-restart failed: ${t.message ?: t::class.simpleName}"
                postCallbackToUi(forceAsync = true) {
                    onJavaScriptError?.invoke(msg)
                }
            } finally {
                restartingFlag.store(false)
            }
        }
    }

    private suspend fun restartWorkerInternal() {
        lifecycleMutex.withLock {
            recoverWorkerIfDeadLocked(replayDocument = true)
        }
    }

    private suspend fun recoverWorkerIfDeadLocked(replayDocument: Boolean) {
        if (disposedFlag.load() || isWorkerAlive) return
        cleanupTransportLocked()
        workerRestartCount++
        spawnAndInitializeLocked()
        if (replayDocument) {
            replayLastDocumentLocked()
        }
        val newPid = workerProcess?.pid ?: -1L
        val restartCountSnapshot = workerRestartCount
        postCallbackToUi(forceAsync = true) {
            onWorkerRestarted?.invoke(newPid, restartCountSnapshot)
        }
    }

    private suspend fun replayLastDocumentLocked() {
        val conn = activeConnection ?: return
        val wasHtml = withPendingLock { lastLoadedWasHtml }
        val htmlSnapshot = withPendingLock { lastLoadedHtml }
        val baseSnapshot = withPendingLock { lastLoadedBaseUrl }
        val urlSnapshot = withPendingLock { currentUrlState }
        if (wasHtml && htmlSnapshot != null) {
            val pkt = sendRequestOnConnectionSuspend(
                connection = conn,
                type = WebViewIpcPacketTypes.CMD_LOAD_HTML,
                payload = PreferenceElement.ofObject(
                    linkedMapOf(
                        "html" to PreferenceElement.ofString(htmlSnapshot),
                        "baseUrl" to (baseSnapshot?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null)
                    )
                ),
                timeoutMs = NAV_RPC_TIMEOUT_MS
            )
            val outcome = applyNavStateAndCollectEvents(pkt)
            emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
        } else if (urlSnapshot != null) {
            val pkt = sendRequestOnConnectionSuspend(
                connection = conn,
                type = WebViewIpcPacketTypes.CMD_LOAD_URL,
                payload = PreferenceElement.ofObject(
                    mapOf("url" to PreferenceElement.ofString(urlSnapshot))
                ),
                timeoutMs = NAV_RPC_TIMEOUT_MS
            )
            val outcome = applyNavStateAndCollectEvents(pkt)
            emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
        }
    }

    private fun notifyLoadingStartedOnCaller() {
        if (loadingFlag.compareAndSet(false, true)) {
            postCallbackToUi(forceAsync = false) {
                onLoadingChanged?.invoke(true)
            }
        }
    }

    private fun ensureLoadingStartedFromBackground() {
        if (loadingFlag.compareAndSet(false, true)) {
            postCallbackToUi(forceAsync = true) {
                onLoadingChanged?.invoke(true)
            }
        }
    }

    fun loadUrl(url: String) {
        withPendingLock {
            currentUrlState = url
            lastLoadedWasHtml = false
            lastLoadedHtml = null
        }
        notifyLoadingStartedOnCaller()
        val reply = sendRequest(
            type = WebViewIpcPacketTypes.CMD_LOAD_URL,
            payload = PreferenceElement.ofObject(mapOf("url" to PreferenceElement.ofString(url))),
            timeoutMs = NAV_RPC_TIMEOUT_MS
        )
        val outcome = applyNavStateAndCollectEvents(reply)
        emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
    }

    fun loadUrlAsync(url: String) {
        if (disposedFlag.load()) return
        withPendingLock {
            currentUrlState = url
            lastLoadedWasHtml = false
            lastLoadedHtml = null
        }
        notifyLoadingStartedOnCaller()
        dispatchBackground {
            ensureLoadingStartedFromBackground()
            try {
                val reply = sendRequestSuspend(
                    type = WebViewIpcPacketTypes.CMD_LOAD_URL,
                    payload = PreferenceElement.ofObject(mapOf("url" to PreferenceElement.ofString(url))),
                    timeoutMs = NAV_RPC_TIMEOUT_MS
                )
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            } catch (t: Throwable) {
                handleAsyncNavFailure(url, t)
            }
        }
    }

    fun loadUrlSmart(url: String) {
        if (shouldUseNonBlockingUiMode()) {
            loadUrlAsync(url)
        } else {
            loadUrl(url)
        }
    }

    fun loadHtml(html: String, baseUrl: String? = null) {
        val resolvedBase = baseUrl ?: "about:blank"
        withPendingLock {
            lastLoadedWasHtml = true
            lastLoadedHtml = html
            lastLoadedBaseUrl = baseUrl
            currentUrlState = resolvedBase
        }
        notifyLoadingStartedOnCaller()
        val reply = sendRequest(
            type = WebViewIpcPacketTypes.CMD_LOAD_HTML,
            payload = PreferenceElement.ofObject(
                linkedMapOf(
                    "html" to PreferenceElement.ofString(html),
                    "baseUrl" to (baseUrl?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null)
                )
            ),
            timeoutMs = NAV_RPC_TIMEOUT_MS
        )
        val outcome = applyNavStateAndCollectEvents(reply)
        emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
    }

    fun loadHtmlAsync(html: String, baseUrl: String? = null) {
        if (disposedFlag.load()) return
        val resolvedBase = baseUrl ?: "about:blank"
        withPendingLock {
            lastLoadedWasHtml = true
            lastLoadedHtml = html
            lastLoadedBaseUrl = baseUrl
            currentUrlState = resolvedBase
        }
        notifyLoadingStartedOnCaller()
        dispatchBackground {
            ensureLoadingStartedFromBackground()
            try {
                val reply = sendRequestSuspend(
                    type = WebViewIpcPacketTypes.CMD_LOAD_HTML,
                    payload = PreferenceElement.ofObject(
                        linkedMapOf(
                            "html" to PreferenceElement.ofString(html),
                            "baseUrl" to (baseUrl?.let { PreferenceElement.ofString(it) } ?: PreferenceElement.Null)
                        )
                    ),
                    timeoutMs = NAV_RPC_TIMEOUT_MS
                )
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            } catch (t: Throwable) {
                handleAsyncNavFailure(resolvedBase, t)
            }
        }
    }

    fun loadHtmlSmart(html: String, baseUrl: String? = null) {
        if (shouldUseNonBlockingUiMode()) {
            loadHtmlAsync(html, baseUrl)
        } else {
            loadHtml(html, baseUrl)
        }
    }

    fun reload() {
        notifyLoadingStartedOnCaller()
        val reply = sendRequest(WebViewIpcPacketTypes.CMD_RELOAD, PreferenceElement.Null, timeoutMs = NAV_RPC_TIMEOUT_MS)
        val outcome = applyNavStateAndCollectEvents(reply)
        emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
    }

    fun reloadAsync() {
        if (disposedFlag.load()) return
        val fallbackUrl = withPendingLock { currentUrlState.orEmpty() }
        notifyLoadingStartedOnCaller()
        dispatchBackground {
            ensureLoadingStartedFromBackground()
            try {
                val reply = sendRequestSuspend(WebViewIpcPacketTypes.CMD_RELOAD, PreferenceElement.Null, timeoutMs = NAV_RPC_TIMEOUT_MS)
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            } catch (t: Throwable) {
                handleAsyncNavFailure(fallbackUrl, t)
            }
        }
    }

    fun reloadSmart() {
        if (shouldUseNonBlockingUiMode()) reloadAsync() else reload()
    }

    fun stopLoading() {
        if (!isWorkerAlive) return
        sendRequest(WebViewIpcPacketTypes.CMD_STOP_LOADING, PreferenceElement.Null)
    }

    fun stopLoadingSmart() {
        if (disposedFlag.load()) return
        val wasLoading = loadingFlag.compareAndSet(true, false)
        if (wasLoading) {
            postCallbackToUi(forceAsync = false) {
                onLoadingChanged?.invoke(false)
            }
        }
        if (!shouldUseNonBlockingUiMode()) {
            stopLoading()
            return
        }
        val conn = activeConnection ?: return
        if (conn.isClosed || workerProcess?.isAlive() != true) return
        scope.launch {
            try {
                sendRequestOnConnectionSuspend(
                    connection = conn,
                    type = WebViewIpcPacketTypes.CMD_STOP_LOADING,
                    payload = PreferenceElement.Null,
                    timeoutMs = DEFAULT_RPC_TIMEOUT_MS
                )
            } catch (_: Throwable) {
                // Best-effort abort during interactive navigation or shutdown.
            }
        }
    }

    fun goBack() {
        val reply = sendRequest(WebViewIpcPacketTypes.CMD_GO_BACK, PreferenceElement.Null, timeoutMs = NAV_RPC_TIMEOUT_MS)
        val outcome = applyNavStateAndCollectEvents(reply)
        emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
    }

    fun goBackAsync() {
        if (disposedFlag.load()) return
        val fallbackUrl = withPendingLock { currentUrlState.orEmpty() }
        dispatchBackground {
            try {
                val reply = sendRequestSuspend(WebViewIpcPacketTypes.CMD_GO_BACK, PreferenceElement.Null, timeoutMs = NAV_RPC_TIMEOUT_MS)
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            } catch (t: Throwable) {
                handleAsyncNavFailure(fallbackUrl, t)
            }
        }
    }

    fun goBackSmart() {
        if (shouldUseNonBlockingUiMode()) goBackAsync() else goBack()
    }

    fun goForward() {
        val reply = sendRequest(WebViewIpcPacketTypes.CMD_GO_FORWARD, PreferenceElement.Null, timeoutMs = NAV_RPC_TIMEOUT_MS)
        val outcome = applyNavStateAndCollectEvents(reply)
        emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
    }

    fun goForwardAsync() {
        if (disposedFlag.load()) return
        val fallbackUrl = withPendingLock { currentUrlState.orEmpty() }
        dispatchBackground {
            try {
                val reply = sendRequestSuspend(WebViewIpcPacketTypes.CMD_GO_FORWARD, PreferenceElement.Null, timeoutMs = NAV_RPC_TIMEOUT_MS)
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            } catch (t: Throwable) {
                handleAsyncNavFailure(fallbackUrl, t)
            }
        }
    }

    fun goForwardSmart() {
        if (shouldUseNonBlockingUiMode()) goForwardAsync() else goForward()
    }

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        if (!webViewConfig.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        val reply = sendRequest(
            type = WebViewIpcPacketTypes.CMD_EVALUATE_JS,
            payload = PreferenceElement.ofObject(mapOf("script" to PreferenceElement.ofString(script)))
        )
        if (reply.error != null) {
            onJavaScriptError?.invoke(reply.error!!)
            callback?.invoke(null)
            return
        }
        val hasResult = reply.payload["hasResult"]?.asBoolean(false) ?: false
        val value = if (hasResult) reply.payload["result"]?.asString() else null
        callback?.invoke(value)
        if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
            val outcome = applyNavStateAndCollectEvents(reply)
            emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
        } else {
            val favEl = reply.payload["faviconUrl"]
            if (favEl != null) {
                val faviconUrl = favEl.takeIf { !it.isNull }?.asString()?.takeIf { it.isNotEmpty() }
                val favChanged = withPendingLock {
                    if (faviconUrl != currentFaviconUrlState) {
                        currentFaviconUrlState = faviconUrl
                        true
                    } else {
                        false
                    }
                }
                if (favChanged) {
                    if (shouldMarshalCallbacksToUi()) {
                        postCallbackToUi(forceAsync = true) { onFaviconChanged?.invoke(faviconUrl) }
                    } else {
                        onFaviconChanged?.invoke(faviconUrl)
                    }
                }
            }
        }
    }

    fun evaluateJavascriptSmart(script: String, callback: ((String?) -> Unit)? = null) {
        if (!webViewConfig.enableJavaScript) {
            callback?.invoke(null)
            return
        }
        if (!shouldUseNonBlockingUiMode()) {
            evaluateJavascript(script, callback)
            return
        }
        dispatchBackground {
            try {
                val reply = sendRequestSuspend(
                    type = WebViewIpcPacketTypes.CMD_EVALUATE_JS,
                    payload = PreferenceElement.ofObject(mapOf("script" to PreferenceElement.ofString(script)))
                )
                if (reply.error != null) {
                    val err = reply.error!!
                    postCallbackToUi(forceAsync = true) {
                        onJavaScriptError?.invoke(err)
                        callback?.invoke(null)
                    }
                    return@dispatchBackground
                }
                val hasResult = reply.payload["hasResult"]?.asBoolean(false) ?: false
                val value = if (hasResult) reply.payload["result"]?.asString() else null
                val favEl = reply.payload["faviconUrl"]
                var favChanged = false
                val faviconUrl = favEl?.takeIf { !it.isNull }?.asString()?.takeIf { it.isNotEmpty() }
                if (favEl != null && reply.payload["pageFinished"]?.asBoolean(false) != true) {
                    favChanged = withPendingLock {
                        if (faviconUrl != currentFaviconUrlState) {
                            currentFaviconUrlState = faviconUrl
                            true
                        } else {
                            false
                        }
                    }
                }
                postCallbackToUi(forceAsync = true) {
                    callback?.invoke(value)
                    if (favChanged) {
                        onFaviconChanged?.invoke(faviconUrl)
                    }
                }
                if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
                    val outcome = applyNavStateAndCollectEvents(reply)
                    emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
                }
            } catch (t: Throwable) {
                val err = t.message ?: t::class.simpleName ?: "JavaScriptEvaluationError"
                postCallbackToUi(forceAsync = true) {
                    onJavaScriptError?.invoke(err)
                    callback?.invoke(null)
                }
            }
        }
    }

    fun postWebMessage(message: String) {
        val reply = sendRequest(
            type = WebViewIpcPacketTypes.CMD_POST_MESSAGE,
            payload = PreferenceElement.ofObject(mapOf("message" to PreferenceElement.ofString(message)))
        )
        if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
            val outcome = applyNavStateAndCollectEvents(reply)
            emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
        }
    }

    fun postWebMessageSmart(message: String) {
        if (!shouldUseNonBlockingUiMode()) {
            postWebMessage(message)
            return
        }
        dispatchBackground {
            val reply = sendRequestSuspend(
                type = WebViewIpcPacketTypes.CMD_POST_MESSAGE,
                payload = PreferenceElement.ofObject(mapOf("message" to PreferenceElement.ofString(message)))
            )
            if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            }
        }
    }

    fun registerCommunicationFunction(name: String, handler: (String) -> String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        bridgeDispatcher.registerHandler(trimmed, handler)
        withPendingLock { registeredBridgeFunctions.add(trimmed) }
        if (isWorkerAlive) {
            sendRequest(
                type = WebViewIpcPacketTypes.CMD_REGISTER_BRIDGE_FN,
                payload = PreferenceElement.ofObject(mapOf("name" to PreferenceElement.ofString(trimmed)))
            )
        }
    }

    fun registerCommunicationFunctionSmart(name: String, handler: (String) -> String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (!shouldUseNonBlockingUiMode()) {
            registerCommunicationFunction(trimmed, handler)
            return
        }
        bridgeDispatcher.registerHandler(trimmed, handler)
        withPendingLock { registeredBridgeFunctions.add(trimmed) }
        dispatchBackground {
            if (isWorkerAlive) {
                sendRequestSuspend(
                    type = WebViewIpcPacketTypes.CMD_REGISTER_BRIDGE_FN,
                    payload = PreferenceElement.ofObject(mapOf("name" to PreferenceElement.ofString(trimmed)))
                )
            }
        }
    }

    fun unregisterCommunicationFunction(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        bridgeDispatcher.unregisterHandler(trimmed)
        withPendingLock { registeredBridgeFunctions.remove(trimmed) }
        if (isWorkerAlive) {
            sendRequest(
                type = WebViewIpcPacketTypes.CMD_UNREGISTER_BRIDGE_FN,
                payload = PreferenceElement.ofObject(mapOf("name" to PreferenceElement.ofString(trimmed)))
            )
        }
    }

    fun unregisterCommunicationFunctionSmart(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (!shouldUseNonBlockingUiMode()) {
            unregisterCommunicationFunction(trimmed)
            return
        }
        bridgeDispatcher.unregisterHandler(trimmed)
        withPendingLock { registeredBridgeFunctions.remove(trimmed) }
        dispatchBackground {
            if (isWorkerAlive) {
                sendRequestSuspend(
                    type = WebViewIpcPacketTypes.CMD_UNREGISTER_BRIDGE_FN,
                    payload = PreferenceElement.ofObject(mapOf("name" to PreferenceElement.ofString(trimmed)))
                )
            }
        }
    }

    fun callCommunicationFunction(name: String, payloadJson: String = "", callback: ((String?) -> Unit)? = null) {
        val trimmed = name.trim()
        if (!webViewConfig.enableJavaScript || trimmed.isEmpty()) {
            callback?.invoke(null)
            return
        }
        val reply = sendRequest(
            type = WebViewIpcPacketTypes.CMD_CALL_COMM_FN,
            payload = PreferenceElement.ofObject(
                linkedMapOf(
                    "name" to PreferenceElement.ofString(trimmed),
                    "payloadJson" to PreferenceElement.ofString(payloadJson)
                )
            )
        )
        val hasResult = reply.payload["hasResult"]?.asBoolean(false) ?: false
        callback?.invoke(if (hasResult) reply.payload["result"]?.asString() else null)
        if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
            val outcome = applyNavStateAndCollectEvents(reply)
            emitNavOutcomeCallbacks(outcome, forceMarshalToUi = shouldMarshalCallbacksToUi())
        }
    }

    fun callCommunicationFunctionSmart(name: String, payloadJson: String = "", callback: ((String?) -> Unit)? = null) {
        val trimmed = name.trim()
        if (!webViewConfig.enableJavaScript || trimmed.isEmpty()) {
            callback?.invoke(null)
            return
        }
        if (!shouldUseNonBlockingUiMode()) {
            callCommunicationFunction(trimmed, payloadJson, callback)
            return
        }
        dispatchBackground {
            try {
                val reply = sendRequestSuspend(
                    type = WebViewIpcPacketTypes.CMD_CALL_COMM_FN,
                    payload = PreferenceElement.ofObject(
                        linkedMapOf(
                            "name" to PreferenceElement.ofString(trimmed),
                            "payloadJson" to PreferenceElement.ofString(payloadJson)
                        )
                    )
                )
                val hasResult = reply.payload["hasResult"]?.asBoolean(false) ?: false
                val value = if (hasResult) reply.payload["result"]?.asString() else null
                postCallbackToUi(forceAsync = true) {
                    callback?.invoke(value)
                }
                if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
                    val outcome = applyNavStateAndCollectEvents(reply)
                    emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
                }
            } catch (_: Throwable) {
                postCallbackToUi(forceAsync = true) {
                    callback?.invoke(null)
                }
            }
        }
    }

    fun resize(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (syncedResizeW.load() == w && syncedResizeH.load() == h && viewportWidth == w && viewportHeight == h) {
            return
        }
        viewportWidth = w
        viewportHeight = h
        framebuffer.resize(w, h)
        if (!isWorkerAlive) return
        val reply = sendRequest(
            type = WebViewIpcPacketTypes.CMD_RESIZE,
            payload = PreferenceElement.ofObject(
                linkedMapOf(
                    "width" to PreferenceElement.ofInt(w),
                    "height" to PreferenceElement.ofInt(h)
                )
            )
        )
        syncedResizeW.store(w)
        syncedResizeH.store(h)
        framebuffer.applyDirtyTilesFromPacket(reply)
    }

    fun resizeSmart(width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (!shouldUseNonBlockingUiMode()) {
            resize(w, h)
            return
        }
        if (syncedResizeW.load() == w && syncedResizeH.load() == h && viewportWidth == w && viewportHeight == h) {
            return
        }
        viewportWidth = w
        viewportHeight = h
        framebuffer.resize(w, h)
        pendingResizeW.store(w)
        pendingResizeH.store(h)
        if (!resizeDispatchQueued.compareAndSet(false, true)) return
        dispatchBackground {
            resizeDispatchQueued.store(false)
            val targetW = pendingResizeW.load()
            val targetH = pendingResizeH.load()
            if (syncedResizeW.load() == targetW && syncedResizeH.load() == targetH) {
                return@dispatchBackground
            }
            if (!isWorkerAlive && !startupScheduledFlag.load()) {
                return@dispatchBackground
            }
            val reply = sendRequestSuspend(
                type = WebViewIpcPacketTypes.CMD_RESIZE,
                payload = PreferenceElement.ofObject(
                    linkedMapOf(
                        "width" to PreferenceElement.ofInt(targetW),
                        "height" to PreferenceElement.ofInt(targetH)
                    )
                )
            )
            syncedResizeW.store(targetW)
            syncedResizeH.store(targetH)
            framebuffer.applyDirtyTilesFromPacket(reply)
            postCallbackToUi(forceAsync = true) {
                onViewportRedraw?.invoke()
            }
        }
    }

    fun dispatchPointerEvent(kind: String, x: Float, y: Float, button: Int = 0) {
        if (!isWorkerAlive) return
        sendRequest(
            type = WebViewIpcPacketTypes.CMD_POINTER_EVENT,
            payload = PreferenceElement.ofObject(
                linkedMapOf(
                    "kind" to PreferenceElement.ofString(kind),
                    "x" to PreferenceElement.ofDouble(x.toDouble()),
                    "y" to PreferenceElement.ofDouble(y.toDouble()),
                    "button" to PreferenceElement.ofInt(button)
                )
            )
        )
    }

    fun dispatchPointerEventSmart(kind: String, x: Float, y: Float, button: Int = 0) {
        if (!shouldUseNonBlockingUiMode()) {
            dispatchPointerEvent(kind, x, y, button)
            return
        }
        if (kind == "move") {
            pendingMoveXBits.store(x.toBits())
            pendingMoveYBits.store(y.toBits())
            pendingMoveButton.store(button)
            if (!moveDispatchQueued.compareAndSet(false, true)) return
            dispatchBackground {
                moveDispatchQueued.store(false)
                if (!isWorkerAlive) return@dispatchBackground
                val targetX = Float.fromBits(pendingMoveXBits.load())
                val targetY = Float.fromBits(pendingMoveYBits.load())
                val targetBtn = pendingMoveButton.load()
                sendRequestSuspend(
                    type = WebViewIpcPacketTypes.CMD_POINTER_EVENT,
                    payload = PreferenceElement.ofObject(
                        linkedMapOf(
                            "kind" to PreferenceElement.ofString("move"),
                            "x" to PreferenceElement.ofDouble(targetX.toDouble()),
                            "y" to PreferenceElement.ofDouble(targetY.toDouble()),
                            "button" to PreferenceElement.ofInt(targetBtn)
                        )
                    )
                )
            }
            return
        }
        dispatchBackground {
            if (!isWorkerAlive) return@dispatchBackground
            val reply = sendRequestSuspend(
                type = WebViewIpcPacketTypes.CMD_POINTER_EVENT,
                payload = PreferenceElement.ofObject(
                    linkedMapOf(
                        "kind" to PreferenceElement.ofString(kind),
                        "x" to PreferenceElement.ofDouble(x.toDouble()),
                        "y" to PreferenceElement.ofDouble(y.toDouble()),
                        "button" to PreferenceElement.ofInt(button)
                    )
                )
            )
            if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            }
        }
    }

    fun dispatchWheelEvent(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (!isWorkerAlive) return
        sendRequest(
            type = WebViewIpcPacketTypes.CMD_WHEEL_EVENT,
            payload = PreferenceElement.ofObject(
                linkedMapOf(
                    "x" to PreferenceElement.ofDouble(x.toDouble()),
                    "y" to PreferenceElement.ofDouble(y.toDouble()),
                    "deltaX" to PreferenceElement.ofDouble(deltaX.toDouble()),
                    "deltaY" to PreferenceElement.ofDouble(deltaY.toDouble())
                )
            )
        )
    }

    fun dispatchWheelEventSmart(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (!shouldUseNonBlockingUiMode()) {
            dispatchWheelEvent(x, y, deltaX, deltaY)
            return
        }
        dispatchBackground {
            if (!isWorkerAlive) return@dispatchBackground
            sendRequestSuspend(
                type = WebViewIpcPacketTypes.CMD_WHEEL_EVENT,
                payload = PreferenceElement.ofObject(
                    linkedMapOf(
                        "x" to PreferenceElement.ofDouble(x.toDouble()),
                        "y" to PreferenceElement.ofDouble(y.toDouble()),
                        "deltaX" to PreferenceElement.ofDouble(deltaX.toDouble()),
                        "deltaY" to PreferenceElement.ofDouble(deltaY.toDouble())
                    )
                )
            )
        }
    }

    fun dispatchKeyEvent(
        kind: String,
        key: String,
        code: String = key,
        keyCode: Int = 0,
        shift: Boolean = false,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
        text: String = ""
    ) {
        if (!isWorkerAlive) return
        sendRequest(
            type = WebViewIpcPacketTypes.CMD_KEY_EVENT,
            payload = PreferenceElement.ofObject(
                linkedMapOf(
                    "kind" to PreferenceElement.ofString(kind),
                    "key" to PreferenceElement.ofString(key),
                    "code" to PreferenceElement.ofString(code),
                    "keyCode" to PreferenceElement.ofInt(keyCode),
                    "shift" to PreferenceElement.ofBoolean(shift),
                    "ctrl" to PreferenceElement.ofBoolean(ctrl),
                    "alt" to PreferenceElement.ofBoolean(alt),
                    "meta" to PreferenceElement.ofBoolean(meta),
                    "text" to PreferenceElement.ofString(text)
                )
            )
        )
    }

    fun dispatchKeyEventSmart(
        kind: String,
        key: String,
        code: String = key,
        keyCode: Int = 0,
        shift: Boolean = false,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
        text: String = ""
    ) {
        if (!shouldUseNonBlockingUiMode()) {
            dispatchKeyEvent(kind, key, code, keyCode, shift, ctrl, alt, meta, text)
            return
        }
        dispatchBackground {
            if (!isWorkerAlive) return@dispatchBackground
            val reply = sendRequestSuspend(
                type = WebViewIpcPacketTypes.CMD_KEY_EVENT,
                payload = PreferenceElement.ofObject(
                    linkedMapOf(
                        "kind" to PreferenceElement.ofString(kind),
                        "key" to PreferenceElement.ofString(key),
                        "code" to PreferenceElement.ofString(code),
                        "keyCode" to PreferenceElement.ofInt(keyCode),
                        "shift" to PreferenceElement.ofBoolean(shift),
                        "ctrl" to PreferenceElement.ofBoolean(ctrl),
                        "alt" to PreferenceElement.ofBoolean(alt),
                        "meta" to PreferenceElement.ofBoolean(meta),
                        "text" to PreferenceElement.ofString(text)
                    )
                )
            )
            if (reply.payload["pageFinished"]?.asBoolean(false) == true) {
                val outcome = applyNavStateAndCollectEvents(reply)
                emitNavOutcomeCallbacks(outcome, forceMarshalToUi = true)
            }
        }
    }

    fun dispatchFocusEvent(focused: Boolean) {
        if (!isWorkerAlive) return
        sendRequest(
            type = WebViewIpcPacketTypes.CMD_FOCUS_EVENT,
            payload = PreferenceElement.ofObject(mapOf("focused" to PreferenceElement.ofBoolean(focused)))
        )
    }

    fun dispatchFocusEventSmart(focused: Boolean) {
        if (!shouldUseNonBlockingUiMode()) {
            dispatchFocusEvent(focused)
            return
        }
        dispatchBackground {
            if (!isWorkerAlive) return@dispatchBackground
            sendRequestSuspend(
                type = WebViewIpcPacketTypes.CMD_FOCUS_EVENT,
                payload = PreferenceElement.ofObject(mapOf("focused" to PreferenceElement.ofBoolean(focused)))
            )
        }
    }

    fun tickAnimation(deltaMs: Float) {
        if (!isWorkerAlive) return
        sendRequest(
            type = WebViewIpcPacketTypes.CMD_TICK,
            payload = PreferenceElement.ofObject(mapOf("deltaMs" to PreferenceElement.ofDouble(deltaMs.toDouble())))
        )
    }

    fun pingWorker(timeoutMs: Long = 5_000L): Boolean {
        if (!isWorkerAlive) return false
        val reply = sendRequest(WebViewIpcPacketTypes.CMD_PING, PreferenceElement.Null, timeoutMs = timeoutMs)
        return reply.type == WebViewIpcPacketTypes.RES_OK
    }

    /**
     * Abruptly kills the active child OS worker process (`SIGKILL` / `TerminateProcess`) to exercise
     * real out-of-process crash detection and recovery.
     */
    fun terminateWorkerProcessForCrashRecovery(): Long? {
        val proc = workerProcess ?: return null
        val pid = proc.pid
        proc.killForcibly()
        return pid
    }

    fun waitForRestartRecovery(expectedRestartCount: Int, timeoutMs: Int = 15_000): Boolean {
        val deadlineStep = 20L
        var elapsed = 0L
        while (elapsed < timeoutMs.toLong()) {
            if (workerRestartCount >= expectedRestartCount && !restartingFlag.load() && isWorkerAlive) {
                return true
            }
            runBlocking { kotlinx.coroutines.delay(deadlineStep) }
            elapsed += deadlineStep
        }
        return workerRestartCount >= expectedRestartCount && isWorkerAlive
    }

    private fun applyNavStateAndCollectEvents(reply: IpcPacket): NavReplyOutcome {
        val tilesApplied = (reply.payload["tiles"]?.arrayElements?.isNotEmpty() == true)
        framebuffer.applyDirtyTilesFromPacket(reply)
        val rawUrl = reply.payload["url"]?.asString()?.takeIf { it.isNotEmpty() }
        val title = reply.payload["title"]?.asString()
        val favEl = reply.payload["faviconUrl"]
        val hasFavicon = favEl != null
        val faviconUrl = favEl?.takeIf { !it.isNull }?.asString()?.takeIf { it.isNotEmpty() }
        val rawBack = reply.payload["canGoBack"]
        val rawFwd = reply.payload["canGoForward"]
        var resolvedUrl = ""
        var titleChanged = false
        var faviconChanged = false
        var navChanged = false
        var resolvedBack = false
        var resolvedFwd = false
        withPendingLock {
            if (rawUrl != null) {
                currentUrlState = rawUrl
            }
            resolvedUrl = currentUrlState.orEmpty()
            if (title != null && title != currentTitleState) {
                currentTitleState = title
                titleChanged = true
            }
            if (hasFavicon && faviconUrl != currentFaviconUrlState) {
                currentFaviconUrlState = faviconUrl
                faviconChanged = true
            }
            val nextBack = rawBack?.asBoolean(canGoBackState) ?: canGoBackState
            val nextFwd = rawFwd?.asBoolean(canGoForwardState) ?: canGoForwardState
            if (nextBack != canGoBackState || nextFwd != canGoForwardState) {
                canGoBackState = nextBack
                canGoForwardState = nextFwd
                navChanged = true
            }
            resolvedBack = canGoBackState
            resolvedFwd = canGoForwardState
        }
        val pageFinished = reply.payload["pageFinished"]?.asBoolean(false) ?: false
        val finishedUrl = reply.payload["pageFinishedUrl"]?.asString()?.takeIf { it.isNotEmpty() } ?: resolvedUrl
        val wasLoading = if (pageFinished || reply.error != null) loadingFlag.compareAndSet(true, false) else false
        return NavReplyOutcome(
            resolvedUrl = resolvedUrl,
            title = title,
            titleChanged = titleChanged,
            faviconUrl = faviconUrl,
            faviconChanged = faviconChanged,
            canGoBack = resolvedBack,
            canGoForward = resolvedFwd,
            navChanged = navChanged,
            tilesApplied = tilesApplied,
            pageFinished = pageFinished,
            pageFinishedUrl = finishedUrl,
            wasLoading = wasLoading,
            errorMessage = reply.error
        )
    }

    private fun emitNavOutcomeCallbacks(outcome: NavReplyOutcome, forceMarshalToUi: Boolean) {
        val deliver = {
            if (outcome.tilesApplied) {
                onViewportRedraw?.invoke()
            }
            if (outcome.titleChanged && outcome.title != null) {
                onTitleChanged?.invoke(outcome.title)
            }
            if (outcome.faviconChanged) {
                onFaviconChanged?.invoke(outcome.faviconUrl)
            }
            if (outcome.navChanged) {
                onNavStateChanged?.invoke(outcome.canGoBack, outcome.canGoForward)
            }
            if (outcome.wasLoading) {
                onLoadingChanged?.invoke(false)
            }
            if (outcome.errorMessage != null) {
                onJavaScriptError?.invoke(outcome.errorMessage)
            }
            if (outcome.pageFinished || outcome.errorMessage != null) {
                onPageFinished?.invoke(outcome.pageFinishedUrl)
            }
        }
        if (forceMarshalToUi) {
            postCallbackToUi(forceAsync = true, block = deliver)
        } else {
            deliver()
        }
    }

    private fun handleAsyncNavFailure(fallbackUrl: String, error: Throwable) {
        if (disposedFlag.load()) return
        val wasLoading = loadingFlag.compareAndSet(true, false)
        val msg = "Navigation failed for '$fallbackUrl': ${error.message ?: error::class.simpleName}"
        postCallbackToUi(forceAsync = true) {
            if (wasLoading) onLoadingChanged?.invoke(false)
            onJavaScriptError?.invoke(msg)
            onPageFinished?.invoke(fallbackUrl)
        }
    }

    private fun sendRequest(
        type: String,
        payload: PreferenceElement,
        timeoutMs: Long = DEFAULT_RPC_TIMEOUT_MS
    ): IpcPacket = runBlocking {
        sendRequestSuspend(type, payload, timeoutMs)
    }

    private suspend fun sendRequestSuspend(
        type: String,
        payload: PreferenceElement,
        timeoutMs: Long = DEFAULT_RPC_TIMEOUT_MS
    ): IpcPacket {
        check(!disposedFlag.load()) { "WebViewWorkerHostSession($webViewId) is disposed" }
        lifecycleMutex.withLock {
            ensureStartedLocked()
        }
        val conn = activeConnection
            ?: throw IllegalStateException("No active IPC connection for WebView($webViewId)")
        val reply = trySendOnConnectionSuspend(conn, type, payload, timeoutMs)
        if (reply != null) return reply

        // Connection may have died mid-call due to subprocess crash -> auto-recover once and retry.
        lifecycleMutex.withLock {
            if (!isWorkerAlive) {
                workerCrashCount++
                val shouldReplay =
                    type != WebViewIpcPacketTypes.CMD_LOAD_URL &&
                        type != WebViewIpcPacketTypes.CMD_LOAD_HTML
                recoverWorkerIfDeadLocked(replayDocument = shouldReplay)
            }
        }
        val recoveredConn = activeConnection
            ?: throw IllegalStateException("Failed to re-establish WebView($webViewId) worker IPC connection")
        return sendRequestOnConnectionSuspend(recoveredConn, type, payload, timeoutMs)
    }

    private suspend fun trySendOnConnectionSuspend(
        connection: IpcConnection,
        type: String,
        payload: PreferenceElement,
        timeoutMs: Long
    ): IpcPacket? {
        if (connection.isClosed || workerProcess?.isAlive() != true) return null
        return try {
            sendRequestOnConnectionSuspend(connection, type, payload, timeoutMs)
        } catch (t: Throwable) {
            if (disposedFlag.load() || (!connection.isClosed && workerProcess?.isAlive() == true)) {
                throw t
            }
            null
        }
    }

    private fun sendRequestOnConnectionBlocking(
        connection: IpcConnection,
        type: String,
        payload: PreferenceElement,
        timeoutMs: Long
    ): IpcPacket = runBlocking {
        sendRequestOnConnectionSuspend(connection, type, payload, timeoutMs)
    }

    private suspend fun sendRequestOnConnectionSuspend(
        connection: IpcConnection,
        type: String,
        payload: PreferenceElement,
        timeoutMs: Long
    ): IpcPacket {
        val reqId = nextRequestId.fetchAndAdd(1L)
        val waiter = WebViewHostRpcWaiter(reqId)
        withPendingLock {
            pendingReplies[reqId] = waiter
        }
        val packet = IpcPacket(
            id = reqId,
            type = type,
            payload = payload
        )
        if (!connection.sendPacket(packet)) {
            withPendingLock { pendingReplies.remove(reqId) }
            throw IllegalStateException("Failed to transmit IPC packet '$type' (id=$reqId) to worker $webViewId")
        }
        val response = waiter.awaitSuspend(timeoutMs)
        if (response == null) {
            withPendingLock { pendingReplies.remove(reqId) }
            throw IllegalStateException("Timed out after ${timeoutMs}ms waiting for worker reply to '$type' (id=$reqId)")
        }
        return response
    }

    private fun failAllPendingWaiters(error: Throwable) {
        val snapshot = withPendingLock {
            val list = pendingReplies.values.toList()
            pendingReplies.clear()
            list
        }
        for (waiter in snapshot) {
            waiter.fail(error)
        }
    }

    private fun cleanupTransportLocked() {
        readerJob?.cancel()
        readerJob = null
        activeConnection?.close()
        activeConnection = null
        workerProcess?.let { proc ->
            if (proc.isAlive()) {
                proc.killForcibly()
            }
            proc.closeHandle()
        }
        workerProcess = null
        ipcServer?.close()
        ipcServer = null
        readySignal = null
        syncedResizeW.store(-1)
        syncedResizeH.store(-1)
    }

    fun disposeSmart() {
        if (shouldUseNonBlockingUiMode()) {
            if (!disposedFlag.compareAndSet(false, true)) return
            commandChannel.close()
            failAllPendingWaiters(IllegalStateException("WebView($webViewId) disposed"))
            CoroutineScope(Dispatchers.IO).launch {
                val conn = activeConnection
                val proc = workerProcess
                if (conn != null && !conn.isClosed) {
                    val shutdownPkt = IpcPacket(
                        id = nextRequestId.fetchAndAdd(1L),
                        type = WebViewIpcPacketTypes.CMD_SHUTDOWN
                    )
                    conn.sendPacket(shutdownPkt)
                    proc?.waitForExit(SHUTDOWN_GRACE_MS)
                }
                lifecycleMutex.withLock {
                    cleanupTransportLocked()
                }
                scope.cancel()
            }
        } else {
            dispose()
        }
    }

    fun dispose() {
        if (!disposedFlag.compareAndSet(false, true)) return
        commandChannel.close()
        failAllPendingWaiters(IllegalStateException("WebView($webViewId) disposed"))
        runBlocking {
            lifecycleMutex.withLock {
                val conn = activeConnection
                val proc = workerProcess
                if (conn != null && !conn.isClosed) {
                    val shutdownPkt = IpcPacket(
                        id = nextRequestId.fetchAndAdd(1L),
                        type = WebViewIpcPacketTypes.CMD_SHUTDOWN
                    )
                    conn.sendPacket(shutdownPkt)
                    proc?.waitForExit(SHUTDOWN_GRACE_MS)
                }
                cleanupTransportLocked()
            }
        }
        scope.cancel()
    }

    private fun WebViewHostFramebuffer.applyDirtyTilesFromPacket(packet: IpcPacket) {
        val tiles = packet.payload["tiles"]?.arrayElements ?: return
        if (tiles.isEmpty()) return
        val w = packet.payload["frameWidth"]?.asInt(viewportWidth) ?: viewportWidth
        val h = packet.payload["frameHeight"]?.asInt(viewportHeight) ?: viewportHeight
        val seq = packet.payload["frameSeq"]?.asLong(0L) ?: 0L
        applyDirtyTiles(w, h, seq, tiles)
    }

    private fun escapeJsonString(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
