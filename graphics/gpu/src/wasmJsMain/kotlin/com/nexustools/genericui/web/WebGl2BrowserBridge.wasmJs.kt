@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.nexustools.genericui.web

@JsFun("() => { " +
    "try { " +
    "  if (typeof document === 'undefined') return false; " +
    "  const c = document.createElement('canvas'); " +
    "  return typeof c.getContext === 'function' && c.getContext('webgl2') !== null; " +
    "} catch (_e) { return false; } }")
private external fun wasmIsWebGl2Supported(): Boolean

@JsFun("(peer, initW, initH, alpha, depth, stencil, antialias) => { " +
    "try { " +
    "  if (!peer || typeof peer.getContext !== 'function') return null; " +
    "  if ((peer.width | 0) <= 0 || (peer.width | 0) !== initW) peer.width = initW; " +
    "  if ((peer.height | 0) <= 0 || (peer.height | 0) !== initH) peer.height = initH; " +
    "  const attrs = { " +
    "    alpha: !!alpha, " +
    "    depth: !!depth, " +
    "    stencil: !!stencil, " +
    "    antialias: !!antialias, " +
    "    premultipliedAlpha: true, " +
    "    preserveDrawingBuffer: false, " +
    "    powerPreference: 'high-performance' " +
    "  }; " +
    "  return peer.getContext('webgl2', attrs) || peer.getContext('webgl2') || null; " +
    "} catch (_e) { return null; } }")
private external fun wasmGetOrCreateWebGl2Context(
    peer: JsAny,
    initW: Int,
    initH: Int,
    alpha: Boolean,
    depth: Boolean,
    stencil: Boolean,
    antialias: Boolean
): JsAny?

@JsFun("(peer, w, h) => { " +
    "try { " +
    "  if ((peer.width | 0) !== w) peer.width = w; " +
    "  if ((peer.height | 0) !== h) peer.height = h; " +
    "} catch (_e) {} }")
private external fun wasmSyncCanvasSize(peer: JsAny, w: Int, h: Int)

@JsFun("(len) => new Uint8Array(len)")
private external fun wasmNewUint8Array(len: Int): JsAny

@JsFun("(u8, idx, packed) => { " +
    "u8[idx] = packed & 0xFF; " +
    "u8[idx + 1] = (packed >>> 8) & 0xFF; " +
    "u8[idx + 2] = (packed >>> 16) & 0xFF; " +
    "u8[idx + 3] = (packed >>> 24) & 0xFF; " +
    "}")
private external fun wasmSetUint8x4(u8: JsAny, idx: Int, packed: Int)

@JsFun("(u8, idx, b) => { u8[idx] = b & 0xFF; }")
private external fun wasmSetUint8(u8: JsAny, idx: Int, b: Int)

@JsFun("(u8, idx) => { " +
    "return ((u8[idx] & 0xFF) | ((u8[idx + 1] & 0xFF) << 8) | ((u8[idx + 2] & 0xFF) << 16) | ((u8[idx + 3] & 0xFF) << 24)) | 0; " +
    "}")
private external fun wasmGetUint8x4(u8: JsAny, idx: Int): Int

@JsFun("(u8, idx) => (u8[idx] & 0xFF) | 0")
private external fun wasmGetUint8(u8: JsAny, idx: Int): Int

@JsFun("(len) => new Float32Array(len)")
private external fun wasmNewFloat32Array(len: Int): JsAny

@JsFun("(f32, idx, v0, v1, v2, v3) => { " +
    "f32[idx] = v0; f32[idx + 1] = v1; f32[idx + 2] = v2; f32[idx + 3] = v3; " +
    "}")
private external fun wasmSetFloat32x4(f32: JsAny, idx: Int, v0: Float, v1: Float, v2: Float, v3: Float)

@JsFun("(f32, idx, v) => { f32[idx] = v; }")
private external fun wasmSetFloat32(f32: JsAny, idx: Int, v: Float)

@JsFun("(len) => new Int32Array(len)")
private external fun wasmNewInt32Array(len: Int): JsAny

@JsFun("(i32, idx, v0, v1, v2, v3) => { " +
    "i32[idx] = v0; i32[idx + 1] = v1; i32[idx + 2] = v2; i32[idx + 3] = v3; " +
    "}")
private external fun wasmSetInt32x4(i32: JsAny, idx: Int, v0: Int, v1: Int, v2: Int, v3: Int)

@JsFun("(i32, idx, v) => { i32[idx] = v; }")
private external fun wasmSetInt32(i32: JsAny, idx: Int, v: Int)

@JsFun("(len) => new Int16Array(len)")
private external fun wasmNewInt16Array(len: Int): JsAny

@JsFun("(i16, idx, v) => { i16[idx] = v; }")
private external fun wasmSetInt16(i16: JsAny, idx: Int, v: Int)

@JsFun("(gl, name) => { try { const s = gl.getParameter(name); return typeof s === 'string' ? s : ''; } catch (_e) { return ''; } }")
private external fun wasmGlGetString(gl: JsAny, name: Int): String

@JsFun("(gl, pname) => { " +
    "try { " +
    "  const v = gl.getParameter(pname); " +
    "  if (typeof v === 'number') return v | 0; " +
    "  if (typeof v === 'boolean') return v ? 1 : 0; " +
    "  return 0; " +
    "} catch (_e) { return 0; } }")
private external fun wasmGlGetInteger(gl: JsAny, pname: Int): Int

@JsFun("(gl) => { try { return gl.getError() | 0; } catch (_e) { return 0; } }")
private external fun wasmGlGetError(gl: JsAny): Int

@JsFun("(gl, x, y, w, h) => { try { gl.viewport(x, y, w, h); } catch (_e) {} }")
private external fun wasmGlViewport(gl: JsAny, x: Int, y: Int, w: Int, h: Int)

@JsFun("(gl, x, y, w, h) => { try { gl.scissor(x, y, w, h); } catch (_e) {} }")
private external fun wasmGlScissor(gl: JsAny, x: Int, y: Int, w: Int, h: Int)

@JsFun("(gl, r, g, b, a) => { try { gl.clearColor(r, g, b, a); } catch (_e) {} }")
private external fun wasmGlClearColor(gl: JsAny, r: Float, g: Float, b: Float, a: Float)

@JsFun("(gl, d) => { try { gl.clearDepth(d); } catch (_e) {} }")
private external fun wasmGlClearDepth(gl: JsAny, d: Float)

@JsFun("(gl, s) => { try { gl.clearStencil(s); } catch (_e) {} }")
private external fun wasmGlClearStencil(gl: JsAny, s: Int)

