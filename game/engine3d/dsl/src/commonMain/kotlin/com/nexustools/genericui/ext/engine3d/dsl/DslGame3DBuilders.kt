package com.nexustools.genericui.ext.engine3d.dsl

import com.nexustools.genericui.component.media.Canvas3D
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.box3d.BodyType3D
import com.nexustools.genericui.ext.box3d.Vec3
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.dsl.EntityDslScope
import com.nexustools.genericui.ext.engine.dsl.GameSceneDslScope
import com.nexustools.genericui.ext.engine.math.Vec3f
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.engine3d.CharacterController3DModule
import com.nexustools.genericui.ext.engine3d.Collider3DModule
import com.nexustools.genericui.ext.engine3d.DirectionalLight3DModule
import com.nexustools.genericui.ext.engine3d.FollowCamera3DModule
import com.nexustools.genericui.ext.engine3d.Game3DCanvasHost
import com.nexustools.genericui.ext.engine3d.MeshRenderer3DModule
import com.nexustools.genericui.ext.engine3d.Node3DBindingModule
import com.nexustools.genericui.ext.engine3d.OrbitCamera3DModule
import com.nexustools.genericui.ext.engine3d.PhysicsWorld3DSubsystem
import com.nexustools.genericui.ext.engine3d.PointLight3DModule
import com.nexustools.genericui.ext.engine3d.ProceduralHumanoidRig3D
import com.nexustools.genericui.ext.engine3d.RigidBody3DModule
import com.nexustools.genericui.ext.engine3d.SkeletalModel3DModule
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.geometries.BoxGeometry
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.renderers.WebGLRenderer
import com.nexustools.genericui.ext.threejs.renderers.WebGLRendererConfig
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * Mounts an in-place hardware-accelerated 3D [Game3DCanvasHost] inside this [AbstractContainer].
 */
fun AbstractContainer.game3DCanvas(
    width: Int = 800,
    height: Int = 600,
    backend: GpuBackendType = GpuBackendType.OPENGL_ES,
    acceleration: GpuAccelerationPolicy = GpuAccelerationPolicy.HARDWARE_PREFERRED,
    targetTicksPerSecond: Int = 60,
    autoStart: Boolean = false,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Game3DDslScope.() -> Unit)? = null
): Game3DCanvasHost {
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
    val renderer = WebGLRenderer(
        config = WebGLRendererConfig(preferVulkanWhenAvailable = backend == GpuBackendType.VULKAN),
        canvas = canvas
    )
    val aspect = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1).toFloat()
    val host = Game3DCanvasHost(
        scene = GameScene("Game3DScene"),
        renderer = renderer,
        threeScene = Scene(),
        activeCamera = PerspectiveCamera(50f, aspect, 0.1f, 2000f).apply {
            position.set(0f, 2f, 6f)
        },
        targetTicksPerSecond = targetTicksPerSecond
    )
    if (block != null) {
        Game3DDslScope(host).block()
    }
    addChild(canvas)
    if (autoStart) {
        host.start()
    }
    return host
}

fun GameSceneDslScope.physics3D(
    gravity: Vec3 = Vec3(0f, -9.81f, 0f),
    threeScene: Scene? = null,
    debugDraw: Boolean = false,
    block: (@GenericUiDslMarker PhysicsWorld3DSubsystem.() -> Unit)? = null
): PhysicsWorld3DSubsystem {
    val sub = PhysicsWorld3DSubsystem(gravity = gravity, threeScene = threeScene).apply {
        debugDrawEnabled = debugDraw
    }
    block?.invoke(sub)
    return scene.registerSubsystem(sub)
}

