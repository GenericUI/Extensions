package com.nexustools.genericui.ext.box3d

import com.nexustools.genericui.component.media.VBlankSubscription
import com.nexustools.genericui.ext.canvas3d.GLCanvasExt
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.core.BufferAttribute
import com.nexustools.genericui.ext.threejs.core.BufferGeometry
import com.nexustools.genericui.ext.threejs.geometries.BoxGeometry
import com.nexustools.genericui.ext.threejs.geometries.CylinderGeometry
import com.nexustools.genericui.ext.threejs.geometries.SphereGeometry
import com.nexustools.genericui.ext.threejs.materials.LineBasicMaterial
import com.nexustools.genericui.ext.threejs.materials.MeshBasicMaterial
import com.nexustools.genericui.ext.threejs.math.Color
import com.nexustools.genericui.ext.threejs.objects.Group
import com.nexustools.genericui.ext.threejs.objects.LineSegments
import com.nexustools.genericui.ext.threejs.objects.Mesh
import com.nexustools.genericui.ext.threejs.renderers.WebGLRenderer
import com.nexustools.genericui.ext.threejs.scenes.Scene

/**
 * 3D Physics Debug Renderer bridging `:extensions:box3d` ([World3D]) with `:extensions:threejs`
 * ([Scene], [Group], [WebGLRenderer]) and `:extensions:canvas3d` ([GLCanvasExt]).
 *
 * Generates and updates wireframe meshes for 3D colliders, AABBs, joints, and contact normals.
 */
