package com.nexustools.genericui.ext.threejs.loaders

import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.objects.Group
import com.nexustools.genericui.ext.threejs.objects.Mesh

/**
 * Wavefront `.obj` 3D geometry parser (`THREE.OBJLoader`) producing a [Group] of [Mesh] nodes.
 */
class ObjLoader {

    fun parse(objText: String): Group {
        val group = Group()
        val rawPositions = ArrayList<Float>()
        val rawNormals = ArrayList<Float>()
        val rawUvs = ArrayList<Float>()

        val outPositions = ArrayList<Float>()
        val outNormals = ArrayList<Float>()
        val outUvs = ArrayList<Float>()
        var currentName = "default"

        for (rawLine in objText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "o", "g" -> if (parts.size > 1) currentName = parts[1]
                "v" -> if (parts.size >= 4) {
                    rawPositions.add(parts[1].toFloatOrNull() ?: 0f)
                    rawPositions.add(parts[2].toFloatOrNull() ?: 0f)
                    rawPositions.add(parts[3].toFloatOrNull() ?: 0f)
                }
                "vn" -> if (parts.size >= 4) {
                    rawNormals.add(parts[1].toFloatOrNull() ?: 0f)
                    rawNormals.add(parts[2].toFloatOrNull() ?: 0f)
                    rawNormals.add(parts[3].toFloatOrNull() ?: 1f)
                }
                "vt" -> if (parts.size >= 3) {
                    rawUvs.add(parts[1].toFloatOrNull() ?: 0f)
                    rawUvs.add(parts[2].toFloatOrNull() ?: 0f)
                }
                "f" -> if (parts.size >= 4) {
                    val faceVerts = parts.subList(1, parts.size)
                    for (tri in 1 until faceVerts.size - 1) {
                        for (token in listOf(faceVerts[0], faceVerts[tri], faceVerts[tri + 1])) {
                            val indices = token.split('/')
                            val vIdx = (indices.getOrNull(0)?.toIntOrNull() ?: 1) - 1
                            val vtIdx = (indices.getOrNull(1)?.toIntOrNull() ?: 0) - 1
                            val vnIdx = (indices.getOrNull(2)?.toIntOrNull() ?: 0) - 1

                            if (vIdx * 3 + 2 < rawPositions.size && vIdx >= 0) {
                                outPositions.add(rawPositions[vIdx * 3])
                                outPositions.add(rawPositions[vIdx * 3 + 1])
                                outPositions.add(rawPositions[vIdx * 3 + 2])
                            }
                            if (vtIdx >= 0 && vtIdx * 2 + 1 < rawUvs.size) {
                                outUvs.add(rawUvs[vtIdx * 2])
                                outUvs.add(rawUvs[vtIdx * 2 + 1])
                            } else {
                                outUvs.add(0f); outUvs.add(0f)
                            }
                            if (vnIdx >= 0 && vnIdx * 3 + 2 < rawNormals.size) {
                                outNormals.add(rawNormals[vnIdx * 3])
                                outNormals.add(rawNormals[vnIdx * 3 + 1])
                                outNormals.add(rawNormals[vnIdx * 3 + 2])
                            }
                        }
                    }
                }
            }
        }

        val geometry = BufferGeometry().apply {
            name = currentName
            setAttribute("position", BufferAttribute(outPositions.toFloatArray(), 3))
            if (outUvs.isNotEmpty()) {
                setAttribute("uv", BufferAttribute(outUvs.toFloatArray(), 2))
            }
            if (outNormals.size == outPositions.size && outNormals.isNotEmpty()) {
                setAttribute("normal", BufferAttribute(outNormals.toFloatArray(), 3))
            } else {
                computeVertexNormals()
            }
            computeBoundingBox()
            computeBoundingSphere()
        }

        val mesh = Mesh(geometry, MeshStandardMaterial()).apply {
            name = currentName
        }
        group.add(mesh)
        return group
    }
}
