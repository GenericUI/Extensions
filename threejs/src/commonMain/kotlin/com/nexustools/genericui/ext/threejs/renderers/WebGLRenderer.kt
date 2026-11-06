package com.nexustools.genericui.ext.threejs.renderers

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.component.media.GLBuffer
import com.nexustools.genericui.component.media.GLBufferTarget
import com.nexustools.genericui.component.media.GLBufferUsage
import com.nexustools.genericui.component.media.GLCapability
import com.nexustools.genericui.component.media.GLClearBuffer
import com.nexustools.genericui.component.media.GLCompareFunction
import com.nexustools.genericui.component.media.GLContext
import com.nexustools.genericui.component.media.GLDataType
import com.nexustools.genericui.component.media.GLFaceSide
import com.nexustools.genericui.component.media.GLFramebuffer
import com.nexustools.genericui.component.media.GLFramebufferAttachment
import com.nexustools.genericui.component.media.GLFramebufferTarget
import com.nexustools.genericui.component.media.GLIndexType
import com.nexustools.genericui.component.media.GLPrimitive
import com.nexustools.genericui.component.media.GLProgram
import com.nexustools.genericui.component.media.GLTexture
import com.nexustools.genericui.component.media.GLTextureFormat
import com.nexustools.genericui.component.media.GLTextureTarget
import com.nexustools.genericui.component.media.GLVertexAttribLayout
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.component.media.VKLoadOp
import com.nexustools.genericui.component.media.VKRenderPassDescriptor
import com.nexustools.genericui.enums.media.ShaderType
import com.nexustools.genericui.ext.canvas3d.Canvas3DExtension
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.lights.AmbientLight
import com.nexustools.genericui.ext.threejs.lights.DirectionalLight
import com.nexustools.genericui.ext.threejs.lights.Light
import com.nexustools.genericui.ext.threejs.lights.PointLight
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshBasicMaterial
import com.nexustools.genericui.ext.threejs.materials.MeshPhongMaterial
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.materials.ShaderMaterial
import com.nexustools.genericui.ext.threejs.materials.Side
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.math.Frustum
import com.nexustools.genericui.ext.threejs.math.Matrix3
import com.nexustools.genericui.ext.threejs.math.Matrix4
import com.nexustools.genericui.ext.threejs.math.Vector4
import com.nexustools.genericui.ext.threejs.objects.InstancedMesh
import com.nexustools.genericui.ext.threejs.objects.Line
import com.nexustools.genericui.ext.threejs.objects.LineSegments
import com.nexustools.genericui.ext.threejs.objects.Mesh
import com.nexustools.genericui.ext.threejs.objects.Points
import com.nexustools.genericui.ext.threejs.scenes.Scene
import com.nexustools.genericui.ext.threejs.textures.DataTexture
import com.nexustools.genericui.ext.threejs.textures.Texture
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Hardware-accelerated Three.js 3D Renderer backed by `:extensions:canvas3d` ([Canvas3D])
 * and `:extensions:gpu` ([GLContext] / [VKContext]).
 *
 * Supports OpenGL ES 3.0 (including Google ANGLE on Windows) and Vulkan 1.3 via [Canvas3D],
 * with hardware VBlank animation loops (`setAnimationLoop { deltaMs, elapsedTimeMs -> ... }`).
 */