fun EntityDslScope.node3D(
    object3D: Object3D = Object3D(),
    block: (@GenericUiDslMarker Node3DBindingModule.() -> Unit)? = null
): Node3DBindingModule {
    val mod = Node3DBindingModule(object3D = object3D)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.meshRenderer3D(
    geometry: BufferGeometry = BoxGeometry(1f, 1f, 1f),
    material: Material = MeshStandardMaterial(),
    block: (@GenericUiDslMarker MeshRenderer3DModule.() -> Unit)? = null
): MeshRenderer3DModule {
    val mod = MeshRenderer3DModule(geometry = geometry, material = material)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.directionalLight3D(
    colorHex: Int = 0xffffff,
    intensity: Float = 1f,
    block: (@GenericUiDslMarker DirectionalLight3DModule.() -> Unit)? = null
): DirectionalLight3DModule {
    val mod = DirectionalLight3DModule(color = Color(colorHex), intensity = intensity)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.pointLight3D(
    colorHex: Int = 0xffffff,
    intensity: Float = 1f,
    distance: Float = 0f,
    decay: Float = 2f,
    block: (@GenericUiDslMarker PointLight3DModule.() -> Unit)? = null
): PointLight3DModule {
    val mod = PointLight3DModule(color = Color(colorHex), intensity = intensity, distance = distance, decay = decay)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.orbitCamera3D(
    fov: Float = 50f,
    aspect: Float = 800f / 600f,
    block: (@GenericUiDslMarker OrbitCamera3DModule.() -> Unit)? = null
): OrbitCamera3DModule {
    val mod = OrbitCamera3DModule(camera = PerspectiveCamera(fov, aspect, 0.1f, 2000f))
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.followCamera3D(
    target: SceneEntity? = null,
    offset: Vec3f = Vec3f(0f, 3f, 6f),
    damping: Float = 8f,
    block: (@GenericUiDslMarker FollowCamera3DModule.() -> Unit)? = null
): FollowCamera3DModule {
    val mod = FollowCamera3DModule(target = target, offset = offset, damping = damping)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.rigidBody3D(
    bodyType: BodyType3D = BodyType3D.DYNAMIC,
    gravityScale: Float = 1f,
    lockRotations: Boolean = false,
    block: (@GenericUiDslMarker RigidBody3DModule.() -> Unit)? = null
): RigidBody3DModule {
    val mod = RigidBody3DModule(bodyType = bodyType, gravityScale = gravityScale, lockRotations = lockRotations)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.boxCollider3D(
    halfWidth: Float = 0.5f,
    halfHeight: Float = 0.5f,
    halfDepth: Float = 0.5f,
    density: Float = 1f,
    friction: Float = 0.5f,
    restitution: Float = 0.1f,
    isSensor: Boolean = false
): Collider3DModule = entity.addModule(
    Collider3DModule.box(halfWidth, halfHeight, halfDepth, density, friction, restitution, isSensor)
)

fun EntityDslScope.sphereCollider3D(
    radius: Float = 0.5f,
    density: Float = 1f,
    friction: Float = 0.5f,
    restitution: Float = 0.1f,
    isSensor: Boolean = false
): Collider3DModule = entity.addModule(
    Collider3DModule.sphere(radius, density, friction, restitution, isSensor)
)

fun EntityDslScope.characterController3D(
    maxSlopeAngleRad: Float = 0.7853982f,
    maxStepHeight: Float = 0.35f,
    skinWidth: Float = 0.02f,
    block: (@GenericUiDslMarker CharacterController3DModule.() -> Unit)? = null
): CharacterController3DModule {
    val mod = CharacterController3DModule(maxSlopeAngleRad, maxStepHeight, skinWidth)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.humanoidRig3D(
    colorHex: Int = 0x60a5fa,
    scale: Float = 1f,
    block: (@GenericUiDslMarker SkeletalModel3DModule.() -> Unit)? = null
): SkeletalModel3DModule {
    val rig = ProceduralHumanoidRig3D.create(
        material = MeshStandardMaterial(color = Color(colorHex), roughness = 0.55f),
        scale = scale
    )
    val mod = SkeletalModel3DModule(rig)
    block?.invoke(mod)
    return entity.addModule(mod)
}
