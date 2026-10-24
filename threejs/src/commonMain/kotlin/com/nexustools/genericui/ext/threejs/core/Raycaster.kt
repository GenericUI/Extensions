package com.nexustools.genericui.ext.threejs.core

import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.cameras.OrthographicCamera
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import com.nexustools.genericui.ext.threejs.math.Matrix4
import com.nexustools.genericui.ext.threejs.math.Ray
import com.nexustools.genericui.ext.threejs.math.Sphere
import com.nexustools.genericui.ext.threejs.math.Vector2
import com.nexustools.genericui.ext.threejs.math.Vector3
import com.nexustools.genericui.ext.threejs.objects.Mesh

/**
 * 3D scene picking and ray-triangle intersection tester (`THREE.Raycaster`).
 */
class Raycaster(
    origin: Vector3 = Vector3(),
    direction: Vector3 = Vector3(0f, 0f, -1f),
    var near: Float = 0f,
    var far: Float = Float.POSITIVE_INFINITY
) {
    val ray: Ray = Ray(origin, direction)
    val layers: Layers = Layers()

    fun set(origin: Vector3, direction: Vector3) {
        ray.set(origin, direction)
    }

    /**
     * Configures [ray] from normalized device coordinates [coords] (`x, y` in `-1..1`) and [camera].
     */
    fun setFromCamera(coords: Vector2, camera: Camera) {
        camera.updateMatrixWorld()
        when (camera) {
            is PerspectiveCamera -> {
                ray.origin.setFromMatrixPosition(camera.matrixWorld)
                val unproj = Matrix4().copy(camera.projectionMatrix).invert()
                ray.direction.set(coords.x, coords.y, 0.5f)
                    .applyMatrix4(unproj)
                    .applyMatrix4(camera.matrixWorld)
                    .sub(ray.origin)
                    .normalize()
            }
            is OrthographicCamera -> {
                val unproj = Matrix4().copy(camera.projectionMatrix).invert()
                ray.origin.set(coords.x, coords.y, (camera.near + camera.far) / (camera.near - camera.far))
                    .applyMatrix4(unproj)
                    .applyMatrix4(camera.matrixWorld)
                ray.direction.set(0f, 0f, -1f).transformDirection(camera.matrixWorld)
            }
            else -> {
                ray.origin.setFromMatrixPosition(camera.matrixWorld)
                ray.direction.set(0f, 0f, -1f).transformDirection(camera.matrixWorld)
            }
        }
    }

    fun intersectObject(object3D: Object3D, recursive: Boolean = true): List<Intersection> {
        val hits = mutableListOf<Intersection>()
        intersectObjectInternal(object3D, recursive, hits)
        return hits.sortedBy { it.distance }
    }

    fun intersectObjects(objects: List<Object3D>, recursive: Boolean = true): List<Intersection> {
        val hits = mutableListOf<Intersection>()
        for (obj in objects) {
            intersectObjectInternal(obj, recursive, hits)
        }
        return hits.sortedBy { it.distance }
    }

    private fun intersectObjectInternal(
        object3D: Object3D,
        recursive: Boolean,
        outHits: MutableList<Intersection>
    ) {
        if (!object3D.visible) return
        if (object3D.layers.test(layers) && object3D is Mesh) {
            intersectMesh(object3D, outHits)
        }
        if (recursive) {
            for (child in object3D.children) {
                intersectObjectInternal(child, true, outHits)
            }
        }
    }

    private fun intersectMesh(mesh: Mesh, outHits: MutableList<Intersection>) {
        val geometry = mesh.geometry
        if (geometry.boundingSphere == null) geometry.computeBoundingSphere()
        val worldSphere = Sphere().copy(geometry.boundingSphere ?: return).applyMatrix4(mesh.matrixWorld)
        if (ray.intersectSphere(worldSphere) == null) return

        val inverseMatrix = Matrix4().copy(mesh.matrixWorld).invert()
        val localRay = Ray().copy(ray).applyMatrix4(inverseMatrix)

        val pos = geometry.getAttribute("position") ?: return
        val vA = Vector3()
        val vB = Vector3()
        val vC = Vector3()
        val hitLocal = Vector3()

        val idx = geometry.index
        if (idx != null) {
            var i = 0
            while (i + 2 < idx.size) {
                pos.getVector3(idx[i], vA)
                pos.getVector3(idx[i + 1], vB)
                pos.getVector3(idx[i + 2], vC)
                if (localRay.intersectTriangle(vA, vB, vC, false, hitLocal) != null) {
                    val worldPoint = hitLocal.clone().applyMatrix4(mesh.matrixWorld)
                    val dist = ray.origin.distanceTo(worldPoint)
                    if (dist in near..far) {
                        outHits.add(
                            Intersection(
                                distance = dist,
                                point = worldPoint,
                                object3D = mesh,
                                faceIndex = i / 3
                            )
                        )
                    }
                }
                i += 3
            }
        } else {
            var i = 0
            while (i + 2 < pos.count) {
                pos.getVector3(i, vA)
                pos.getVector3(i + 1, vB)
                pos.getVector3(i + 2, vC)
                if (localRay.intersectTriangle(vA, vB, vC, false, hitLocal) != null) {
                    val worldPoint = hitLocal.clone().applyMatrix4(mesh.matrixWorld)
                    val dist = ray.origin.distanceTo(worldPoint)
                    if (dist in near..far) {
                        outHits.add(
                            Intersection(
                                distance = dist,
                                point = worldPoint,
                                object3D = mesh,
                                faceIndex = i / 3
                            )
                        )
                    }
                }
                i += 3
            }
        }
    }
}
