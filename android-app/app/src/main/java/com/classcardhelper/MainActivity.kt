package com.classcardhelper

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var serviceStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
        }, matchWidth())

        content.addView(TextView(this).apply {
            text = "Enable the accessibility service yourself to show the CH button over other apps. The service does not read screen content or control other apps."
            textSize = 16f
            setPadding(0, padding / 2, 0, padding / 2)
        }, matchWidth())

        serviceStatus = TextView(this).apply { textSize = 15f }
        content.addView(serviceStatus, matchWidth())

        content.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, matchWidth())

        setContentView(content)
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, ClasscardAccessibilityService::class.java).flattenToString()
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                ComponentName(service.packageName, service.name).flattenToString() == expected
            }
        serviceStatus.text = if (enabled) {
            "Service is enabled. Tap CH to open the menu."
        } else {
            "Service is off. Turn on ClasscardHelper in Accessibility settings when you choose to use it."
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
