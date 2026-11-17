package com.nexustools.genericui.ext.threejs.loaders

import com.nexustools.genericui.ext.threejs.animation.AnimationClip
import com.nexustools.genericui.ext.threejs.animation.KeyframeTrack
import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.materials.Material
import com.nexustools.genericui.ext.threejs.materials.MeshStandardMaterial
import com.nexustools.genericui.ext.threejs.materials.Side
import com.nexustools.genericui.ext.threejs.math.Matrix4
import com.nexustools.genericui.ext.threejs.objects.Bone
import com.nexustools.genericui.ext.threejs.objects.Group
import com.nexustools.genericui.ext.threejs.objects.Mesh
import com.nexustools.genericui.ext.threejs.objects.Skeleton
import com.nexustools.genericui.ext.threejs.objects.SkinnedMesh
import com.nexustools.genericui.res.DataUrlCodec
import kotlin.math.abs

/**
 * Full glTF 2.0 (`.gltf` JSON + base64 data URIs and `.glb` binary containers) loader (`THREE.GLTFLoader`).
 *
 * Decodes scene hierarchies, TRS/matrix transforms, interleaved/strided binary accessors, PBR materials,
 * skeletal [Bone] hierarchies, [Skeleton] inverse bind matrices, [SkinnedMesh] bindings, and
 * keyframe [AnimationClip] tracks targeting node and bone names.
 */
class GltfLoader {

    fun loadFromGltf(json: String): GltfAsset = parseAsset(json, binChunk = null)

    fun loadGlb(bytes: ByteArray): GltfAsset {
        if (bytes.size < 12) return parseAsset("{}", null)
        val magic = readInt32LE(bytes, 0)
        if (magic != GLB_MAGIC) {
            return parseAsset("{}", null)
        }
        var offset = 12
        var jsonText = "{}"
        var binChunk: ByteArray? = null
        while (offset + 8 <= bytes.size) {
            val chunkLength = readInt32LE(bytes, offset)
            val chunkType = readInt32LE(bytes, offset + 4)
            offset += 8
            val chunkEnd = (offset + chunkLength).coerceAtMost(bytes.size)
            if (chunkType == GLB_CHUNK_JSON && chunkEnd > offset) {
                jsonText = bytes.copyOfRange(offset, chunkEnd).decodeToString()
            } else if (chunkType == GLB_CHUNK_BIN && binChunk == null && chunkEnd >= offset) {
                binChunk = bytes.copyOfRange(offset, chunkEnd)
            }
            offset = chunkEnd
        }
        return parseAsset(jsonText, binChunk)
    }

    fun parseJson(gltfJson: String): Group = loadFromGltf(gltfJson).scene

    fun parseGlb(glbBytes: ByteArray): Group = loadGlb(glbBytes).scene

