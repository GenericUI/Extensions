package com.nexustools.genericui.web

/**
 * Kotlin/JS implementation of [WebGl2BrowserBridge] delegating directly to the browser's
 * hardware `WebGL2RenderingContext`.
 */
actual object WebGl2BrowserBridge {

    private class ContextState(
        val canvasElement: WebDomElement,
        val canvasPeer: dynamic,
        val gl: dynamic
    ) {
        var nextObjectId: Int = 1
        var boundDrawFramebuffer: Int = 0
        var boundReadFramebuffer: Int = 0

        val shaders = LinkedHashMap<Int, dynamic>()
        val programs = LinkedHashMap<Int, dynamic>()
        val buffers = LinkedHashMap<Int, dynamic>()
        val textures = LinkedHashMap<Int, dynamic>()
        val framebuffers = LinkedHashMap<Int, dynamic>()
        val renderbuffers = LinkedHashMap<Int, dynamic>()
        val vertexArrays = LinkedHashMap<Int, dynamic>()
        val uniformLocations = LinkedHashMap<Int, dynamic>()
        val uniformLocationByProgramAndName = LinkedHashMap<String, Int>()
    }

    private var cachedWebGl2Support: Boolean? = null
    private var nextContextId: Int = 1
    private val contexts = LinkedHashMap<Int, ContextState>()
    private val contextIdByElement = LinkedHashMap<WebDomElement, Int>()

    private fun toUint8View(bytes: ByteArray): dynamic {
        val raw: dynamic = bytes
        return js(
            "(raw && raw.buffer) ? new Uint8Array(raw.buffer, raw.byteOffset || 0, raw.byteLength || raw.length) : new Uint8Array(raw)"
        )
    }

    private fun toFloat32View(floats: FloatArray): dynamic {
        val raw: dynamic = floats
        return js(
            "(raw && raw.buffer) ? new Float32Array(raw.buffer, raw.byteOffset || 0, raw.length) : new Float32Array(raw)"
        )
    }

    private fun toInt32View(ints: IntArray): dynamic {
        val raw: dynamic = ints
        return js(
            "(raw && raw.buffer) ? new Int32Array(raw.buffer, raw.byteOffset || 0, raw.length) : new Int32Array(raw)"
        )
    }

    private fun toInt16View(shorts: ShortArray): dynamic {
        val raw: dynamic = shorts
        return js(
            "(raw && raw.buffer) ? new Int16Array(raw.buffer, raw.byteOffset || 0, raw.length) : new Int16Array(raw)"
        )
    }

    actual fun isWebGl2Supported(): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return false
        cachedWebGl2Support?.let { return it }
        val supported = try {
            val doc: dynamic = js("document")
            val canvas: dynamic = doc.createElement("canvas")
            val ctx: dynamic = if (jsTypeOf(canvas.getContext) == "function") {
                canvas.getContext("webgl2")
            } else {
                null
            }
            ctx != null
        } catch (_: Throwable) {
            false
        }
        cachedWebGl2Support = supported
        return supported
    }

    actual fun getOrCreateContext(canvasElement: WebDomElement): Int {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return 0
        contextIdByElement[canvasElement]?.let { existingId ->
            val existingState = contexts[existingId]
            val currentPeer = canvasElement.nativeBrowserPeer
            if (existingState != null && (currentPeer == null || existingState.canvasPeer === currentPeer)) {
                return existingId
            }
            if (existingState != null) {
                destroyContext(existingId)
            }
        }
        var peer: dynamic = canvasElement.nativeBrowserPeer
        if (peer == null) {
            WebBrowserDomBridge.createPeer(canvasElement)
            peer = canvasElement.nativeBrowserPeer
        }
        if (peer == null) return 0

        val gl: dynamic = try {
            val initW = canvasElement.getAttribute("width")?.toIntOrNull()
                ?: canvasElement.clientWidth.takeIf { it > 0 }
                ?: 800
            val initH = canvasElement.getAttribute("height")?.toIntOrNull()
                ?: canvasElement.clientHeight.takeIf { it > 0 }
                ?: 600
            val curW = (peer.width as? Number)?.toInt() ?: 0
            val curH = (peer.height as? Number)?.toInt() ?: 0
            if (curW <= 0 || curW != initW) peer.width = initW
            if (curH <= 0 || curH != initH) peer.height = initH

            val alpha = canvasElement.getAttribute("data-webgl-alpha")?.toBooleanStrictOrNull() ?: true
            val depth = canvasElement.getAttribute("data-webgl-depth")?.toBooleanStrictOrNull() ?: true
            val stencil = canvasElement.getAttribute("data-webgl-stencil")?.toBooleanStrictOrNull() ?: false
            val antialias = canvasElement.getAttribute("data-webgl-antialias")?.toBooleanStrictOrNull() ?: true
            val attrs: dynamic = js("({})")
            attrs.alpha = alpha
            attrs.depth = depth
            attrs.stencil = stencil
            attrs.antialias = antialias
            attrs.premultipliedAlpha = true
            attrs.preserveDrawingBuffer = false
            attrs.powerPreference = "high-performance"

            if (jsTypeOf(peer.getContext) == "function") {
                peer.getContext("webgl2", attrs) ?: peer.getContext("webgl2")
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } ?: return 0

        val id = nextContextId++
        contexts[id] = ContextState(canvasElement, peer, gl)
        contextIdByElement[canvasElement] = id
        return id
    }

    actual fun destroyContext(contextId: Int) {
        val state = contexts.remove(contextId) ?: return
        contextIdByElement.remove(state.canvasElement)
        try {
            for (vao in state.vertexArrays.values) state.gl.deleteVertexArray(vao)
            for (fbo in state.framebuffers.values) state.gl.deleteFramebuffer(fbo)
            for (rbo in state.renderbuffers.values) state.gl.deleteRenderbuffer(rbo)
            for (tex in state.textures.values) state.gl.deleteTexture(tex)
            for (buf in state.buffers.values) state.gl.deleteBuffer(buf)
            for (prog in state.programs.values) state.gl.deleteProgram(prog)
            for (sh in state.shaders.values) state.gl.deleteShader(sh)
        } catch (_: Throwable) {
        }
        state.vertexArrays.clear()
        state.framebuffers.clear()
        state.renderbuffers.clear()
        state.textures.clear()
        state.buffers.clear()
        state.programs.clear()
        state.shaders.clear()
        state.uniformLocations.clear()
        state.uniformLocationByProgramAndName.clear()
    }

    actual fun getString(contextId: Int, name: Int): String {
        val state = contexts[contextId] ?: return ""
        return try {
            (state.gl.getParameter(name) as? String) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    actual fun getInteger(contextId: Int, pname: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val res: dynamic = state.gl.getParameter(pname)
            when (res) {
                is Number -> res.toInt()
                is Boolean -> if (res) 1 else 0
                else -> 0
            }
        } catch (_: Throwable) {
            0
        }
    }

    actual fun getError(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            (state.gl.getError() as? Number)?.toInt() ?: 0
        } catch (_: Throwable) {
            0
        }
    }

    actual fun viewport(contextId: Int, x: Int, y: Int, width: Int, height: Int) {
        val state = contexts[contextId] ?: return
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        try {
            if (x == 0 && y == 0 && state.boundDrawFramebuffer == 0) {
                val curW = (state.canvasPeer.width as? Number)?.toInt() ?: 0
                val curH = (state.canvasPeer.height as? Number)?.toInt() ?: 0
                if (curW != w || curH != h) {
                    state.canvasPeer.width = w
                    state.canvasPeer.height = h
                    state.canvasElement.clientWidth = w
                    state.canvasElement.clientHeight = h
                }
            }
            state.gl.viewport(x, y, w, h)
        } catch (_: Throwable) {
        }
    }

    actual fun scissor(contextId: Int, x: Int, y: Int, width: Int, height: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.scissor(x, y, width.coerceAtLeast(0), height.coerceAtLeast(0))
        } catch (_: Throwable) {
        }
    }

    actual fun clearColor(contextId: Int, r: Float, g: Float, b: Float, a: Float) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.clearColor(r, g, b, a)
        } catch (_: Throwable) {
        }
    }

    actual fun clearDepth(contextId: Int, depth: Float) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.clearDepth(depth)
        } catch (_: Throwable) {
        }
    }

    actual fun clearStencil(contextId: Int, stencil: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.clearStencil(stencil)
        } catch (_: Throwable) {
        }
    }

    actual fun clear(contextId: Int, mask: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.clear(mask)
        } catch (_: Throwable) {
        }
    }

    actual fun enable(contextId: Int, cap: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.enable(cap)
        } catch (_: Throwable) {
        }
    }

    actual fun disable(contextId: Int, cap: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.disable(cap)
        } catch (_: Throwable) {
        }
    }

    actual fun blendFunc(contextId: Int, sfactor: Int, dfactor: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.blendFunc(sfactor, dfactor)
        } catch (_: Throwable) {
        }
    }

    actual fun blendFuncSeparate(contextId: Int, srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha)
        } catch (_: Throwable) {
        }
    }

    actual fun blendEquation(contextId: Int, mode: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.blendEquation(mode)
        } catch (_: Throwable) {
        }
    }

    actual fun depthFunc(contextId: Int, func: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.depthFunc(func)
        } catch (_: Throwable) {
        }
    }

    actual fun depthMask(contextId: Int, flag: Boolean) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.depthMask(flag)
        } catch (_: Throwable) {
        }
    }

    actual fun cullFace(contextId: Int, mode: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.cullFace(mode)
        } catch (_: Throwable) {
        }
    }

    actual fun frontFace(contextId: Int, mode: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.frontFace(mode)
        } catch (_: Throwable) {
        }
    }

    actual fun colorMask(contextId: Int, red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.colorMask(red, green, blue, alpha)
        } catch (_: Throwable) {
        }
    }

    actual fun createShader(contextId: Int, type: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val shader: dynamic = state.gl.createShader(type) ?: return 0
            val id = state.nextObjectId++
            state.shaders[id] = shader
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun shaderSource(contextId: Int, shaderId: Int, source: String) {
        val state = contexts[contextId] ?: return
        val shader = state.shaders[shaderId] ?: return
        val trimmed = source.trimStart()
        val cleanSource = if (trimmed.startsWith("#version")) trimmed else source
        try {
            state.gl.shaderSource(shader, cleanSource)
        } catch (_: Throwable) {
        }
    }

    actual fun compileShader(contextId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val shader = state.shaders[shaderId] ?: return
        try {
            state.gl.compileShader(shader)
        } catch (_: Throwable) {
        }
    }

    actual fun getShaderParameter(contextId: Int, shaderId: Int, pname: Int): Int {
        val state = contexts[contextId] ?: return 0
        val shader = state.shaders[shaderId] ?: return 0
        return try {
            val res: dynamic = state.gl.getShaderParameter(shader, pname)
            when (res) {
                is Boolean -> if (res) 1 else 0
                is Number -> res.toInt()
                else -> if (res == true) 1 else 0
            }
        } catch (_: Throwable) {
            0
        }
    }

    actual fun getShaderInfoLog(contextId: Int, shaderId: Int): String {
        val state = contexts[contextId] ?: return ""
        val shader = state.shaders[shaderId] ?: return ""
        return try {
            (state.gl.getShaderInfoLog(shader) as? String) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    actual fun deleteShader(contextId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val shader = state.shaders.remove(shaderId) ?: return
        try {
            state.gl.deleteShader(shader)
        } catch (_: Throwable) {
        }
    }

    actual fun createProgram(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val program: dynamic = state.gl.createProgram() ?: return 0
            val id = state.nextObjectId++
            state.programs[id] = program
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun attachShader(contextId: Int, programId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val program = state.programs[programId] ?: return
        val shader = state.shaders[shaderId] ?: return
        try {
            state.gl.attachShader(program, shader)
        } catch (_: Throwable) {
        }
    }

    actual fun detachShader(contextId: Int, programId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val program = state.programs[programId] ?: return
        val shader = state.shaders[shaderId] ?: return
        try {
            state.gl.detachShader(program, shader)
        } catch (_: Throwable) {
        }
    }

    actual fun linkProgram(contextId: Int, programId: Int) {
        val state = contexts[contextId] ?: return
        val program = state.programs[programId] ?: return
        try {
            state.gl.linkProgram(program)
        } catch (_: Throwable) {
        }
    }

    actual fun getProgramParameter(contextId: Int, programId: Int, pname: Int): Int {
        val state = contexts[contextId] ?: return 0
        val program = state.programs[programId] ?: return 0
        return try {
            val res: dynamic = state.gl.getProgramParameter(program, pname)
            when (res) {
                is Boolean -> if (res) 1 else 0
                is Number -> res.toInt()
                else -> if (res == true) 1 else 0
            }
        } catch (_: Throwable) {
            0
        }
    }

    actual fun getProgramInfoLog(contextId: Int, programId: Int): String {
        val state = contexts[contextId] ?: return ""
        val program = state.programs[programId] ?: return ""
        return try {
            (state.gl.getProgramInfoLog(program) as? String) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    actual fun useProgram(contextId: Int, programId: Int) {
        val state = contexts[contextId] ?: return
        val program = if (programId == 0) null else state.programs[programId]
        try {
            state.gl.useProgram(program)
        } catch (_: Throwable) {
        }
    }

    actual fun deleteProgram(contextId: Int, programId: Int) {
        val state = contexts[contextId] ?: return
        val program = state.programs.remove(programId) ?: return
        val prefix = "$programId:"
        val keysToRemove = state.uniformLocationByProgramAndName.keys.filter { it.startsWith(prefix) }
        for (k in keysToRemove) {
            val locId = state.uniformLocationByProgramAndName.remove(k)
            if (locId != null) {
                state.uniformLocations.remove(locId)
            }
        }
        try {
            state.gl.deleteProgram(program)
        } catch (_: Throwable) {
        }
    }

    actual fun getAttribLocation(contextId: Int, programId: Int, name: String): Int {
        val state = contexts[contextId] ?: return -1
        val program = state.programs[programId] ?: return -1
        return try {
            (state.gl.getAttribLocation(program, name) as? Number)?.toInt() ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    actual fun getUniformLocation(contextId: Int, programId: Int, name: String): Int {
        val state = contexts[contextId] ?: return 0
        val program = state.programs[programId] ?: return 0
        val cacheKey = "$programId:$name"
        state.uniformLocationByProgramAndName[cacheKey]?.let { existingId ->
            if (state.uniformLocations.containsKey(existingId)) return existingId
        }
        return try {
            val loc: dynamic = state.gl.getUniformLocation(program, name) ?: return 0
            val id = state.nextObjectId++
            state.uniformLocations[id] = loc
            state.uniformLocationByProgramAndName[cacheKey] = id
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun getUniformBlockIndex(contextId: Int, programId: Int, blockName: String): Int {
        val state = contexts[contextId] ?: return -1
        val program = state.programs[programId] ?: return -1
        return try {
            (state.gl.getUniformBlockIndex(program, blockName) as? Number)?.toInt() ?: -1
        } catch (_: Throwable) {
            -1
        }
    }

    actual fun uniformBlockBinding(
        contextId: Int,
        programId: Int,
        uniformBlockIndex: Int,
        uniformBlockBinding: Int
    ) {
        val state = contexts[contextId] ?: return
        val program = state.programs[programId] ?: return
        try {
            state.gl.uniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding)
        } catch (_: Throwable) {
        }
    }

    actual fun uniform1i(contextId: Int, locationId: Int, v0: Int) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform1i(loc, v0)
        } catch (_: Throwable) {
        }
    }

    actual fun uniform1f(contextId: Int, locationId: Int, v0: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform1f(loc, v0)
        } catch (_: Throwable) {
        }
    }

    actual fun uniform2f(contextId: Int, locationId: Int, v0: Float, v1: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform2f(loc, v0, v1)
        } catch (_: Throwable) {
        }
    }

    actual fun uniform3f(contextId: Int, locationId: Int, v0: Float, v1: Float, v2: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform3f(loc, v0, v1, v2)
        } catch (_: Throwable) {
        }
    }

    actual fun uniform4f(contextId: Int, locationId: Int, v0: Float, v1: Float, v2: Float, v3: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform4f(loc, v0, v1, v2, v3)
        } catch (_: Throwable) {
        }
    }

    actual fun uniform1fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform1fv(loc, toFloat32View(values))
        } catch (_: Throwable) {
        }
    }

    actual fun uniform2fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform2fv(loc, toFloat32View(values))
        } catch (_: Throwable) {
        }
    }

    actual fun uniform3fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform3fv(loc, toFloat32View(values))
        } catch (_: Throwable) {
        }
    }

    actual fun uniform4fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniform4fv(loc, toFloat32View(values))
        } catch (_: Throwable) {
        }
    }

    actual fun uniformMatrix3fv(contextId: Int, locationId: Int, transpose: Boolean, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniformMatrix3fv(loc, transpose, toFloat32View(values))
        } catch (_: Throwable) {
        }
    }

    actual fun uniformMatrix4fv(contextId: Int, locationId: Int, transpose: Boolean, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        try {
            state.gl.uniformMatrix4fv(loc, transpose, toFloat32View(values))
        } catch (_: Throwable) {
        }
    }

    actual fun createBuffer(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val buf: dynamic = state.gl.createBuffer() ?: return 0
            val id = state.nextObjectId++
            state.buffers[id] = buf
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun bindBuffer(contextId: Int, target: Int, bufferId: Int) {
        val state = contexts[contextId] ?: return
        val buf = if (bufferId == 0) null else state.buffers[bufferId]
        try {
            state.gl.bindBuffer(target, buf)
        } catch (_: Throwable) {
        }
    }

    actual fun bindBufferBase(contextId: Int, target: Int, index: Int, bufferId: Int) {
        val state = contexts[contextId] ?: return
        val buf = if (bufferId == 0) null else state.buffers[bufferId]
        try {
            state.gl.bindBufferBase(target, index, buf)
        } catch (_: Throwable) {
        }
    }

    actual fun bufferData(contextId: Int, target: Int, sizeBytes: Int, usage: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.bufferData(target, sizeBytes.coerceAtLeast(0), usage)
        } catch (_: Throwable) {
        }
    }

    actual fun bufferData(contextId: Int, target: Int, data: ByteArray, usage: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.bufferData(target, toUint8View(data), usage)
        } catch (_: Throwable) {
        }
    }

    actual fun bufferData(contextId: Int, target: Int, data: FloatArray, usage: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.bufferData(target, toFloat32View(data), usage)
        } catch (_: Throwable) {
        }
    }

    actual fun bufferData(contextId: Int, target: Int, data: IntArray, usage: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.bufferData(target, toInt32View(data), usage)
        } catch (_: Throwable) {
        }
    }

    actual fun bufferData(contextId: Int, target: Int, data: ShortArray, usage: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.bufferData(target, toInt16View(data), usage)
        } catch (_: Throwable) {
        }
    }

    actual fun bufferSubData(contextId: Int, target: Int, offsetBytes: Int, data: ByteArray) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.bufferSubData(target, offsetBytes.coerceAtLeast(0), toUint8View(data))
        } catch (_: Throwable) {
        }
    }

    actual fun bufferSubData(contextId: Int, target: Int, offsetBytes: Int, data: FloatArray) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.bufferSubData(target, offsetBytes.coerceAtLeast(0), toFloat32View(data))
        } catch (_: Throwable) {
        }
    }

    actual fun deleteBuffer(contextId: Int, bufferId: Int) {
        val state = contexts[contextId] ?: return
        val buf = state.buffers.remove(bufferId) ?: return
        try {
            state.gl.deleteBuffer(buf)
        } catch (_: Throwable) {
        }
    }

    actual fun createVertexArray(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val vao: dynamic = state.gl.createVertexArray() ?: return 0
            val id = state.nextObjectId++
            state.vertexArrays[id] = vao
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun bindVertexArray(contextId: Int, vaoId: Int) {
        val state = contexts[contextId] ?: return
        val vao = if (vaoId == 0) null else state.vertexArrays[vaoId]
        try {
            state.gl.bindVertexArray(vao)
        } catch (_: Throwable) {
        }
    }

    actual fun deleteVertexArray(contextId: Int, vaoId: Int) {
        val state = contexts[contextId] ?: return
        val vao = state.vertexArrays.remove(vaoId) ?: return
        try {
            state.gl.deleteVertexArray(vao)
        } catch (_: Throwable) {
        }
    }

    actual fun enableVertexAttribArray(contextId: Int, index: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.enableVertexAttribArray(index)
        } catch (_: Throwable) {
        }
    }

    actual fun disableVertexAttribArray(contextId: Int, index: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.disableVertexAttribArray(index)
        } catch (_: Throwable) {
        }
    }

    actual fun vertexAttribPointer(
        contextId: Int,
        index: Int,
        size: Int,
        type: Int,
        normalized: Boolean,
        stride: Int,
        offset: Int
    ) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.vertexAttribPointer(index, size, type, normalized, stride, offset)
        } catch (_: Throwable) {
        }
    }

    actual fun vertexAttribDivisor(contextId: Int, index: Int, divisor: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.vertexAttribDivisor(index, divisor)
        } catch (_: Throwable) {
        }
    }

    actual fun createTexture(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val tex: dynamic = state.gl.createTexture() ?: return 0
            val id = state.nextObjectId++
            state.textures[id] = tex
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun bindTexture(contextId: Int, target: Int, textureId: Int) {
        val state = contexts[contextId] ?: return
        val tex = if (textureId == 0) null else state.textures[textureId]
        try {
            state.gl.bindTexture(target, tex)
        } catch (_: Throwable) {
        }
    }

    actual fun activeTexture(contextId: Int, textureUnit: Int) {
        val state = contexts[contextId] ?: return
        val glUnit = if (textureUnit >= 0x84C0) textureUnit else 0x84C0 + textureUnit.coerceAtLeast(0)
        try {
            state.gl.activeTexture(glUnit)
        } catch (_: Throwable) {
        }
    }

    actual fun texImage2D(
        contextId: Int,
        target: Int,
        level: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        border: Int,
        format: Int,
        type: Int,
        pixels: ByteArray?
    ) {
        val state = contexts[contextId] ?: return
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        try {
            if (pixels == null) {
                state.gl.texImage2D(target, level, internalFormat, safeW, safeH, border, format, type, null)
            } else {
                state.gl.pixelStorei(0x0CF5, 1) // UNPACK_ALIGNMENT = 1
                state.gl.texImage2D(
                    target,
                    level,
                    internalFormat,
                    safeW,
                    safeH,
                    border,
                    format,
                    type,
                    toUint8View(pixels)
                )
            }
        } catch (_: Throwable) {
        }
    }

    actual fun texSubImage2D(
        contextId: Int,
        target: Int,
        level: Int,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        pixels: ByteArray
    ) {
        val state = contexts[contextId] ?: return
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        try {
            state.gl.pixelStorei(0x0CF5, 1) // UNPACK_ALIGNMENT = 1
            state.gl.texSubImage2D(
                target,
                level,
                xOffset,
                yOffset,
                safeW,
                safeH,
                format,
                type,
                toUint8View(pixels)
            )
        } catch (_: Throwable) {
        }
    }

    actual fun texParameteri(contextId: Int, target: Int, pname: Int, param: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.texParameteri(target, pname, param)
        } catch (_: Throwable) {
        }
    }

    actual fun texParameterf(contextId: Int, target: Int, pname: Int, param: Float) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.texParameterf(target, pname, param)
        } catch (_: Throwable) {
        }
    }

    actual fun generateMipmap(contextId: Int, target: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.generateMipmap(target)
        } catch (_: Throwable) {
        }
    }

    actual fun deleteTexture(contextId: Int, textureId: Int) {
        val state = contexts[contextId] ?: return
        val tex = state.textures.remove(textureId) ?: return
        try {
            state.gl.deleteTexture(tex)
        } catch (_: Throwable) {
        }
    }

    actual fun createFramebuffer(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val fbo: dynamic = state.gl.createFramebuffer() ?: return 0
            val id = state.nextObjectId++
            state.framebuffers[id] = fbo
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun bindFramebuffer(contextId: Int, target: Int, framebufferId: Int) {
        val state = contexts[contextId] ?: return
        when (target) {
            0x8D40 -> { // GL_FRAMEBUFFER
                state.boundDrawFramebuffer = framebufferId
                state.boundReadFramebuffer = framebufferId
            }
            0x8CA9 -> state.boundDrawFramebuffer = framebufferId // GL_DRAW_FRAMEBUFFER
            0x8CA8 -> state.boundReadFramebuffer = framebufferId // GL_READ_FRAMEBUFFER
        }
        val fbo = if (framebufferId == 0) null else state.framebuffers[framebufferId]
        try {
            state.gl.bindFramebuffer(target, fbo)
        } catch (_: Throwable) {
        }
    }

    actual fun framebufferTexture2D(
        contextId: Int,
        target: Int,
        attachment: Int,
        textarget: Int,
        textureId: Int,
        level: Int
    ) {
        val state = contexts[contextId] ?: return
        val tex = if (textureId == 0) null else state.textures[textureId]
        try {
            state.gl.framebufferTexture2D(target, attachment, textarget, tex, level)
        } catch (_: Throwable) {
        }
    }

    actual fun framebufferRenderbuffer(
        contextId: Int,
        target: Int,
        attachment: Int,
        renderbufferTarget: Int,
        renderbufferId: Int
    ) {
        val state = contexts[contextId] ?: return
        val rbo = if (renderbufferId == 0) null else state.renderbuffers[renderbufferId]
        try {
            state.gl.framebufferRenderbuffer(target, attachment, renderbufferTarget, rbo)
        } catch (_: Throwable) {
        }
    }

    actual fun checkFramebufferStatus(contextId: Int, target: Int): Int {
        val state = contexts[contextId] ?: return 0x8CD5
        return try {
            (state.gl.checkFramebufferStatus(target) as? Number)?.toInt() ?: 0x8CD5
        } catch (_: Throwable) {
            0x8CD5
        }
    }

    actual fun deleteFramebuffer(contextId: Int, framebufferId: Int) {
        val state = contexts[contextId] ?: return
        if (state.boundDrawFramebuffer == framebufferId) state.boundDrawFramebuffer = 0
        if (state.boundReadFramebuffer == framebufferId) state.boundReadFramebuffer = 0
        val fbo = state.framebuffers.remove(framebufferId) ?: return
        try {
            state.gl.deleteFramebuffer(fbo)
        } catch (_: Throwable) {
        }
    }

    actual fun createRenderbuffer(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return try {
            val rbo: dynamic = state.gl.createRenderbuffer() ?: return 0
            val id = state.nextObjectId++
            state.renderbuffers[id] = rbo
            id
        } catch (_: Throwable) {
            0
        }
    }

    actual fun bindRenderbuffer(contextId: Int, target: Int, renderbufferId: Int) {
        val state = contexts[contextId] ?: return
        val rbo = if (renderbufferId == 0) null else state.renderbuffers[renderbufferId]
        try {
            state.gl.bindRenderbuffer(target, rbo)
        } catch (_: Throwable) {
        }
    }

    actual fun renderbufferStorage(
        contextId: Int,
        target: Int,
        internalFormat: Int,
        width: Int,
        height: Int
    ) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.renderbufferStorage(target, internalFormat, width.coerceAtLeast(1), height.coerceAtLeast(1))
        } catch (_: Throwable) {
        }
    }

    actual fun deleteRenderbuffer(contextId: Int, renderbufferId: Int) {
        val state = contexts[contextId] ?: return
        val rbo = state.renderbuffers.remove(renderbufferId) ?: return
        try {
            state.gl.deleteRenderbuffer(rbo)
        } catch (_: Throwable) {
        }
    }

    actual fun drawArrays(contextId: Int, mode: Int, first: Int, count: Int) {
        if (count <= 0) return
        val state = contexts[contextId] ?: return
        try {
            state.gl.drawArrays(mode, first, count)
        } catch (_: Throwable) {
        }
    }

    actual fun drawElements(contextId: Int, mode: Int, count: Int, type: Int, offset: Int) {
        if (count <= 0) return
        val state = contexts[contextId] ?: return
        try {
            state.gl.drawElements(mode, count, type, offset)
        } catch (_: Throwable) {
        }
    }

    actual fun drawArraysInstanced(contextId: Int, mode: Int, first: Int, count: Int, instanceCount: Int) {
        if (count <= 0 || instanceCount <= 0) return
        val state = contexts[contextId] ?: return
        try {
            state.gl.drawArraysInstanced(mode, first, count, instanceCount)
        } catch (_: Throwable) {
        }
    }

    actual fun drawElementsInstanced(
        contextId: Int,
        mode: Int,
        count: Int,
        type: Int,
        offset: Int,
        instanceCount: Int
    ) {
        if (count <= 0 || instanceCount <= 0) return
        val state = contexts[contextId] ?: return
        try {
            state.gl.drawElementsInstanced(mode, count, type, offset, instanceCount)
        } catch (_: Throwable) {
        }
    }

    actual fun readPixels(
        contextId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int
    ): ByteArray {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val channels = if (format == 0x1907) 3 else 4
        val byteLen = safeW * safeH * channels
        val out = ByteArray(byteLen)
        val state = contexts[contextId] ?: return out
        try {
            state.gl.pixelStorei(0x0D05, 1) // PACK_ALIGNMENT = 1
            val u8: dynamic = js("new Uint8Array(byteLen)")
            state.gl.readPixels(x, y, safeW, safeH, format, type, u8)
            for (i in 0 until byteLen) {
                out[i] = ((u8[i] as Number).toInt() and 0xFF).toByte()
            }
        } catch (_: Throwable) {
        }
        return out
    }

    actual fun finish(contextId: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.finish()
        } catch (_: Throwable) {
        }
    }

    actual fun flush(contextId: Int) {
        val state = contexts[contextId] ?: return
        try {
            state.gl.flush()
        } catch (_: Throwable) {
        }
    }
}
