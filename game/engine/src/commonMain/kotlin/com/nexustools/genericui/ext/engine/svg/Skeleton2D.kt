package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.math.AffineMatrix2D
import kotlin.math.PI

/**
 * Hierarchical 2D articulated bone rig computing topological forward-kinematic world transforms
 * and exporting valid SVG markup.
 */
class Skeleton2D(
    bones: List<Bone2D>,
    var rootOffsetX: Float = 0f,
    var rootOffsetY: Float = 0f,
    initialParts: List<SvgBonePart> = emptyList()
) {
    private val boneMap: LinkedHashMap<String, Bone2D> = LinkedHashMap()
    val orderedBones: List<Bone2D>
    private val _parts = mutableListOf<SvgBonePart>()
    val parts: List<SvgBonePart>
        get() = _parts

    init {
        for (bone in bones) {
            boneMap[bone.name] = bone
        }
        orderedBones = buildTopologicalOrder(bones)
        _parts.addAll(initialParts)
        updateWorldTransforms()
    }

    val bones: List<Bone2D>
        get() = orderedBones

    fun getBone(name: String): Bone2D? = boneMap[name]

    fun attachPart(part: SvgBonePart): Skeleton2D {
        _parts.add(part)
        return this
    }

    fun clearParts() {
        _parts.clear()
    }

    /**
     * Evaluates forward kinematics in strict parent-before-child topological order.
     */
    fun updateWorldTransforms() {
        val rootMat = AffineMatrix2D.translation(rootOffsetX, rootOffsetY)
        for (bone in orderedBones) {
            val parent = bone.parentName?.let { boneMap[it] }
            val parentMatrix = parent?.worldMatrix ?: rootMat
            val parentAngle = parent?.worldAngleRad ?: 0f

            val localMat = AffineMatrix2D.fromTRS(
                x = bone.localX,
                y = bone.localY,
                rotationRad = bone.localAngleRad
            )
            val worldMat = parentMatrix * localMat
            bone.worldMatrix = worldMat
            bone.worldX = worldMat.tx
            bone.worldY = worldMat.ty
            bone.worldAngleRad = parentAngle + bone.localAngleRad
        }
    }

    /**
     * Exports the current posed skeleton and attached [SvgBonePart] shapes sorted by `zOrder` as
     * standalone valid `<svg>` XML markup for pure-UI rendering or inspection.
     */
    fun exportSvgMarkup(width: Int = 256, height: Int = 256): String {
        updateWorldTransforms()
        val sortedParts = _parts.sortedBy { it.zOrder }
        val radToDeg = (180.0 / PI).toFloat()
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
        sb.append(width)
        sb.append("\" height=\"")
        sb.append(height)
        sb.append("\" viewBox=\"0 0 ")
        sb.append(width)
        sb.append(' ')
        sb.append(height)
        sb.append("\">")

        for (part in sortedParts) {
            val bone = boneMap[part.boneName] ?: continue
            val angleDeg = bone.worldAngleRad * radToDeg
            sb.append("<g data-bone=\"")
            sb.append(bone.name)
            sb.append("\" transform=\"translate(")
            sb.append(bone.worldX)
            sb.append(", ")
            sb.append(bone.worldY)
            sb.append(") rotate(")
            sb.append(angleDeg)
            sb.append(")\">")
            sb.append(part.primitive.toSvgElementXml(part.fillHex, part.strokeHex, part.strokeWidth))
            sb.append("</g>")
        }
        sb.append("</svg>")
        return sb.toString()
    }

    private fun buildTopologicalOrder(input: List<Bone2D>): List<Bone2D> {
        val result = ArrayList<Bone2D>(input.size)
        val visited = HashSet<String>()
        val visiting = HashSet<String>()

        fun visit(bone: Bone2D) {
            if (bone.name in visited) return
            if (!visiting.add(bone.name)) return
            val parent = bone.parentName?.let { boneMap[it] }
            if (parent != null) {
                visit(parent)
            }
            visiting.remove(bone.name)
            visited.add(bone.name)
            result.add(bone)
        }

        for (bone in input) {
            visit(bone)
        }
        return result
    }
}
