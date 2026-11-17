package com.nexustools.genericui.ext.engine.scene

import com.nexustools.genericui.ext.engine.math.SpatialTransform

/**
 * Reusable blueprint that instantiates and configures a [SceneEntity] inside a [GameScene].
 */
class EntityTemplate(
    val name: String = "Entity",
    val tagAndLayer: TagAndLayerMask = TagAndLayerMask(),
    val defaultTransform: SpatialTransform = SpatialTransform(),
    private val moduleFactories: List<() -> EntityModule> = emptyList(),
    private val customizer: ((SceneEntity) -> Unit)? = null
) {
    fun spawn(
        scene: GameScene,
        parent: SceneEntity? = null,
        configure: (SceneEntity.() -> Unit)? = null
    ): SceneEntity {
        val spawned = scene.createEntity(
            name = name,
            tag = tagAndLayer.tag,
            parent = parent
        )
        spawned.tagAndLayer.layerMask = tagAndLayer.layerMask
        spawned.transform.copyFrom(defaultTransform)
        for (factory in moduleFactories) {
            spawned.addModule(factory())
        }
        customizer?.invoke(spawned)
        configure?.invoke(spawned)
        return spawned
    }
}
