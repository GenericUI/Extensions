package com.nexustools.genericui.ext.box3d

/**
 * Base class for 3D geometric collision shapes.
 */
abstract class ColliderShape3D(val type: ShapeType3D) {
    abstract fun computeAABB(worldTransform: Transform3D): AABB3D
    abstract fun computeMassProperties(density: Float): MassProperties3D
    abstract fun supportPoint(worldTransform: Transform3D, direction: Vec3): Vec3
}
