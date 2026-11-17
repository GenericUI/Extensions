package com.nexustools.genericui.ext.engine.ui

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.engine.threading.GameLoopScheduler
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.style.css.CssLength

/**
 * Pure-UI host mounting a [GameScene] onto a GenericUI [Container] / [Window] with zero canvas
 * dependencies, forwarding pointer/wheel/key input into [GameScene.input], binding VBlank
 * animations to [Container]'s `NativeImpl.setOnBeforeTargetFramePresent`, and driving
 * [GameLoopScheduler].
 */
class UiStageHost(
    val scene: GameScene = GameScene("UiStageScene"),
    val rootContainer: Container = Container("genericui:stage-host", "position-relative"),
    targetTicksPerSecond: Int = 60
) {
    val scheduler: GameLoopScheduler = GameLoopScheduler(
        scene = scene,
        targetTicksPerSecond = targetTicksPerSecond
    )

    init {
        rootContainer.getStyle().setPosition(CssPosition.RELATIVE)
        rootContainer.getStyle().setWidth(CssLength.PERCENT_100)
        rootContainer.getStyle().setHeight(CssLength.PERCENT_100)

        rootContainer.onPointerDown = { ev -> scene.input.notifyPointerDown(ev) }
        rootContainer.onPointerMove = { ev -> scene.input.notifyPointerMove(ev) }
        rootContainer.onPointerUp = { ev -> scene.input.notifyPointerUp(ev) }
        rootContainer.onWheel = { ev -> scene.input.notifyWheel(ev) }
        rootContainer.onKeyDown = { ev -> scene.input.notifyKeyDown(ev) }
        rootContainer.onKeyUp = { ev -> scene.input.notifyKeyUp(ev) }

        scene.vblankDriver.bindToComponent(rootContainer)
    }

    fun attachComponentEntity(
        name: String,
        component: AbstractComponent,
        x: Float = 0f,
        y: Float = 0f,
        widthPx: Int? = component.widthPx,
        heightPx: Int? = component.heightPx
    ): SceneEntity {
        if (component.parent !== rootContainer) {
            rootContainer.addChild(component)
        }
        val entity = scene.createEntity(name = name)
        entity.transform.setPosition2D(x, y)
        entity.addModule(
            UiElementBindingModule(
                component = component,
                widthPx = widthPx,
                heightPx = heightPx
            )
        )
        return entity
    }

    fun mountInWindow(window: Window): UiStageHost {
        window.setContent(rootContainer)
        scene.vblankDriver.syncComponentNativeHook()
        return this
    }

    fun syncVBlankHook() {
        scene.vblankDriver.syncComponentNativeHook()
    }

    fun start() {
        syncVBlankHook()
        scheduler.start()
    }

    fun stop() {
        scheduler.stop()
    }

    fun stepVBlankFrame(frameIndex: Long, timestampMs: Double) {
        scene.vblankDriver.onBeforeFramePresent(frameIndex, timestampMs)
    }

    fun dispose() {
        scheduler.stop()
        scene.dispose()
    }
}