@JsFun("(gl, mask) => { try { gl.clear(mask); } catch (_e) {} }")
private external fun wasmGlClear(gl: JsAny, mask: Int)

@JsFun("(gl, cap) => { try { gl.enable(cap); } catch (_e) {} }")
private external fun wasmGlEnable(gl: JsAny, cap: Int)

@JsFun("(gl, cap) => { try { gl.disable(cap); } catch (_e) {} }")
private external fun wasmGlDisable(gl: JsAny, cap: Int)

@JsFun("(gl, sfactor, dfactor) => { try { gl.blendFunc(sfactor, dfactor); } catch (_e) {} }")
private external fun wasmGlBlendFunc(gl: JsAny, sfactor: Int, dfactor: Int)

@JsFun("(gl, srcRGB, dstRGB, srcA, dstA) => { try { gl.blendFuncSeparate(srcRGB, dstRGB, srcA, dstA); } catch (_e) {} }")
private external fun wasmGlBlendFuncSeparate(gl: JsAny, srcRGB: Int, dstRGB: Int, srcA: Int, dstA: Int)

@JsFun("(gl, mode) => { try { gl.blendEquation(mode); } catch (_e) {} }")
private external fun wasmGlBlendEquation(gl: JsAny, mode: Int)

@JsFun("(gl, func) => { try { gl.depthFunc(func); } catch (_e) {} }")
private external fun wasmGlDepthFunc(gl: JsAny, func: Int)

@JsFun("(gl, flag) => { try { gl.depthMask(flag); } catch (_e) {} }")
private external fun wasmGlDepthMask(gl: JsAny, flag: Boolean)

@JsFun("(gl, mode) => { try { gl.cullFace(mode); } catch (_e) {} }")
private external fun wasmGlCullFace(gl: JsAny, mode: Int)

@JsFun("(gl, mode) => { try { gl.frontFace(mode); } catch (_e) {} }")
private external fun wasmGlFrontFace(gl: JsAny, mode: Int)

@JsFun("(gl, r, g, b, a) => { try { gl.colorMask(r, g, b, a); } catch (_e) {} }")
private external fun wasmGlColorMask(gl: JsAny, r: Boolean, g: Boolean, b: Boolean, a: Boolean)

@JsFun("(gl, type) => { try { return gl.createShader(type); } catch (_e) { return null; } }")
private external fun wasmGlCreateShader(gl: JsAny, type: Int): JsAny?

@JsFun("(gl, shader, src) => { try { gl.shaderSource(shader, src); } catch (_e) {} }")
private external fun wasmGlShaderSource(gl: JsAny, shader: JsAny, src: String)

@JsFun("(gl, shader) => { try { gl.compileShader(shader); } catch (_e) {} }")
private external fun wasmGlCompileShader(gl: JsAny, shader: JsAny)

@JsFun("(gl, shader, pname) => { " +
    "try { " +
    "  const v = gl.getShaderParameter(shader, pname); " +
    "  if (typeof v === 'boolean') return v ? 1 : 0; " +
    "  if (typeof v === 'number') return v | 0; " +
    "  return v ? 1 : 0; " +
    "} catch (_e) { return 0; } }")
private external fun wasmGlGetShaderParameter(gl: JsAny, shader: JsAny, pname: Int): Int

@JsFun("(gl, shader) => { try { const s = gl.getShaderInfoLog(shader); return typeof s === 'string' ? s : ''; } catch (_e) { return ''; } }")
private external fun wasmGlGetShaderInfoLog(gl: JsAny, shader: JsAny): String

@JsFun("(gl, shader) => { try { gl.deleteShader(shader); } catch (_e) {} }")
private external fun wasmGlDeleteShader(gl: JsAny, shader: JsAny)

@JsFun("(gl) => { try { return gl.createProgram(); } catch (_e) { return null; } }")
private external fun wasmGlCreateProgram(gl: JsAny): JsAny?

@JsFun("(gl, prog, shader) => { try { gl.attachShader(prog, shader); } catch (_e) {} }")
private external fun wasmGlAttachShader(gl: JsAny, prog: JsAny, shader: JsAny)

@JsFun("(gl, prog, shader) => { try { gl.detachShader(prog, shader); } catch (_e) {} }")
private external fun wasmGlDetachShader(gl: JsAny, prog: JsAny, shader: JsAny)

@JsFun("(gl, prog) => { try { gl.linkProgram(prog); } catch (_e) {} }")
private external fun wasmGlLinkProgram(gl: JsAny, prog: JsAny)

@JsFun("(gl, prog, pname) => { " +
    "try { " +
    "  const v = gl.getProgramParameter(prog, pname); " +
    "  if (typeof v === 'boolean') return v ? 1 : 0; " +
    "  if (typeof v === 'number') return v | 0; " +
    "  return v ? 1 : 0; " +
    "} catch (_e) { return 0; } }")
private external fun wasmGlGetProgramParameter(gl: JsAny, prog: JsAny, pname: Int): Int

@JsFun("(gl, prog) => { try { const s = gl.getProgramInfoLog(prog); return typeof s === 'string' ? s : ''; } catch (_e) { return ''; } }")
private external fun wasmGlGetProgramInfoLog(gl: JsAny, prog: JsAny): String

@JsFun("(gl, prog) => { try { gl.useProgram(prog); } catch (_e) {} }")
private external fun wasmGlUseProgram(gl: JsAny, prog: JsAny?)

@JsFun("(gl, prog) => { try { gl.deleteProgram(prog); } catch (_e) {} }")
private external fun wasmGlDeleteProgram(gl: JsAny, prog: JsAny)

@JsFun("(gl, prog, name) => { try { return gl.getAttribLocation(prog, name) | 0; } catch (_e) { return -1; } }")
private external fun wasmGlGetAttribLocation(gl: JsAny, prog: JsAny, name: String): Int

@JsFun("(gl, prog, name) => { try { return gl.getUniformLocation(prog, name); } catch (_e) { return null; } }")
private external fun wasmGlGetUniformLocation(gl: JsAny, prog: JsAny, name: String): JsAny?

@JsFun("(gl, prog, blockName) => { try { return gl.getUniformBlockIndex(prog, blockName) | 0; } catch (_e) { return -1; } }")
private external fun wasmGlGetUniformBlockIndex(gl: JsAny, prog: JsAny, blockName: String): Int

@JsFun("(gl, prog, blockIdx, binding) => { try { gl.uniformBlockBinding(prog, blockIdx, binding); } catch (_e) {} }")
private external fun wasmGlUniformBlockBinding(gl: JsAny, prog: JsAny, blockIdx: Int, binding: Int)

@JsFun("(gl, loc, v0) => { try { gl.uniform1i(loc, v0); } catch (_e) {} }")
private external fun wasmGlUniform1i(gl: JsAny, loc: JsAny, v0: Int)

