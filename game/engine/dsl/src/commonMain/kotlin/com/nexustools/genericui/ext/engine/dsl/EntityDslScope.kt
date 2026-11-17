package com.nexustools.genericui.ext.engine.dsl

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.engine.scene.EntityModule
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.engine.svg.Skeleton2D
import com.nexustools.genericui.ext.engine.ui.UiElementBindingModule
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Fluent DSL scope for configuring a [SceneEntity], its placement, modules, and UI bindings.
 */
@GenericUiDslMarker
class EntityDslScope(
    val entity: SceneEntity
) {
    fun position2D(x: Float, y: Float) {
        entity.transform.setPosition2D(x, y)
    }

    fun position(x: Float, y: Float, z: Float = 0f) {
        entity.transform.setPosition(x, y, z)
    }

    fun rotation2DRad(radians: Float) {
        entity.transform.rotation2DRad = radians
    }

    fun scale2D(sx: Float, sy: Float = sx) {
        entity.transform.setScale2D(sx, sy)
    }

    fun <T : EntityModule> module(mod: T): T = entity.addModule(mod)

    fun behavior(block: (@GenericUiDslMarker InlineEntityBehavior.() -> Unit)): InlineEntityBehavior {
        val b = InlineEntityBehavior().apply(block)
        entity.addModule(b)
        return b
    }

    fun uiBinding(
        component: AbstractComponent,
        widthPx: Int? = component.widthPx,
        heightPx: Int? = component.heightPx,
        backgroundColor: RgbaColor? = null,
        useMarginPositioning: Boolean = false,
        skeleton2D: Skeleton2D? = null,
        block: (@GenericUiDslMarker UiElementBindingModule.() -> Unit)? = null
    ): UiElementBindingModule {
        val mod = UiElementBindingModule(
            component = component,
            widthPx = widthPx,
            heightPx = heightPx,
            backgroundColor = backgroundColor,
            useMarginPositioning = useMarginPositioning,
            skeleton2D = skeleton2D
        )
        block?.invoke(mod)
        entity.addModule(mod)
        return mod
    }

    fun skeleton2D(
        rootOffsetX: Float = 0f,
        rootOffsetY: Float = 0f,
        block: (@GenericUiDslMarker Skeleton2DDslScope.() -> Unit)
    ): Skeleton2D {
        val scope = Skeleton2DDslScope(rootOffsetX = rootOffsetX, rootOffsetY = rootOffsetY)
        scope.block()
        return scope.build()
    }

    fun child(
        name: String = "Entity",
        tag: String = "Default",
        block: (@GenericUiDslMarker EntityDslScope.() -> Unit)? = null
    ): SceneEntity {
        val childEntity = SceneEntity(name = name).apply {
            tagAndLayer.tag = tag
        }
        entity.addChild(childEntity)
        if (block != null) {
            EntityDslScope(childEntity).block()
        }
        return childEntity
    }
}
