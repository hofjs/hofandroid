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
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLConnection
import java.util.concurrent.TimeUnit

open class PwaActivity: AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private val httpClient = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val fetchDetails: MutableMap<String, String> = mutableMapOf()

    // Origin needs to be set to domain instead of *, because fetch option "credentials: include"
    // does not work with * and is required to pass cookies such as login cookies to server
    private val corsHeaders = mapOf(
        "access-control-allow-origin" to "https://${WebViewAssetLoader.DEFAULT_DOMAIN}",
        "access-control-allow-credentials" to "true",
        "access-control-allow-headers" to "Content-Type, Authorization"
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
                loadUrl(request)

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("geo:"))
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

                return true
            }
        }

        // Support links with target="_blank" to open in new browser window
        webView.settings.setSupportMultipleWindows(true)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, dialog: Boolean, userGesture: Boolean,
                                        resultMsg: Message) = loadUrlInNewWindow(resultMsg)
        }

        return webView
    }

    @JavascriptInterface
    fun addFetchDetails(url: String, body: String) {
        fetchDetails[url] = body
    }

    private fun loadUrl(request: WebResourceRequest): WebResourceResponse? {
        if (request.url.toString().startsWith("https://${WebViewAssetLoader.DEFAULT_DOMAIN}"))
            return loadAssetRequest(request)
        else
            return loadCorsRequest(request)
    }


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
                return true
            }
        }
        return true
    }

    private fun loadAssetRequest(request: WebResourceRequest): WebResourceResponse? {
        val response = assetLoader.shouldInterceptRequest(request.url)

        // Support js modules mime type on Android API level < 30
        if (request.url.toString().endsWith(".js") && response?.mimeType == "text/plain")
            response.mimeType = "application/javascript"

        return response
    }

    private fun loadCorsRequest(request: WebResourceRequest): WebResourceResponse? {
        val urlWithRequestId = request.url.toString()
        val url = urlWithRequestId.split("###uniqueRequestId")[0]

        val method = request.method

        val headers = request.requestHeaders.run {
            val result = Headers.Builder()
            this.forEach { result.add(it.key.lowercase(), it.value) }
            result.build()
        }
        val body = if ((method == "POST" || method == "PUT") && fetchDetails.containsKey(urlWithRequestId))
            fetchDetails[urlWithRequestId]?.toRequestBody() else null

        try {
            val request = Request.Builder()
                .url(url)
                .method(method, body)
                .headers(headers)
                .build()
            httpClient.newCall(request).execute()
                .let { response: Response ->
                    val responseHeaders = response.headers.toMap()

                    // Detect content type
                    val contentType: String =
                        URLConnection.guessContentTypeFromName(url)
                            ?: "application/javascript"

                    // Patch response header to support cors requests
                    val response =
                        WebResourceResponse(contentType, "utf-8", response.body.byteStream())
                    response.responseHeaders = responseHeaders + corsHeaders

                    return response
                }
        }
        catch (e: Exception) {
            e.printStackTrace()

            return null
        }
    }
}