    fun parseAsset(gltfJson: String, binChunk: ByteArray? = null): GltfAsset {
        val rootJson = GltfJsonParser(gltfJson).parseRoot()
        val buffers = decodeBuffers(rootJson, binChunk)
        val bufferViews = parseBufferViews(rootJson)
        val accessors = parseAccessors(rootJson)
        val materials = parseMaterials(rootJson)

        val decoder = AccessorDecoder(buffers, bufferViews, accessors)
        val meshSpecs = parseMeshes(rootJson, decoder, materials)
        val skinDefs = parseSkinDefinitions(rootJson)
        val jointNodeIndices = LinkedHashSet<Int>()
        for (skin in skinDefs) {
            jointNodeIndices.addAll(skin.jointIndices)
        }

        val nodeJsons = rootJson.arr("nodes")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        val builtNodes = arrayOfNulls<Object3D>(nodeJsons.size)
        val skinnedMeshesBySkin = HashMap<Int, MutableList<SkinnedMesh>>()

        for (idx in nodeJsons.indices) {
            val nodeJson = nodeJsons[idx]
            val nodeName = nodeJson.str("name")?.takeIf { it.isNotEmpty() } ?: "node_$idx"
            val meshIdx = nodeJson.int("mesh")
            val skinIdx = nodeJson.int("skin")
            val isJoint = idx in jointNodeIndices

            val nodeObj: Object3D = if (meshIdx != null && meshIdx in meshSpecs.indices) {
                val prims = meshSpecs[meshIdx]
                if (prims.size == 1) {
                    val prim = prims[0]
                    if (skinIdx != null && skinIdx >= 0) {
                        val sm = SkinnedMesh(prim.geometry, prim.material)
                        sm.name = nodeName
                        skinnedMeshesBySkin.getOrPut(skinIdx) { mutableListOf() }.add(sm)
                        sm
                    } else {
                        Mesh(prim.geometry, prim.material).apply { name = nodeName }
                    }
                } else {
                    val holder = Group().apply { name = nodeName }
                    for (pIdx in prims.indices) {
                        val prim = prims[pIdx]
                        if (skinIdx != null && skinIdx >= 0) {
                            val sm = SkinnedMesh(prim.geometry, prim.material).apply {
                                name = "${nodeName}_prim_$pIdx"
                            }
                            skinnedMeshesBySkin.getOrPut(skinIdx) { mutableListOf() }.add(sm)
                            holder.add(sm)
                        } else {
                            holder.add(
                                Mesh(prim.geometry, prim.material).apply {
                                    name = "${nodeName}_prim_$pIdx"
                                }
                            )
                        }
                    }
                    holder
                }
            } else if (isJoint) {
                Bone().apply { name = nodeName }
            } else {
                Group().apply { name = nodeName }
            }

            applyNodeTransform(nodeJson, nodeObj)
            builtNodes[idx] = nodeObj
        }

        val parented = BooleanArray(nodeJsons.size)
        for (idx in nodeJsons.indices) {
            val parentObj = builtNodes[idx] ?: continue
            val children = nodeJsons[idx].intList("children") ?: continue
            for (childIdx in children) {
                if (childIdx in builtNodes.indices && childIdx != idx) {
                    builtNodes[childIdx]?.let { childObj ->
                        parentObj.add(childObj)
                        parented[childIdx] = true
                    }
                }
            }
        }

        val rootGroup = Group()
        val scenes = rootJson.arr("scenes")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        val defaultSceneIdx = rootJson.int("scene") ?: 0
        if (scenes.isNotEmpty() && defaultSceneIdx in scenes.indices) {
            val activeScene = scenes[defaultSceneIdx]
            rootGroup.name = activeScene.str("name")?.takeIf { it.isNotEmpty() } ?: "GLTFScene"
            val rootNodeIndices = activeScene.intList("nodes") ?: emptyList()
            for (rnIdx in rootNodeIndices) {
                if (rnIdx in builtNodes.indices) {
                    builtNodes[rnIdx]?.let { rootGroup.add(it) }
                }
            }
        } else {
            rootGroup.name = "GLTFScene"
            for (idx in builtNodes.indices) {
                if (!parented[idx]) {
                    builtNodes[idx]?.let { rootGroup.add(it) }
                }
            }
        }

        rootGroup.updateMatrixWorld(true)

        val identityMat = Matrix4()
        val skeletons = ArrayList<Skeleton>(skinDefs.size)
        for (skinIdx in skinDefs.indices) {
            val skinDef = skinDefs[skinIdx]
            val bones = skinDef.jointIndices.mapNotNull { jIdx ->
                builtNodes.getOrNull(jIdx) as? Bone
            }
            val parsedInverses: List<Matrix4>? = skinDef.inverseBindMatricesAccessor?.let { accIdx ->
                val floats = decoder.readFloats(accIdx)
                val count = minOf(bones.size, floats.size / 16)
                List(count) { b ->
                    val m = Matrix4()
                    floats.copyInto(m.elements, destinationOffset = 0, startIndex = b * 16, endIndex = (b + 1) * 16)
                    m
                }
            }

            val targets = skinnedMeshesBySkin[skinIdx] ?: emptyList()
            if (targets.isEmpty()) {
                val skel = Skeleton(bones, parsedInverses).apply { update() }
                skeletons.add(skel)
            } else {
                for (sm in targets) {
                    val meshWorldIsIdentity = sm.matrixWorld.elements.contentEquals(identityMat.elements)
                    val perMeshInverses = if (parsedInverses == null) {
                        null
                    } else if (meshWorldIsIdentity) {
                        parsedInverses.map { it.clone() }
                    } else {
                        val meshWorldInv = sm.matrixWorld.clone().invert()
                        parsedInverses.map { ibm -> Matrix4().multiplyMatrices(ibm, meshWorldInv) }
                    }
                    val skel = Skeleton(bones, perMeshInverses)
                    sm.bind(skel, sm.matrixWorld)
                    skel.update()
                    skeletons.add(skel)
                }
            }
        }

        val animations = parseAnimations(rootJson, decoder, builtNodes)
        rootGroup.userData["animations"] = animations
        rootGroup.userData["skins"] = skeletons

        return GltfAsset(
            scene = rootGroup,
            animations = animations,
            skins = skeletons
        )
    }