@JsFun("(gl, loc, v0) => { try { gl.uniform1f(loc, v0); } catch (_e) {} }")
private external fun wasmGlUniform1f(gl: JsAny, loc: JsAny, v0: Float)

@JsFun("(gl, loc, v0, v1) => { try { gl.uniform2f(loc, v0, v1); } catch (_e) {} }")
private external fun wasmGlUniform2f(gl: JsAny, loc: JsAny, v0: Float, v1: Float)

@JsFun("(gl, loc, v0, v1, v2) => { try { gl.uniform3f(loc, v0, v1, v2); } catch (_e) {} }")
private external fun wasmGlUniform3f(gl: JsAny, loc: JsAny, v0: Float, v1: Float, v2: Float)

@JsFun("(gl, loc, v0, v1, v2, v3) => { try { gl.uniform4f(loc, v0, v1, v2, v3); } catch (_e) {} }")
private external fun wasmGlUniform4f(gl: JsAny, loc: JsAny, v0: Float, v1: Float, v2: Float, v3: Float)

@JsFun("(gl, loc, arr) => { try { gl.uniform1fv(loc, arr); } catch (_e) {} }")
private external fun wasmGlUniform1fv(gl: JsAny, loc: JsAny, arr: JsAny)

@JsFun("(gl, loc, arr) => { try { gl.uniform2fv(loc, arr); } catch (_e) {} }")
private external fun wasmGlUniform2fv(gl: JsAny, loc: JsAny, arr: JsAny)

@JsFun("(gl, loc, arr) => { try { gl.uniform3fv(loc, arr); } catch (_e) {} }")
private external fun wasmGlUniform3fv(gl: JsAny, loc: JsAny, arr: JsAny)

@JsFun("(gl, loc, arr) => { try { gl.uniform4fv(loc, arr); } catch (_e) {} }")
private external fun wasmGlUniform4fv(gl: JsAny, loc: JsAny, arr: JsAny)

@JsFun("(gl, loc, transpose, arr) => { try { gl.uniformMatrix3fv(loc, transpose, arr); } catch (_e) {} }")
private external fun wasmGlUniformMatrix3fv(gl: JsAny, loc: JsAny, transpose: Boolean, arr: JsAny)

@JsFun("(gl, loc, transpose, arr) => { try { gl.uniformMatrix4fv(loc, transpose, arr); } catch (_e) {} }")
private external fun wasmGlUniformMatrix4fv(gl: JsAny, loc: JsAny, transpose: Boolean, arr: JsAny)

@JsFun("(gl) => { try { return gl.createBuffer(); } catch (_e) { return null; } }")
private external fun wasmGlCreateBuffer(gl: JsAny): JsAny?

@JsFun("(gl, target, buf) => { try { gl.bindBuffer(target, buf); } catch (_e) {} }")
private external fun wasmGlBindBuffer(gl: JsAny, target: Int, buf: JsAny?)

@JsFun("(gl, target, index, buf) => { try { gl.bindBufferBase(target, index, buf); } catch (_e) {} }")
private external fun wasmGlBindBufferBase(gl: JsAny, target: Int, index: Int, buf: JsAny?)

@JsFun("(gl, target, sizeBytes, usage) => { try { gl.bufferData(target, sizeBytes, usage); } catch (_e) {} }")
private external fun wasmGlBufferDataSize(gl: JsAny, target: Int, sizeBytes: Int, usage: Int)

@JsFun("(gl, target, view, usage) => { try { gl.bufferData(target, view, usage); } catch (_e) {} }")
private external fun wasmGlBufferDataView(gl: JsAny, target: Int, view: JsAny, usage: Int)

@JsFun("(gl, target, offsetBytes, view) => { try { gl.bufferSubData(target, offsetBytes, view); } catch (_e) {} }")
private external fun wasmGlBufferSubDataView(gl: JsAny, target: Int, offsetBytes: Int, view: JsAny)

@JsFun("(gl, buf) => { try { gl.deleteBuffer(buf); } catch (_e) {} }")
private external fun wasmGlDeleteBuffer(gl: JsAny, buf: JsAny)

@JsFun("(gl) => { try { return gl.createVertexArray(); } catch (_e) { return null; } }")
private external fun wasmGlCreateVertexArray(gl: JsAny): JsAny?

@JsFun("(gl, vao) => { try { gl.bindVertexArray(vao); } catch (_e) {} }")
private external fun wasmGlBindVertexArray(gl: JsAny, vao: JsAny?)

@JsFun("(gl, vao) => { try { gl.deleteVertexArray(vao); } catch (_e) {} }")
private external fun wasmGlDeleteVertexArray(gl: JsAny, vao: JsAny)

@JsFun("(gl, idx) => { try { gl.enableVertexAttribArray(idx); } catch (_e) {} }")
private external fun wasmGlEnableVertexAttribArray(gl: JsAny, idx: Int)

@JsFun("(gl, idx) => { try { gl.disableVertexAttribArray(idx); } catch (_e) {} }")
private external fun wasmGlDisableVertexAttribArray(gl: JsAny, idx: Int)

@JsFun("(gl, idx, size, type, normalized, stride, offset) => { try { gl.vertexAttribPointer(idx, size, type, normalized, stride, offset); } catch (_e) {} }")
private external fun wasmGlVertexAttribPointer(
    gl: JsAny,
    idx: Int,
    size: Int,
    type: Int,
    normalized: Boolean,
    stride: Int,
    offset: Int
)

@JsFun("(gl, idx, divisor) => { try { gl.vertexAttribDivisor(idx, divisor); } catch (_e) {} }")
private external fun wasmGlVertexAttribDivisor(gl: JsAny, idx: Int, divisor: Int)

@JsFun("(gl) => { try { return gl.createTexture(); } catch (_e) { return null; } }")
private external fun wasmGlCreateTexture(gl: JsAny): JsAny?

@JsFun("(gl, target, tex) => { try { gl.bindTexture(target, tex); } catch (_e) {} }")
private external fun wasmGlBindTexture(gl: JsAny, target: Int, tex: JsAny?)

@JsFun("(gl, unit) => { try { gl.activeTexture(unit); } catch (_e) {} }")
private external fun wasmGlActiveTexture(gl: JsAny, unit: Int)

@JsFun("(gl, target, level, internalFormat, w, h, border, format, type, viewOrNull) => { " +
    "try { " +
    "  if (viewOrNull !== null) gl.pixelStorei(0x0CF5, 1); " +
    "  gl.texImage2D(target, level, internalFormat, w, h, border, format, type, viewOrNull); " +
    "} catch (_e) {} }")
