package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Renders an entire 2D [GameScene] (camera transform, tilemaps, sprites, SVG skeletal characters,
 * particles, and optional Box2D debug overlays) onto an [ImageBuffer2DContext] sorted by `zOrder`.
 */
class Engine2DSceneRenderer(
    var clearColor: RgbaColor? = RgbaColor(18, 22, 34, 255)
) {
    private class DrawCommand(val zOrder: Int, val draw: (ImageBuffer2DContext) -> Unit)

    fun render(scene: GameScene, ctx: ImageBuffer2DContext) {
        val bg = clearColor
        if (bg != null) {
            ctx.setFillColor(bg)
            ctx.fillRect(0f, 0f, ctx.width.toFloat(), ctx.height.toFloat())
        } else {
            ctx.clearRect(0f, 0f, ctx.width.toFloat(), ctx.height.toFloat())
        }

        val entities = scene.allEntities()
        var activeCamera: Camera2DModule? = null
        val commands = mutableListOf<DrawCommand>()

        for (entity in entities) {
            if (!entity.isActive) continue
            for (module in entity.modules) {
                if (!module.isEnabled) continue
                when (module) {
                    is Camera2DModule -> if (activeCamera == null) activeCamera = module
                    is Tilemap2DModule -> commands.add(DrawCommand(module.zOrder) { module.render(it) })
                    is Sprite2DModule -> commands.add(DrawCommand(module.zOrder) { module.render(it) })
                    is SvgCharacter2DModule -> commands.add(DrawCommand(module.zOrder) { module.render(it) })
                    is ParticleEmitter2DModule -> commands.add(DrawCommand(module.zOrder) { module.render(it) })
                }
            }
        }

        ctx.save()
        activeCamera?.applyTransform(ctx)

        commands.sortBy { it.zOrder }
        for (cmd in commands) {
            cmd.draw(ctx)
        }

        scene.getSubsystem<PhysicsWorld2DSubsystem>()?.drawDebug(ctx)
        ctx.restore()
    }
}
