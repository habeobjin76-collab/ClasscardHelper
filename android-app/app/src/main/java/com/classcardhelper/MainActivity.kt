package com.classcardhelper

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    companion object {
        const val EXTRA_ACTION = "com.classcardhelper.extra.ACTION"
        const val ACTION_OPEN = "OPEN"
        const val ACTION_MEMORIZE = "MEMORIZE"
        const val ACTION_STOP = "STOP"
        const val ACTION_RECALL = "RECALL"
        const val ACTION_SPELL = "SPELL"
        private const val CLASSCARD_HOME = "https://www.classcard.net/"
    }

    private lateinit var webView: WebView
    private lateinit var statusLabel: TextView
    private lateinit var locationLabel: TextView
    private var controls: View? = null
    private var pendingAction: String? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 24, 39)
        window.navigationBarColor = Color.BLACK
        pendingAction = intent?.getStringExtra(EXTRA_ACTION)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)); setBackgroundColor(Color.rgb(17, 24, 39)) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "ClasscardHelper"; textSize = 18f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) }
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "접근성 설정"; isAllCaps = false; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
        header.addView(row)
        statusLabel = TextView(this).apply { text = "클래스카드에 직접 로그인하고 카드 화면에서 CH → 암기 시작을 누르세요."; textSize = 12f; setTextColor(Color.rgb(218, 226, 239)); setPadding(0, dp(4), 0, 0) }
        header.addView(statusLabel)
        locationLabel = TextView(this).apply { text = "주소: 클래스카드 홈"; textSize = 11f; setTextColor(Color.rgb(148, 163, 184)); setPadding(0, dp(3), 0, 0) }
        header.addView(locationLabel)
        root.addView(header)

        val content = FrameLayout(this)
        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase(Locale.ROOT)
                return scheme != "https" && scheme != "http"
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                locationLabel.text = "주소: " + (Uri.parse(url ?: CLASSCARD_HOME).host ?: "웹페이지")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                statusLabel.text = "로그인과 카드 선택은 직접 해 주세요. 자동화는 CH 메뉴에서만 시작됩니다."
                val action = pendingAction
                if (action != null) { pendingAction = null; webView.postDelayed({ performAction(action) }, 450) }
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) statusLabel.text = "페이지를 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
            }
        }
        content.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        buildControls(content)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        val restored = savedInstanceState?.let { webView.restoreState(it) }
        if (restored == null) webView.loadUrl(CLASSCARD_HOME)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); intent.getStringExtra(EXTRA_ACTION)?.let(::handleAction)
    }
    override fun onResume() {
        super.onResume(); if (::webView.isInitialized) webView.onResume()
        controls?.visibility = if (serviceEnabled()) View.GONE else View.VISIBLE
    }
    override fun onPause() { if (::webView.isInitialized) webView.onPause(); CookieManager.getInstance().flush(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { if (::webView.isInitialized) webView.saveState(outState); super.onSaveInstanceState(outState) }
    @Deprecated("Use WebView back navigation")
    override fun onBackPressed() { if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed() }
    override fun onDestroy() { if (::webView.isInitialized) { webView.stopLoading(); webView.destroy() }; super.onDestroy() }

    private fun buildControls(content: FrameLayout) {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; elevation = dp(8).toFloat() }
        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(dp(8), dp(8), dp(8), dp(8)); background = rounded(Color.rgb(24, 36, 58), dp(14)) }
        menu.addView(menuButton("암기 시작") { handleAction(ACTION_MEMORIZE) })
        menu.addView(menuButton("암기 정지") { handleAction(ACTION_STOP) })
        menu.addView(menuButton("회상") { handleAction(ACTION_RECALL) })
        menu.addView(menuButton("철자") { handleAction(ACTION_SPELL) })
        menu.addView(menuButton("클래스카드 홈") { webView.loadUrl(CLASSCARD_HOME) })
        wrapper.addView(menu)
        wrapper.addView(Button(this).apply {
            text = "CH"; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
            contentDescription = "CH 메뉴 열기"; background = rounded(Color.rgb(37, 99, 235), dp(28))
            setOnClickListener { menu.visibility = if (menu.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }, LinearLayout.LayoutParams(dp(64), dp(48)).apply { topMargin = dp(6) })
        content.addView(wrapper, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END; setMargins(0, dp(12), dp(12), 0)
        })
        controls = wrapper
    }
    private fun menuButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
        background = rounded(Color.rgb(44, 62, 89), dp(10)); minHeight = dp(42); minimumHeight = dp(42)
        setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(dp(176), dp(44)).apply { topMargin = dp(4) }
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun serviceEnabled(): Boolean {
        val expected = ComponentName(this, ClasscardAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { it.equals(expected, true) }
    }
    private fun handleAction(action: String) {
        if (!::webView.isInitialized) { pendingAction = action; return }
        if (webView.progress < 100 && (action == ACTION_MEMORIZE || action == ACTION_STOP)) {
            pendingAction = action; statusLabel.text = "페이지가 열린 뒤 요청을 처리할게요."; return
        }
        performAction(action)
    }
    private fun performAction(action: String) {
        when (action) {
            ACTION_OPEN -> if (webView.url.isNullOrBlank()) webView.loadUrl(CLASSCARD_HOME)
            ACTION_MEMORIZE -> startMemorize()
            ACTION_STOP -> stopMemorize()
            ACTION_RECALL -> { statusLabel.text = "회상 모드는 아직 준비 중입니다."; Toast.makeText(this, statusLabel.text, Toast.LENGTH_SHORT).show() }
            ACTION_SPELL -> { statusLabel.text = "철자 모드는 아직 준비 중입니다."; Toast.makeText(this, statusLabel.text, Toast.LENGTH_SHORT).show() }
        }
    }
    private fun classcardPage(): Boolean {
        val host = Uri.parse(webView.url ?: return false).host?.lowercase(Locale.ROOT) ?: return false
        return host == "classcard.net" || host.endsWith(".classcard.net")
    }
    private fun startMemorize() {
        if (!classcardPage()) {
            statusLabel.text = "클래스카드 웹페이지의 카드 화면에서 시작해 주세요."
            Toast.makeText(this, "클래스카드 웹페이지에서만 시작할 수 있어요.", Toast.LENGTH_LONG).show(); return
        }
        val script = assets.open("memorize.js").bufferedReader().use { it.readText() }
        statusLabel.text = "암기 시작 요청됨 · 페이지 안 진행 배너에서 상태를 확인하세요."
        webView.evaluateJavascript(script, null)
    }
    private fun stopMemorize() {
        webView.evaluateJavascript("window.__chMemorize && window.__chMemorize.stop && window.__chMemorize.stop(); 'stop-requested';", null)
        statusLabel.text = "암기 정지를 요청했어요."
    }
}
