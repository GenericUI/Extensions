package com.nexustools.genericui.ext.engine2d.dsl

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.box2d.BodyType2D
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.dsl.EntityDslScope
import com.nexustools.genericui.ext.engine.dsl.GameSceneDslScope
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.svg.HumanoidSkeleton2DFactory
import com.nexustools.genericui.ext.engine.svg.Skeleton2D
import com.nexustools.genericui.ext.engine2d.Camera2DModule
import com.nexustools.genericui.ext.engine2d.Collider2DModule
import com.nexustools.genericui.ext.engine2d.Game2DCanvasHost
import com.nexustools.genericui.ext.engine2d.ParticleEmitter2DModule
import com.nexustools.genericui.ext.engine2d.PhysicsWorld2DSubsystem
import com.nexustools.genericui.ext.engine2d.RigidBody2DModule
import com.nexustools.genericui.ext.engine2d.Sprite2DModule
import com.nexustools.genericui.ext.engine2d.SvgCharacter2DModule
import com.nexustools.genericui.ext.engine2d.Tilemap2DModule
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Mounts an in-place hardware-accelerated 2D [Game2DCanvasHost] inside this [AbstractContainer].
 */
fun AbstractContainer.game2DCanvas(
    width: Int = 800,
    height: Int = 600,
    targetTicksPerSecond: Int = 60,
    autoStart: Boolean = false,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Game2DDslScope.() -> Unit)? = null
): Game2DCanvasHost {
    val canvas = Canvas2D(width = width, height = height)
    if (!classes.isNullOrBlank()) {
        canvas.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        canvas.id = id
    }
    val host = Game2DCanvasHost(
        scene = GameScene("Game2DScene"),
        canvas = canvas,
        targetTicksPerSecond = targetTicksPerSecond
    )
    if (block != null) {
        Game2DDslScope(host).block()
    }
    addChild(canvas)
    if (autoStart) {
        host.start()
    }
    return host
}

fun GameSceneDslScope.physics2D(
    gravity: Vec2 = Vec2(0f, -9.81f),
    debugDraw: Boolean = false,
    block: (@GenericUiDslMarker PhysicsWorld2DSubsystem.() -> Unit)? = null
): PhysicsWorld2DSubsystem {
    val sub = PhysicsWorld2DSubsystem(gravity = gravity).apply {
        debugDrawEnabled = debugDraw
    }
    block?.invoke(sub)
    return scene.registerSubsystem(sub)
}

fun EntityDslScope.camera2D(
    viewportWidth: Float = 800f,
    viewportHeight: Float = 600f,
    zoom: Float = 1f,
    block: (@GenericUiDslMarker Camera2DModule.() -> Unit)? = null
): Camera2DModule {
    val mod = Camera2DModule(viewportWidth = viewportWidth, viewportHeight = viewportHeight, zoom = zoom)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.sprite2D(
    image: ImageBuffer? = null,
    width: Float = 64f,
    height: Float = 64f,
    tintColor: RgbaColor = RgbaColor.WHITE,
    zOrder: Int = 0,
    block: (@GenericUiDslMarker Sprite2DModule.() -> Unit)? = null
): Sprite2DModule {
    val mod = Sprite2DModule(
        image = image,
        width = width,
        height = height,
        tintColor = tintColor,
        zOrder = zOrder
    )
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.rigidBody2D(
    bodyType: BodyType2D = BodyType2D.DYNAMIC,
    gravityScale: Float = 1f,
    fixedRotation: Boolean = false,
    block: (@GenericUiDslMarker RigidBody2DModule.() -> Unit)? = null
): RigidBody2DModule {
    val mod = RigidBody2DModule(
        bodyType = bodyType,
        gravityScale = gravityScale,
        fixedRotation = fixedRotation
    )
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.boxCollider2D(
    width: Float,
    height: Float,
    density: Float = 1f,
    friction: Float = 0.25f,
    restitution: Float = 0.1f,
    isSensor: Boolean = false
): Collider2DModule = entity.addModule(
    Collider2DModule.box(
        width = width,
        height = height,
        density = density,
        friction = friction,
        restitution = restitution,
        isSensor = isSensor
    )
)

fun EntityDslScope.circleCollider2D(
    radius: Float,
    density: Float = 1f,
    friction: Float = 0.25f,
    restitution: Float = 0.1f,
    isSensor: Boolean = false
): Collider2DModule = entity.addModule(
    Collider2DModule.circle(
        radius = radius,
        density = density,
        friction = friction,
        restitution = restitution,
        isSensor = isSensor
    )
)

fun EntityDslScope.svgCharacter2D(
    skeleton: Skeleton2D = HumanoidSkeleton2DFactory.create(),
    zOrder: Int = 0,
    drawBoneOverlay: Boolean = false,
    block: (@GenericUiDslMarker SvgCharacter2DModule.() -> Unit)? = null
): SvgCharacter2DModule {
    val mod = SvgCharacter2DModule(
        skeleton = skeleton,
        zOrder = zOrder,
        drawBoneOverlay = drawBoneOverlay
    )
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.particles2D(
    emissionRatePerSecond: Float = 20f,
    block: (@GenericUiDslMarker ParticleEmitter2DModule.() -> Unit)? = null
): ParticleEmitter2DModule {
    val mod = ParticleEmitter2DModule(emissionRatePerSecond = emissionRatePerSecond)
    block?.invoke(mod)
    return entity.addModule(mod)
}

fun EntityDslScope.tilemap2D(
    columns: Int,
    rows: Int,
    tileWidth: Float = 32f,
    tileHeight: Float = 32f,
    block: (@GenericUiDslMarker Tilemap2DModule.() -> Unit)? = null
): Tilemap2DModule {
    val mod = Tilemap2DModule(
        columns = columns,
        rows = rows,
        tileWidth = tileWidth,
        tileHeight = tileHeight
    )
    block?.invoke(mod)
    return entity.addModule(mod)
}
