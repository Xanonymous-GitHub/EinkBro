package de.baumann.browser.browser

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.webkit.WebView.*
import de.baumann.browser.database.FaviconInfo
import de.baumann.browser.unit.HelperUnit
import de.baumann.browser.view.NinjaWebView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class NinjaWebChromeClient(
    private val ninjaWebView: NinjaWebView,
    private val onReceiveFavicon: (Bitmap) -> Unit
) : WebChromeClient() {
    private lateinit var webviewParent: ViewGroup

    override fun onCreateWindow(view: WebView, dialog: Boolean, userGesture: Boolean, resultMsg: Message): Boolean {
            val newWebView = WebView(view.context).apply { initWebView(this) }
        newWebView.webViewClient = object : WebViewClient() {
            private var isUrlProcessed = false

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlString = request?.url?.toString() ?: return false

                // handle login requests
                return if (isGoogleLoginUrl(urlString) || isFacebookLoginUrl(urlString)) {
                    view?.loadUrl(urlString)
                    true
                } else if (!isUrlProcessed) {
                    request.url?.let {
                        handleWebViewLinks(urlString)
                        isUrlProcessed = true
                    } // you can get your target url here
                    false
                } else false
            }
        }
        newWebView.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                webviewParent.removeView(window)
            }
        }
        webviewParent = ninjaWebView.parent as ViewGroup
        webviewParent.addView(newWebView)

        val transport = resultMsg.obj as WebViewTransport
        transport.webView = newWebView
        resultMsg.sendToTarget()
        return true
    }

    private fun isGoogleLoginUrl(url: String): Boolean {
        return url.contains("accounts.google.com");
    }

    private fun isFacebookLoginUrl(url: String): Boolean {
        return url.contains("facebook") && !url.contains("story") && !url.contains("l.php")
    }

    private fun initWebView(webView: WebView)  {
        val webSettings = webView.settings
        val defaultUserAgent = webSettings.userAgentString

        webSettings.userAgentString = defaultUserAgent.replace("wv", "")
        webSettings.setAppCacheEnabled(true)
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.allowFileAccessFromFileURLs = true
        webSettings.allowUniversalAccessFromFileURLs = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        val manager = CookieManager.getInstance()
        manager.setAcceptThirdPartyCookies(webView, true)

        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH)
    }

    override fun onCloseWindow(window: WebView?) {
        (ninjaWebView.parent as ViewGroup).removeView(window)
    }

    private fun handleWebViewLinks(url: String) = ninjaWebView.browserController?.addNewTab(url)

    override fun onProgressChanged(view: WebView, progress: Int) {
        super.onProgressChanged(view, progress)
        ninjaWebView.update(progress)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        super.onReceivedTitle(view, title)
        // prevent setting title for data: contents
        if (!title.startsWith("data:text")) {
            ninjaWebView.update(title)
        }
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        ninjaWebView.browserController?.onShowCustomView(view, callback)
        super.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        ninjaWebView.browserController?.onHideCustomView()
        super.onHideCustomView()
    }

    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
        ninjaWebView.browserController?.showFileChooser(filePathCallback)
        return true
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        val activity = ninjaWebView.context as Activity
        HelperUnit.grantPermissionsLoc(activity)
        callback.invoke(origin, true, false)
        super.onGeolocationPermissionsShowPrompt(origin, callback)
    }

    private val posterBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            .apply { setPixel(0, 0, Color.argb(0, 255, 255, 255)) }

    override fun getDefaultVideoPoster(): Bitmap? = posterBitmap

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        val bitmap = icon ?: return
        onReceiveFavicon(bitmap)
    }
}