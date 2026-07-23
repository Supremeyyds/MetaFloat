package com.metafloat.app.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.metafloat.app.settings.AppThemeMode

internal object DashboardWebViewFactory {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    ): WebView {
        val dashboardDirectory = DashboardInstaller.dashboardDirectory(context)
        val assetLoader = WebViewAssetLoader.Builder()
            .setHttpAllowed(true)
            .addPathHandler(
                "/zashboard/",
                WebViewAssetLoader.InternalStoragePathHandler(context, dashboardDirectory),
            )
            .build()

        val systemInDarkTheme = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val useDarkTheme = themeMode.usesDarkTheme(systemInDarkTheme)
        val pageCompatibilityScript = pageCompatibilityScript(useDarkTheme)
        return WebView(context).apply {
            setBackgroundColor(
                if (useDarkTheme) Color.rgb(29, 29, 31) else Color.WHITE,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = false
            }
            installCompatibilityScript(pageCompatibilityScript)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                ) = assetLoader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    view.postDelayed(
                        {
                            if (view.isAttachedToWindow) {
                                view.evaluateJavascript(pageCompatibilityScript, null)
                            }
                        },
                        VIEWPORT_UPDATE_DELAY_MILLIS,
                    )
                }
            }
        }
    }

    fun destroy(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun WebView.installCompatibilityScript(script: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                this,
                script,
                setOf(APP_ASSETS_ORIGIN),
            )
        }
    }

    private fun pageCompatibilityScript(useDarkTheme: Boolean): String {
        return dashboardThemeScript(useDarkTheme) + VIEWPORT_COMPATIBILITY_SCRIPT
    }

    private fun dashboardThemeScript(useDarkTheme: Boolean): String {
        val defaultTheme = if (useDarkTheme) "dark" else "light"
        val pageBackground = if (useDarkTheme) {
            DARK_PAGE_BACKGROUND_CSS
        } else {
            LIGHT_PAGE_BACKGROUND_CSS
        }
        return """
            (() => {
              const applyPageBackground = () => {
                const background = '$pageBackground';
                if (document.documentElement) {
                  document.documentElement.style.backgroundColor = background;
                }
                if (document.body) {
                  document.body.style.backgroundColor = background;
                }
              };
              applyPageBackground();
              document.addEventListener('DOMContentLoaded', applyPageBackground, { once: true });

              const themeValues = {
                'config/auto-theme': 'false',
                'config/default-theme': '$defaultTheme',
                'config/dark-theme': 'dark',
              };
              let changed = false;
              Object.entries(themeValues).forEach(([key, value]) => {
                if (localStorage.getItem(key) !== value) {
                  localStorage.setItem(key, value);
                  changed = true;
                }
              });
              if (changed && document.readyState === 'complete') {
                location.reload();
              }
            })();
        """
    }

    private const val APP_ASSETS_ORIGIN = "http://appassets.androidplatform.net"
    private const val VIEWPORT_UPDATE_DELAY_MILLIS = 150L
    private const val DARK_PAGE_BACKGROUND_CSS = "#1d1d1f"
    private const val LIGHT_PAGE_BACKGROUND_CSS = "#ffffff"
    private const val VIEWPORT_COMPATIBILITY_SCRIPT = """
        (() => {
          const applyViewportHeight = () => {
            const height = window.visualViewport?.height || window.innerHeight;
            if (height > 0 && document.documentElement) {
              const heightPx = height + 'px';
              document.documentElement.style.setProperty('--app-height', heightPx);
              document.documentElement.style.height = heightPx;
              if (document.body) {
                document.body.style.height = heightPx;
              }
            }
          };

          if (!window.__metaFloatViewportCompatibilityInstalled) {
            window.__metaFloatViewportCompatibilityInstalled = true;
            window.addEventListener('resize', applyViewportHeight, { passive: true });
            window.visualViewport?.addEventListener(
              'resize',
              applyViewportHeight,
              { passive: true },
            );
            document.addEventListener('DOMContentLoaded', applyViewportHeight, { once: true });
          }

          applyViewportHeight();
        })();
    """
}
