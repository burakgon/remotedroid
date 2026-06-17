package com.remotedroid.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.remotedroid.input.CommandExecutor
import com.remotedroid.input.Cursor
import com.remotedroid.protocol.ClientMessage
import com.remotedroid.protocol.Features
import com.remotedroid.protocol.Screen
import com.remotedroid.protocol.ServerMessage
import com.remotedroid.ui.CursorView

/**
 * Komutları sisteme uygulayan Erişilebilirlik Servisi: imleç overlay'i + dispatchGesture
 * (dokunma/kaydırma), ses, metin (ACTION_SET_TEXT / ACTION_IME_ENTER) ve genel eylemler.
 * Sunucu thread'inden gelen komutlar ana thread'e post edilir.
 */
class RemoteAccessibilityService : AccessibilityService(), CommandExecutor {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private var windowManager: WindowManager? = null
    private var cursorView: View? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var cursorSize = 1
    private var cursor = Cursor(0f, 0f, 1, 1)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val m = resources.displayMetrics
        cursor = Cursor(m.widthPixels / 2f, m.heightPixels / 2f, m.widthPixels, m.heightPixels)
        addCursorOverlay()
    }

    private fun addCursorOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        cursorSize = (resources.displayMetrics.density * 22).toInt().coerceAtLeast(12)
        val view = CursorView(this)
        val params = WindowManager.LayoutParams(
            cursorSize,
            cursorSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (cursor.x - cursorSize / 2).toInt()
            y = (cursor.y - cursorSize / 2).toInt()
        }
        wm.addView(view, params)
        cursorView = view
        cursorParams = params
    }

    private fun moveCursorView() {
        val wm = windowManager ?: return
        val params = cursorParams ?: return
        val view = cursorView ?: return
        params.x = (cursor.x - cursorSize / 2).toInt()
        params.y = (cursor.y - cursorSize / 2).toInt()
        wm.updateViewLayout(view, params)
    }

    override fun execute(msg: ClientMessage) {
        main.post { handle(msg) }
    }

    private fun handle(msg: ClientMessage) {
        when (msg) {
            is ClientMessage.Move -> {
                cursor.move(msg.dx.toFloat(), msg.dy.toFloat())
                moveCursorView()
            }
            is ClientMessage.Tap -> tap(cursor.x, cursor.y, 50)
            is ClientMessage.LongPress -> tap(cursor.x, cursor.y, 600)
            is ClientMessage.DragStart -> {}
            is ClientMessage.DragMove -> {
                cursor.move(msg.dx.toFloat(), msg.dy.toFloat())
                moveCursorView()
            }
            is ClientMessage.DragEnd -> {}
            is ClientMessage.Scroll -> scroll(msg.dx.toFloat(), msg.dy.toFloat())
            is ClientMessage.Volume -> adjustVolume(msg.dir)
            is ClientMessage.Mute ->
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            is ClientMessage.Text -> setText(msg.value)
            is ClientMessage.Submit -> imeEnter()
            is ClientMessage.Backspace -> backspace()
            is ClientMessage.Clear -> setText("")
            is ClientMessage.Global -> performGlobal(msg.action)
            is ClientMessage.Hello, is ClientMessage.Ping -> {}
        }
    }

    override fun welcome(): ServerMessage.Welcome = ServerMessage.Welcome(
        screen = Screen(cursor.width, cursor.height),
        android = Build.VERSION.SDK_INT,
        features = Features(imeEnter = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R, scroll = true),
    )

    private fun tap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x.clampX(), y.clampY()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun scroll(dx: Float, dy: Float) {
        val x = cursor.x
        val y = cursor.y
        val gap = 40f
        val p1 = Path().apply { moveTo((x - gap).clampX(), y.clampY()); lineTo((x - gap + dx).clampX(), (y + dy).clampY()) }
        val p2 = Path().apply { moveTo((x + gap).clampX(), y.clampY()); lineTo((x + gap + dx).clampX(), (y + dy).clampY()) }
        val builder = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, 120))
            .addStroke(GestureDescription.StrokeDescription(p2, 0, 120))
        dispatchGesture(builder.build(), null, null)
    }

    private fun adjustVolume(dir: String) {
        val direction = if (dir == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun focusedEditable(): AccessibilityNodeInfo? =
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    private fun setText(value: String) {
        val node = focusedEditable() ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun backspace() {
        val node = focusedEditable() ?: return
        val current = node.text?.toString().orEmpty()
        if (current.isNotEmpty()) setText(current.substring(0, current.length - 1))
    }

    private fun imeEnter() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val node = focusedEditable() ?: return
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    private fun performGlobal(action: String) {
        val code = when (action) {
            "home" -> GLOBAL_ACTION_HOME
            "back" -> GLOBAL_ACTION_BACK
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quicksettings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "sleep" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else return
            else -> return
        }
        performGlobalAction(code)
    }

    private fun Float.clampX() = coerceIn(0f, (cursor.width - 1).toFloat())
    private fun Float.clampY() = coerceIn(0f, (cursor.height - 1).toFloat())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        cursorView?.let { v -> runCatching { windowManager?.removeView(v) } }
        cursorView = null
        if (instance === this) instance = null
    }

    companion object {
        @Volatile
        var instance: RemoteAccessibilityService? = null
    }
}