    private fun decodeBuffers(
        root: GltfJsonParser.Value.Obj,
        binChunk: ByteArray?
    ): List<ByteArray> {
        val bufJsons = root.arr("buffers")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        if (bufJsons.isEmpty()) {
            return if (binChunk != null) listOf(binChunk) else emptyList()
        }
        return bufJsons.mapIndexed { index, bufObj ->
            val uri = bufObj.str("uri")
            when {
                uri != null && DataUrlCodec.isDataUrl(uri) -> DataUrlCodec.decodeBytes(uri) ?: ByteArray(0)
                index == 0 && binChunk != null -> binChunk
                else -> ByteArray(bufObj.int("byteLength")?.coerceAtLeast(0) ?: 0)
            }
        }
    }

    private fun parseBufferViews(root: GltfJsonParser.Value.Obj): List<BufferViewDef> {
        val arr = root.arr("bufferViews")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        return arr.map { obj ->
            BufferViewDef(
                bufferIndex = obj.int("buffer") ?: 0,
                byteOffset = obj.int("byteOffset") ?: 0,
                byteLength = obj.int("byteLength") ?: 0,
                byteStride = obj.int("byteStride") ?: 0
            )
        }
    }

    private fun parseAccessors(root: GltfJsonParser.Value.Obj): List<AccessorDef> {
        val arr = root.arr("accessors")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        return arr.map { obj ->
            val sparseObj = obj.obj("sparse")
            val sparse = if (sparseObj != null) {
                val sparseIndices = sparseObj.obj("indices")
                val sparseValues = sparseObj.obj("values")
                if (sparseIndices != null && sparseValues != null) {
                    SparseDef(
                        count = sparseObj.int("count") ?: 0,
                        indicesBufferView = sparseIndices.int("bufferView") ?: 0,
                        indicesByteOffset = sparseIndices.int("byteOffset") ?: 0,
                        indicesComponentType = sparseIndices.int("componentType") ?: COMPONENT_UNSIGNED_SHORT,
                        valuesBufferView = sparseValues.int("bufferView") ?: 0,
                        valuesByteOffset = sparseValues.int("byteOffset") ?: 0
                    )
                } else {
                    null
                }
            } else {
                null
            }
            AccessorDef(
                bufferView = obj.int("bufferView"),
                byteOffset = obj.int("byteOffset") ?: 0,
                componentType = obj.int("componentType") ?: COMPONENT_FLOAT,
                count = obj.int("count") ?: 0,
                type = obj.str("type") ?: "SCALAR",
                normalized = obj.bool("normalized") ?: false,
                sparse = sparse
            )
        }
    }