private external fun wasmGlTexImage2D(
    gl: JsAny,
    target: Int,
    level: Int,
    internalFormat: Int,
    w: Int,
    h: Int,
    border: Int,
    format: Int,
    type: Int,
    viewOrNull: JsAny?
)

@JsFun("(gl, target, level, xOff, yOff, w, h, format, type, view) => { " +
    "try { " +
    "  gl.pixelStorei(0x0CF5, 1); " +
    "  gl.texSubImage2D(target, level, xOff, yOff, w, h, format, type, view); " +
    "} catch (_e) {} }")
private external fun wasmGlTexSubImage2D(
    gl: JsAny,
    target: Int,
    level: Int,
    xOff: Int,
    yOff: Int,
    w: Int,
    h: Int,
    format: Int,
    type: Int,
    view: JsAny
)

@JsFun("(gl, target, pname, param) => { try { gl.texParameteri(target, pname, param); } catch (_e) {} }")
private external fun wasmGlTexParameteri(gl: JsAny, target: Int, pname: Int, param: Int)

@JsFun("(gl, target, pname, param) => { try { gl.texParameterf(target, pname, param); } catch (_e) {} }")
private external fun wasmGlTexParameterf(gl: JsAny, target: Int, pname: Int, param: Float)

@JsFun("(gl, target) => { try { gl.generateMipmap(target); } catch (_e) {} }")
private external fun wasmGlGenerateMipmap(gl: JsAny, target: Int)

@JsFun("(gl, tex) => { try { gl.deleteTexture(tex); } catch (_e) {} }")
private external fun wasmGlDeleteTexture(gl: JsAny, tex: JsAny)

@JsFun("(gl) => { try { return gl.createFramebuffer(); } catch (_e) { return null; } }")
private external fun wasmGlCreateFramebuffer(gl: JsAny): JsAny?

@JsFun("(gl, target, fbo) => { try { gl.bindFramebuffer(target, fbo); } catch (_e) {} }")
private external fun wasmGlBindFramebuffer(gl: JsAny, target: Int, fbo: JsAny?)

@JsFun("(gl, target, attachment, textarget, tex, level) => { try { gl.framebufferTexture2D(target, attachment, textarget, tex, level); } catch (_e) {} }")
private external fun wasmGlFramebufferTexture2D(
    gl: JsAny,
    target: Int,
    attachment: Int,
    textarget: Int,
    tex: JsAny?,
    level: Int
)

@JsFun("(gl, target, attachment, rboTarget, rbo) => { try { gl.framebufferRenderbuffer(target, attachment, rboTarget, rbo); } catch (_e) {} }")
private external fun wasmGlFramebufferRenderbuffer(
    gl: JsAny,
    target: Int,
    attachment: Int,
    rboTarget: Int,
    rbo: JsAny?
)

@JsFun("(gl, target) => { try { return gl.checkFramebufferStatus(target) | 0; } catch (_e) { return 0x8CD5; } }")
private external fun wasmGlCheckFramebufferStatus(gl: JsAny, target: Int): Int

@JsFun("(gl, fbo) => { try { gl.deleteFramebuffer(fbo); } catch (_e) {} }")
private external fun wasmGlDeleteFramebuffer(gl: JsAny, fbo: JsAny)

@JsFun("(gl) => { try { return gl.createRenderbuffer(); } catch (_e) { return null; } }")
private external fun wasmGlCreateRenderbuffer(gl: JsAny): JsAny?

@JsFun("(gl, target, rbo) => { try { gl.bindRenderbuffer(target, rbo); } catch (_e) {} }")
private external fun wasmGlBindRenderbuffer(gl: JsAny, target: Int, rbo: JsAny?)

@JsFun("(gl, target, internalFormat, w, h) => { try { gl.renderbufferStorage(target, internalFormat, w, h); } catch (_e) {} }")
private external fun wasmGlRenderbufferStorage(gl: JsAny, target: Int, internalFormat: Int, w: Int, h: Int)

@JsFun("(gl, rbo) => { try { gl.deleteRenderbuffer(rbo); } catch (_e) {} }")
private external fun wasmGlDeleteRenderbuffer(gl: JsAny, rbo: JsAny)

@JsFun("(gl, mode, first, count) => { try { gl.drawArrays(mode, first, count); } catch (_e) {} }")
private external fun wasmGlDrawArrays(gl: JsAny, mode: Int, first: Int, count: Int)

@JsFun("(gl, mode, count, type, offset) => { try { gl.drawElements(mode, count, type, offset); } catch (_e) {} }")
private external fun wasmGlDrawElements(gl: JsAny, mode: Int, count: Int, type: Int, offset: Int)

@JsFun("(gl, mode, first, count, instanceCount) => { try { gl.drawArraysInstanced(mode, first, count, instanceCount); } catch (_e) {} }")
private external fun wasmGlDrawArraysInstanced(gl: JsAny, mode: Int, first: Int, count: Int, instanceCount: Int)

@JsFun("(gl, mode, count, type, offset, instanceCount) => { try { gl.drawElementsInstanced(mode, count, type, offset, instanceCount); } catch (_e) {} }")
private external fun wasmGlDrawElementsInstanced(
    gl: JsAny,
    mode: Int,
    count: Int,
    type: Int,
    offset: Int,
    instanceCount: Int
)

@JsFun("(gl, x, y, w, h, format, type, u8) => { " +
    "try { " +
    "  gl.pixelStorei(0x0D05, 1); " +
    "  gl.readPixels(x, y, w, h, format, type, u8); " +
    "} catch (_e) {} }")
private external fun wasmGlReadPixels(
    gl: JsAny,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    format: Int,
    type: Int,
    u8: JsAny
)

@JsFun("(gl) => { try { gl.finish(); } catch (_e) {} }")
private external fun wasmGlFinish(gl: JsAny)

@JsFun("(gl) => { try { gl.flush(); } catch (_e) {} }")
private external fun wasmGlFlush(gl: JsAny)

/**
 * Kotlin/WasmJS implementation of [WebGl2BrowserBridge] delegating directly to the browser's
 * hardware `WebGL2RenderingContext`.
 */
