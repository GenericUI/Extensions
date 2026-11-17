package com.nexustools.genericui.ext.gamepad

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives Android `Activity`/`View` [KeyEvent], [MotionEvent], touchpad contacts, and API 31+
 * `InputDevice.getSensorManager()` 6-axis IMU gyro/accelerometer samples for [PlatformGamepadBackend].
 */
object AndroidGamepadInputBridge {
    internal class DeviceCapture(
        val deviceId: Int,
        val buttons: BooleanArray = BooleanArray(GamepadButton.STANDARD_BUTTON_COUNT),
        val axes: FloatArray = FloatArray(8),
        val fusion: SixAxisSensorFusion = SixAxisSensorFusion()
    ) {
        @Volatile var touchpad: TouchpadState = TouchpadState()
        @Volatile var motion: SixAxisMotionState = SixAxisMotionState.UNAVAILABLE
        @Volatile var sensorsAttached: Boolean = false

        var lastGyroX: Float = 0f
        var lastGyroY: Float = 0f
        var lastGyroZ: Float = 0f
        var lastAccelX: Float = 0f
        var lastAccelY: Float = 1f
        var lastAccelZ: Float = 0f
        var lastSensorTimeMs: Long = 0L
    }

    private val captures = ConcurrentHashMap<Int, DeviceCapture>()
    private val sensorListeners = ConcurrentHashMap<Int, SensorEventListener>()

    internal fun captureForDevice(deviceId: Int): DeviceCapture =
        captures.getOrPut(deviceId) { DeviceCapture(deviceId) }

    internal fun ensureSensorsRegistered(device: InputDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cap = captureForDevice(device.id)
        if (cap.sensorsAttached) return
        cap.sensorsAttached = true
        val sm = device.sensorManager
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyro == null && accel == null) return

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nowMs = event.timestamp / 1_000_000L
                val prevMs = cap.lastSensorTimeMs
                cap.lastSensorTimeMs = nowMs
                val dt = if (prevMs > 0L) ((nowMs - prevMs).coerceIn(1L, 100L) / 1000f) else (1f / 60f)
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        cap.lastGyroX = event.values[0]
                        cap.lastGyroY = event.values[1]
                        cap.lastGyroZ = event.values[2]
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        cap.lastAccelX = event.values[0] / SensorManager.GRAVITY_EARTH
                        cap.lastAccelY = event.values[1] / SensorManager.GRAVITY_EARTH
                        cap.lastAccelZ = event.values[2] / SensorManager.GRAVITY_EARTH
                    }
                }
                cap.motion = cap.fusion.update(
                    gyroRadX = cap.lastGyroX,
                    gyroRadY = cap.lastGyroY,
                    gyroRadZ = cap.lastGyroZ,
                    accelGX = cap.lastAccelX,
                    accelGY = cap.lastAccelY,
                    accelGZ = cap.lastAccelZ,
                    dtSeconds = dt,
                    timestampMs = nowMs
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorListeners[device.id] = listener
        gyro?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val deviceId = event?.deviceId ?: return false
        val idx = mapAndroidKeyCodeToStandardIndex(keyCode) ?: return false
        captureForDevice(deviceId).buttons[idx] = true
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val deviceId = event?.deviceId ?: return false
        val idx = mapAndroidKeyCodeToStandardIndex(keyCode) ?: return false
        captureForDevice(deviceId).buttons[idx] = false
        return true
    }

    fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        val isTouchpad = (event.source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
        if (isTouchpad) {
            val cap = captureForDevice(event.deviceId)
            val action = event.actionMasked
            val dev = event.device
            val rangeX = dev?.getMotionRange(MotionEvent.AXIS_X)?.range?.takeIf { it > 0f } ?: 1f
            val rangeY = dev?.getMotionRange(MotionEvent.AXIS_Y)?.range?.takeIf { it > 0f } ?: 1f
            val f1Active = event.pointerCount >= 1 && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
            val f2Active = event.pointerCount >= 2 && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL
            val f1 = TouchpadFingerState(
                touchId = if (f1Active) event.getPointerId(0) else -1,
                active = f1Active,
                x = if (f1Active) (event.getX(0) / rangeX).coerceIn(0f, 1f) else 0f,
                y = if (f1Active) (event.getY(0) / rangeY).coerceIn(0f, 1f) else 0f
            )
            val f2 = TouchpadFingerState(
                touchId = if (f2Active) event.getPointerId(1) else -1,
                active = f2Active,
                x = if (f2Active) (event.getX(1) / rangeX).coerceIn(0f, 1f) else 0f,
                y = if (f2Active) (event.getY(1) / rangeY).coerceIn(0f, 1f) else 0f
            )
            val delta = TouchpadState(f1, f2, cap.buttons[GamepadButton.SHARE.standardIndex]).computeSwipeDelta(cap.touchpad)
            cap.touchpad = TouchpadState(
                finger1 = f1,
                finger2 = f2,
                isTouchpadClicked = cap.buttons[GamepadButton.SHARE.standardIndex],
                deltaX = delta.x,
                deltaY = delta.y
            )
            return true
        }

        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        if (!isJoystick) return false

        val cap = captureForDevice(event.deviceId)
        cap.axes[0] = event.getAxisValue(MotionEvent.AXIS_X)
        cap.axes[1] = event.getAxisValue(MotionEvent.AXIS_Y)
        cap.axes[2] = event.getAxisValue(MotionEvent.AXIS_Z)
        cap.axes[3] = event.getAxisValue(MotionEvent.AXIS_RZ)
        cap.axes[4] = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        cap.axes[5] = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        cap.axes[6] = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        cap.axes[7] = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        return true
    }

    private fun mapAndroidKeyCodeToStandardIndex(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> GamepadButton.A.standardIndex
        KeyEvent.KEYCODE_BUTTON_B -> GamepadButton.B.standardIndex
        KeyEvent.KEYCODE_BUTTON_X -> GamepadButton.X.standardIndex
        KeyEvent.KEYCODE_BUTTON_Y -> GamepadButton.Y.standardIndex
        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadButton.LEFT_BUMPER.standardIndex
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadButton.RIGHT_BUMPER.standardIndex
        KeyEvent.KEYCODE_BUTTON_L2 -> GamepadButton.LEFT_TRIGGER_BUTTON.standardIndex
        KeyEvent.KEYCODE_BUTTON_R2 -> GamepadButton.RIGHT_TRIGGER_BUTTON.standardIndex
        KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadButton.VIEW.standardIndex
        KeyEvent.KEYCODE_BUTTON_START -> GamepadButton.MENU.standardIndex
        KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadButton.LEFT_STICK.standardIndex
        KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadButton.RIGHT_STICK.standardIndex
        KeyEvent.KEYCODE_DPAD_UP -> GamepadButton.DPAD_UP.standardIndex
        KeyEvent.KEYCODE_DPAD_DOWN -> GamepadButton.DPAD_DOWN.standardIndex
        KeyEvent.KEYCODE_DPAD_LEFT -> GamepadButton.DPAD_LEFT.standardIndex
        KeyEvent.KEYCODE_DPAD_RIGHT -> GamepadButton.DPAD_RIGHT.standardIndex
        KeyEvent.KEYCODE_BUTTON_MODE -> GamepadButton.GUIDE.standardIndex
        KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_SYSRQ -> GamepadButton.SHARE.standardIndex
        else -> null
    }
}
