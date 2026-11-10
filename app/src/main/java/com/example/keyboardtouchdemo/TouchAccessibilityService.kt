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
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.os.SystemClock

class TouchAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pressedDirections = mutableMapOf<Int, Pair<Float, Float>>()
    private var movementScheduled = false
    private var directionHoldStartedAt = 0L
    private lateinit var windowManager: WindowManager
    private var cursorView: CursorView? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var dragActive = false
    private var touchStroke: GestureDescription.StrokeDescription? = null
    private var touchGestureRunning = false
    private var touchReleaseRequested = false
    private var touchSegmentContinues = false
    private var touchLastX = 0f
    private var touchLastY = 0f
    private var touchTargetX = 0f
    private var touchTargetY = 0f
    private var touchSegmentEndX = 0f
    private var touchSegmentEndY = 0f
    private val touchGestureCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription) {
            mainHandler.post {
                touchGestureRunning = false
                touchLastX = touchSegmentEndX
                touchLastY = touchSegmentEndY
                if (touchSegmentContinues && (dragActive || touchReleaseRequested)) {
                    dispatchNextTouchSegment()
                } else {
                    touchStroke = null
                    touchReleaseRequested = false
                }
            }
        }

        override fun onCancelled(gestureDescription: GestureDescription) {
            mainHandler.post {
                touchGestureRunning = false
                touchStroke = null
                touchReleaseRequested = false
            }
        }
    }
    private val hideCursorRunnable = Runnable {
        cursorView?.animate()
            ?.alpha(0f)
            ?.setDuration(250L)
            ?.start()
    }
    private val moveLoop = object : Runnable {
        override fun run() {
            if (pressedDirections.isEmpty()) {
                movementScheduled = false
                return
            }

            var dx = pressedDirections.values.sumOf { it.first.toDouble() }.toFloat()
            var dy = pressedDirections.values.sumOf { it.second.toDouble() }.toFloat()
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            if (length > 1f) {
                dx /= length
                dy /= length
            }

            val heldMs = SystemClock.uptimeMillis() - directionHoldStartedAt
            val acceleration = when {
                heldMs >= 600 -> 12f
                heldMs >= 300 -> 7f
                heldMs >= 120 -> 3f
                else -> 1f
            }
            moveCursor(dx, dy, acceleration)
            mainHandler.postDelayed(this, 16L)
        }
    }

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
        val isPointerMove = direction != null &&
            !event.isCtrlPressed &&
            !event.isAltPressed
        if (isPointerMove) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (!pressedDirections.containsKey(event.keyCode)) {
                    pressedDirections[event.keyCode] = direction
                    if (pressedDirections.size == 1) {
                        directionHoldStartedAt = SystemClock.uptimeMillis()
                    }
                    if (!movementScheduled) {
                        movementScheduled = true
                        mainHandler.post(moveLoop)
                    }
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                pressedDirections.remove(event.keyCode)
                if (pressedDirections.isEmpty()) {
                    mainHandler.removeCallbacks(moveLoop)
                    movementScheduled = false
                }
            }
            return true
        }

        if (!event.isCtrlPressed && !event.isAltPressed &&
            isPointerClickKey(event.keyCode)
        ) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!dragActive) {
                        dragActive = true
                        wakeCursor()
                        startTouchGesture(cursorX, cursorY)
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (dragActive) {
                        dragActive = false
                        endTouchGesture()
                    }
                }
            }
            return true
        }

        if (!event.isCtrlPressed && !event.isAltPressed &&
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return true
        }

        val isCtrlShortcut = event.isCtrlPressed && !event.isAltPressed
        val isCtrlAltVerticalSwipe = event.isCtrlPressed &&
            event.isAltPressed &&
            !event.isShiftPressed &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        if (event.action != KeyEvent.ACTION_DOWN ||
            (!isCtrlShortcut && !isCtrlAltVerticalSwipe)
        ) {
            return false
        }

        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> performGlobalAction(GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_DPAD_UP -> swipe(0.5f, 0.4f, 0.5f, 0.6f)
            KeyEvent.KEYCODE_DPAD_DOWN -> swipe(0.5f, 0.6f, 0.5f, 0.4f)
            else -> false
        }
        return handled
    }

    private fun isPointerClickKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_0

    private fun directionFor(keyCode: Int): Pair<Float, Float>? = when (keyCode) {
        KeyEvent.KEYCODE_NUMPAD_8 -> 0f to -1f
        KeyEvent.KEYCODE_NUMPAD_5 -> 0f to 1f
        KeyEvent.KEYCODE_NUMPAD_4 -> -1f to 0f
        KeyEvent.KEYCODE_NUMPAD_6 -> 1f to 0f
        else -> null
    }

    private fun moveCursor(dx: Float, dy: Float, acceleration: Float) {
        val (width, height) = screenSize()
        val density = resources.displayMetrics.density
        val step = 5f * density * acceleration
        val radius = 20f * density
        cursorX = (cursorX + dx * step).coerceIn(radius, width - radius)
        cursorY = (cursorY + dy * step).coerceIn(radius, height - radius)
        mainHandler.post {
            wakeCursor()
            updateCursor()
            if (dragActive) {
                queueTouchPoint(cursorX, cursorY)
            }
        }
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        cursorView = view
        cursorParams = params
        windowManager.addView(view, params)
        updateCursor()
        scheduleCursorFade()
    }

    private fun wakeCursor() {
        val view = cursorView ?: return
        mainHandler.removeCallbacks(hideCursorRunnable)
        view.animate().cancel()
        view.alpha = 1f
        scheduleCursorFade()
    }

    private fun scheduleCursorFade() {
        mainHandler.removeCallbacks(hideCursorRunnable)
        mainHandler.postDelayed(hideCursorRunnable, 900L)
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

    private fun startTouchGesture(x: Float, y: Float) {
        touchLastX = x
        touchLastY = y
        touchTargetX = x
        touchTargetY = y
        touchSegmentEndX = x
        touchSegmentEndY = y
        touchReleaseRequested = false
        touchSegmentContinues = true
        val path = touchPath(x, y, x, y)
        touchStroke = GestureDescription.StrokeDescription(path, 0, 16, true)
        touchGestureRunning = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(touchStroke!!)
                .build(),
            touchGestureCallback,
            mainHandler
        )
        if (!touchGestureRunning) touchStroke = null
    }

    private fun queueTouchPoint(x: Float, y: Float) {
        touchTargetX = x
        touchTargetY = y
        if (!touchGestureRunning) {
            if (touchStroke == null && dragActive) {
                startTouchGesture(x, y)
            } else {
                dispatchNextTouchSegment()
            }
        }
    }

    private fun endTouchGesture() {
        touchReleaseRequested = true
        if (!touchGestureRunning && touchStroke != null) {
            dispatchNextTouchSegment()
        }
    }

    private fun dispatchNextTouchSegment() {
        val previousStroke = touchStroke ?: return
        val continueGesture = dragActive
        val endX = touchTargetX
        val endY = touchTargetY
        val path = touchPath(touchLastX, touchLastY, endX, endY)
        touchSegmentEndX = endX
        touchSegmentEndY = endY
        touchSegmentContinues = continueGesture
        touchStroke = previousStroke.continueStroke(path, 0, 16, continueGesture)
        touchGestureRunning = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(touchStroke!!)
                .build(),
            touchGestureCallback,
            mainHandler
        )
        if (!touchGestureRunning) touchStroke = null
    }

    private fun touchPath(fromX: Float, fromY: Float, toX: Float, toY: Float): Path {
        val path = Path().apply {
            moveTo(fromX, fromY)
        }
        if (fromX != toX || fromY != toY) {
            path.lineTo(toX, toY)
        }
        return path
    }

    private fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
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
        mainHandler.removeCallbacks(hideCursorRunnable)
        mainHandler.removeCallbacks(moveLoop)
        dragActive = false
        touchReleaseRequested = true
        touchStroke = null
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
            color = Color.WHITE
            alpha = 72
            style = Paint.Style.FILL
            setShadowLayer(4f, 0f, 1f, 0x66000000)
        }

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            val center = width / 2f
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = 72
            canvas.drawCircle(center, center, width * 0.24f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width * 0.08f
            paint.color = Color.WHITE
            paint.alpha = 96
            canvas.drawCircle(center, center, width * 0.32f, paint)
        }
    }
}
