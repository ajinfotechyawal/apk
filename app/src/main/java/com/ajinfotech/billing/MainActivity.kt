package com.ajinfotech.billing

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * Hosts the Billing & Ledger web app (assets/index.html) inside a WebView and adds the native bits a
 * browser would normally give: file saving to Downloads, share sheet (WhatsApp etc.), printing, file picker.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var printView: WebView? = null
    private var lastBack = 0L

    private val host = "appassets.androidplatform.net"

    private val bridgeJs: String by lazy { assets.open("bridge.js").bufferedReader().use { it.readText() } }

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCallback
        fileCallback = null
        cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = FrameLayout(this)
        root.setBackgroundColor(0xFFF3F5F9.toInt())
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        // keep the page clear of the status bar / navigation bar / keyboard
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true

        setupWebView()
        web.loadUrl("https://$host/assets/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) finish() else {
                    lastBack = now
                    toast("Band karne ke liye dobara back dabao")
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true            // localStorage (all company data) + IndexedDB (offline cache)
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
        }
        web.addJavascriptInterface(Bridge(), "AndroidApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == host) return false
                openExternal(request.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (Uri.parse(url).host == host) view.evaluateJavascript(bridgeJs, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    chooser.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun safeName(name: String): String {
        val n = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { "file" }
        return n.take(120)
    }

    private fun openExternal(url: String) {
        try {
            val u = Uri.parse(url)
            val scheme = u.scheme ?: ""
            if (scheme !in listOf("https", "http", "tel", "mailto", "sms", "whatsapp")) return
            startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("Is link ke liye koi app nahi mili")
        }
    }

    private fun saveToDownloads(bytes: ByteArray, name: String, mime: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AJ Billing")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("insert failed")
        contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IllegalStateException("open failed")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        return "Downloads/AJ Billing/$name"
    }

    /** Functions the web page can call (see assets/bridge.js). Only our own bundled page is ever loaded. */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(b64: String, name: String, mime: String) {
            try {
                val where = saveToDownloads(Base64.decode(b64, Base64.DEFAULT), safeName(name), mime)
                runOnUiThread { toast("Save ho gaya: $where") }
            } catch (e: Exception) {
                runOnUiThread { toast("File save nahi ho payi") }
            }
        }

        @JavascriptInterface
        fun shareFile(b64: String, name: String, mime: String, text: String): Boolean {
            return try {
                val dir = File(cacheDir, "shared").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, safeName(name))
                f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime.ifBlank { "application/octet-stream" }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "Share")) }
                true
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun shareText(text: String) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            runOnUiThread { startActivity(Intent.createChooser(send, "Share")) }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread { openExternal(url) }
        }

        @JavascriptInterface
        fun printHtml(html: String) {
            runOnUiThread {
                val wv = WebView(this@MainActivity)
                printView = wv                      // keep a reference until printing is handed over
                var started = false
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (started) return
                        started = true
                        val pm = getSystemService(PRINT_SERVICE) as PrintManager
                        val attrs = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        pm.print("Bill", view.createPrintDocumentAdapter("Bill"), attrs)
                    }
                }
                wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        }

        @JavascriptInterface
        fun printPage() {
            runOnUiThread {
                val pm = getSystemService(PRINT_SERVICE) as PrintManager
                pm.print("Page", web.createPrintDocumentAdapter("Page"), PrintAttributes.Builder().build())
            }
        }

        /** Keeps status-bar colour/icons in step with the app's light/dark mode. */
        @JavascriptInterface
        fun setTheme(dark: Boolean) {
            runOnUiThread {
                root.setBackgroundColor(if (dark) 0xFF0A101E.toInt() else 0xFFF3F5F9.toInt())
                val c = WindowCompat.getInsetsController(window, root)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    override fun onDestroy() {
        printView = null
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
