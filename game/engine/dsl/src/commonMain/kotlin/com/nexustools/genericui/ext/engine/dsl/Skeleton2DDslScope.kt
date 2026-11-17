package com.nexustools.genericui.ext.engine.dsl

import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.svg.Bone2D
import com.nexustools.genericui.ext.engine.svg.HumanoidSkeleton2DFactory
import com.nexustools.genericui.ext.engine.svg.Skeleton2D
import com.nexustools.genericui.ext.engine.svg.SvgBonePart
import com.nexustools.genericui.ext.engine.svg.SvgShapePrimitive

/**
 * Fluent DSL builder scope for constructing an articulated 2D [Skeleton2D] and attaching SVG parts.
 */
@GenericUiDslMarker
class Skeleton2DDslScope(
    var rootOffsetX: Float = 0f,
    var rootOffsetY: Float = 0f
) {
    private val bones = mutableListOf<Bone2D>()
    private val parts = mutableListOf<SvgBonePart>()
    private var humanoidBase: Skeleton2D? = null

    fun bone(
        name: String,
        lengthPx: Float,
        localX: Float = 0f,
        localY: Float = 0f,
        localAngleRad: Float = 0f,
        parentName: String? = null
    ): Bone2D {
        val created = Bone2D(
            name = name,
            lengthPx = lengthPx,
            localX = localX,
            localY = localY,
            localAngleRad = localAngleRad,
            parentName = parentName
        )
        bones.add(created)
        return created
    }

    fun part(
        boneName: String,
        primitive: SvgShapePrimitive,
        fillHex: String = "#4A90D9",
        strokeHex: String? = null,
        strokeWidth: Float = 1.5f,
        zOrder: Int = 0
    ): SvgBonePart {
        val part = SvgBonePart(
            boneName = boneName,
            primitive = primitive,
            fillHex = fillHex,
            strokeHex = strokeHex,
            strokeWidth = strokeWidth,
            zOrder = zOrder
        )
        parts.add(part)
        return part
    }

    fun humanoid(
        scale: Float = 1f,
        attachDefaultParts: Boolean = true
    ) {
        humanoidBase = HumanoidSkeleton2DFactory.create(
            scale = scale,
            rootOffsetX = rootOffsetX,
            rootOffsetY = rootOffsetY,
            attachDefaultParts = attachDefaultParts
        )
    }

    fun build(): Skeleton2D {
        val base = humanoidBase
        if (base != null) {
            base.rootOffsetX = rootOffsetX
            base.rootOffsetY = rootOffsetY
            for (p in parts) {
                base.attachPart(p)
            }
            base.updateWorldTransforms()
            return base
        }
        return Skeleton2D(
            bones = bones.toList(),
            rootOffsetX = rootOffsetX,
            rootOffsetY = rootOffsetY,
            initialParts = parts.toList()
        )
    }
}
