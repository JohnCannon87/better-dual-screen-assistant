package com.stormpanda.betterdualscreenassistant.input

import android.view.MotionEvent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.betterdualscreenassistant.privileged.PrivilegedBridge
import java.util.concurrent.Executors

/**
 * Sends virtual keyboard / mouse input to the top screen through the privileged helper.
 *
 * All binder calls run on a single worker thread so the UI never blocks and events stay ordered.
 * Pointer moves are coalesced: while a drain is pending, further deltas are summed together.
 */
object InputInjector {
    /** Display that receives the injected input (Top screen). */
    const val TARGET_DISPLAY_ID = 0

    private const val TAG = "InputInjector"

    const val BUTTON_LEFT = MotionEvent.BUTTON_PRIMARY
    const val BUTTON_RIGHT = MotionEvent.BUTTON_SECONDARY
    const val BUTTON_MIDDLE = MotionEvent.BUTTON_TERTIARY

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "BDSA-InputInjector").apply { isDaemon = true }
    }

    private val moveLock = Any()
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var moveScheduled = false

    fun key(keyCode: Int, metaState: Int = 0, action: Int = 2) {
        executor.execute { PrivilegedBridge.injectKey(TARGET_DISPLAY_ID, keyCode, metaState, action) }
    }

    fun text(text: String) {
        AppLog.d(TAG, "text injection length=${text.length}")
        executor.execute { PrivilegedBridge.injectText(TARGET_DISPLAY_ID, text) }
    }

    fun move(dx: Float, dy: Float) {
        synchronized(moveLock) {
            pendingDx += dx
            pendingDy += dy
            if (moveScheduled) return
            moveScheduled = true
        }
        executor.execute {
            val mx: Float
            val my: Float
            synchronized(moveLock) {
                mx = pendingDx
                my = pendingDy
                pendingDx = 0f
                pendingDy = 0f
                moveScheduled = false
            }
            if (mx != 0f || my != 0f) {
                PrivilegedBridge.injectPointerMove(TARGET_DISPLAY_ID, mx, my)
            }
        }
    }

    fun button(buttons: Int, down: Boolean) {
        AppLog.d(TAG, "mouse button mask=$buttons down=$down")
        executor.execute { PrivilegedBridge.injectPointerButton(TARGET_DISPLAY_ID, buttons, down) }
    }

    fun click(buttons: Int) {
        executor.execute {
            PrivilegedBridge.injectPointerButton(TARGET_DISPLAY_ID, buttons, true)
            PrivilegedBridge.injectPointerButton(TARGET_DISPLAY_ID, buttons, false)
        }
    }

    fun scroll(horizontal: Float, vertical: Float) {
        executor.execute { PrivilegedBridge.injectPointerScroll(TARGET_DISPLAY_ID, horizontal, vertical) }
    }
}