    private fun parseMaterials(root: GltfJsonParser.Value.Obj): List<Material> {
        val arr = root.arr("materials")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        return arr.map { obj ->
            val pbr = obj.obj("pbrMetallicRoughness")
            val baseColor = pbr?.floatList("baseColorFactor")
            val mat = MeshStandardMaterial()
            mat.name = obj.str("name") ?: ""
            if (baseColor != null && baseColor.size >= 3) {
                mat.color.set(baseColor[0], baseColor[1], baseColor[2])
                if (baseColor.size >= 4) {
                    mat.opacity = baseColor[3].coerceIn(0f, 1f)
                }
            }
            pbr?.float("metallicFactor")?.let { mat.metalness = it }
            pbr?.float("roughnessFactor")?.let { mat.roughness = it }
            obj.floatList("emissiveFactor")?.let { em ->
                if (em.size >= 3) mat.emissive.set(em[0], em[1], em[2])
            }
            if (obj.bool("doubleSided") == true) {
                mat.side = Side.DOUBLE
            }
            when (obj.str("alphaMode")) {
                "BLEND" -> mat.transparent = true
                "MASK" -> mat.alphaTest = obj.float("alphaCutoff") ?: 0.5f
            }
            if (mat.opacity < 1f) mat.transparent = true
            mat
        }
    }

    private fun parseMeshes(
        root: GltfJsonParser.Value.Obj,
        decoder: AccessorDecoder,
        materials: List<Material>
    ): List<List<PrimitiveSpec>> {
        val arr = root.arr("meshes")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        return arr.map { meshObj ->
            val primJsons = meshObj.arr("primitives")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
            primJsons.map { primObj ->
                val geom = BufferGeometry()
                val attrsObj = primObj.obj("attributes")
                if (attrsObj != null) {
                    attrsObj.int("POSITION")?.let { accIdx ->
                        geom.setAttribute("position", BufferAttribute(decoder.readFloats(accIdx), 3))
                    }
                    attrsObj.int("NORMAL")?.let { accIdx ->
                        geom.setAttribute("normal", BufferAttribute(decoder.readFloats(accIdx), 3))
                    }
                    attrsObj.int("TEXCOORD_0")?.let { accIdx ->
                        geom.setAttribute("uv", BufferAttribute(decoder.readFloats(accIdx), 2))
                    }
                    attrsObj.int("COLOR_0")?.let { accIdx ->
                        val size = decoder.componentCountFor(accIdx).coerceIn(3, 4)
                        geom.setAttribute("color", BufferAttribute(decoder.readFloats(accIdx), size))
                    }
                    attrsObj.int("JOINTS_0")?.let { accIdx ->
                        val joints = decoder.readFloats(accIdx)
                        geom.setAttribute("skinIndex", BufferAttribute(joints, 4))
                    }
                    attrsObj.int("WEIGHTS_0")?.let { accIdx ->
                        val weights = decoder.readFloats(accIdx)
                        normalizeSkinWeights(weights)
                        geom.setAttribute("skinWeight", BufferAttribute(weights, 4))
                    }
                }
                primObj.int("indices")?.let { idxAcc ->
                    geom.setIndex(decoder.readInts(idxAcc))
                }
                if (geom.getAttribute("normal") == null && geom.getAttribute("position") != null) {
                    geom.computeVertexNormals()
                }
                geom.computeBoundingBox()
                geom.computeBoundingSphere()

                val matIdx = primObj.int("material")
                val material = if (matIdx != null && matIdx in materials.indices) {
                    materials[matIdx]
                } else {
                    MeshStandardMaterial()
                }
                PrimitiveSpec(geom, material)
            }
        }
    }

    private fun parseSkinDefinitions(root: GltfJsonParser.Value.Obj): List<SkinDef> {
        val arr = root.arr("skins")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        return arr.map { obj ->
            SkinDef(
                name = obj.str("name") ?: "",
                jointIndices = obj.intList("joints") ?: emptyList(),
                inverseBindMatricesAccessor = obj.int("inverseBindMatrices"),
                skeletonRootIndex = obj.int("skeleton")
            )
        }
    }

