package com.nexustools.genericui.ext.box3d.dsl

import com.nexustools.genericui.ext.box3d.BodyType3D
import com.nexustools.genericui.ext.box3d.Box3DDebugRenderer
import com.nexustools.genericui.ext.box3d.RigidBody3D
import com.nexustools.genericui.ext.box3d.RigidBodyDesc3D
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.box3d.World3D
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * Fluent DSL scope for populating a 3D physics [World3D] and configuring its [Box3DDebugRenderer].
 */
@GenericUiDslMarker
class Box3DWorldDslScope(
    val world: World3D = World3D(),
    val scene: Scene = Scene(),
    var camera: Camera = PerspectiveCamera(50f, 800f / 600f, 0.1f, 2000f).apply {
        position.set(0f, 3f, 8f)
    },
    val debugRenderer: Box3DDebugRenderer = Box3DDebugRenderer(world, scene)
) {
    /**
     * Creates a [RigidBody3D] in [world] and configures its colliders via [Box3DRigidBodyDslScope].
     */
    fun rigidBody(
        type: BodyType3D = BodyType3D.DYNAMIC,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        boundObject3D: Object3D? = null,
        block: (Box3DRigidBodyDslScope.() -> Unit)? = null
    ): RigidBody3D {
        val created = world.createRigidBody(
            desc = RigidBodyDesc3D(type = type, position = Vec3(x, y, z)),
            boundObject3D = boundObject3D
        )
        if (block != null) {
            Box3DRigidBodyDslScope(created).block()
        }
        return created
    }

    /**
     * Creates a dynamic [RigidBody3D] in [world].
     */
    fun dynamicRigidBody(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        boundObject3D: Object3D? = null,
        block: (Box3DRigidBodyDslScope.() -> Unit)? = null
    ): RigidBody3D = rigidBody(
        type = BodyType3D.DYNAMIC,
        x = x,
        y = y,
        z = z,
        boundObject3D = boundObject3D,
        block = block
    )

    /**
     * Creates a static [RigidBody3D] in [world].
     */
    fun staticRigidBody(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        boundObject3D: Object3D? = null,
        block: (Box3DRigidBodyDslScope.() -> Unit)? = null
    ): RigidBody3D = rigidBody(
        type = BodyType3D.STATIC,
        x = x,
        y = y,
        z = z,
        boundObject3D = boundObject3D,
        block = block
    )

    /**
     * Creates a kinematic [RigidBody3D] in [world].
     */
    fun kinematicRigidBody(
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        boundObject3D: Object3D? = null,
        block: (Box3DRigidBodyDslScope.() -> Unit)? = null
    ): RigidBody3D = rigidBody(
        type = BodyType3D.KINEMATIC,
        x = x,
        y = y,
        z = z,
        boundObject3D = boundObject3D,
        block = block
    )
}
