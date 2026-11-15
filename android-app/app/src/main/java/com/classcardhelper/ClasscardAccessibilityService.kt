package com.classcardhelper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class ClasscardAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }
    private fun showOverlay() {
        if (overlay != null) return
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; elevation = dp(10).toFloat() }
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(8), dp(8), dp(8), dp(8)); background = rounded(Color.rgb(24, 36, 58), dp(14)) }
        menu.addView(menuButton("ClasscardHelper 열기") { openHelper(MainActivity.ACTION_OPEN) })
        menu.addView(menuButton("암기 시작") { openHelper(MainActivity.ACTION_MEMORIZE) })
        menu.addView(menuButton("암기 정지") { openHelper(MainActivity.ACTION_STOP) })
        menu.addView(menuButton("회상") { openHelper(MainActivity.ACTION_RECALL) })
        menu.addView(menuButton("철자") { openHelper(MainActivity.ACTION_SPELL) })
        menu.addView(menuButton("접근성 설정") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        container.addView(menu)
        val toggle = TextView(this).apply {
            text = "CH"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            contentDescription = "CH 메뉴 열기"; background = rounded(Color.rgb(37, 99, 235), dp(28))
            setOnClickListener { menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }
        container.addView(toggle, LinearLayout.LayoutParams(dp(64), dp(48)).apply { topMargin = dp(6) })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(72) }
        windowManager?.addView(container, params)
        overlay = container
    }
    private fun menuButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
        background = rounded(Color.rgb(44, 62, 89), dp(10)); minHeight = dp(42); minimumHeight = dp(42)
        setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(184), dp(44)).apply { topMargin = dp(4) }
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun openHelper(action: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_ACTION, action)
        })
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally does not inspect screen text, nodes, or other apps' content.
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        overlay?.let { try { windowManager?.removeView(it) } catch (_: IllegalArgumentException) { } }
        overlay = null
        super.onDestroy()
    }
}