    private fun parseAnimations(
        root: GltfJsonParser.Value.Obj,
        decoder: AccessorDecoder,
        nodes: Array<Object3D?>
    ): List<AnimationClip> {
        val animJsons = root.arr("animations")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
        return animJsons.mapIndexed { animIdx, animObj ->
            val clipName = animObj.str("name")?.takeIf { it.isNotEmpty() } ?: "animation_$animIdx"
            val samplers = animObj.arr("samplers")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
            val channels = animObj.arr("channels")?.items?.mapNotNull { it as? GltfJsonParser.Value.Obj } ?: emptyList()
            val tracks = ArrayList<KeyframeTrack>(channels.size)
            var maxDuration = 0f

            for (ch in channels) {
                val samplerIdx = ch.int("sampler") ?: continue
                if (samplerIdx !in samplers.indices) continue
                val samplerObj = samplers[samplerIdx]
                val targetObj = ch.obj("target") ?: continue
                val targetNodeIdx = targetObj.int("node") ?: continue
                val targetPath = targetObj.str("path") ?: continue
                val targetNode = nodes.getOrNull(targetNodeIdx) ?: continue

                val inputAcc = samplerObj.int("input") ?: continue
                val outputAcc = samplerObj.int("output") ?: continue
                val interpolation = samplerObj.str("interpolation") ?: "LINEAR"

                val times = decoder.readFloats(inputAcc)
                val rawOutputs = decoder.readFloats(outputAcc)
                if (times.isEmpty()) continue

                val valueSize = when (targetPath) {
                    "rotation" -> 4
                    "translation", "scale" -> 3
                    else -> continue
                }
                val trackProperty = when (targetPath) {
                    "translation" -> "position"
                    "rotation" -> "quaternion"
                    else -> "scale"
                }

                val values = if (interpolation == "CUBICSPLINE" && rawOutputs.size >= times.size * valueSize * 3) {
                    FloatArray(times.size * valueSize) { k ->
                        val kf = k / valueSize
                        val c = k % valueSize
                        rawOutputs[kf * valueSize * 3 + valueSize + c]
                    }
                } else {
                    rawOutputs
                }

                val lastTime = times.lastOrNull() ?: 0f
                if (lastTime > maxDuration) maxDuration = lastTime

                tracks.add(
                    KeyframeTrack(
                        name = "${targetNode.name}.$trackProperty",
                        times = times,
                        values = values,
                        valueSize = valueSize
                    )
                )
            }

            AnimationClip(
                name = clipName,
                duration = maxDuration,
                tracks = tracks
            )
        }
    }

    private fun applyNodeTransform(nodeJson: GltfJsonParser.Value.Obj, node: Object3D) {
        val mat = nodeJson.floatList("matrix")
        if (mat != null && mat.size >= 16) {
            for (i in 0 until 16) {
                node.matrix.elements[i] = mat[i]
            }
            node.matrix.decompose(node.position, node.quaternion, node.scale)
            node.syncEulerFromQuaternion()
            return
        }
        nodeJson.floatList("translation")?.let { t ->
            if (t.size >= 3) node.position.set(t[0], t[1], t[2])
        }
        nodeJson.floatList("rotation")?.let { r ->
            if (r.size >= 4) {
                node.quaternion.set(r[0], r[1], r[2], r[3]).normalize()
                node.syncEulerFromQuaternion()
            }
        }
        nodeJson.floatList("scale")?.let { s ->
            if (s.size >= 3) node.scale.set(s[0], s[1], s[2])
        }
    }

    private fun normalizeSkinWeights(weights: FloatArray) {
        var i = 0
        while (i + 3 < weights.size) {
            val sum = weights[i] + weights[i + 1] + weights[i + 2] + weights[i + 3]
            if (sum > 0f && abs(sum - 1f) > 1e-4f) {
                val inv = 1f / sum
                weights[i] *= inv
                weights[i + 1] *= inv
                weights[i + 2] *= inv
                weights[i + 3] *= inv
            }
            i += 4
        }
    }

    private class BufferViewDef(
        val bufferIndex: Int,
        val byteOffset: Int,
        val byteLength: Int,
        val byteStride: Int
    )

    private class SparseDef(
        val count: Int,
        val indicesBufferView: Int,
        val indicesByteOffset: Int,
        val indicesComponentType: Int,
        val valuesBufferView: Int,
        val valuesByteOffset: Int
    )

