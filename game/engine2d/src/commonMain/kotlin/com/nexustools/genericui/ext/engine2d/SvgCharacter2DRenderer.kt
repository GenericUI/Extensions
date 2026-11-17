package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.svg.Skeleton2D
import com.nexustools.genericui.ext.engine.svg.SvgBonePart
import com.nexustools.genericui.ext.engine.svg.SvgCirclePrimitive
import com.nexustools.genericui.ext.engine.svg.SvgPathPrimitive
import com.nexustools.genericui.ext.engine.svg.SvgPolygonPrimitive
import com.nexustools.genericui.ext.engine.svg.SvgRectPrimitive
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBufferPath2D
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.PI

/**
 * Renders an articulated [Skeleton2D] and its [SvgBonePart] hierarchy sorted by `zOrder`
 * directly onto an [ImageBuffer2DContext] using hierarchical per-bone affine transforms.
 */
object SvgCharacter2DRenderer {
    fun render(
        skeleton: Skeleton2D,
        ctx: ImageBuffer2DContext,
        drawBoneOverlay: Boolean = false
    ) {
        skeleton.updateWorldTransforms()
        val sortedParts = skeleton.parts.sortedBy { it.zOrder }

        for (part in sortedParts) {
            val bone = skeleton.getBone(part.boneName) ?: continue
            val m = bone.worldMatrix

            ctx.save()
            ctx.transform(m.a, m.b, m.c, m.d, m.tx, m.ty)

            val fillColor = RgbaColor.fromHex(part.fillHex)
            val strokeColor = part.strokeHex?.let { RgbaColor.fromHex(it) }

            when (val prim = part.primitive) {
                is SvgCirclePrimitive -> {
                    ctx.beginPath()
                    ctx.arc(prim.cx, prim.cy, prim.radius, 0f, (PI * 2.0).toFloat(), false)
                    ctx.setFillColor(fillColor)
                    ctx.fill()
                    if (strokeColor != null && part.strokeWidth > 0f) {
                        ctx.setStrokeColor(strokeColor)
                        ctx.setLineWidth(part.strokeWidth)
                        ctx.stroke()
                    }
                }
                is SvgRectPrimitive -> {
                    val path = ImageBufferPath2D()
                    if (prim.cornerRadius > 0f) {
                        path.roundRect(prim.x, prim.y, prim.width, prim.height, prim.cornerRadius)
                    } else {
                        path.rect(prim.x, prim.y, prim.width, prim.height)
                    }
                    ctx.setFillColor(fillColor)
                    ctx.fill(path)
                    if (strokeColor != null && part.strokeWidth > 0f) {
                        ctx.setStrokeColor(strokeColor)
                        ctx.setLineWidth(part.strokeWidth)
                        ctx.stroke(path)
                    }
                }
                is SvgPolygonPrimitive -> {
                    if (prim.points.size >= 3) {
                        ctx.beginPath()
                        ctx.moveTo(prim.points[0].x, prim.points[0].y)
                        for (i in 1 until prim.points.size) {
                            ctx.lineTo(prim.points[i].x, prim.points[i].y)
                        }
                        ctx.closePath()
                        ctx.setFillColor(fillColor)
                        ctx.fill()
                        if (strokeColor != null && part.strokeWidth > 0f) {
                            ctx.setStrokeColor(strokeColor)
                            ctx.setLineWidth(part.strokeWidth)
                            ctx.stroke()
                        }
                    }
                }
                is SvgPathPrimitive -> {
                    val path = SvgPath2DParser.parseToPath(prim.pathData)
                    ctx.setFillColor(fillColor)
                    ctx.fill(path)
                    if (strokeColor != null && part.strokeWidth > 0f) {
                        ctx.setStrokeColor(strokeColor)
                        ctx.setLineWidth(part.strokeWidth)
                        ctx.stroke(path)
                    }
                }
            }
            ctx.restore()
        }

        if (drawBoneOverlay) {
            ctx.setStrokeColor(RgbaColor(250, 204, 21, 255))
            ctx.setLineWidth(2f)
            for (bone in skeleton.bones) {
                ctx.beginPath()
                ctx.moveTo(bone.worldX, bone.worldY)
                ctx.lineTo(bone.tipX, bone.tipY)
                ctx.stroke()

                ctx.setFillColor(RgbaColor(239, 68, 68, 255))
                ctx.beginPath()
                ctx.arc(bone.worldX, bone.worldY, 3f, 0f, (PI * 2.0).toFloat(), false)
                ctx.fill()
            }
        }
    }
}
