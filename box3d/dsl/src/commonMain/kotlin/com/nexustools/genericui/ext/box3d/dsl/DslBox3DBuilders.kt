package com.nexustools.genericui.ext.box3d.dsl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.box3d.World3D
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * In-place 3D physics debug canvas builder backed by [GLCanvasExt], [World3D], and [Box3DDebugRenderer].
 */
fun AbstractContainer.box3dDebugCanvas(
    world: World3D = World3D(),
    scene: Scene = Scene(),
    width: Int = 800,
    height: Int = 600,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Box3DWorldDslScope.() -> Unit)? = null
): GLCanvasExt {
    val glCanvas = GLCanvasExt(width = width, height = height)
    if (!classes.isNullOrBlank()) {
        glCanvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        glCanvas.id = id
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
    scope.debugRenderer.bindToCanvas3D(camera = scope.camera, glCanvas = glCanvas)
    addChild(glCanvas)
    return glCanvas
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