    private class AccessorDef(
        val bufferView: Int?,
        val byteOffset: Int,
        val componentType: Int,
        val count: Int,
        val type: String,
        val normalized: Boolean,
        val sparse: SparseDef?
    )

    private class PrimitiveSpec(
        val geometry: BufferGeometry,
        val material: Material
    )

    private class SkinDef(
        val name: String,
        val jointIndices: List<Int>,
        val inverseBindMatricesAccessor: Int?,
        val skeletonRootIndex: Int?
    )

    private class AccessorDecoder(
        private val buffers: List<ByteArray>,
        private val bufferViews: List<BufferViewDef>,
        private val accessors: List<AccessorDef>
    ) {
        fun componentCountFor(accessorIndex: Int): Int =
            componentCountForType(accessors.getOrNull(accessorIndex)?.type ?: "SCALAR")

        fun readFloats(accessorIndex: Int): FloatArray {
            val acc = accessors.getOrNull(accessorIndex) ?: return FloatArray(0)
            val compCount = componentCountForType(acc.type)
            val totalFloats = acc.count * compCount
            if (totalFloats <= 0) return FloatArray(0)
            val result = FloatArray(totalFloats)

            val bvIdx = acc.bufferView
            if (bvIdx != null && bvIdx in bufferViews.indices) {
                val bv = bufferViews[bvIdx]
                val buf = buffers.getOrNull(bv.bufferIndex)
                if (buf != null) {
                    val elemSize = componentByteSize(acc.componentType)
                    val stride = if (bv.byteStride > 0) bv.byteStride else compCount * elemSize
                    val baseOffset = bv.byteOffset + acc.byteOffset
                    for (i in 0 until acc.count) {
                        val itemOffset = baseOffset + i * stride
                        for (c in 0 until compCount) {
                            result[i * compCount + c] = readComponentFloat(
                                buf,
                                itemOffset + c * elemSize,
                                acc.componentType,
                                acc.normalized
                            )
                        }
                    }
                }
            }

            acc.sparse?.let { sparse ->
                val idxBv = bufferViews.getOrNull(sparse.indicesBufferView)
                val valBv = bufferViews.getOrNull(sparse.valuesBufferView)
                val idxBuf = idxBv?.let { buffers.getOrNull(it.bufferIndex) }
                val valBuf = valBv?.let { buffers.getOrNull(it.bufferIndex) }
                if (idxBv != null && valBv != null && idxBuf != null && valBuf != null) {
                    val idxElemSize = componentByteSize(sparse.indicesComponentType)
                    val idxBase = idxBv.byteOffset + sparse.indicesByteOffset
                    val valElemSize = componentByteSize(acc.componentType)
                    val valBase = valBv.byteOffset + sparse.valuesByteOffset
                    for (k in 0 until sparse.count) {
                        val targetElem = readComponentInt(
                            idxBuf,
                            idxBase + k * idxElemSize,
                            sparse.indicesComponentType
                        )
                        if (targetElem in 0 until acc.count) {
                            for (c in 0 until compCount) {
                                result[targetElem * compCount + c] = readComponentFloat(
                                    valBuf,
                                    valBase + (k * compCount + c) * valElemSize,
                                    acc.componentType,
                                    acc.normalized
                                )
                            }
                        }
                    }
                }
            }

            return result
        }

        fun readInts(accessorIndex: Int): IntArray {
            val acc = accessors.getOrNull(accessorIndex) ?: return IntArray(0)
            val compCount = componentCountForType(acc.type)
            val totalInts = acc.count * compCount
            if (totalInts <= 0) return IntArray(0)
            val result = IntArray(totalInts)
            val bvIdx = acc.bufferView ?: return result
            val bv = bufferViews.getOrNull(bvIdx) ?: return result
            val buf = buffers.getOrNull(bv.bufferIndex) ?: return result
            val elemSize = componentByteSize(acc.componentType)
            val stride = if (bv.byteStride > 0) bv.byteStride else compCount * elemSize
            val baseOffset = bv.byteOffset + acc.byteOffset
            for (i in 0 until acc.count) {
                val itemOffset = baseOffset + i * stride
                for (c in 0 until compCount) {
                    result[i * compCount + c] = readComponentInt(
                        buf,
                        itemOffset + c * elemSize,
                        acc.componentType
                    )
                }
            }
            return result
        }

        private fun componentCountForType(type: String): Int = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4", "MAT2" -> 4
            "MAT3" -> 9
            "MAT4" -> 16
            else -> 1
        }

