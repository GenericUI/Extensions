package com.nexustools.genericui.ext.threejs.dsl

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendType
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
 * In-place Three.js 3D canvas builder backed by [Canvas3D] and [WebGLRenderer].
 */
fun AbstractContainer.threeJsCanvas(
    width: Int = 800,
    height: Int = 600,
    backend: GpuBackendType = GpuBackendType.OPENGL_ES,
    acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
    config: WebGLRendererConfig = WebGLRendererConfig(preferVulkanWhenAvailable = backend == GpuBackendType.VULKAN),
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker ThreeSceneDslScope.() -> Unit)? = null
): Canvas3D {
    val canvas = Canvas3D(
        backend = backend,
        acceleration = acceleration,
        width = width,
        height = height
    )
    if (!classes.isNullOrBlank()) {
        canvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        canvas.id = id
    }
    val renderer = WebGLRenderer(config = config, canvas = canvas)
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
    addChild(canvas)
    return canvas
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
