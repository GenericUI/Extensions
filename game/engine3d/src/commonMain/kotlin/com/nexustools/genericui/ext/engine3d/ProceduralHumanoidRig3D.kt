package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.engine.math.Quat4f
import com.nexustools.genericui.ext.threejs.animation.AnimationClip
import com.nexustools.genericui.ext.threejs.animation.KeyframeTrack
import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.objects.Bone
import com.nexustools.genericui.ext.threejs.objects.Skeleton
import com.nexustools.genericui.ext.threejs.objects.SkinnedMesh
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural 15-bone rigged 3D humanoid [SkinnedMesh] and [Skeleton] hierarchy (`pelvis`, `spine`,
 * `chest`, `neck`, `head`, `shoulderL`, `upperArmL`, `forearmL`, `shoulderR`, `upperArmR`,
 * `forearmR`, `thighL`, `shinL`, `thighR`, `shinR`) complete with `skinIndex`/`skinWeight` vertex
 * attributes and built-in `walk`, `run`, `wave`, and `attack` [AnimationClip]s.
 */
class ProceduralHumanoidRig3D(
    val skinnedMesh: SkinnedMesh,
    val skeleton: Skeleton,
    val clips: Map<String, AnimationClip>,
    val boneMap: Map<String, Bone>
) {
    val rootBone: Bone
        get() = boneMap.getValue("pelvis")

    val walkClip: AnimationClip
        get() = clips.getValue("walk")

    val runClip: AnimationClip
        get() = clips.getValue("run")

    val waveClip: AnimationClip
        get() = clips.getValue("wave")

    val attackClip: AnimationClip
        get() = clips.getValue("attack")

    fun getBone(name: String): Bone? = boneMap[name]

    companion object {
        val BONE_NAMES: List<String> = listOf(
            "pelvis",
            "spine",
            "chest",
            "neck",
            "head",
            "shoulderL",
            "upperArmL",
            "forearmL",
            "shoulderR",
            "upperArmR",
            "forearmR",
            "thighL",
            "shinL",
            "thighR",
            "shinR"
        )

        private data class SegmentSpec(
            val boneIndex: Int,
            val parentIndex: Int,
            val halfW: Float,
            val minY: Float,
            val maxY: Float,
            val halfD: Float,
            val bindWorldPos: FloatArray
        )

        fun create(
            material: Material = MeshStandardMaterial(color = Color(0x60a5fa), roughness = 0.55f),
            scale: Float = 1f
        ): ProceduralHumanoidRig3D {
            val s = scale.coerceAtLeast(0.1f)
            val bones = BONE_NAMES.map { boneName -> Bone().apply { name = boneName } }
            val boneMap = bones.associateBy { it.name }

            fun parentChild(parentName: String, childName: String, lx: Float, ly: Float, lz: Float) {
                val p = boneMap.getValue(parentName)
                val c = boneMap.getValue(childName)
                c.position.set(lx * s, ly * s, lz * s)
                p.add(c)
            }

            val pelvis = boneMap.getValue("pelvis")
            pelvis.position.set(0f, 0.95f * s, 0f)

            parentChild("pelvis", "spine", 0f, 0.12f, 0f)
            parentChild("spine", "chest", 0f, 0.18f, 0f)
            parentChild("chest", "neck", 0f, 0.22f, 0f)
            parentChild("neck", "head", 0f, 0.10f, 0f)

            parentChild("chest", "shoulderL", -0.18f, 0.18f, 0f)
            parentChild("shoulderL", "upperArmL", -0.08f, 0f, 0f)
            parentChild("upperArmL", "forearmL", 0f, -0.28f, 0f)

            parentChild("chest", "shoulderR", 0.18f, 0.18f, 0f)
            parentChild("shoulderR", "upperArmR", 0.08f, 0f, 0f)
            parentChild("upperArmR", "forearmR", 0f, -0.28f, 0f)

            parentChild("pelvis", "thighL", -0.11f, -0.05f, 0f)
            parentChild("thighL", "shinL", 0f, -0.42f, 0f)

            parentChild("pelvis", "thighR", 0.11f, -0.05f, 0f)
            parentChild("thighR", "shinR", 0f, -0.42f, 0f)

            pelvis.updateMatrixWorld(true)

            val segmentSpecs = listOf(
                SegmentSpec(0, 0, 0.16f * s, -0.06f * s, 0.12f * s, 0.11f * s, worldPosArr(bones[0])),
                SegmentSpec(1, 0, 0.14f * s, 0f, 0.18f * s, 0.10f * s, worldPosArr(bones[1])),
                SegmentSpec(2, 1, 0.20f * s, 0f, 0.22f * s, 0.12f * s, worldPosArr(bones[2])),
                SegmentSpec(3, 2, 0.06f * s, 0f, 0.10f * s, 0.06f * s, worldPosArr(bones[3])),
                SegmentSpec(4, 3, 0.11f * s, 0f, 0.22f * s, 0.11f * s, worldPosArr(bones[4])),
                SegmentSpec(5, 2, 0.06f * s, -0.05f * s, 0.05f * s, 0.06f * s, worldPosArr(bones[5])),
                SegmentSpec(6, 5, 0.055f * s, -0.28f * s, 0f, 0.055f * s, worldPosArr(bones[6])),
                SegmentSpec(7, 6, 0.045f * s, -0.26f * s, 0f, 0.045f * s, worldPosArr(bones[7])),
                SegmentSpec(8, 2, 0.06f * s, -0.05f * s, 0.05f * s, 0.06f * s, worldPosArr(bones[8])),
                SegmentSpec(9, 8, 0.055f * s, -0.28f * s, 0f, 0.055f * s, worldPosArr(bones[9])),
                SegmentSpec(10, 9, 0.045f * s, -0.26f * s, 0f, 0.045f * s, worldPosArr(bones[10])),
                SegmentSpec(11, 0, 0.075f * s, -0.42f * s, 0f, 0.075f * s, worldPosArr(bones[11])),
                SegmentSpec(12, 11, 0.06f * s, -0.44f * s, 0f, 0.06f * s, worldPosArr(bones[12])),
                SegmentSpec(13, 0, 0.075f * s, -0.42f * s, 0f, 0.075f * s, worldPosArr(bones[13])),
                SegmentSpec(14, 13, 0.06f * s, -0.44f * s, 0f, 0.06f * s, worldPosArr(bones[14]))
            )

            val geometry = buildSkinnedGeometry(segmentSpecs)
            val mesh = SkinnedMesh(geometry, material).apply {
                name = "ProceduralHumanoidMesh"
            }
            mesh.add(pelvis)
            val skeleton = Skeleton(bones)
            mesh.bind(skeleton)
            mesh.updateMatrixWorld(true)
            skeleton.update()

            val clips = mapOf(
                "walk" to createWalkClip(),
                "run" to createRunClip(),
                "wave" to createWaveClip(),
                "attack" to createAttackClip()
            )
            return ProceduralHumanoidRig3D(mesh, skeleton, clips, boneMap)
        }

        private fun worldPosArr(bone: Bone): FloatArray {
            val wp = bone.getWorldPosition()
            return floatArrayOf(wp.x, wp.y, wp.z)
        }

        private fun buildSkinnedGeometry(specs: List<SegmentSpec>): BufferGeometry {
            val positions = mutableListOf<Float>()
            val normals = mutableListOf<Float>()
            val uvs = mutableListOf<Float>()
            val skinIndices = mutableListOf<Float>()
            val skinWeights = mutableListOf<Float>()
            val indices = mutableListOf<Int>()

            for (spec in specs) {
                val ox = spec.bindWorldPos[0]
                val oy = spec.bindWorldPos[1]
                val oz = spec.bindWorldPos[2]
                val x0 = ox - spec.halfW
                val x1 = ox + spec.halfW
                val y0 = oy + spec.minY
                val y1 = oy + spec.maxY
                val z0 = oz - spec.halfD
                val z1 = oz + spec.halfD

                fun addFace(
                    p0: FloatArray,
                    p1: FloatArray,
                    p2: FloatArray,
                    p3: FloatArray,
                    nx: Float,
                    ny: Float,
                    nz: Float
                ) {
                    val baseVertex = positions.size / 3
                    val corners = arrayOf(p0, p1, p2, p3)
                    val faceUvs = arrayOf(
                        floatArrayOf(0f, 0f),
                        floatArrayOf(1f, 0f),
                        floatArrayOf(1f, 1f),
                        floatArrayOf(0f, 1f)
                    )
                    for (cIdx in 0 until 4) {
                        val pt = corners[cIdx]
                        positions.add(pt[0]); positions.add(pt[1]); positions.add(pt[2])
                        normals.add(nx); normals.add(ny); normals.add(nz)
                        uvs.add(faceUvs[cIdx][0]); uvs.add(faceUvs[cIdx][1])

                        val isProximal = kotlin.math.abs(pt[1] - oy) < kotlin.math.abs(spec.maxY - spec.minY) * 0.35f
                        val wPrimary = if (isProximal && spec.parentIndex != spec.boneIndex) 0.85f else 1.0f
                        val wSecondary = 1f - wPrimary
                        skinIndices.add(spec.boneIndex.toFloat())
                        skinIndices.add(spec.parentIndex.toFloat())
                        skinIndices.add(0f)
                        skinIndices.add(0f)
                        skinWeights.add(wPrimary)
                        skinWeights.add(wSecondary)
                        skinWeights.add(0f)
                        skinWeights.add(0f)
                    }
                    indices.add(baseVertex); indices.add(baseVertex + 1); indices.add(baseVertex + 2)
                    indices.add(baseVertex); indices.add(baseVertex + 2); indices.add(baseVertex + 3)
                }

                // +Z front, -Z back, +X right, -X left, +Y top, -Y bottom
                addFace(floatArrayOf(x0, y0, z1), floatArrayOf(x1, y0, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x0, y1, z1), 0f, 0f, 1f)
                addFace(floatArrayOf(x1, y0, z0), floatArrayOf(x0, y0, z0), floatArrayOf(x0, y1, z0), floatArrayOf(x1, y1, z0), 0f, 0f, -1f)
                addFace(floatArrayOf(x1, y0, z1), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y1, z0), floatArrayOf(x1, y1, z1), 1f, 0f, 0f)
                addFace(floatArrayOf(x0, y0, z0), floatArrayOf(x0, y0, z1), floatArrayOf(x0, y1, z1), floatArrayOf(x0, y1, z0), -1f, 0f, 0f)
                addFace(floatArrayOf(x0, y1, z1), floatArrayOf(x1, y1, z1), floatArrayOf(x1, y1, z0), floatArrayOf(x0, y1, z0), 0f, 1f, 0f)
                addFace(floatArrayOf(x0, y0, z0), floatArrayOf(x1, y0, z0), floatArrayOf(x1, y0, z1), floatArrayOf(x0, y0, z1), 0f, -1f, 0f)
            }

            return BufferGeometry().apply {
                name = "ProceduralHumanoidGeometry"
                setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))
                setAttribute("normal", BufferAttribute(normals.toFloatArray(), 3))
                setAttribute("uv", BufferAttribute(uvs.toFloatArray(), 2))
                setAttribute("skinIndex", BufferAttribute(skinIndices.toFloatArray(), 4))
                setAttribute("skinWeight", BufferAttribute(skinWeights.toFloatArray(), 4))
                setIndex(indices.toIntArray())
                computeBoundingBox()
                computeBoundingSphere()
            }
        }

        private fun eulerQuatTrack(
            boneName: String,
            times: FloatArray,
            eulerX: (Float) -> Float = { 0f },
            eulerY: (Float) -> Float = { 0f },
            eulerZ: (Float) -> Float = { 0f }
        ): KeyframeTrack {
            val values = FloatArray(times.size * 4)
            for (i in times.indices) {
                val t = times[i]
                val q = Quat4f.fromEulerRadians(eulerX(t), eulerY(t), eulerZ(t))
                values[i * 4] = q.x
                values[i * 4 + 1] = q.y
                values[i * 4 + 2] = q.z
                values[i * 4 + 3] = q.w
            }
            return KeyframeTrack("$boneName.quaternion", times, values, 4)
        }

        private fun createWalkClip(): AnimationClip {
            val dur = 0.8f
            val times = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f)
            val twoPi = (PI * 2.0).toFloat()
            fun phase(t: Float) = (t / dur) * twoPi
            return AnimationClip(
                name = "walk",
                duration = dur,
                tracks = listOf(
                    eulerQuatTrack("thighL", times, eulerX = { sin(phase(it)) * 0.55f }),
                    eulerQuatTrack("shinL", times, eulerX = { (-sin(phase(it)) * 0.4f).coerceAtLeast(0f) }),
                    eulerQuatTrack("thighR", times, eulerX = { -sin(phase(it)) * 0.55f }),
                    eulerQuatTrack("shinR", times, eulerX = { (sin(phase(it)) * 0.4f).coerceAtLeast(0f) }),
                    eulerQuatTrack("upperArmL", times, eulerX = { -sin(phase(it)) * 0.45f }),
                    eulerQuatTrack("upperArmR", times, eulerX = { sin(phase(it)) * 0.45f })
                )
            )
        }

        private fun createRunClip(): AnimationClip {
            val dur = 0.5f
            val times = floatArrayOf(0f, 0.125f, 0.25f, 0.375f, 0.5f)
            val twoPi = (PI * 2.0).toFloat()
            fun phase(t: Float) = (t / dur) * twoPi
            return AnimationClip(
                name = "run",
                duration = dur,
                tracks = listOf(
                    eulerQuatTrack("spine", times, eulerX = { 0.18f }),
                    eulerQuatTrack("thighL", times, eulerX = { sin(phase(it)) * 0.95f }),
                    eulerQuatTrack("shinL", times, eulerX = { 0.4f + (-sin(phase(it)) * 0.7f).coerceAtLeast(0f) }),
                    eulerQuatTrack("thighR", times, eulerX = { -sin(phase(it)) * 0.95f }),
                    eulerQuatTrack("shinR", times, eulerX = { 0.4f + (sin(phase(it)) * 0.7f).coerceAtLeast(0f) }),
                    eulerQuatTrack("upperArmL", times, eulerX = { -sin(phase(it)) * 0.85f }),
                    eulerQuatTrack("forearmL", times, eulerX = { -0.9f }),
                    eulerQuatTrack("upperArmR", times, eulerX = { sin(phase(it)) * 0.85f }),
                    eulerQuatTrack("forearmR", times, eulerX = { -0.9f })
                )
            )
        }

        private fun createWaveClip(): AnimationClip {
            val dur = 1.0f
            val times = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1.0f)
            return AnimationClip(
                name = "wave",
                duration = dur,
                tracks = listOf(
                    eulerQuatTrack("upperArmR", times, eulerZ = { -2.4f }),
                    eulerQuatTrack("forearmR", times, eulerZ = { t ->
                        -0.4f + sin((t / dur) * PI.toFloat() * 4f) * 0.45f
                    })
                )
            )
        }

        private fun createAttackClip(): AnimationClip {
            val dur = 0.45f
            val times = floatArrayOf(0f, 0.15f, 0.30f, 0.45f)
            return AnimationClip(
                name = "attack",
                duration = dur,
                tracks = listOf(
                    eulerQuatTrack("spine", times, eulerY = { t ->
                        when {
                            t < 0.2f -> -0.35f
                            t < 0.35f -> 0.55f
                            else -> 0f
                        }
                    }),
                    eulerQuatTrack("upperArmR", times, eulerX = { t ->
                        when {
                            t < 0.2f -> -1.9f
                            t < 0.35f -> 0.65f
                            else -> 0f
                        }
                    })
                )
            )
        }
    }
}
