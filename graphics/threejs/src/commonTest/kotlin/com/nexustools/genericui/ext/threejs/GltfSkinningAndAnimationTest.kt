package com.nexustools.genericui.ext.threejs

import com.nexustools.genericui.ext.canvas3d.Canvas3DRegistry
import com.nexustools.genericui.ext.gpu.GpuAccelerationPolicy
import com.nexustools.genericui.ext.gpu.GpuBackendSupport
import com.nexustools.genericui.ext.gpu.GpuBackendType
import com.nexustools.genericui.ext.threejs.animation.AnimationClip
import com.nexustools.genericui.ext.threejs.animation.AnimationMixer
import com.nexustools.genericui.ext.threejs.animation.KeyframeTrack
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.loaders.GltfLoader
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.math.Matrix4
import com.nexustools.genericui.ext.threejs.objects.Bone
import com.nexustools.genericui.ext.threejs.objects.Skeleton
import com.nexustools.genericui.ext.threejs.objects.SkinnedMesh
import com.nexustools.genericui.ext.threejs.renderers.WebGLRenderer
import com.nexustools.genericui.ext.threejs.renderers.WebGLRendererConfig
import com.nexustools.genericui.ext.threejs.scenes.Scene
import com.nexustools.genericui.res.DataUrlCodec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GltfSkinningAndAnimationTest {

    @BeforeTest
    fun setUp() {
        Canvas3DRegistry.overrideSupportForTesting(
            GpuBackendType.OPENGL_ES,
            GpuBackendSupport(
                backend = GpuBackendType.OPENGL_ES,
                hardwareAvailable = true,
                softwareAvailable = true
            )
        )
    }

    @AfterTest
    fun tearDown() {
        Canvas3DRegistry.resetForTesting()
    }

    private fun appendFloat32LE(dst: MutableList<Byte>, v: Float) {
        val bits = v.toBits()
        dst.add((bits and 0xFF).toByte())
        dst.add(((bits ushr 8) and 0xFF).toByte())
        dst.add(((bits ushr 16) and 0xFF).toByte())
        dst.add(((bits ushr 24) and 0xFF).toByte())
    }

    private fun appendUShortLE(dst: MutableList<Byte>, v: Int) {
        dst.add((v and 0xFF).toByte())
        dst.add(((v ushr 8) and 0xFF).toByte())
    }

    private fun buildRiggedGltfBinaryBuffer(): ByteArray {
        val out = ArrayList<Byte>()
        // 0: 3 vertex positions (3 * 3 floats = 36 bytes)
        floatArrayOf(
            0f, 0f, 0f,
            2f, 0f, 0f,
            0f, 2f, 0f
        ).forEach { appendFloat32LE(out, it) }

        // 36: 3 indices UNSIGNED_SHORT (6 bytes + 2 padding = 8 bytes)
        appendUShortLE(out, 0)
        appendUShortLE(out, 1)
        appendUShortLE(out, 2)
        appendUShortLE(out, 0)

        // 44: JOINTS_0 (3 * 4 UNSIGNED_BYTE = 12 bytes) -> vertex 0 -> bone 0, vertex 1/2 -> bone 1
        byteArrayOf(
            0, 0, 0, 0,
            1, 0, 0, 0,
            1, 0, 0, 0
        ).forEach { out.add(it) }

        // 56: WEIGHTS_0 (3 * 4 FLOAT = 48 bytes)
        floatArrayOf(
            1f, 0f, 0f, 0f,
            0.5f, 0.5f, 0f, 0f,
            1f, 0f, 0f, 0f
        ).forEach { appendFloat32LE(out, it) }

        // 104: 2 inverseBindMatrices MAT4 (2 * 16 floats = 128 bytes) - identity
        val id = Matrix4().elements
        id.forEach { appendFloat32LE(out, it) }
        id.forEach { appendFloat32LE(out, it) }

        // 232: anim times (2 floats = 8 bytes)
        appendFloat32LE(out, 0f)
        appendFloat32LE(out, 1f)

        // 240: anim ChildBone translation (2 * 3 floats = 24 bytes)
        floatArrayOf(
            0f, 0f, 0f,
            0f, 4f, 0f
        ).forEach { appendFloat32LE(out, it) }

        return out.toByteArray()
    }

    private fun buildRiggedGltfJson(uri: String?, byteLength: Int): String {
        val uriField = if (uri != null) ", \"uri\": \"$uri\"" else ""
        return """
            {
              "asset": { "version": "2.0" },
              "scene": 0,
              "scenes": [{ "name": "TestScene", "nodes": [0, 1] }],
              "nodes": [
                { "name": "SkinMeshNode", "mesh": 0, "skin": 0 },
                { "name": "RootBone", "children": [2], "translation": [1.0, 0.0, 0.0] },
                { "name": "ChildBone", "translation": [0.0, 0.0, 0.0] }
              ],
              "meshes": [{
                "primitives": [{
                  "attributes": {
                    "POSITION": 0,
                    "JOINTS_0": 2,
                    "WEIGHTS_0": 3
                  },
                  "indices": 1,
                  "material": 0
                }]
              }],
              "materials": [{
                "name": "SkinMat",
                "pbrMetallicRoughness": {
                  "baseColorFactor": [0.8, 0.4, 0.2, 1.0],
                  "metallicFactor": 0.25,
                  "roughnessFactor": 0.6
                }
              }],
              "skins": [{
                "name": "Rig",
                "joints": [1, 2],
                "inverseBindMatrices": 4
              }],
              "animations": [{
                "name": "LiftChild",
                "samplers": [{ "input": 5, "output": 6, "interpolation": "LINEAR" }],
                "channels": [{ "sampler": 0, "target": { "node": 2, "path": "translation" } }]
              }],
              "buffers": [{ "byteLength": $byteLength$uriField }],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 6 },
                { "buffer": 0, "byteOffset": 44, "byteLength": 12 },
                { "buffer": 0, "byteOffset": 56, "byteLength": 48 },
                { "buffer": 0, "byteOffset": 104, "byteLength": 128 },
                { "buffer": 0, "byteOffset": 232, "byteLength": 8 },
                { "buffer": 0, "byteOffset": 240, "byteLength": 24 }
              ],
              "accessors": [
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" },
                { "bufferView": 2, "componentType": 5121, "count": 3, "type": "VEC4" },
                { "bufferView": 3, "componentType": 5126, "count": 3, "type": "VEC4" },
                { "bufferView": 4, "componentType": 5126, "count": 2, "type": "MAT4" },
                { "bufferView": 5, "componentType": 5126, "count": 2, "type": "SCALAR" },
                { "bufferView": 6, "componentType": 5126, "count": 2, "type": "VEC3" }
              ]
            }
        """.trimIndent()
    }

    @Test
    fun testGltfJsonAndGlbRiggedLoadingAndAnimation() {
        val bin = buildRiggedGltfBinaryBuffer()
        val dataUrl = DataUrlCodec.encodeBytes(bin, "application/octet-stream")
        val json = buildRiggedGltfJson(dataUrl, bin.size)

        val loader = GltfLoader()
        val asset = loader.loadFromGltf(json)
        assertEquals("TestScene", asset.scene.name)

        val skinnedMesh = asset.scene.getObjectByName("SkinMeshNode") as? SkinnedMesh
        assertNotNull(skinnedMesh)
        val mat = skinnedMesh.material as? MeshStandardMaterial
        assertNotNull(mat)
        assertTrue(abs(mat.color.r - 0.8f) < 1e-4f)
        assertTrue(abs(mat.metalness - 0.25f) < 1e-4f)

        val skel = skinnedMesh.skeleton
        assertNotNull(skel)
        assertEquals(2, skel.bones.size)
        assertEquals("RootBone", skel.bones[0].name)
        assertEquals("ChildBone", skel.bones[1].name)
        assertEquals(3, skinnedMesh.geometry.vertexCount)
        assertNotNull(skinnedMesh.geometry.getAttribute("normal"))

        assertEquals(1, asset.animations.size)
        val clip = asset.animations[0]
        assertEquals("LiftChild", clip.name)
        assertEquals(1f, clip.duration)

        val mixer = AnimationMixer(asset.scene)
        mixer.clipAction(clip).play()
        mixer.update(0.5f)

        val animatedChild = asset.scene.getObjectByName("ChildBone") as? Bone
        assertNotNull(animatedChild)
        assertTrue(abs(animatedChild.position.y - 2f) < 1e-4f)
        // ChildBone inherits RootBone's x = 1.0 translation into world matrix
        assertTrue(abs(animatedChild.matrixWorld.elements[12] - 1f) < 1e-4f)
        assertTrue(abs(animatedChild.matrixWorld.elements[13] - 2f) < 1e-4f)

        // Verify .glb binary container loading with BIN chunk
        val jsonBytes = buildRiggedGltfJson(null, bin.size).encodeToByteArray()
        val jsonPad = (4 - (jsonBytes.size % 4)) % 4
        val jsonPaddedLen = jsonBytes.size + jsonPad
        val binPad = (4 - (bin.size % 4)) % 4
        val binPaddedLen = bin.size + binPad
        val totalLen = 12 + 8 + jsonPaddedLen + 8 + binPaddedLen

        val glb = ArrayList<Byte>(totalLen)
        fun putI32(v: Int) {
            glb.add((v and 0xFF).toByte())
            glb.add(((v ushr 8) and 0xFF).toByte())
            glb.add(((v ushr 16) and 0xFF).toByte())
            glb.add(((v ushr 24) and 0xFF).toByte())
        }
        putI32(0x46546C67)
        putI32(2)
        putI32(totalLen)
        putI32(jsonPaddedLen)
        putI32(0x4E4F534A)
        jsonBytes.forEach { glb.add(it) }
        repeat(jsonPad) { glb.add(0x20.toByte()) }
        putI32(binPaddedLen)
        putI32(0x004E4942)
        bin.forEach { glb.add(it) }
        repeat(binPad) { glb.add(0) }

        val glbAsset = loader.loadGlb(glb.toByteArray())
        assertNotNull(glbAsset.scene.getObjectByName("SkinMeshNode") as? SkinnedMesh)
        assertEquals(1, glbAsset.animations.size)
    }

    @Test
    fun testHierarchicalBoneAnimationAndLinearBlendSkinning() {
        val rootBone = Bone().apply { name = "Shoulder" }
        val childBone = Bone().apply {
            name = "Elbow"
            position.set(1f, 0f, 0f)
        }
        rootBone.add(childBone)

        val geom = BufferGeometry()
        geom.setAttribute(
            "position",
            BufferAttribute(
                floatArrayOf(
                    1f, 0f, 0f,
                    2f, 0f, 0f,
                    2f, 1f, 0f
                ),
                3
            )
        )
        geom.setAttribute(
            "normal",
            BufferAttribute(
                floatArrayOf(
                    0f, 0f, 1f,
                    0f, 0f, 1f,
                    0f, 0f, 1f
                ),
                3
            )
        )
        geom.setAttribute(
            "skinIndex",
            BufferAttribute(
                floatArrayOf(
                    0f, 1f, 0f, 0f,
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f
                ),
                4
            )
        )
        geom.setAttribute(
            "skinWeight",
            BufferAttribute(
                floatArrayOf(
                    0.5f, 0.5f, 0f, 0f,
                    1f, 0f, 0f, 0f,
                    1f, 0f, 0f, 0f
                ),
                4
            )
        )

        val mesh = SkinnedMesh(geom, MeshStandardMaterial(color = com.nexustools.genericui.ext.threejs.math.Color(0xffffff)))
        val scene = Scene().apply {
            add(rootBone)
            add(mesh)
            updateMatrixWorld(true)
        }
        val skeleton = Skeleton(listOf(rootBone, childBone))
        mesh.bind(skeleton)

        // Rotate Shoulder 90 deg around Z at t = 1s
        val halfAngle = (PI / 4.0).toFloat()
        val rotTrack = KeyframeTrack(
            name = "Shoulder.quaternion",
            times = floatArrayOf(0f, 1f),
            values = floatArrayOf(
                0f, 0f, 0f, 1f,
                0f, 0f, sin(halfAngle), cos(halfAngle)
            ),
            valueSize = 4
        )
        val clip = AnimationClip("Swing", 1f, listOf(rotTrack))
        val mixer = AnimationMixer(scene)
        mixer.clipAction(clip).apply { loop = false }.play()
        mixer.update(1f)

        val renderer = WebGLRenderer(
            config = WebGLRendererConfig(preferVulkanWhenAvailable = false)
        )
        val skinned = renderer.computeSkinnedPositions(mesh)
        // Vertex 1 at rest was (2,0,0); after 90 deg CCW Z rotation around (0,0,0), Elbow is at (0,1,0) and (2,0,0) becomes (0,2,0)
        assertTrue(abs(skinned[3] - 0f) < 1e-3f)
        assertTrue(abs(skinned[4] - 2f) < 1e-3f)

        // Also exercise GL pipeline draw call cleanly
        val camera = PerspectiveCamera(fov = 60f, aspect = 1f).apply {
            position.set(0f, 0f, 5f)
            updateMatrixWorld(true)
        }
        renderer.render(scene, camera)
        assertEquals(1, renderer.info.calls)
        renderer.dispose()
    }
}
