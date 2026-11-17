package com.nexustools.genericui.ext.box3d.dsl

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.box3d.World3D
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * In-place 3D physics debug canvas builder backed by [Canvas3D], [World3D], and [com.nexustools.genericui.ext.box3d.Box3DDebugRenderer].
 */
fun AbstractContainer.box3dDebugCanvas(
    world: World3D = World3D(),
    scene: Scene = Scene(),
    backend: GpuBackendType = GpuBackendType.OPENGL_ES,
    acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Box3DWorldDslScope.() -> Unit)? = null
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
    val aspect = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1).toFloat()
    val scope = Box3DWorldDslScope(
        world = world,
        scene = scene,
        camera = PerspectiveCamera(50f, aspect, 0.1f, 2000f).apply {
            position.set(0f, 3f, 8f)
        }
    )
    if (block != null) {
        scope.block()
    }
    scope.debugRenderer.update()
    scope.debugRenderer.bindToCanvas3D(camera = scope.camera, canvas = canvas)
    addChild(canvas)
    return canvas
}

/**
 * Standalone [World3D] DSL builder.
 */
fun world3D(
    gravity: Vec3 = Vec3(0f, -9.81f, 0f),
    block: (@GenericUiDslMarker Box3DWorldDslScope.() -> Unit)? = null
): World3D {
    val world = World3D(gravity = gravity)
    if (block != null) {
        Box3DWorldDslScope(world = world).block()
    }
    return world
}
