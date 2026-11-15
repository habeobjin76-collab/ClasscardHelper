package com.classcardhelper

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ClasscardAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    private fun showOverlay() {
        if (overlay != null) return

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF18243A.toInt())
                cornerRadius = dp(14).toFloat()
            }
        }

        val heading = TextView(this).apply {
            text = "CH"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        card.addView(heading, LinearLayout.LayoutParams(dp(64), dp(44)))

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        listOf("Memorize", "Recall", "Spell").forEach { mode ->
            menu.addView(Button(this).apply {
                text = mode
                contentDescription = "Select $mode"
                setOnClickListener {
                    heading.text = "CH · $mode"
                    menu.visibility = View.GONE
                    Toast.makeText(this@ClasscardAccessibilityService, "$mode selected", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(dp(160), dp(48)))
        }
        card.addView(menu)

        heading.setOnClickListener {
            menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(96)
        }

        windowManager?.addView(card, params)
        overlay = card
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen content is intentionally not inspected or acted upon.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        overlay?.let { view -> windowManager?.removeView(view) }
        overlay = null
        windowManager = null
        super.onDestroy()
    }
}
