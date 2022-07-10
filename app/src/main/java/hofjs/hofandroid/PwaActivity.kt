package hofjs.hofandroid

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.Window
import android.webkit.*
import android.webkit.WebView.WebViewTransport
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection

open class PwaActivity: AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    // Origin needs to be set to domain instead of *, because fetch option "credentials: include"
    // does not work with * and is required to pass cookies such as login cookies to server
    private val corsHeaders = mapOf(
        "Access-Control-Allow-Origin" to "https://${WebViewAssetLoader.DEFAULT_DOMAIN}",
        "Access-Control-Allow-Credentials" to "true",
        "Access-Control-Allow-Headers" to "Content-Type, Authorization"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide action bar
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()

        // Create WebViewAssetLoader to load local app resources
        // with path "/" referencing root of assets folder
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // Create webview with pwa specific settings and set webview as content of activity
        webView = createPwaWebView(this)
        setContentView(webView)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Delegate back button actions to webview
        // if webview history is available
        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack())
                webView.goBack()
            else
                finish()

            return true
        }

        // Default handling for other actions than KeyEvent.ACTION_DOWN
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("JavascriptInterface")
    fun setContentUrl(relativeUrl: String, pwaInterfaceName: String = this::class.java.simpleName) {
        // Load application start url
        webView.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/${relativeUrl}")

        // Add JavaScript interface
        webView.addJavascriptInterface(this, pwaInterfaceName)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPwaWebView(context: Context): WebView {
        // Enable inspecting of running app of device/emulator
        // by using chrome://inspect on developer machine
        if (BuildConfig.DEBUG)
            WebView.setWebContentsDebuggingEnabled(true)

        // Create webview
        val webView = WebView(context)

        // Enable JavaScript and DOMStorage
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // Use custom web client to load local assets with WebViewAssetLoader and
        // remote assets with cors headers
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                loadUrl(request.url)

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
        }

        // Support links with target="_blank" to open in new browser window
        webView.settings.setSupportMultipleWindows(true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, dialog: Boolean, userGesture: Boolean,
                                        resultMsg: Message) = loadUrlInNewWindow(resultMsg)
        }

        return webView
    }

    private fun loadUrl(url: Uri) =
        if (url.toString().startsWith("https://${WebViewAssetLoader.DEFAULT_DOMAIN}"))
            assetLoader.shouldInterceptRequest(url)
        else
            loadCorsRequest(url)

    private fun loadUrlInNewWindow(loadUrlMessage: Message): Boolean {
        // From Android 10 regular approach to get url via view.getHitTestResult().getExtra() no
        // longer works, because null is returned, therefore create second webview to intercept url
        val newWebView = WebView(webView.context)
        val transport = loadUrlMessage.obj as WebViewTransport
        transport.webView = newWebView
        loadUrlMessage.sendToTarget()

        newWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.url != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, (request.url))
                    newWebView.context.startActivity(browserIntent)
                }
                return false
            }
        }
        return true
    }

    private fun loadCorsRequest(url: Uri): WebResourceResponse {
        var inputStream: InputStream? = null

        // Load remote url
        try {
            inputStream = URL(url.toString()).openConnection().getInputStream()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Detect content type
        val contentType: String = URLConnection.guessContentTypeFromName(url.path) ?: "application/javascript"

        // Patch response header to support cors requests
        val response = WebResourceResponse(contentType, "utf-8", inputStream)
        response.responseHeaders = corsHeaders

        return response
    }
}