        private fun componentByteSize(componentType: Int): Int = when (componentType) {
            COMPONENT_BYTE, COMPONENT_UNSIGNED_BYTE -> 1
            COMPONENT_SHORT, COMPONENT_UNSIGNED_SHORT -> 2
            COMPONENT_UNSIGNED_INT, COMPONENT_FLOAT -> 4
            else -> 4
        }

        private fun readComponentInt(buf: ByteArray, offset: Int, componentType: Int): Int {
            return when (componentType) {
                COMPONENT_BYTE -> if (offset in buf.indices) buf[offset].toInt() else 0
                COMPONENT_UNSIGNED_BYTE -> if (offset in buf.indices) buf[offset].toInt() and 0xFF else 0
                COMPONENT_SHORT -> {
                    if (offset + 1 >= buf.size) return 0
                    val lo = buf[offset].toInt() and 0xFF
                    val hi = buf[offset + 1].toInt()
                    (hi shl 8) or lo
                }
                COMPONENT_UNSIGNED_SHORT -> {
                    if (offset + 1 >= buf.size) return 0
                    (buf[offset].toInt() and 0xFF) or ((buf[offset + 1].toInt() and 0xFF) shl 8)
                }
                COMPONENT_UNSIGNED_INT -> {
                    if (offset + 3 >= buf.size) return 0
                    readInt32LE(buf, offset)
                }
                else -> 0
            }
        }

        private fun readComponentFloat(
            buf: ByteArray,
            offset: Int,
            componentType: Int,
            normalized: Boolean
        ): Float {
            return when (componentType) {
                COMPONENT_FLOAT -> {
                    if (offset + 3 >= buf.size) return 0f
                    Float.fromBits(readInt32LE(buf, offset))
                }
                COMPONENT_BYTE -> {
                    val v = readComponentInt(buf, offset, COMPONENT_BYTE).toFloat()
                    if (normalized) maxOf(v / 127f, -1f) else v
                }
                COMPONENT_UNSIGNED_BYTE -> {
                    val v = readComponentInt(buf, offset, COMPONENT_UNSIGNED_BYTE).toFloat()
                    if (normalized) v / 255f else v
                }
                COMPONENT_SHORT -> {
                    val v = readComponentInt(buf, offset, COMPONENT_SHORT).toFloat()
                    if (normalized) maxOf(v / 32767f, -1f) else v
                }
                COMPONENT_UNSIGNED_SHORT -> {
                    val v = readComponentInt(buf, offset, COMPONENT_UNSIGNED_SHORT).toFloat()
                    if (normalized) v / 65535f else v
                }
                COMPONENT_UNSIGNED_INT -> {
                    if (offset + 3 >= buf.size) return 0f
                    val u = readInt32LE(buf, offset).toLong() and 0xFFFF_FFFFL
                    if (normalized) u.toFloat() / 4294967295f else u.toFloat()
                }
                else -> 0f
            }
        }
    }

    companion object {
        private const val GLB_MAGIC = 0x46546C67 // "glTF"
        private const val GLB_CHUNK_JSON = 0x4E4F534A // "JSON"
        private const val GLB_CHUNK_BIN = 0x004E4942 // "BIN\0"

        private const val COMPONENT_BYTE = 5120
        private const val COMPONENT_UNSIGNED_BYTE = 5121
        private const val COMPONENT_SHORT = 5122
        private const val COMPONENT_UNSIGNED_SHORT = 5123
        private const val COMPONENT_UNSIGNED_INT = 5125
        private const val COMPONENT_FLOAT = 5126

        private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
            if (offset + 3 >= bytes.size) return 0
            return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        }
    }
}
