package com.example.keyboardtouchdemo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.KeyEvent
import android.view.WindowManager

class TouchAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var cursorView: CursorView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var cursorX = 0f
    private var cursorY = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val (width, height) = screenSize()
        cursorX = width / 2f
        cursorY = height / 2f
        mainHandler.post { showCursor() }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val direction = directionFor(event.keyCode)
        if (direction != null && !event.isCtrlPressed && !event.isAltPressed) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                moveCursor(direction.first, direction.second, event.repeatCount)
            }
            return true
        }

        if (!event.isCtrlPressed && !event.isAltPressed &&
            (event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_5)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                clickAt(cursorX, cursorY)
            }
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN ||
            !event.isCtrlPressed ||
            !event.isAltPressed
        ) {
            return false
        }

        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_1 -> clickAtPercent(0.25f, 0.25f)
            KeyEvent.KEYCODE_2 -> clickAtPercent(0.75f, 0.25f)
            KeyEvent.KEYCODE_3 -> clickAtPercent(0.25f, 0.75f)
            KeyEvent.KEYCODE_4 -> clickAtPercent(0.75f, 0.75f)
            KeyEvent.KEYCODE_DPAD_UP -> swipe(0.5f, 0.7f, 0.5f, 0.3f)
            KeyEvent.KEYCODE_DPAD_DOWN -> swipe(0.5f, 0.3f, 0.5f, 0.7f)
            KeyEvent.KEYCODE_DPAD_LEFT -> swipe(0.7f, 0.5f, 0.3f, 0.5f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> swipe(0.3f, 0.5f, 0.7f, 0.5f)
            else -> false
        }
        return handled
    }

    private fun directionFor(keyCode: Int): Pair<Float, Float>? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_NUMPAD_8 -> 0f to -1f
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_NUMPAD_2 -> 0f to 1f
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_NUMPAD_4 -> -1f to 0f
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_NUMPAD_6 -> 1f to 0f
        else -> null
    }

    private fun moveCursor(dx: Float, dy: Float, repeatCount: Int) {
        val (width, height) = screenSize()
        val density = resources.displayMetrics.density
        val baseStep = 14f * density
        val acceleration = when {
            repeatCount >= 20 -> 4.5f
            repeatCount >= 10 -> 3f
            repeatCount >= 4 -> 1.8f
            else -> 1f
        }
        val step = baseStep * acceleration
        val radius = 20f * density
        cursorX = (cursorX + dx * step).coerceIn(radius, width - radius)
        cursorY = (cursorY + dy * step).coerceIn(radius, height - radius)
        mainHandler.post { updateCursor() }
    }

    private fun showCursor() {
        if (cursorView != null) return
        val size = (40 * resources.displayMetrics.density).toInt()
        val view = CursorView(this)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        cursorView = view
        cursorParams = params
        windowManager.addView(view, params)
        updateCursor()
    }

    private fun updateCursor() {
        val view = cursorView ?: return
        val params = cursorParams ?: return
        params.x = cursorX.toInt() - view.width / 2
        params.y = cursorY.toInt() - view.height / 2
        if (view.windowToken != null) {
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun clickAtPercent(relativeX: Float, relativeY: Float): Boolean {
        val (width, height) = screenSize()
        return clickAt(width * relativeX, height * relativeY)
    }

    private fun clickAt(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
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

    override fun onDestroy() {
        mainHandler.post {
            cursorView?.let { view ->
                if (view.windowToken != null) windowManager.removeView(view)
            }
            cursorView = null
        }
        super.onDestroy()
    }

    private class CursorView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 64, 64)
            alpha = 115
            style = Paint.Style.FILL
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            val center = width / 2f
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 64, 64)
            paint.alpha = 115
            canvas.drawCircle(center, center, width * 0.24f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width * 0.08f
            paint.color = Color.WHITE
            paint.alpha = 130
            canvas.drawCircle(center, center, width * 0.32f, paint)
        }
    }
}
