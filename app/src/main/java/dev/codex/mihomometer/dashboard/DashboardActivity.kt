package dev.codex.mihomometer.dashboard

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import dev.codex.mihomometer.R
import dev.codex.mihomometer.settings.AppThemeMode

class DashboardActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarColors: DashboardToolbarColors

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        toolbarColors = resolveToolbarColors()
        applySystemBarColors(toolbarColors)

        val dashboardDir = DashboardInstaller.dashboardDirectory(this)
        if (!dashboardDir.resolve("index.html").isFile) {
            Toast.makeText(this, R.string.dashboard_not_installed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .setHttpAllowed(true)
            .addPathHandler(
                "/zashboard/",
                WebViewAssetLoader.InternalStoragePathHandler(this, dashboardDir),
            )
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowContentAccess = true
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ) = assetLoader.shouldInterceptRequest(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                title = getString(R.string.dashboard_title)
            }
        }

        val contentView = buildContentView()
        setContentView(contentView)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            toolbar.setPadding(dp(8), statusBars.top, dp(16), 0)
            toolbar.layoutParams = toolbar.layoutParams.apply {
                height = dp(56) + statusBars.top
            }
            view.setPadding(0, 0, 0, navigationBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(contentView)

        webView.loadUrl(
            intent.getStringExtra(EXTRA_DASHBOARD_URL) ?: DEFAULT_DASHBOARD_URL,
        )
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(toolbarColors.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        toolbar = buildToolbar()
        root.addView(toolbar)
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    private fun buildToolbar(): LinearLayout {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(16), 0)
            setBackgroundColor(toolbarColors.background)
        }
        toolbar.addView(
            ImageButton(this).apply {
                setImageResource(R.drawable.ic_arrow_back_24)
                setColorFilter(toolbarColors.foreground)
                background = null
                contentDescription = getString(R.string.action_back)
                setOnClickListener { finish() }
            },
            LinearLayout.LayoutParams(dp(48), dp(48)),
        )
        toolbar.addView(
            TextView(this).apply {
                text = getString(R.string.dashboard_title)
                textSize = 18f
                setTextColor(toolbarColors.foreground)
                gravity = Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        toolbar.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56),
        )
        return toolbar
    }

    private fun resolveToolbarColors(): DashboardToolbarColors {
        val themeMode = AppThemeMode.fromStorageValue(intent.getStringExtra(EXTRA_THEME_MODE))
        val useDarkTheme = when (themeMode) {
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
        return if (useDarkTheme) {
            DashboardToolbarColors(
                background = Color.rgb(16, 18, 20),
                foreground = Color.WHITE,
                lightStatusBar = false,
            )
        } else {
            DashboardToolbarColors(
                background = Color.rgb(247, 248, 250),
                foreground = Color.rgb(23, 26, 31),
                lightStatusBar = true,
            )
        }
    }

    private fun isSystemInDarkTheme(): Boolean {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun applySystemBarColors(colors: DashboardToolbarColors) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = colors.background
            window.navigationBarColor = colors.background
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = colors.lightStatusBar
            isAppearanceLightNavigationBars = colors.lightStatusBar
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_DASHBOARD_URL = "dashboard_url"
        const val EXTRA_THEME_MODE = "theme_mode"
        private const val DEFAULT_DASHBOARD_URL =
            "http://appassets.androidplatform.net/zashboard/index.html"
    }
}

private data class DashboardToolbarColors(
    val background: Int,
    val foreground: Int,
    val lightStatusBar: Boolean,
)
