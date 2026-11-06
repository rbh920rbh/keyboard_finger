package com.example.keyboardtouchdemo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.WindowManager

class TouchAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN ||
            !event.isCtrlPressed ||
            !event.isAltPressed
        ) {
            return false
        }

        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_1 -> clickAt(0.25f, 0.25f)
            KeyEvent.KEYCODE_2 -> clickAt(0.75f, 0.25f)
            KeyEvent.KEYCODE_3 -> clickAt(0.25f, 0.75f)
            KeyEvent.KEYCODE_4 -> clickAt(0.75f, 0.75f)
            KeyEvent.KEYCODE_DPAD_UP -> swipe(0.5f, 0.7f, 0.5f, 0.3f)
            KeyEvent.KEYCODE_DPAD_DOWN -> swipe(0.5f, 0.3f, 0.5f, 0.7f)
            KeyEvent.KEYCODE_DPAD_LEFT -> swipe(0.7f, 0.5f, 0.3f, 0.5f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> swipe(0.3f, 0.5f, 0.7f, 0.5f)
            else -> false
        }
        return handled
    }

    private fun clickAt(relativeX: Float, relativeY: Float): Boolean {
        val (width, height) = screenSize()
        val path = Path().apply {
            moveTo(width * relativeX, height * relativeY)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                .build(),
            null,
            null
        )
    }

    private fun swipe(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float
    ): Boolean {
        val (width, height) = screenSize()
        val path = Path().apply {
            moveTo(width * fromX, height * fromY)
            lineTo(width * toX, height * toY)
        }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
                .build(),
            null,
            null
        )
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Float, Float> {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = android.util.DisplayMetrics()
        manager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels.toFloat() to metrics.heightPixels.toFloat()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
