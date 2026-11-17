package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.math.Vec2f
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Renders a 2D texture [image] or solid/tinted quad onto an [ImageBuffer2DContext] using the
 * owning entity's world 2D transform.
 */
class Sprite2DModule(
    var image: ImageBuffer? = null,
    var width: Float = 64f,
    var height: Float = 64f,
    var tintColor: RgbaColor = RgbaColor.WHITE,
    var anchor: Vec2f = Vec2f(0.5f, 0.5f),
    var flipX: Boolean = false,
    var flipY: Boolean = false,
    var zOrder: Int = 0
) : EntityModule() {

    var sourceX: Float = 0f
    var sourceY: Float = 0f
    var sourceWidth: Float = 0f
    var sourceHeight: Float = 0f

    fun setSourceRect(x: Float, y: Float, w: Float, h: Float) {
        sourceX = x
        sourceY = y
        sourceWidth = w
        sourceHeight = h
    }

    fun render(ctx: ImageBuffer2DContext) {
        if (!isEnabled) return
        val owner = entity ?: return
        val m = owner.worldAffine2D()

        ctx.save()
        ctx.transform(m.a, m.b, m.c, m.d, m.tx, m.ty)
        if (flipX || flipY) {
            ctx.scale(if (flipX) -1f else 1f, if (flipY) -1f else 1f)
        }
        val prevAlpha = ctx.globalAlpha
        ctx.globalAlpha = (prevAlpha * tintColor.alphaFloat).coerceIn(0f, 1f)

        val drawX = -width * anchor.x
        val drawY = -height * anchor.y
        val tex = image
        if (tex != null) {
            if (sourceWidth > 0f && sourceHeight > 0f) {
                ctx.drawImage(tex, sourceX, sourceY, sourceWidth, sourceHeight, drawX, drawY, width, height)
            } else {
                ctx.drawImage(tex, drawX, drawY, width, height)
            }
        } else {
            ctx.setFillColor(tintColor)
            ctx.fillRect(drawX, drawY, width, height)
        }

        ctx.globalAlpha = prevAlpha
        ctx.restore()
    }
}
