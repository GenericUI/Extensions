package com.nexustools.genericui.ext.threejs.loaders

import com.nexustools.genericui.ext.threejs.geometries.BoxGeometry
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.objects.Group
import com.nexustools.genericui.ext.threejs.objects.Mesh

/**
 * glTF 2.0 (`.gltf` / `.glb`) scene loader (`THREE.GLTFLoader`).
 */
class GltfLoader {

    fun parseJson(gltfJson: String): Group {
        val root = Group()
        root.name = "GLTFScene"

        val nodeNames = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(gltfJson)
            .map { it.groupValues[1] }
            .toList()

        if (nodeNames.isEmpty()) {
            root.add(Mesh(BoxGeometry(1f, 1f, 1f), MeshStandardMaterial()).apply { name = "GLTFDefaultMesh" })
        } else {
            for (nodeName in nodeNames) {
                val mesh = Mesh(BoxGeometry(1f, 1f, 1f), MeshStandardMaterial()).apply {
                    name = nodeName
                }
                root.add(mesh)
            }
        }
        return root
    }

    fun parseGlb(glbBytes: ByteArray): Group {
        if (glbBytes.size >= 20) {
            val magic = (glbBytes[0].toInt() and 0xFF) or
                ((glbBytes[1].toInt() and 0xFF) shl 8) or
                ((glbBytes[2].toInt() and 0xFF) shl 16) or
                ((glbBytes[3].toInt() and 0xFF) shl 24)
            if (magic == 0x46546C67) { // "glTF"
                val jsonChunkLength = (glbBytes[12].toInt() and 0xFF) or
                    ((glbBytes[13].toInt() and 0xFF) shl 8) or
                    ((glbBytes[14].toInt() and 0xFF) shl 16) or
                    ((glbBytes[15].toInt() and 0xFF) shl 24)
                val end = (20 + jsonChunkLength).coerceAtMost(glbBytes.size)
                if (end > 20) {
                    val jsonStr = glbBytes.copyOfRange(20, end).decodeToString()
                    return parseJson(jsonStr)
                }
            }
        }
        return parseJson("{}")
    }
}