actual object WebGl2BrowserBridge {

    private class ContextState(
        val canvasElement: WebDomElement,
        val canvasPeer: JsAny,
        val gl: JsAny
    ) {
        var nextObjectId: Int = 1
        var boundDrawFramebuffer: Int = 0
        var boundReadFramebuffer: Int = 0
        var lastSyncedCanvasW: Int = 0
        var lastSyncedCanvasH: Int = 0

        val shaders = LinkedHashMap<Int, JsAny>()
        val programs = LinkedHashMap<Int, JsAny>()
        val buffers = LinkedHashMap<Int, JsAny>()
        val textures = LinkedHashMap<Int, JsAny>()
        val framebuffers = LinkedHashMap<Int, JsAny>()
        val renderbuffers = LinkedHashMap<Int, JsAny>()
        val vertexArrays = LinkedHashMap<Int, JsAny>()
        val uniformLocations = LinkedHashMap<Int, JsAny>()
        val uniformLocationByProgramAndName = LinkedHashMap<String, Int>()
    }

    private var cachedWebGl2Support: Boolean? = null
    private var nextContextId: Int = 1
    private val contexts = LinkedHashMap<Int, ContextState>()
    private val contextIdByElement = LinkedHashMap<WebDomElement, Int>()

    private fun toUint8JsArray(bytes: ByteArray): JsAny {
        val len = bytes.size
        val u8 = wasmNewUint8Array(len)
        var i = 0
        while (i + 3 < len) {
            val packed = (bytes[i].toInt() and 0xFF) or
                ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                ((bytes[i + 2].toInt() and 0xFF) shl 16) or
                ((bytes[i + 3].toInt() and 0xFF) shl 24)
            wasmSetUint8x4(u8, i, packed)
            i += 4
        }
        while (i < len) {
            wasmSetUint8(u8, i, bytes[i].toInt() and 0xFF)
            i++
        }
        return u8
    }

    private fun toFloat32JsArray(floats: FloatArray): JsAny {
        val len = floats.size
        val f32 = wasmNewFloat32Array(len)
        var i = 0
        while (i + 3 < len) {
            wasmSetFloat32x4(f32, i, floats[i], floats[i + 1], floats[i + 2], floats[i + 3])
            i += 4
        }
        while (i < len) {
            wasmSetFloat32(f32, i, floats[i])
            i++
        }
        return f32
    }

    private fun toInt32JsArray(ints: IntArray): JsAny {
        val len = ints.size
        val i32 = wasmNewInt32Array(len)
        var i = 0
        while (i + 3 < len) {
            wasmSetInt32x4(i32, i, ints[i], ints[i + 1], ints[i + 2], ints[i + 3])
            i += 4
        }
        while (i < len) {
            wasmSetInt32(i32, i, ints[i])
            i++
        }
        return i32
    }

    private fun toInt16JsArray(shorts: ShortArray): JsAny {
        val len = shorts.size
        val i16 = wasmNewInt16Array(len)
        for (i in 0 until len) {
            wasmSetInt16(i16, i, shorts[i].toInt())
        }
        return i16
    }

    actual fun isWebGl2Supported(): Boolean {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return false
        cachedWebGl2Support?.let { return it }
        val supported = wasmIsWebGl2Supported()
        cachedWebGl2Support = supported
        return supported
    }

    actual fun getOrCreateContext(canvasElement: WebDomElement): Int {
        if (!WebBrowserDomBridge.isBrowserEnvironment) return 0
        contextIdByElement[canvasElement]?.let { existingId ->
            val existingState = contexts[existingId]
            val currentPeer = canvasElement.nativeBrowserPeer as? JsAny
            if (existingState != null && (currentPeer == null || existingState.canvasPeer == currentPeer)) {
                return existingId
            }
            if (existingState != null) {
                destroyContext(existingId)
            }
        }
        var peer = canvasElement.nativeBrowserPeer as? JsAny
        if (peer == null) {
            WebBrowserDomBridge.createPeer(canvasElement)
            peer = canvasElement.nativeBrowserPeer as? JsAny
        }
        if (peer == null) return 0

        val initW = canvasElement.getAttribute("width")?.toIntOrNull()
            ?: canvasElement.clientWidth.takeIf { it > 0 }
            ?: 800
        val initH = canvasElement.getAttribute("height")?.toIntOrNull()
            ?: canvasElement.clientHeight.takeIf { it > 0 }
            ?: 600
        val alpha = canvasElement.getAttribute("data-webgl-alpha")?.toBooleanStrictOrNull() ?: true
        val depth = canvasElement.getAttribute("data-webgl-depth")?.toBooleanStrictOrNull() ?: true
        val stencil = canvasElement.getAttribute("data-webgl-stencil")?.toBooleanStrictOrNull() ?: false
        val antialias = canvasElement.getAttribute("data-webgl-antialias")?.toBooleanStrictOrNull() ?: true

        val gl = wasmGetOrCreateWebGl2Context(peer, initW, initH, alpha, depth, stencil, antialias)
            ?: return 0

        val id = nextContextId++
        val state = ContextState(canvasElement, peer, gl).apply {
            lastSyncedCanvasW = initW
            lastSyncedCanvasH = initH
        }
        contexts[id] = state
        contextIdByElement[canvasElement] = id
        return id
    }

    actual fun destroyContext(contextId: Int) {
        val state = contexts.remove(contextId) ?: return
        contextIdByElement.remove(state.canvasElement)
        for (vao in state.vertexArrays.values) wasmGlDeleteVertexArray(state.gl, vao)
        for (fbo in state.framebuffers.values) wasmGlDeleteFramebuffer(state.gl, fbo)
        for (rbo in state.renderbuffers.values) wasmGlDeleteRenderbuffer(state.gl, rbo)
        for (tex in state.textures.values) wasmGlDeleteTexture(state.gl, tex)
        for (buf in state.buffers.values) wasmGlDeleteBuffer(state.gl, buf)
        for (prog in state.programs.values) wasmGlDeleteProgram(state.gl, prog)
        for (sh in state.shaders.values) wasmGlDeleteShader(state.gl, sh)
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
        return wasmGlGetString(state.gl, name)
    }

    actual fun getInteger(contextId: Int, pname: Int): Int {
        val state = contexts[contextId] ?: return 0
        return wasmGlGetInteger(state.gl, pname)
    }

    actual fun getError(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        return wasmGlGetError(state.gl)
    }

    actual fun viewport(contextId: Int, x: Int, y: Int, width: Int, height: Int) {
        val state = contexts[contextId] ?: return
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (x == 0 && y == 0 && state.boundDrawFramebuffer == 0) {
            if (state.lastSyncedCanvasW != w || state.lastSyncedCanvasH != h) {
                state.lastSyncedCanvasW = w
                state.lastSyncedCanvasH = h
                wasmSyncCanvasSize(state.canvasPeer, w, h)
                state.canvasElement.clientWidth = w
                state.canvasElement.clientHeight = h
            }
        }
        wasmGlViewport(state.gl, x, y, w, h)
    }

    actual fun scissor(contextId: Int, x: Int, y: Int, width: Int, height: Int) {
        val state = contexts[contextId] ?: return
        wasmGlScissor(state.gl, x, y, width.coerceAtLeast(0), height.coerceAtLeast(0))
    }

    actual fun clearColor(contextId: Int, r: Float, g: Float, b: Float, a: Float) {
        val state = contexts[contextId] ?: return
        wasmGlClearColor(state.gl, r, g, b, a)
    }

    actual fun clearDepth(contextId: Int, depth: Float) {
        val state = contexts[contextId] ?: return
        wasmGlClearDepth(state.gl, depth)
    }

    actual fun clearStencil(contextId: Int, stencil: Int) {
        val state = contexts[contextId] ?: return
        wasmGlClearStencil(state.gl, stencil)
    }

    actual fun clear(contextId: Int, mask: Int) {
        val state = contexts[contextId] ?: return
        wasmGlClear(state.gl, mask)
    }

    actual fun enable(contextId: Int, cap: Int) {
        val state = contexts[contextId] ?: return
        wasmGlEnable(state.gl, cap)
    }

    actual fun disable(contextId: Int, cap: Int) {
        val state = contexts[contextId] ?: return
        wasmGlDisable(state.gl, cap)
    }

    actual fun blendFunc(contextId: Int, sfactor: Int, dfactor: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBlendFunc(state.gl, sfactor, dfactor)
    }

    actual fun blendFuncSeparate(contextId: Int, srcRGB: Int, dstRGB: Int, srcAlpha: Int, dstAlpha: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBlendFuncSeparate(state.gl, srcRGB, dstRGB, srcAlpha, dstAlpha)
    }

    actual fun blendEquation(contextId: Int, mode: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBlendEquation(state.gl, mode)
    }

    actual fun depthFunc(contextId: Int, func: Int) {
        val state = contexts[contextId] ?: return
        wasmGlDepthFunc(state.gl, func)
    }

    actual fun depthMask(contextId: Int, flag: Boolean) {
        val state = contexts[contextId] ?: return
        wasmGlDepthMask(state.gl, flag)
    }

    actual fun cullFace(contextId: Int, mode: Int) {
        val state = contexts[contextId] ?: return
        wasmGlCullFace(state.gl, mode)
    }

    actual fun frontFace(contextId: Int, mode: Int) {
        val state = contexts[contextId] ?: return
        wasmGlFrontFace(state.gl, mode)
    }

    actual fun colorMask(contextId: Int, red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        val state = contexts[contextId] ?: return
        wasmGlColorMask(state.gl, red, green, blue, alpha)
    }

    actual fun createShader(contextId: Int, type: Int): Int {
        val state = contexts[contextId] ?: return 0
        val shader = wasmGlCreateShader(state.gl, type) ?: return 0
        val id = state.nextObjectId++
        state.shaders[id] = shader
        return id
    }

    actual fun shaderSource(contextId: Int, shaderId: Int, source: String) {
        val state = contexts[contextId] ?: return
        val shader = state.shaders[shaderId] ?: return
        val trimmed = source.trimStart()
        val cleanSource = if (trimmed.startsWith("#version")) trimmed else source
        wasmGlShaderSource(state.gl, shader, cleanSource)
    }

    actual fun compileShader(contextId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val shader = state.shaders[shaderId] ?: return
        wasmGlCompileShader(state.gl, shader)
    }

    actual fun getShaderParameter(contextId: Int, shaderId: Int, pname: Int): Int {
        val state = contexts[contextId] ?: return 0
        val shader = state.shaders[shaderId] ?: return 0
        return wasmGlGetShaderParameter(state.gl, shader, pname)
    }

    actual fun getShaderInfoLog(contextId: Int, shaderId: Int): String {
        val state = contexts[contextId] ?: return ""
        val shader = state.shaders[shaderId] ?: return ""
        return wasmGlGetShaderInfoLog(state.gl, shader)
    }

    actual fun deleteShader(contextId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val shader = state.shaders.remove(shaderId) ?: return
        wasmGlDeleteShader(state.gl, shader)
    }

    actual fun createProgram(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        val prog = wasmGlCreateProgram(state.gl) ?: return 0
        val id = state.nextObjectId++
        state.programs[id] = prog
        return id
    }

    actual fun attachShader(contextId: Int, programId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val prog = state.programs[programId] ?: return
        val shader = state.shaders[shaderId] ?: return
        wasmGlAttachShader(state.gl, prog, shader)
    }

    actual fun detachShader(contextId: Int, programId: Int, shaderId: Int) {
        val state = contexts[contextId] ?: return
        val prog = state.programs[programId] ?: return
        val shader = state.shaders[shaderId] ?: return
        wasmGlDetachShader(state.gl, prog, shader)
    }

    actual fun linkProgram(contextId: Int, programId: Int) {
        val state = contexts[contextId] ?: return
        val prog = state.programs[programId] ?: return
        wasmGlLinkProgram(state.gl, prog)
    }

    actual fun getProgramParameter(contextId: Int, programId: Int, pname: Int): Int {
        val state = contexts[contextId] ?: return 0
        val prog = state.programs[programId] ?: return 0
        return wasmGlGetProgramParameter(state.gl, prog, pname)
    }

    actual fun getProgramInfoLog(contextId: Int, programId: Int): String {
        val state = contexts[contextId] ?: return ""
        val prog = state.programs[programId] ?: return ""
        return wasmGlGetProgramInfoLog(state.gl, prog)
    }

    actual fun useProgram(contextId: Int, programId: Int) {
        val state = contexts[contextId] ?: return
        val prog = if (programId == 0) null else state.programs[programId]
        wasmGlUseProgram(state.gl, prog)
    }

    actual fun deleteProgram(contextId: Int, programId: Int) {
        val state = contexts[contextId] ?: return
        val prog = state.programs.remove(programId) ?: return
        val prefix = "$programId:"
        val keysToRemove = state.uniformLocationByProgramAndName.keys.filter { it.startsWith(prefix) }
        for (k in keysToRemove) {
            val locId = state.uniformLocationByProgramAndName.remove(k)
            if (locId != null) {
                state.uniformLocations.remove(locId)
            }
        }
        wasmGlDeleteProgram(state.gl, prog)
    }

    actual fun getAttribLocation(contextId: Int, programId: Int, name: String): Int {
        val state = contexts[contextId] ?: return -1
        val prog = state.programs[programId] ?: return -1
        return wasmGlGetAttribLocation(state.gl, prog, name)
    }

    actual fun getUniformLocation(contextId: Int, programId: Int, name: String): Int {
        val state = contexts[contextId] ?: return 0
        val prog = state.programs[programId] ?: return 0
        val cacheKey = "$programId:$name"
        state.uniformLocationByProgramAndName[cacheKey]?.let { existingId ->
            if (state.uniformLocations.containsKey(existingId)) return existingId
        }
        val loc = wasmGlGetUniformLocation(state.gl, prog, name) ?: return 0
        val id = state.nextObjectId++
        state.uniformLocations[id] = loc
        state.uniformLocationByProgramAndName[cacheKey] = id
        return id
    }

    actual fun getUniformBlockIndex(contextId: Int, programId: Int, blockName: String): Int {
        val state = contexts[contextId] ?: return -1
        val prog = state.programs[programId] ?: return -1
        return wasmGlGetUniformBlockIndex(state.gl, prog, blockName)
    }

    actual fun uniformBlockBinding(
        contextId: Int,
        programId: Int,
        uniformBlockIndex: Int,
        uniformBlockBinding: Int
    ) {
        val state = contexts[contextId] ?: return
        val prog = state.programs[programId] ?: return
        wasmGlUniformBlockBinding(state.gl, prog, uniformBlockIndex, uniformBlockBinding)
    }

    actual fun uniform1i(contextId: Int, locationId: Int, v0: Int) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform1i(state.gl, loc, v0)
    }

    actual fun uniform1f(contextId: Int, locationId: Int, v0: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform1f(state.gl, loc, v0)
    }

    actual fun uniform2f(contextId: Int, locationId: Int, v0: Float, v1: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform2f(state.gl, loc, v0, v1)
    }

    actual fun uniform3f(contextId: Int, locationId: Int, v0: Float, v1: Float, v2: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform3f(state.gl, loc, v0, v1, v2)
    }

    actual fun uniform4f(contextId: Int, locationId: Int, v0: Float, v1: Float, v2: Float, v3: Float) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform4f(state.gl, loc, v0, v1, v2, v3)
    }

    actual fun uniform1fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform1fv(state.gl, loc, toFloat32JsArray(values))
    }

    actual fun uniform2fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform2fv(state.gl, loc, toFloat32JsArray(values))
    }

    actual fun uniform3fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform3fv(state.gl, loc, toFloat32JsArray(values))
    }

    actual fun uniform4fv(contextId: Int, locationId: Int, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniform4fv(state.gl, loc, toFloat32JsArray(values))
    }

    actual fun uniformMatrix3fv(contextId: Int, locationId: Int, transpose: Boolean, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniformMatrix3fv(state.gl, loc, transpose, toFloat32JsArray(values))
    }

    actual fun uniformMatrix4fv(contextId: Int, locationId: Int, transpose: Boolean, values: FloatArray) {
        val state = contexts[contextId] ?: return
        val loc = state.uniformLocations[locationId] ?: return
        wasmGlUniformMatrix4fv(state.gl, loc, transpose, toFloat32JsArray(values))
    }

    actual fun createBuffer(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        val buf = wasmGlCreateBuffer(state.gl) ?: return 0
        val id = state.nextObjectId++
        state.buffers[id] = buf
        return id
    }

    actual fun bindBuffer(contextId: Int, target: Int, bufferId: Int) {
        val state = contexts[contextId] ?: return
        val buf = if (bufferId == 0) null else state.buffers[bufferId]
        wasmGlBindBuffer(state.gl, target, buf)
    }

    actual fun bindBufferBase(contextId: Int, target: Int, index: Int, bufferId: Int) {
        val state = contexts[contextId] ?: return
        val buf = if (bufferId == 0) null else state.buffers[bufferId]
        wasmGlBindBufferBase(state.gl, target, index, buf)
    }

    actual fun bufferData(contextId: Int, target: Int, sizeBytes: Int, usage: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBufferDataSize(state.gl, target, sizeBytes.coerceAtLeast(0), usage)
    }

    actual fun bufferData(contextId: Int, target: Int, data: ByteArray, usage: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBufferDataView(state.gl, target, toUint8JsArray(data), usage)
    }

    actual fun bufferData(contextId: Int, target: Int, data: FloatArray, usage: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBufferDataView(state.gl, target, toFloat32JsArray(data), usage)
    }

    actual fun bufferData(contextId: Int, target: Int, data: IntArray, usage: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBufferDataView(state.gl, target, toInt32JsArray(data), usage)
    }

    actual fun bufferData(contextId: Int, target: Int, data: ShortArray, usage: Int) {
        val state = contexts[contextId] ?: return
        wasmGlBufferDataView(state.gl, target, toInt16JsArray(data), usage)
    }

    actual fun bufferSubData(contextId: Int, target: Int, offsetBytes: Int, data: ByteArray) {
        val state = contexts[contextId] ?: return
        wasmGlBufferSubDataView(state.gl, target, offsetBytes.coerceAtLeast(0), toUint8JsArray(data))
    }

    actual fun bufferSubData(contextId: Int, target: Int, offsetBytes: Int, data: FloatArray) {
        val state = contexts[contextId] ?: return
        wasmGlBufferSubDataView(state.gl, target, offsetBytes.coerceAtLeast(0), toFloat32JsArray(data))
    }

    actual fun deleteBuffer(contextId: Int, bufferId: Int) {
        val state = contexts[contextId] ?: return
        val buf = state.buffers.remove(bufferId) ?: return
        wasmGlDeleteBuffer(state.gl, buf)
    }

    actual fun createVertexArray(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        val vao = wasmGlCreateVertexArray(state.gl) ?: return 0
        val id = state.nextObjectId++
        state.vertexArrays[id] = vao
        return id
    }

    actual fun bindVertexArray(contextId: Int, vaoId: Int) {
        val state = contexts[contextId] ?: return
        val vao = if (vaoId == 0) null else state.vertexArrays[vaoId]
        wasmGlBindVertexArray(state.gl, vao)
    }

    actual fun deleteVertexArray(contextId: Int, vaoId: Int) {
        val state = contexts[contextId] ?: return
        val vao = state.vertexArrays.remove(vaoId) ?: return
        wasmGlDeleteVertexArray(state.gl, vao)
    }

    actual fun enableVertexAttribArray(contextId: Int, index: Int) {
        val state = contexts[contextId] ?: return
        wasmGlEnableVertexAttribArray(state.gl, index)
    }

    actual fun disableVertexAttribArray(contextId: Int, index: Int) {
        val state = contexts[contextId] ?: return
        wasmGlDisableVertexAttribArray(state.gl, index)
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
        wasmGlVertexAttribPointer(state.gl, index, size, type, normalized, stride, offset)
    }

    actual fun vertexAttribDivisor(contextId: Int, index: Int, divisor: Int) {
        val state = contexts[contextId] ?: return
        wasmGlVertexAttribDivisor(state.gl, index, divisor)
    }

    actual fun createTexture(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        val tex = wasmGlCreateTexture(state.gl) ?: return 0
        val id = state.nextObjectId++
        state.textures[id] = tex
        return id
    }

    actual fun bindTexture(contextId: Int, target: Int, textureId: Int) {
        val state = contexts[contextId] ?: return
        val tex = if (textureId == 0) null else state.textures[textureId]
        wasmGlBindTexture(state.gl, target, tex)
    }

    actual fun activeTexture(contextId: Int, textureUnit: Int) {
        val state = contexts[contextId] ?: return
        val glUnit = if (textureUnit >= 0x84C0) textureUnit else 0x84C0 + textureUnit.coerceAtLeast(0)
        wasmGlActiveTexture(state.gl, glUnit)
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
        val viewOrNull = if (pixels != null) toUint8JsArray(pixels) else null
        wasmGlTexImage2D(
            state.gl,
            target,
            level,
            internalFormat,
            safeW,
            safeH,
            border,
            format,
            type,
            viewOrNull
        )
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
        wasmGlTexSubImage2D(
            state.gl,
            target,
            level,
            xOffset,
            yOffset,
            safeW,
            safeH,
            format,
            type,
            toUint8JsArray(pixels)
        )
    }

    actual fun texParameteri(contextId: Int, target: Int, pname: Int, param: Int) {
        val state = contexts[contextId] ?: return
        wasmGlTexParameteri(state.gl, target, pname, param)
    }

    actual fun texParameterf(contextId: Int, target: Int, pname: Int, param: Float) {
        val state = contexts[contextId] ?: return
        wasmGlTexParameterf(state.gl, target, pname, param)
    }

    actual fun generateMipmap(contextId: Int, target: Int) {
        val state = contexts[contextId] ?: return
        wasmGlGenerateMipmap(state.gl, target)
    }

    actual fun deleteTexture(contextId: Int, textureId: Int) {
        val state = contexts[contextId] ?: return
        val tex = state.textures.remove(textureId) ?: return
        wasmGlDeleteTexture(state.gl, tex)
    }

    actual fun createFramebuffer(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        val fbo = wasmGlCreateFramebuffer(state.gl) ?: return 0
        val id = state.nextObjectId++
        state.framebuffers[id] = fbo
        return id
    }

    actual fun bindFramebuffer(contextId: Int, target: Int, framebufferId: Int) {
        val state = contexts[contextId] ?: return
        when (target) {
            0x8D40 -> {
                state.boundDrawFramebuffer = framebufferId
                state.boundReadFramebuffer = framebufferId
            }
            0x8CA9 -> state.boundDrawFramebuffer = framebufferId
            0x8CA8 -> state.boundReadFramebuffer = framebufferId
        }
        val fbo = if (framebufferId == 0) null else state.framebuffers[framebufferId]
        wasmGlBindFramebuffer(state.gl, target, fbo)
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
        wasmGlFramebufferTexture2D(state.gl, target, attachment, textarget, tex, level)
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
        wasmGlFramebufferRenderbuffer(state.gl, target, attachment, renderbufferTarget, rbo)
    }

    actual fun checkFramebufferStatus(contextId: Int, target: Int): Int {
        val state = contexts[contextId] ?: return 0x8CD5
        return wasmGlCheckFramebufferStatus(state.gl, target)
    }

    actual fun deleteFramebuffer(contextId: Int, framebufferId: Int) {
        val state = contexts[contextId] ?: return
        if (state.boundDrawFramebuffer == framebufferId) state.boundDrawFramebuffer = 0
        if (state.boundReadFramebuffer == framebufferId) state.boundReadFramebuffer = 0
        val fbo = state.framebuffers.remove(framebufferId) ?: return
        wasmGlDeleteFramebuffer(state.gl, fbo)
    }

    actual fun createRenderbuffer(contextId: Int): Int {
        val state = contexts[contextId] ?: return 0
        val rbo = wasmGlCreateRenderbuffer(state.gl) ?: return 0
        val id = state.nextObjectId++
        state.renderbuffers[id] = rbo
        return id
    }

    actual fun bindRenderbuffer(contextId: Int, target: Int, renderbufferId: Int) {
        val state = contexts[contextId] ?: return
        val rbo = if (renderbufferId == 0) null else state.renderbuffers[renderbufferId]
        wasmGlBindRenderbuffer(state.gl, target, rbo)
    }

    actual fun renderbufferStorage(
        contextId: Int,
        target: Int,
        internalFormat: Int,
        width: Int,
        height: Int
    ) {
        val state = contexts[contextId] ?: return
        wasmGlRenderbufferStorage(state.gl, target, internalFormat, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    actual fun deleteRenderbuffer(contextId: Int, renderbufferId: Int) {
        val state = contexts[contextId] ?: return
        val rbo = state.renderbuffers.remove(renderbufferId) ?: return
        wasmGlDeleteRenderbuffer(state.gl, rbo)
    }

    actual fun drawArrays(contextId: Int, mode: Int, first: Int, count: Int) {
        if (count <= 0) return
        val state = contexts[contextId] ?: return
        wasmGlDrawArrays(state.gl, mode, first, count)
    }

    actual fun drawElements(contextId: Int, mode: Int, count: Int, type: Int, offset: Int) {
        if (count <= 0) return
        val state = contexts[contextId] ?: return
        wasmGlDrawElements(state.gl, mode, count, type, offset)
    }

    actual fun drawArraysInstanced(contextId: Int, mode: Int, first: Int, count: Int, instanceCount: Int) {
        if (count <= 0 || instanceCount <= 0) return
        val state = contexts[contextId] ?: return
        wasmGlDrawArraysInstanced(state.gl, mode, first, count, instanceCount)
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
        wasmGlDrawElementsInstanced(state.gl, mode, count, type, offset, instanceCount)
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
        val u8 = wasmNewUint8Array(byteLen)
        wasmGlReadPixels(state.gl, x, y, safeW, safeH, format, type, u8)
        var i = 0
        while (i + 3 < byteLen) {
            val packed = wasmGetUint8x4(u8, i)
            out[i] = (packed and 0xFF).toByte()
            out[i + 1] = ((packed ushr 8) and 0xFF).toByte()
            out[i + 2] = ((packed ushr 16) and 0xFF).toByte()
            out[i + 3] = ((packed ushr 24) and 0xFF).toByte()
            i += 4
        }
        while (i < byteLen) {
            out[i] = (wasmGetUint8(u8, i) and 0xFF).toByte()
            i++
        }
        return out
    }

    actual fun finish(contextId: Int) {
        val state = contexts[contextId] ?: return
        wasmGlFinish(state.gl)
    }

    actual fun flush(contextId: Int) {
        val state = contexts[contextId] ?: return
        wasmGlFlush(state.gl)
    }
}
