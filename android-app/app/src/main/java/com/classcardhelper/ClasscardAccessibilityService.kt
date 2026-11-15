package com.classcardhelper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.roundToInt

class ClasscardAccessibilityService : AccessibilityService() {
    companion object { private const val CLASSCARD_PACKAGE = "classcard.net" }
    private enum class Stage { WAITING_FOR_CARD, WAITING_FOR_KNOWN, WAITING_FOR_NEXT }

    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var statusView: TextView? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var running = false
    private var stage = Stage.WAITING_FOR_CARD
    private var stageStartedAt = 0L
    private var lastEventAt = 0L
    private var handledCards = 0
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val root = rootInActiveWindow
            if (root == null) {
                setStatus("클래스카드 화면을 기다리는 중")
                handler.postDelayed(this, 900)
                return
            }
            if (root.packageName?.toString() != CLASSCARD_PACKAGE) {
                root.recycle()
                setStatus("클래스카드 앱으로 돌아간 뒤 다시 시작해 주세요")
                handler.postDelayed(this, 900)
                return
            }
            try { runStep(root) } finally { root.recycle() }
            if (running) handler.postDelayed(this, 650)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
        setStatus("준비됨 · 앱 암기는 CH에서 직접 시작")
    }

    private fun showOverlay() {
        if (overlay != null) return
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; elevation = dp(10).toFloat() }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(dp(8), dp(8), dp(8), dp(8)); background = rounded(Color.rgb(24, 36, 58), dp(14))
        }
        statusView = TextView(this).apply {
            text = "준비됨"; textSize = 12f; setTextColor(Color.WHITE)
            setPadding(dp(8), dp(6), dp(8), dp(6)); maxWidth = dp(200)
        }
        menu.addView(statusView)
        menu.addView(menuButton("클래스카드 앱 열기") { openClasscardApp() })
        menu.addView(menuButton("앱 암기 시작") { startNativeMemorize() })
        menu.addView(menuButton("앱 암기 정지") { stopNativeMemorize("사용자가 정지함") })
        menu.addView(menuButton("웹 암기 시작") { openHelper(MainActivity.ACTION_MEMORIZE) })
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
        setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(196), dp(44)).apply { topMargin = dp(4) }
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun setStatus(message: String) { handler.post { statusView?.text = message } }

    private fun openClasscardApp() {
        val launch = packageManager.getLaunchIntentForPackage(CLASSCARD_PACKAGE)
        if (launch == null) setStatus("클래스카드 앱을 찾지 못했어요")
        else startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun openHelper(action: String) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_ACTION, action)
        })
    }

    private fun startNativeMemorize() {
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != CLASSCARD_PACKAGE) {
            root?.recycle()
            setStatus("클래스카드 앱의 암기 카드 화면에서 시작해 주세요")
            return
        }
        root.recycle()
        running = true
        handledCards = 0
        stage = Stage.WAITING_FOR_CARD
        stageStartedAt = System.currentTimeMillis()
        handler.removeCallbacks(ticker)
        setStatus("앱 암기 실행 중 · CH에서 정지 가능")
        handler.post(ticker)
    }

    private fun stopNativeMemorize(reason: String) {
        handler.removeCallbacks(ticker)
        val wasRunning = running
        running = false
        setStatus(if (wasRunning) "$reason · ${handledCards}장 처리" else "앱 암기가 실행 중이 아닙니다")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || event?.packageName?.toString() != CLASSCARD_PACKAGE) return
        val now = System.currentTimeMillis()
        if (now - lastEventAt < 350) return
        lastEventAt = now
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun runStep(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        when (stage) {
            Stage.WAITING_FOR_CARD -> {
                val known = findClickable(root) { label -> isKnownLabel(label) }
                if (known != null) {
                    val clicked = known.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    known.recycle()
                    if (!clicked) { stopNativeMemorize("알고 있어요 버튼을 누르지 못했어요"); return }
                    handledCards += 1
                    stage = Stage.WAITING_FOR_NEXT
                    stageStartedAt = now
                    setStatus("앱 암기 중 · ${handledCards}장 처리")
                    return
                }
                val next = findClickable(root) { label -> isNextLabel(label) }
                if (next == null) {
                    setStatus("클래스카드의 암기 카드 화면을 열어 주세요")
                    return
                }
                next.recycle()
                if (now - stageStartedAt < 550) return
                if (!tapCardCenter(root)) { stopNativeMemorize("카드 중앙을 누르지 못했어요"); return }
                stage = Stage.WAITING_FOR_KNOWN
                stageStartedAt = now
                setStatus("카드를 넘겼어요 · 알고 있어요 버튼을 기다리는 중")
            }
            Stage.WAITING_FOR_KNOWN -> {
                val known = findClickable(root) { label -> isKnownLabel(label) }
                if (known != null) {
                    val clicked = known.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    known.recycle()
                    if (!clicked) { stopNativeMemorize("알고 있어요 버튼을 누르지 못했어요"); return }
                    handledCards += 1
                    stage = Stage.WAITING_FOR_NEXT
                    stageStartedAt = now
                    setStatus("앱 암기 중 · ${handledCards}장 처리")
                } else if (now - stageStartedAt > 4500) {
                    stopNativeMemorize("알고 있어요 버튼을 찾지 못했어요")
                }
            }
            Stage.WAITING_FOR_NEXT -> {
                val next = findClickable(root) { label -> isNextLabel(label) }
                if (next != null) {
                    val clicked = next.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    next.recycle()
                    if (!clicked) { stopNativeMemorize("다음 카드 버튼을 누르지 못했어요"); return }
                    stage = Stage.WAITING_FOR_CARD
                    stageStartedAt = now
                    setStatus("다음 카드로 이동 중 · ${handledCards}장 처리")
                } else if (now - stageStartedAt > 4500) {
                    stopNativeMemorize("다음 카드 버튼을 찾지 못했어요")
                }
            }
        }
    }

    private fun isKnownLabel(label: String): Boolean =
        label.contains("알고 있어요") || label.contains("알고있어요") || label.contains("알아요") ||
            label.contains("btn_kown_o") || label.contains("btn_known_o")

    private fun isNextLabel(label: String): Boolean =
        label.contains("btnnextcard2") || label.contains("다음 카드") || label.contains("다음") ||
            label.contains("next card") || label.contains("next")

    private fun nodeLabel(node: AccessibilityNodeInfo): String = listOfNotNull(
        node.text?.toString(), node.contentDescription?.toString(), node.viewIdResourceName
    ).joinToString(" ").lowercase(Locale.ROOT)

    private fun findClickable(root: AccessibilityNodeInfo, matches: (String) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (matches(nodeLabel(node))) {
                val target = if (node.isClickable && node.isEnabled) AccessibilityNodeInfo.obtain(node) else clickableAncestor(node)
                if (target != null) {
                    node.recycle()
                    while (queue.isNotEmpty()) queue.removeFirst().recycle()
                    return target
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
            node.recycle()
        }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable && parent.isEnabled) return parent
            val next = parent.parent
            parent.recycle()
            parent = next
            depth++
        }
        parent?.recycle()
        return null
    }

    private fun tapCardCenter(root: AccessibilityNodeInfo): Boolean {
        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)
        if (rootBounds.isEmpty) return false
        val centerX = rootBounds.centerX()
        val centerY = rootBounds.centerY()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(AccessibilityNodeInfo.obtain(root))
        var candidate: AccessibilityNodeInfo? = null
        var bestArea = Long.MAX_VALUE
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isClickable && node.isEnabled) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.contains(centerX, centerY)) {
                    val area = bounds.width().toLong() * bounds.height().toLong()
                    if (area < bestArea) {
                        candidate?.recycle()
                        candidate = AccessibilityNodeInfo.obtain(node)
                        bestArea = area
                    }
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
            node.recycle()
        }
        if (candidate != null) {
            val clicked = candidate!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            candidate!!.recycle()
            if (clicked) return true
        }
        val path = Path().apply { moveTo(centerX.toFloat(), centerY.toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 90)).build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() { stopNativeMemorize("접근성 서비스가 중단됨") }

    override fun onDestroy() {
        stopNativeMemorize("서비스 종료")
        overlay?.let { try { windowManager?.removeView(it) } catch (_: IllegalArgumentException) { } }
        overlay = null
        super.onDestroy()
    }
}
