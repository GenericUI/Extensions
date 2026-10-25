package com.nexustools.genericui.ext.threejs.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.canvas3d.VKCanvasExt
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.geometries.BoxGeometry
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.objects.Mesh
import com.nexustools.genericui.ext.threejs.renderers.WebGLRenderer
import com.nexustools.genericui.ext.threejs.renderers.WebGLRendererConfig
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * In-place Three.js OpenGL ES 3D canvas builder backed by [GLCanvasExt] and [WebGLRenderer].
 */
fun AbstractContainer.threeJsGlCanvas(
    width: Int = 800,
    height: Int = 600,
    config: WebGLRendererConfig = WebGLRendererConfig(),
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker ThreeSceneDslScope.() -> Unit)? = null
): GLCanvasExt {
    val glCanvas = GLCanvasExt(width = width, height = height)
    if (!classes.isNullOrBlank()) {
        glCanvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        glCanvas.id = id
    }
    val renderer = WebGLRenderer(config = config, glCanvas = glCanvas)
    val defaultAspect = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1).toFloat()
    val scope = ThreeSceneDslScope(
        scene = Scene(),
        activeCamera = PerspectiveCamera(50f, defaultAspect, 0.1f, 2000f).apply {
            position.set(0f, 0f, 5f)
        },
        renderer = renderer
    )
    if (block != null) {
        scope.block()
    }
    renderer.setAnimationLoop { deltaMs, elapsedTimeMs ->
        scope.onBeforeRender?.invoke(deltaMs, elapsedTimeMs)
        renderer.render(scope.scene, scope.activeCamera)
    }
    addChild(glCanvas)
    return glCanvas
}

/**
 * In-place Three.js Vulkan 3D canvas builder backed by [VKCanvasExt] and [WebGLRenderer].
 */
fun AbstractContainer.threeJsVkCanvas(
    width: Int = 800,
    height: Int = 600,
    config: WebGLRendererConfig = WebGLRendererConfig(preferVulkanWhenAvailable = true),
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker ThreeSceneDslScope.() -> Unit)? = null
): VKCanvasExt {
    val vkCanvas = VKCanvasExt(width = width, height = height)
    if (!classes.isNullOrBlank()) {
        vkCanvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        vkCanvas.id = id
    }
    val renderer = WebGLRenderer(
        config = config,
        glCanvas = GLCanvasExt(width = width, height = height),
        vkCanvas = vkCanvas
    )
    val defaultAspect = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1).toFloat()
    val scope = ThreeSceneDslScope(
        scene = Scene(),
        activeCamera = PerspectiveCamera(50f, defaultAspect, 0.1f, 2000f).apply {
            position.set(0f, 0f, 5f)
        },
        renderer = renderer
    )
    if (block != null) {
        scope.block()
    }
    renderer.setAnimationLoop { deltaMs, elapsedTimeMs ->
        scope.onBeforeRender?.invoke(deltaMs, elapsedTimeMs)
        renderer.render(scope.scene, scope.activeCamera)
    }
    addChild(vkCanvas)
    return vkCanvas
}

/**
 * Standalone Three.js [Scene] DSL builder.
 */
fun scene(
    block: (@GenericUiDslMarker ThreeSceneDslScope.() -> Unit)? = null
): Scene {
    val scope = ThreeSceneDslScope()
    if (block != null) {
        scope.block()
    }
    return scope.scene
}

/**
 * Extension builder that creates and adds a [Mesh] directly onto a [Scene].
 */
fun Scene.mesh(
    geometry: BufferGeometry = BoxGeometry(),
    material: Material = MeshStandardMaterial(),
    name: String = "",
    block: (@GenericUiDslMarker Mesh.() -> Unit)? = null
): Mesh {
    val m = Mesh(geometry = geometry, material = material)
    if (name.isNotEmpty()) {
        m.name = name
    }
    if (block != null) {
        m.block()
    }
    add(m)
    return m
}

/**
 * Extension builder that creates and adds a [PerspectiveCamera] directly onto a [Scene].
 */
fun Scene.perspectiveCamera(
    fov: Float = 50f,
    aspect: Float = 1f,
    near: Float = 0.1f,
    far: Float = 2000f,
    block: (@GenericUiDslMarker PerspectiveCamera.() -> Unit)? = null
): PerspectiveCamera {
    val cam = PerspectiveCamera(fov = fov, aspect = aspect, near = near, far = far)
    if (block != null) {
        cam.block()
    }
    cam.updateProjectionMatrix()
    add(cam)
    return cam
}
