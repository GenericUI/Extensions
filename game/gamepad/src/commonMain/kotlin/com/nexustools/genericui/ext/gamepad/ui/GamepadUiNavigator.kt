package com.nexustools.genericui.ext.gamepad.ui

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.ext.gamepad.GamepadFrameSnapshot
import com.nexustools.genericui.ext.gamepad.GamepadManager
import kotlin.math.abs

/**
 * Zero-configuration gamepad UI navigation coordinator attaching to any [AbstractContainer],
 * [com.nexustools.genericui.component.shell.Window], or [com.nexustools.genericui.component.dialog.DialogWindow]
 * to drive spatial focus movement, hover/focus visuals, activation, slider adjustment, tab cycling,
 * modal dialog focus scoping, and [com.nexustools.genericui.component.layout.ScrollView] scrolling.
 */
class GamepadUiNavigator(
    val root: AbstractContainer,
    val gamepadManager: GamepadManager = GamepadManager.shared,
    val playerIndex: Int = 0,
    var strategy: GamepadUiNavigationStrategy = DefaultSpatialGamepadUiNavigationStrategy(),
    val stickThreshold: Float = 0.55f,
    val initialRepeatDelaySeconds: Float = 0.35f,
    val repeatIntervalSeconds: Float = 0.12f
) {
    var focusedComponent: AbstractComponent? = null
        private set

    var isRunning: Boolean = false
        private set

    val isWatchingVBlank: Boolean
        get() = vblankWatcherId > 0

    private var activeDirection: GamepadNavDirection? = null
    private var holdElapsedSeconds: Float = 0f
    private var nextRepeatAtSeconds: Float = initialRepeatDelaySeconds
    private var vblankWatcherId: Int = -1

    fun setFocusedComponent(target: AbstractComponent?) {
        val candidates = strategy.discoverFocusableComponents(root)
        val validTarget = if (target != null && target in candidates) target else null
        val prev = focusedComponent
        if (prev !== validTarget) {
            focusedComponent = validTarget
            strategy.onFocusChanged(prev, validTarget, root)
        }
    }

    fun start(tickIntervalMs: Long = 16L) {
        if (isRunning || root.isDisposed) return
        isRunning = true
        val fallbackDtSec = tickIntervalMs.coerceAtLeast(4L) / 1000f
        if (vblankWatcherId <= 0) {
            vblankWatcherId = Animator.watchVBlank { deltaMs, _, _ ->
                if (!isRunning || root.isDisposed) {
                    stop()
                    return@watchVBlank
                }
                val dtSec = if (deltaMs > 0.0) {
                    (deltaMs / 1000.0).toFloat().coerceIn(0.001f, 0.25f)
                } else {
                    fallbackDtSec
                }
                step(dtSec)
            }
        }
    }

    fun stop() {
        isRunning = false
        val id = vblankWatcherId
        vblankWatcherId = -1
        if (id > 0) {
            Animator.unwatchVBlank(id)
        }
    }

    fun detach() {
        stop()
        val prev = focusedComponent
        if (prev != null) {
            focusedComponent = null
            strategy.onFocusChanged(prev, null, root)
        }
    }

    /**
     * Polls [gamepadManager] once and executes one navigation/activation tick for [deltaSeconds].
     */
    fun step(deltaSeconds: Float = 1f / 60f): GamepadFrameSnapshot {
        gamepadManager.pollAll()
        val frame = gamepadManager.snapshot(playerIndex)
        processFrame(frame, deltaSeconds)
        return frame
    }

    /**
     * Applies an already-polled [frame] to the attached UI tree for [deltaSeconds].
     */
    fun processFrame(
        frame: GamepadFrameSnapshot,
        deltaSeconds: Float = 1f / 60f
    ) {
        val candidates = strategy.discoverFocusableComponents(root)
        var current = focusedComponent
        if (current == null || current !in candidates) {
            val initial = strategy.resolveInitialFocus(root, candidates)
            if (initial !== current) {
                strategy.onFocusChanged(current, initial, root)
                focusedComponent = initial
            }
            current = focusedComponent
        }

        if (!frame.connected) return

        if (frame.isButtonJustPressed(GamepadButton.LEFT_BUMPER)) {
            if (strategy.onTabCycle(root, -1, frame)) {
                reconcileFocusAfterTreeMutation()
                current = focusedComponent
            }
        } else if (frame.isButtonJustPressed(GamepadButton.RIGHT_BUMPER)) {
            if (strategy.onTabCycle(root, +1, frame)) {
                reconcileFocusAfterTreeMutation()
                current = focusedComponent
            }
        }

        if (frame.isButtonJustPressed(GamepadButton.A) && current != null) {
            strategy.onActivate(current, root, frame)
            reconcileFocusAfterTreeMutation()
            current = focusedComponent
        }

        if (frame.isButtonJustPressed(GamepadButton.B)) {
            strategy.onCancel(current, root, frame)
            reconcileFocusAfterTreeMutation()
            current = focusedComponent
        }

        strategy.onContinuousInput(current, root, frame, deltaSeconds)

        val dir = resolveHeldDirection(frame)
        val triggeredDirection = advanceRepeatTimer(dir, deltaSeconds)
        if (triggeredDirection != null) {
            val handledSlider = tryAdjustSlider(current, triggeredDirection)
            if (!handledSlider) {
                val updatedCandidates = strategy.discoverFocusableComponents(root)
                val next = strategy.resolveNextFocus(current, triggeredDirection, updatedCandidates)
                if (next != null && next !== current) {
                    strategy.onFocusChanged(current, next, root)
                    focusedComponent = next
                }
            }
        }
    }

    private fun reconcileFocusAfterTreeMutation() {
        val updated = strategy.discoverFocusableComponents(root)
        val cur = focusedComponent
        if (cur == null || cur !in updated) {
            val next = strategy.resolveInitialFocus(root, updated)
            if (next !== cur) {
                strategy.onFocusChanged(cur, next, root)
                focusedComponent = next
            }
        }
    }

    private fun tryAdjustSlider(
        focused: AbstractComponent?,
        dir: GamepadNavDirection
    ): Boolean {
        val slider = focused as? Slider ?: return false
        return when (dir) {
            GamepadNavDirection.RIGHT -> {
                if (slider.value < slider.max - 1e-6) {
                    slider.dispatchUserValueChange(slider.value + slider.step)
                    true
                } else {
                    false
                }
            }
            GamepadNavDirection.LEFT -> {
                if (slider.value > slider.min + 1e-6) {
                    slider.dispatchUserValueChange(slider.value - slider.step)
                    true
                } else {
                    false
                }
            }
            GamepadNavDirection.UP, GamepadNavDirection.DOWN -> false
        }
    }

    private fun resolveHeldDirection(frame: GamepadFrameSnapshot): GamepadNavDirection? {
        if (frame.isButtonDown(GamepadButton.DPAD_UP)) return GamepadNavDirection.UP
        if (frame.isButtonDown(GamepadButton.DPAD_DOWN)) return GamepadNavDirection.DOWN
        if (frame.isButtonDown(GamepadButton.DPAD_LEFT)) return GamepadNavDirection.LEFT
        if (frame.isButtonDown(GamepadButton.DPAD_RIGHT)) return GamepadNavDirection.RIGHT

        val lx = frame.leftStick.x
        val ly = frame.leftStick.y
        val ax = abs(lx)
        val ay = abs(ly)
        if (ax < stickThreshold && ay < stickThreshold) return null
        return if (ay >= ax) {
            if (ly > 0f) GamepadNavDirection.UP else GamepadNavDirection.DOWN
        } else {
            if (lx > 0f) GamepadNavDirection.RIGHT else GamepadNavDirection.LEFT
        }
    }

    private fun advanceRepeatTimer(
        dir: GamepadNavDirection?,
        deltaSeconds: Float
    ): GamepadNavDirection? {
        if (dir == null) {
            activeDirection = null
            holdElapsedSeconds = 0f
            nextRepeatAtSeconds = initialRepeatDelaySeconds
            return null
        }
        if (dir != activeDirection) {
            activeDirection = dir
            holdElapsedSeconds = 0f
            nextRepeatAtSeconds = initialRepeatDelaySeconds
            return dir
        }
        val dt = deltaSeconds.coerceAtLeast(0f)
        holdElapsedSeconds += dt
        if (holdElapsedSeconds >= nextRepeatAtSeconds) {
            nextRepeatAtSeconds += repeatIntervalSeconds
            return dir
        }
        return null
    }
}