class WebGLRenderer(
    val config: WebGLRendererConfig = WebGLRendererConfig(),
    val canvas: Canvas3D = Canvas3D(
        backend = if (config.preferVulkanWhenAvailable) GpuBackendType.VULKAN else GpuBackendType.OPENGL_ES,
        acceleration = GpuAccelerationPolicy.HARDWARE_PREFERRED,
        width = 800,
        height = 600
    )
) {
    val info: WebGLRenderInfo = WebGLRenderInfo()

    var autoClear: Boolean = true
    var autoClearColor: Boolean = true
    var autoClearDepth: Boolean = true
    var autoClearStencil: Boolean = true
    var sortObjects: Boolean = true

    private val clearColor: Color = Color(0x000000)
    private var clearAlpha: Float = 1f
    private var pixelRatio: Float = 1f
    private var width: Int = 800
    private var height: Int = 600
    private val viewport: Vector4 = Vector4(0f, 0f, 800f, 600f)

    private var activeRenderTarget: WebGLRenderTarget? = null
    private var vblankSubscription: VBlankSubscription? = null
    private var activeGlContext: GLContext? = null
    private var activeVkContext: VKContext? = null

    // GPU resource caches
    private val programCache = mutableMapOf<Long, GLProgram>()
    private val vertexBufferCache = mutableMapOf<Long, Map<String, GLBuffer>>()
    private val indexBufferCache = mutableMapOf<Long, GLBuffer>()
    private val textureCache = mutableMapOf<Long, GLTexture>()
    private val renderTargetFramebuffers = mutableMapOf<Long, GLFramebuffer>()

    private val projScreenMatrix = Matrix4()
    private val modelViewMatrix = Matrix4()
    private val normalMatrix = Matrix3()
    private val frustum = Frustum()

    init {
        canvas.onSurfaceCreated = { ctx, w, h ->
            when (ctx) {
                is GLContext -> activeGlContext = ctx
                is VKContext -> activeVkContext = ctx
            }
            setSize(w, h)
        }
        canvas.onSurfaceResized = { ctx, w, h ->
            when (ctx) {
                is GLContext -> activeGlContext = ctx
                is VKContext -> activeVkContext = ctx
            }
            setSize(w, h)
        }
    }

    fun setSize(width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        val scaledW = (this.width * pixelRatio).toInt().coerceAtLeast(1)
        val scaledH = (this.height * pixelRatio).toInt().coerceAtLeast(1)
        viewport.set(0f, 0f, scaledW.toFloat(), scaledH.toFloat())
        activeGlContext?.viewport(0, 0, scaledW, scaledH)
        activeVkContext?.setViewport(0f, 0f, scaledW.toFloat(), scaledH.toFloat())
    }

    fun setPixelRatio(value: Float) {
        pixelRatio = value.coerceAtLeast(0.5f)
        setSize(width, height)
    }

    fun getPixelRatio(): Float = pixelRatio

    fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        viewport.set(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
        activeGlContext?.viewport(x, y, width, height)
        activeVkContext?.setViewport(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
    }

    fun setClearColor(color: Color, alpha: Float = 1f) {
        clearColor.copy(color)
        clearAlpha = alpha.coerceIn(0f, 1f)
    }

    fun setClearColor(hex: Int, alpha: Float = 1f) {
        clearColor.setHex(hex)
        clearAlpha = alpha.coerceIn(0f, 1f)
    }

    fun setRenderTarget(renderTarget: WebGLRenderTarget?) {
        activeRenderTarget = renderTarget
        val gl = resolveGlContext()
        if (renderTarget == null) {
            gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, null)
            gl.viewport(
                viewport.x.toInt(),
                viewport.y.toInt(),
                viewport.z.toInt(),
                viewport.w.toInt()
            )
        } else {
            val fb = renderTargetFramebuffers.getOrPut(renderTarget.id) {
                val createdFb = gl.createFramebuffer()
                val tex = ensureTextureUploaded(gl, renderTarget.texture)
                gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, createdFb)
                gl.framebufferTexture2D(
                    GLFramebufferTarget.FRAMEBUFFER,
                    GLFramebufferAttachment.COLOR_ATTACHMENT0,
                    GLTextureTarget.TEXTURE_2D,
                    tex,
                    0
                )
                createdFb
            }
            gl.bindFramebuffer(GLFramebufferTarget.FRAMEBUFFER, fb)
            gl.viewport(0, 0, renderTarget.width, renderTarget.height)
        }
    }

    fun clear(color: Boolean = true, depth: Boolean = true, stencil: Boolean = true) {
        val gl = resolveGlContext()
        gl.clearColor(clearColor.r, clearColor.g, clearColor.b, clearAlpha)
        val buffers = mutableSetOf<GLClearBuffer>()
        if (color) buffers.add(GLClearBuffer.COLOR)
        if (depth) buffers.add(GLClearBuffer.DEPTH)
        if (stencil) buffers.add(GLClearBuffer.STENCIL)
        if (buffers.isNotEmpty()) gl.clear(buffers)
    }

    fun setAnimationLoop(callback: ((deltaMs: Double, elapsedTimeMs: Double) -> Unit)?) {
        vblankSubscription?.dispose()
        vblankSubscription = null
        if (callback == null) return

        vblankSubscription = canvas.registerVBlankRender { ctx, deltaMs, totalElapsedMs, _ ->
            when (ctx) {
                is GLContext -> activeGlContext = ctx
                is VKContext -> activeVkContext = ctx
            }
            callback(deltaMs, totalElapsedMs)
        }
    }

    fun render(scene: Scene, camera: Camera) {
        info.resetFrameCounters()
        info.frame++

        if (scene.autoUpdate) {
            scene.updateMatrixWorld()
        }
        if (camera.parent == null) {
            camera.updateMatrixWorld()
        }

        projScreenMatrix.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)
        frustum.setFromProjectionMatrix(projScreenMatrix)

        val lights = mutableListOf<Light>()
        val renderables = mutableListOf<Object3D>()

        scene.traverseVisible { obj ->
            when (obj) {
                is Light -> lights.add(obj)
                is Mesh, is Line, is Points -> {
                    if (camera.layers.test(obj.layers)) {
                        renderables.add(obj)
                    }
                }
            }
        }

        val vk = activeVkContext ?: canvas.vkContext
        if (vk != null) {
            renderWithVulkan(vk, scene, camera, renderables)
            return
        }

        val gl = resolveGlContext()
        val bg = scene.background
        if (bg != null) {
            gl.clearColor(bg.r, bg.g, bg.b, 1f)
        } else {
            gl.clearColor(clearColor.r, clearColor.g, clearColor.b, clearAlpha)
        }
        if (autoClear) {
            clear(autoClearColor, autoClearDepth, autoClearStencil)
        }

        gl.enable(GLCapability.DEPTH_TEST)
        gl.depthFunc(GLCompareFunction.LEQUAL)

        for (obj in renderables) {
            renderObjectGL(gl, obj, camera, lights)
        }
        gl.flush()
    }

    private fun renderWithVulkan(
        vk: VKContext,
        scene: Scene,
        camera: Camera,
        renderables: List<Object3D>
    ) {
        val bg = scene.background ?: clearColor
        val clearRgba = RgbaColor(
            (bg.r * 255f).toInt().coerceIn(0, 255),
            (bg.g * 255f).toInt().coerceIn(0, 255),
            (bg.b * 255f).toInt().coerceIn(0, 255),
            (clearAlpha * 255f).toInt().coerceIn(0, 255)
        )
        vk.beginRenderPass(
            VKRenderPassDescriptor(
                loadOp = VKLoadOp.CLEAR,
                clearColor = clearRgba,
                clearDepth = 1.0f
            )
        )
        for (obj in renderables) {
            val geometry = when (obj) {
                is Mesh -> obj.geometry
                is Line -> obj.geometry
                is Points -> obj.geometry
                else -> null
            } ?: continue
            val posAttr = geometry.getAttribute("position") ?: continue
            val indexArr = geometry.index
            info.calls++
            if (indexArr != null) {
                val triCount = indexArr.size / 3
                info.triangles += triCount
                vk.drawIndexed(indexArr.size, 1, 0, 0, 0)
            } else {
                info.triangles += posAttr.count / 3
                vk.draw(posAttr.count, 1, 0, 0)
            }
        }
        vk.endRenderPass()
    }

    private fun renderObjectGL(
        gl: GLContext,
        obj: Object3D,
        camera: Camera,
        lights: List<Light>
    ) {
        val geometry: BufferGeometry
        val material: Material
        when (obj) {
            is Mesh -> {
                geometry = obj.geometry
                material = obj.material
            }
            is Line -> {
                geometry = obj.geometry
                material = obj.material
            }
            is Points -> {
                geometry = obj.geometry
                material = obj.material
            }
            else -> return
        }
        if (!material.visible) return

        when (material.side) {
            Side.FRONT -> {
                gl.enable(GLCapability.CULL_FACE)
                gl.cullFace(GLFaceSide.BACK)
            }
            Side.BACK -> {
                gl.enable(GLCapability.CULL_FACE)
                gl.cullFace(GLFaceSide.FRONT)
            }
            Side.DOUBLE -> gl.disable(GLCapability.CULL_FACE)
        }
        gl.depthMask(material.depthWrite)

        val program = ensureProgram(gl, material)
        gl.useProgram(program)

        modelViewMatrix.multiplyMatrices(camera.matrixWorldInverse, obj.matrixWorld)
        normalMatrix.getNormalMatrix(modelViewMatrix)

        gl.getUniformLocation(program, "modelViewMatrix")?.let { gl.uniformMatrix4fv(it, false, modelViewMatrix.elements) }
        gl.getUniformLocation(program, "projectionMatrix")?.let { gl.uniformMatrix4fv(it, false, camera.projectionMatrix.elements) }
        gl.getUniformLocation(program, "modelMatrix")?.let { gl.uniformMatrix4fv(it, false, obj.matrixWorld.elements) }
        gl.getUniformLocation(program, "normalMatrix")?.let { gl.uniformMatrix3fv(it, false, normalMatrix.elements) }

        val matColor = when (material) {
            is MeshBasicMaterial -> material.color
            is MeshStandardMaterial -> material.color
            is MeshPhongMaterial -> material.color
            else -> Color(0xffffff)
        }
        gl.getUniformLocation(program, "uColor")?.let { gl.uniform3f(it, matColor.r, matColor.g, matColor.b) }
        gl.getUniformLocation(program, "uOpacity")?.let { gl.uniform1f(it, material.opacity) }

        val mapTex = when (material) {
            is MeshBasicMaterial -> material.map
            is MeshStandardMaterial -> material.map
            is MeshPhongMaterial -> material.map
            else -> null
        }
        if (mapTex != null) {
            val glTex = ensureTextureUploaded(gl, mapTex)
            gl.activeTexture(0)
            gl.bindTexture(GLTextureTarget.TEXTURE_2D, glTex)
            gl.getUniformLocation(program, "uMap")?.let { gl.uniform1i(it, 0) }
            gl.getUniformLocation(program, "uUseMap")?.let { gl.uniform1i(it, 1) }
        } else {
            gl.getUniformLocation(program, "uUseMap")?.let { gl.uniform1i(it, 0) }
        }

        var ambientR = 0.15f
        var ambientG = 0.15f
        var ambientB = 0.15f
        for (light in lights) {
            if (light is AmbientLight) {
                ambientR += light.color.r * light.intensity
                ambientG += light.color.g * light.intensity
                ambientB += light.color.b * light.intensity
            } else if (light is DirectionalLight) {
                gl.getUniformLocation(program, "uDirectionalLightPos")?.let {
                    gl.uniform3f(it, light.position.x, light.position.y, light.position.z)
                }
                gl.getUniformLocation(program, "uDirectionalLightColor")?.let {
                    gl.uniform3f(
                        it,
                        light.color.r * light.intensity,
                        light.color.g * light.intensity,
                        light.color.b * light.intensity
                    )
                }
            } else if (light is PointLight) {
                gl.getUniformLocation(program, "uPointLightPos")?.let {
                    gl.uniform3f(it, light.position.x, light.position.y, light.position.z)
                }
                gl.getUniformLocation(program, "uPointLightColor")?.let {
                    gl.uniform3f(
                        it,
                        light.color.r * light.intensity,
                        light.color.g * light.intensity,
                        light.color.b * light.intensity
                    )
                }
            }
        }
        gl.getUniformLocation(program, "uAmbientLightColor")?.let { gl.uniform3f(it, ambientR, ambientG, ambientB) }

        val attrBuffers = ensureGeometryBuffers(gl, geometry)
        bindAttribute(gl, program, "position", geometry, attrBuffers)
        bindAttribute(gl, program, "normal", geometry, attrBuffers)
        bindAttribute(gl, program, "uv", geometry, attrBuffers)

        val posAttr = geometry.getAttribute("position") ?: return
        val indexArr = geometry.index

        val drawMode = when (obj) {
            is LineSegments -> GLPrimitive.LINES
            is Line -> GLPrimitive.LINE_STRIP
            is Points -> GLPrimitive.POINTS
            else -> if (material.wireframe) GLPrimitive.LINES else GLPrimitive.TRIANGLES
        }

        info.calls++
        if (indexArr != null) {
            val idxBuffer = ensureIndexBuffer(gl, geometry, indexArr)
            gl.bindBuffer(GLBufferTarget.ELEMENT_ARRAY_BUFFER, idxBuffer)
            if (obj is InstancedMesh) {
                gl.drawElementsInstanced(drawMode, indexArr.size, GLIndexType.UNSIGNED_INT, 0, obj.count)
                info.triangles += (indexArr.size / 3) * obj.count
            } else {
                gl.drawElements(drawMode, indexArr.size, GLIndexType.UNSIGNED_INT, 0)
                when (obj) {
                    is Line -> info.lines += indexArr.size / 2
                    is Points -> info.points += indexArr.size
                    else -> info.triangles += indexArr.size / 3
                }
            }
        } else {
            if (obj is InstancedMesh) {
                gl.drawArraysInstanced(drawMode, 0, posAttr.count, obj.count)
                info.triangles += (posAttr.count / 3) * obj.count
            } else {
                gl.drawArrays(drawMode, 0, posAttr.count)
                when (obj) {
                    is Line -> info.lines += posAttr.count / 2
                    is Points -> info.points += posAttr.count
                    else -> info.triangles += posAttr.count / 3
                }
            }
        }
    }

    private fun bindAttribute(
        gl: GLContext,
        program: GLProgram,
        name: String,
        geometry: BufferGeometry,
        attrBuffers: Map<String, GLBuffer>
    ) {
        val attr = geometry.getAttribute(name) ?: return
        val buffer = attrBuffers[name] ?: return
        val loc = gl.getAttribLocation(program, name)
        if (loc < 0) return
        gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, buffer)
        gl.enableVertexAttribArray(loc)
        gl.vertexAttribPointer(
            GLVertexAttribLayout(
                index = loc,
                size = attr.itemSize,
                type = GLDataType.FLOAT,
                normalized = attr.normalized,
                stride = 0,
                offset = 0
            )
        )
    }

    private fun ensureProgram(gl: GLContext, material: Material): GLProgram {
        return programCache.getOrPut(material.id) {
            val vertexSource = if (material is ShaderMaterial && material.vertexShader.isNotBlank()) {
                material.vertexShader
            } else {
                DEFAULT_VERTEX_SHADER
            }
            val fragmentSource = if (material is ShaderMaterial && material.fragmentShader.isNotBlank()) {
                material.fragmentShader
            } else {
                DEFAULT_FRAGMENT_SHADER
            }
            val vs = gl.createShader(ShaderType.VERTEX, vertexSource)
            val fs = gl.createShader(ShaderType.FRAGMENT, fragmentSource)
            info.programsCount++
            gl.createProgram(vs, fs)
        }
    }

    private fun ensureGeometryBuffers(gl: GLContext, geometry: BufferGeometry): Map<String, GLBuffer> {
        return vertexBufferCache.getOrPut(geometry.id) {
            info.geometriesCount++
            val map = mutableMapOf<String, GLBuffer>()
            for ((name, attr) in geometry.attributes) {
                val buf = gl.createBuffer()
                gl.bindBuffer(GLBufferTarget.ARRAY_BUFFER, buf)
                gl.bufferData(GLBufferTarget.ARRAY_BUFFER, attr.array, GLBufferUsage.STATIC_DRAW)
                map[name] = buf
            }
            map
        }
    }

    private fun ensureIndexBuffer(gl: GLContext, geometry: BufferGeometry, intIndices: IntArray): GLBuffer {
        return indexBufferCache.getOrPut(geometry.id) {
            val buf = gl.createBuffer()
            gl.bindBuffer(GLBufferTarget.ELEMENT_ARRAY_BUFFER, buf)
            gl.bufferData(GLBufferTarget.ELEMENT_ARRAY_BUFFER, intIndices, GLBufferUsage.STATIC_DRAW)
            buf
        }
    }

    private fun ensureTextureUploaded(gl: GLContext, texture: Texture): GLTexture {
        val existing = textureCache[texture.id]
        if (existing != null && !texture.needsUpdate) {
            return existing
        }
        val glTex = existing ?: gl.createTexture().also {
            textureCache[texture.id] = it
            info.texturesCount++
        }
        gl.bindTexture(GLTextureTarget.TEXTURE_2D, glTex)
        val img = texture.image
        if (img != null) {
            val rgba = img.toRgba8888()
            gl.texImage2D(GLTextureTarget.TEXTURE_2D, 0, GLTextureFormat.RGBA8, rgba.width, rgba.height, rgba.bytes)
        } else if (texture is DataTexture) {
            gl.texImage2D(GLTextureTarget.TEXTURE_2D, 0, GLTextureFormat.RGBA8, texture.width, texture.height, texture.data)
        } else if (texture.rgbaBytes != null) {
            gl.texImage2D(GLTextureTarget.TEXTURE_2D, 0, GLTextureFormat.RGBA8, texture.width, texture.height, texture.rgbaBytes)
        } else {
            gl.texImage2D(
                GLTextureTarget.TEXTURE_2D,
                0,
                GLTextureFormat.RGBA8,
                texture.width.coerceAtLeast(1),
                texture.height.coerceAtLeast(1),
                null
            )
        }
        if (texture.generateMipmaps) {
            gl.generateMipmap(GLTextureTarget.TEXTURE_2D)
        }
        texture.needsUpdate = false
        return glTex
    }

    private fun resolveGlContext(): GLContext {
        activeGlContext?.let { return it }
        canvas.glContext?.let {
            activeGlContext = it
            return it
        }
        val created = Canvas3DExtension.createGLContext(width = width, height = height)
        activeGlContext = created
        return created
    }

    fun dispose() {
        vblankSubscription?.dispose()
        vblankSubscription = null
        val gl = activeGlContext
        if (gl != null) {
            for (prog in programCache.values) gl.deleteProgram(prog)
            for (bufMap in vertexBufferCache.values) {
                for (b in bufMap.values) gl.deleteBuffer(b)
            }
            for (ib in indexBufferCache.values) gl.deleteBuffer(ib)
            for (tex in textureCache.values) gl.deleteTexture(tex)
            for (fb in renderTargetFramebuffers.values) gl.deleteFramebuffer(fb)
        }
        programCache.clear()
        vertexBufferCache.clear()
        indexBufferCache.clear()
        textureCache.clear()
        renderTargetFramebuffers.clear()
    }

    companion object {
        private const val DEFAULT_VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 normal;
            layout(location = 2) in vec2 uv;
            uniform mat4 modelViewMatrix;
            uniform mat4 projectionMatrix;
            uniform mat3 normalMatrix;
            out vec3 vNormal;
            out vec2 vUv;
            void main() {
                vNormal = normalize(normalMatrix * normal);
                vUv = uv;
                gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
            }
        """

        private const val DEFAULT_FRAGMENT_SHADER = """
            #version 300 es
            precision highp float;
            in vec3 vNormal;
            in vec2 vUv;
            uniform vec3 uColor;
            uniform float uOpacity;
            uniform vec3 uAmbientLightColor;
            uniform vec3 uDirectionalLightPos;
            uniform vec3 uDirectionalLightColor;
            uniform sampler2D uMap;
            uniform int uUseMap;
            out vec4 fragColor;
            void main() {
                vec3 lightDir = normalize(uDirectionalLightPos);
                float diff = max(dot(normalize(vNormal), lightDir), 0.25);
                vec3 baseColor = uColor;
                if (uUseMap == 1) {
                    baseColor *= texture(uMap, vUv).rgb;
                }
                vec3 lit = baseColor * (uAmbientLightColor + uDirectionalLightColor * diff);
                fragColor = vec4(lit, uOpacity);
            }
        """
    }
}
