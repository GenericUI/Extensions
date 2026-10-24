package com.nexustools.genericui.ext.box2d

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.component.media.Canvas2DContext
import com.nexustools.genericui.component.media.GLCanvas
import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtSurface
import com.nexustools.genericui.ext.canvas2d.Canvas2DExtension
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.PI

/**
 * 2D Physics Debug Drawer rendering [World2D] bodies, fixtures, AABBs, joints, and contact points
 * onto a `:extensions:canvas2d` [Canvas2D] / [Canvas2DExtSurface] or `:extensions:canvas3d` [GLCanvas] / [GLCanvasExt].
 */
class Box2DDebugDrawer(
    var pixelsPerMeter: Float = 50f,
    var offsetX: Float = 400f,
    var offsetY: Float = 300f,
    var flipY: Boolean = true
) {
    var drawShapes: Boolean = true
    var drawJoints: Boolean = true
    var drawAABBs: Boolean = false
    var drawContacts: Boolean = true
    var drawCenterOfMass: Boolean = false

    val staticBodyColor = RgbaColor(120, 220, 120, 255)
    val kinematicBodyColor = RgbaColor(120, 120, 240, 255)
    val dynamicBodyColor = RgbaColor(240, 180, 100, 255)
    val sleepingBodyColor = RgbaColor(150, 150, 150, 200)
    val aabbColor = RgbaColor(220, 80, 220, 180)
    val jointColor = RgbaColor(80, 200, 220, 255)
    val contactColor = RgbaColor(255, 60, 60, 255)

    private var vblankSubscription: VBlankSubscription? = null

    fun worldToScreenX(wx: Float): Float = offsetX + wx * pixelsPerMeter
    fun worldToScreenY(wy: Float): Float = if (flipY) offsetY - wy * pixelsPerMeter else offsetY + wy * pixelsPerMeter

    /**
     * Binds a [World2D] simulation to a [Canvas2D] (such as [Canvas2DExtSurface]), automatically stepping
     * the physics world by `deltaMs` and drawing the debug overlay on each hardware VBlank frame.
     */
    fun bindToCanvas2D(
        world: World2D,
        canvas: Canvas2D = Canvas2DExtSurface(),
        clearBackground: Boolean = true
    ): VBlankSubscription {
        vblankSubscription?.dispose()
        val sub = canvas.registerVBlankRender { ctx, deltaMs, _, _ ->
            val dtSec = (deltaMs / 1000.0).toFloat().coerceIn(0f, 0.05f)
            if (dtSec > 0f) {
                world.step(dtSec)
            }
            if (clearBackground) {
                ctx.clearRect(0f, 0f, ctx.width.toFloat(), ctx.height.toFloat())
            }
            drawWorld(world, ctx)
        }
        vblankSubscription = sub
        return sub
    }

    /**
     * Binds a [World2D] simulation to a [GLCanvas] (such as [GLCanvasExt]) for hardware-accelerated stepping.
     */
    fun bindToGLCanvas(
        world: World2D,
        glCanvas: GLCanvas = GLCanvasExt()
    ): VBlankSubscription {
        vblankSubscription?.dispose()
        val offscreen2D = Canvas2DExtension.createContext(
            glCanvas.surfaceWidth.coerceAtLeast(800),
            glCanvas.surfaceHeight.coerceAtLeast(600)
        )
        val sub = glCanvas.registerVBlankRender { _, deltaMs, _, _ ->
            val dtSec = (deltaMs / 1000.0).toFloat().coerceIn(0f, 0.05f)
            if (dtSec > 0f) {
                world.step(dtSec)
            }
            drawWorld(world, offscreen2D)
        }
        vblankSubscription = sub
        return sub
    }

    /**
     * Renders all enabled debug layers of [world] onto [ctx].
     */
    fun drawWorld(world: World2D, ctx: Canvas2DContext) {
        if (drawShapes) {
            for (body in world.bodies) {
                val color = when {
                    !body.isAwake -> sleepingBodyColor
                    body.type == BodyType2D.STATIC -> staticBodyColor
                    body.type == BodyType2D.KINEMATIC -> kinematicBodyColor
                    else -> dynamicBodyColor
                }
                for (fixture in body.fixtures) {
                    drawFixtureShape(ctx, body.transform, fixture.shape, color)
                }
            }
        }

        if (drawAABBs) {
            ctx.setStrokeColor(aabbColor)
            ctx.setLineWidth(1f)
            for (body in world.bodies) {
                for (fixture in body.fixtures) {
                    val aabb = fixture.computeAABB()
                    val x0 = worldToScreenX(aabb.lowerBound.x)
                    val y0 = worldToScreenY(aabb.upperBound.y)
                    val x1 = worldToScreenX(aabb.upperBound.x)
                    val y1 = worldToScreenY(aabb.lowerBound.y)
                    ctx.strokeRect(x0, y0, x1 - x0, y1 - y0)
                }
            }
        }

        if (drawJoints) {
            ctx.setStrokeColor(jointColor)
            ctx.setLineWidth(1.5f)
            for (joint in world.joints) {
                val a = joint.getAnchorA()
                val b = joint.getAnchorB()
                ctx.beginPath()
                ctx.moveTo(worldToScreenX(a.x), worldToScreenY(a.y))
                ctx.lineTo(worldToScreenX(b.x), worldToScreenY(b.y))
                ctx.stroke()
            }
        }

        if (drawContacts) {
            ctx.setFillColor(contactColor)
            for (contact in world.contacts) {
                if (!contact.isTouching) continue
                for (pt in contact.manifold.points) {
                    val sx = worldToScreenX(pt.x)
                    val sy = worldToScreenY(pt.y)
                    ctx.fillRect(sx - 2.5f, sy - 2.5f, 5f, 5f)
                }
            }
        }

        if (drawCenterOfMass) {
            for (body in world.bodies) {
                val pos = body.position
                val sx = worldToScreenX(pos.x)
                val sy = worldToScreenY(pos.y)
                val xAxis = body.getWorldPoint(Vec2(0.3f, 0f))
                val yAxis = body.getWorldPoint(Vec2(0f, 0.3f))
                ctx.setStrokeColor(RgbaColor(255, 80, 80, 255))
                ctx.beginPath()
                ctx.moveTo(sx, sy)
                ctx.lineTo(worldToScreenX(xAxis.x), worldToScreenY(xAxis.y))
                ctx.stroke()
                ctx.setStrokeColor(RgbaColor(80, 255, 80, 255))
                ctx.beginPath()
                ctx.moveTo(sx, sy)
                ctx.lineTo(worldToScreenX(yAxis.x), worldToScreenY(yAxis.y))
                ctx.stroke()
            }
        }
    }

    private fun drawFixtureShape(
        ctx: Canvas2DContext,
        xf: Transform2D,
        shape: Shape2D,
        color: RgbaColor
    ) {
        ctx.setStrokeColor(color)
        ctx.setFillColor(RgbaColor(color.r, color.g, color.b, 50))
        ctx.setLineWidth(1.5f)

        when (shape) {
            is CircleShape2D -> {
                val center = xf.mul(shape.localPosition)
                val sx = worldToScreenX(center.x)
                val sy = worldToScreenY(center.y)
                val sr = shape.radius * pixelsPerMeter
                ctx.beginPath()
                ctx.arc(sx, sy, sr, 0f, (PI * 2.0).toFloat(), false)
                ctx.fill()
                ctx.stroke()

                val edge = xf.mul(Vec2(shape.localPosition.x + shape.radius, shape.localPosition.y))
                ctx.beginPath()
                ctx.moveTo(sx, sy)
                ctx.lineTo(worldToScreenX(edge.x), worldToScreenY(edge.y))
                ctx.stroke()
            }
            is PolygonShape2D -> {
                val verts = shape.vertices
                if (verts.isEmpty()) return
                ctx.beginPath()
                val first = xf.mul(verts[0])
                ctx.moveTo(worldToScreenX(first.x), worldToScreenY(first.y))
                for (i in 1 until verts.size) {
                    val v = xf.mul(verts[i])
                    ctx.lineTo(worldToScreenX(v.x), worldToScreenY(v.y))
                }
                ctx.closePath()
                ctx.fill()
                ctx.stroke()
            }
            is EdgeShape2D -> {
                val v1 = xf.mul(shape.vertex1)
                val v2 = xf.mul(shape.vertex2)
                ctx.beginPath()
                ctx.moveTo(worldToScreenX(v1.x), worldToScreenY(v1.y))
                ctx.lineTo(worldToScreenX(v2.x), worldToScreenY(v2.y))
                ctx.stroke()
            }
            is ChainShape2D -> {
                val verts = shape.vertices
                if (verts.size < 2) return
                ctx.beginPath()
                val first = xf.mul(verts[0])
                ctx.moveTo(worldToScreenX(first.x), worldToScreenY(first.y))
                for (i in 1 until verts.size) {
                    val v = xf.mul(verts[i])
                    ctx.lineTo(worldToScreenX(v.x), worldToScreenY(v.y))
                }
                if (shape.isLoop) ctx.closePath()
                ctx.stroke()
            }
        }
    }
}
