package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.box2d.BodyType2D
import com.nexustools.genericui.ext.box2d.Vec2
import com.nexustools.genericui.ext.engine.scene.FixedStepAccumulator
import com.nexustools.genericui.ext.engine.svg.HumanoidSkeleton2DFactory
import com.nexustools.genericui.ext.imageprocessing.context2d.DefaultCpuImageBuffer
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferPaintStyle
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Engine2DPhysicsAndSvgRenderTest {
    @Test
    fun box2dPhysicsStepSvgParsingAndCharacterRenderingWorkEndToEnd() {
        val host = Game2DCanvasHost()
        val physics = host.scene.registerSubsystem(PhysicsWorld2DSubsystem(gravity = Vec2(0f, -10f)))

        // Ground static body + falling dynamic box
        val ground = host.scene.createEntity("Ground").apply {
            transform.setPosition2D(0f, 0f)
            addModule(RigidBody2DModule(BodyType2D.STATIC))
            addModule(Collider2DModule.box(20f, 1f))
        }
        val crate = host.scene.createEntity("Crate").apply {
            transform.setPosition2D(0f, 10f)
            addModule(RigidBody2DModule(BodyType2D.DYNAMIC))
            addModule(Collider2DModule.box(1f, 1f))
        }

        // 2D SVG skeletal character + tilemap + particle burst
        val rig = HumanoidSkeleton2DFactory.create()
        val walkClip = HumanoidSkeleton2DFactory.createWalkClip(durationSeconds = 0.8f)
        val actor = host.scene.createEntity("Hero2D").apply {
            transform.setPosition2D(64f, 64f)
            addModule(SvgCharacter2DModule(skeleton = rig, drawBoneOverlay = true).apply {
                animator.play(walkClip)
            })
            addModule(ParticleEmitter2DModule(emissionRatePerSecond = 0f).apply {
                emitBurst(6)
            })
            addModule(Tilemap2DModule(4, 4, tileWidth = 16f, tileHeight = 16f).apply {
                setTileColor(1, RgbaColor(40, 180, 99, 255))
                setTile(1, 2, 1)
            })
        }

        val acc = FixedStepAccumulator(fixedStepSeconds = 1f / 60f)
        for (i in 0 until 20) {
            host.scene.stepLogicTick(1f / 60f, acc)
        }
        assertTrue(crate.transform.position.y < 9.5f, "Gravity must move dynamic crate downward")
        assertTrue(ground.transform.position.y == 0f)
        assertTrue(actor.getModule<ParticleEmitter2DModule>()?.activeParticleCount == 6)
        assertEquals(1, actor.getModule<Tilemap2DModule>()?.getTile(1, 2))
        assertTrue(physics.world.bodies.size >= 2)

        // Render SVG character + custom SVG path with cubic & arc onto offscreen ImageBuffer2DContext
        val surface = DefaultCpuImageBuffer(256, 256)
        val ctx = surface.context2D
        host.renderer.clearColor = RgbaColor(12, 18, 28, 255)
        host.renderer.render(host.scene, ctx)

        ctx.beginPath()
        SvgPath2DParser.traceOntoContext("M 10 10 L 50 10 Q 70 10 70 30 A 20 20 0 0 1 50 50 C 30 60 20 40 10 10 Z", ctx)
        ctx.fillStyle = ImageBufferPaintStyle.solid(RgbaColor(255, 120, 40, 255))
        ctx.fill()

        var coloredPixels = 0
        val pixels = surface.bytes
        for (p in pixels.indices step 4) {
            val r = pixels[p].toInt() and 0xFF
            val g = pixels[p + 1].toInt() and 0xFF
            val b = pixels[p + 2].toInt() and 0xFF
            if (r > 20 || g > 25 || b > 35) {
                coloredPixels++
            }
        }
        assertTrue(coloredPixels > 100, "Rendered scene & SVG path should paint visible pixels")

        host.stepVBlank(0.0)
        host.stepVBlank(16.67)
        host.dispose()
    }
}