class Box3DDebugRenderer(
    val world: World3D,
    val scene: Scene
) {
    val debugGroup: Group = Group().apply { name = "Box3DDebugGroup" }

    var showColliders: Boolean = true
    var showAABBs: Boolean = false
    var showJoints: Boolean = true
    var showContacts: Boolean = true

    private val colliderMeshes = mutableMapOf<Collider3D, Mesh>()
    private var overlayLineSegments: LineSegments? = null

    private val dynamicMaterial = MeshBasicMaterial(color = Color(0x4ade80), wireframe = true)
    private val staticMaterial = MeshBasicMaterial(color = Color(0x60a5fa), wireframe = true)
    private val sleepingMaterial = MeshBasicMaterial(color = Color(0x94a3b8), wireframe = true)
    private val overlayLineMaterial = LineBasicMaterial(color = Color(0xfacc15), linewidth = 1.5f)

    init {
        scene.add(debugGroup)
    }

    /**
     * Synchronizes debug wireframe meshes and line segments in [debugGroup] with the current state of [world].
     */
    fun update() {
        val activeColliders = mutableSetOf<Collider3D>()

        if (showColliders) {
            for (body in world.bodies) {
                for (collider in body.colliders) {
                    activeColliders.add(collider)
                    val mesh = colliderMeshes.getOrPut(collider) {
                        val created = createColliderWireframeMesh(collider)
                        debugGroup.add(created)
                        created
                    }
                    val tf = collider.worldTransform()
                    mesh.position.set(tf.position.x, tf.position.y, tf.position.z)
                    mesh.quaternion.set(tf.rotation.x, tf.rotation.y, tf.rotation.z, tf.rotation.w)
                    mesh.material = when {
                        body.type == BodyType3D.STATIC -> staticMaterial
                        !body.isAwake -> sleepingMaterial
                        else -> dynamicMaterial
                    }
                }
            }
        }

        // Remove stale collider meshes
        val iter = colliderMeshes.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key !in activeColliders) {
                debugGroup.remove(entry.value)
                iter.remove()
            }
        }

        // Rebuild AABB, joint, and contact normal line segments
        overlayLineSegments?.let { debugGroup.remove(it) }
        val linePositions = mutableListOf<Float>()

        if (showAABBs) {
            for (body in world.bodies) {
                for (collider in body.colliders) {
                    appendAABBWireframe(linePositions, collider.computeWorldAABB())
                }
            }
        }

        if (showJoints) {
            for (joint in world.joints) {
                val a = joint.worldAnchorA
                val b = joint.worldAnchorB
                linePositions.addAll(listOf(a.x, a.y, a.z, b.x, b.y, b.z))
            }
        }

        if (showContacts) {
            for (manifold in world.contactManifolds) {
                val n = manifold.normal
                for (pt in manifold.points) {
                    val m = pt.midpoint
                    val tip = m + n * 0.35f
                    linePositions.addAll(listOf(m.x, m.y, m.z, tip.x, tip.y, tip.z))
                }
            }
        }

        if (linePositions.isNotEmpty()) {
            val geom = BufferGeometry()
            geom.setAttribute("position", BufferAttribute(linePositions.toFloatArray(), 3))
            val lines = LineSegments(geom, overlayLineMaterial)
            overlayLineSegments = lines
            debugGroup.add(lines)
        }
    }

    /**
     * Starts an automatic VBlank simulation and rendering loop on [renderer] (backed by [GLCanvasExt]),
     * stepping [world], updating debug wireframes, and rendering [scene] from [camera] on every frame.
     */
    fun startSimulationLoop(
        renderer: WebGLRenderer,
        camera: Camera
    ) {
        renderer.setAnimationLoop { deltaMs, _ ->
            val dtSec = (deltaMs / 1000.0).toFloat().coerceIn(0f, 0.05f)
            if (dtSec > 0f) {
                world.step(dtSec)
            }
            update()
            renderer.render(scene, camera)
        }
    }

    /**
     * Convenience helper creating a [WebGLRenderer] bound to [glCanvas] and running the physics + debug render loop.
     */
    fun bindToCanvas3D(
        camera: Camera,
        glCanvas: GLCanvasExt = GLCanvasExt()
    ): WebGLRenderer {
        val renderer = WebGLRenderer(glCanvas = glCanvas)
        startSimulationLoop(renderer, camera)
        return renderer
    }

    private fun createColliderWireframeMesh(collider: Collider3D): Mesh {
        val geometry: BufferGeometry = when (val s = collider.shape) {
            is BoxShape3D -> BoxGeometry(
                s.halfExtents.x * 2f,
                s.halfExtents.y * 2f,
                s.halfExtents.z * 2f
            )
            is SphereShape3D -> SphereGeometry(s.radius, 12, 8)
            is CapsuleShape3D -> CylinderGeometry(s.radius, s.radius, (s.halfHeight + s.radius) * 2f, 12, 1)
            is CylinderShape3D -> CylinderGeometry(s.radius, s.radius, s.halfHeight * 2f, 12, 1)
            else -> {
                val aabb = s.computeAABB(Transform3D())
                val ext = aabb.extents
                BoxGeometry(ext.x * 2f, ext.y * 2f, ext.z * 2f)
            }
        }
        return Mesh(geometry, dynamicMaterial)
    }

    private fun appendAABBWireframe(out: MutableList<Float>, aabb: AABB3D) {
        val min = aabb.min
        val max = aabb.max
        val c000 = Vec3(min.x, min.y, min.z)
        val c100 = Vec3(max.x, min.y, min.z)
        val c010 = Vec3(min.x, max.y, min.z)
        val c110 = Vec3(max.x, max.y, min.z)
        val c001 = Vec3(min.x, min.y, max.z)
        val c101 = Vec3(max.x, min.y, max.z)
        val c011 = Vec3(min.x, max.y, max.z)
        val c111 = Vec3(max.x, max.y, max.z)

        fun seg(a: Vec3, b: Vec3) {
            out.add(a.x); out.add(a.y); out.add(a.z)
            out.add(b.x); out.add(b.y); out.add(b.z)
        }

        seg(c000, c100); seg(c100, c110); seg(c110, c010); seg(c010, c000)
        seg(c001, c101); seg(c101, c111); seg(c111, c011); seg(c011, c001)
        seg(c000, c001); seg(c100, c101); seg(c110, c111); seg(c010, c011)
    }
}
