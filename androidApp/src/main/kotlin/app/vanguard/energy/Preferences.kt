package app.vanguard.energy

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.*

class AndroidPreferenceStore(context: Context) : PreferenceStore {
    private val preferences = context.getSharedPreferences("vanguard-native-v1", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString("preferences", null)
    override fun write(value: String): Boolean = preferences.edit().putString("preferences", value).commit()
}

/** A one-time, invisible import from Capacitor's private https://localhost origin.
 * No bridge, remote navigation, file access, or network requests are permitted.
 * A failed import is retried on the next launch rather than marking it migrated.
 */
@Suppress("SetJavaScriptEnabled")
internal fun readLegacyPreferences(context: Context, complete: (Pair<String?, String?>?) -> Unit): () -> Unit {
    // Fresh installations need no WebView at all.
    if (!java.io.File(context.applicationInfo.dataDir, "app_webview").isDirectory) {
        complete(null to null)
        return {}
    }
    val handler = Handler(Looper.getMainLooper())
    var finished = false
    var webView: WebView? = null
    lateinit var timeout: Runnable
    fun finish(value: Pair<String?, String?>?, notify: Boolean = true) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        if (notify) complete(value)
    }
    timeout = Runnable { finish(null) }
    handler.postDelayed(timeout, 5000)
    try {
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = true
                override fun onPageFinished(view: WebView, url: String) {
                    if (finished) return
                    view.evaluateJavascript("""
                        (function() {
                          try { return JSON.stringify({board:localStorage.getItem('vanguard-energy-v1'),table:localStorage.getItem('vanguard-tabletop-v2')}); }
                          catch(e) { return null; }
                        })()
                    """.trimIndent()) { result ->
                        val legacy = try {
                            if (result.length > 131_072) null else {
                                val raw = (Json.parseToJsonElement(result) as? JsonPrimitive)?.contentOrNull
                                val obj = raw?.let { Json.parseToJsonElement(it) as? JsonObject }
                                obj?.let { (it["board"] as? JsonPrimitive)?.contentOrNull to (it["table"] as? JsonPrimitive)?.contentOrNull }
                            }
                        } catch (_: Exception) { null }
                        finish(legacy)
                    }
                }
            }
            loadDataWithBaseURL("https://localhost/", "<!doctype html><html><body></body></html>", "text/html", "UTF-8", null)
        }
    } catch (_: Exception) { finish(null) }
    return { finish(null, notify = false) }
}
