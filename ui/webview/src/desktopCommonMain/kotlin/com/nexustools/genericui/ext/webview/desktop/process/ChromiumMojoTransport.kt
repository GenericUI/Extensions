package com.nexustools.genericui.ext.webview.desktop.process

/**
 * Bidirectional Mojo IPC transport supporting both OS child process `stdin`/`stdout`
 * line-framed pipes and connected peer transports, with listener registration and
 * request/response correlation.
 */
class ChromiumMojoTransport(
    val channelId: String,
    val localProcess: ChromiumProcessType
) {
    var processId: Long = 0L
    var isExternalOsProcess: Boolean = false
    var executablePath: String? = null
    var commandLine: List<String> = emptyList()

    var isClosed: Boolean = false
        private set

    var sentMessagesCount: Int = 0
        private set

    var receivedMessagesCount: Int = 0
        private set

    var lastEncodedWireLine: String? = null
        private set

    var lastDecodedWireLine: String? = null
        private set

    private var nextSeqId: Long = 1L
    private var peerTransport: ChromiumMojoTransport? = null
    private var pipeWriter: ((String) -> Boolean)? = null
    private var pipeReader: (() -> String?)? = null
    private var pipeCloseHandler: (() -> Unit)? = null
    private var requestHandler: ((ChromiumMojoMessage) -> ChromiumMojoMessage?)? = null
    private val listeners = ArrayList<(ChromiumMojoMessage) -> Unit>()
    private val pendingReplies = LinkedHashMap<Long, ChromiumMojoMessage>()

    fun nextMessageId(): Long = nextSeqId++

    fun addMessageListener(listener: (ChromiumMojoMessage) -> Unit) {
        listeners.add(listener)
    }

    fun setRequestHandler(handler: ((ChromiumMojoMessage) -> ChromiumMojoMessage?)?) {
        requestHandler = handler
    }

    fun connectPeer(peer: ChromiumMojoTransport) {
        this.peerTransport = peer
        peer.peerTransport = this
    }

    fun attachPipeCallbacks(
        writer: (String) -> Boolean,
        reader: () -> String?,
        onClose: (() -> Unit)? = null
    ) {
        this.pipeWriter = writer
        this.pipeReader = reader
        this.pipeCloseHandler = onClose
    }

    fun send(message: ChromiumMojoMessage): Boolean {
        if (isClosed) return false
        val wireLine = message.encodeWireLine()
        lastEncodedWireLine = wireLine
        sentMessagesCount++

        val writer = pipeWriter
        if (writer != null) {
            val ok = runCatching { writer(wireLine) }.getOrDefault(false)
            if (ok) {
                return true
            }
        }

        val peer = peerTransport
        if (peer != null && !peer.isClosed) {
            peer.receiveWireLine(wireLine)
            return true
        }
        return false
    }

    fun sendRequest(
        type: ChromiumMojoMessageType,
        targetProcess: ChromiumProcessType,
        payload: Map<String, String> = emptyMap()
    ): ChromiumMojoMessage? {
        if (isClosed) return null
        val msgId = nextMessageId()
        val request = ChromiumMojoMessage(
            messageId = msgId,
            channelId = channelId,
            type = type,
            sourceProcess = localProcess,
            targetProcess = targetProcess,
            payload = payload
        )
        if (!send(request)) return null

        pendingReplies.remove(msgId)?.let { return it }

        val reader = pipeReader
        if (reader != null) {
            var guard = 0
            while (guard < 2048 && !isClosed) {
                guard++
                val incomingLine = runCatching { reader() }.getOrNull() ?: break
                receiveWireLine(incomingLine)
                pendingReplies.remove(msgId)?.let { return it }
            }
        }

        return pendingReplies.remove(msgId)
    }

    fun receiveWireLine(wireLine: String): ChromiumMojoMessage? {
        if (isClosed) return null
        val decoded = ChromiumMojoMessage.decodeWireLine(wireLine) ?: return null
        lastDecodedWireLine = wireLine
        receivedMessagesCount++

        val replyToId = decoded.payload["replyTo"]?.toLongOrNull()
        if (replyToId != null) {
            pendingReplies[replyToId] = decoded
        }

        val snapshot = listeners.toList()
        for (listener in snapshot) {
            listener(decoded)
        }

        if (replyToId == null) {
            val handler = requestHandler
            if (handler != null) {
                val response = handler(decoded)
                if (response != null) {
                    send(response)
                }
            }
        }
        return decoded
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        runCatching { pipeCloseHandler?.invoke() }
        pipeWriter = null
        pipeReader = null
        pipeCloseHandler = null
        val peer = peerTransport
        peerTransport = null
        if (peer != null && !peer.isClosed) {
            peer.isClosed = true
            peer.peerTransport = null
        }
    }

    companion object {
        fun createLinkedPair(
            channelId: String,
            firstProcess: ChromiumProcessType,
            secondProcess: ChromiumProcessType
        ): Pair<ChromiumMojoTransport, ChromiumMojoTransport> {
            val first = ChromiumMojoTransport(channelId, firstProcess)
            val second = ChromiumMojoTransport(channelId, secondProcess)
            first.connectPeer(second)
            return first to second
        }
    }
}
