package com.example.wawrapper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("wa", MODE_PRIVATE) }
    private var pendingWebPermission: PermissionRequest? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var docStartSupported = false
    private lateinit var script: String

    // WhatsApp Web only loads for desktop browsers
    private val desktopUA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    private fun androidPermFor(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        else -> null
    }

    private fun isGranted(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val req = pendingWebPermission ?: return@registerForActivityResult
            pendingWebPermission = null
            grantWhatIsAllowed(req)
        }

    private val upfrontLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askBatteryExemptionOnce()
        }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
            )
            fileCallback = null
        }

    private fun grantWhatIsAllowed(req: PermissionRequest) {
        val ok = req.resources.filter { r ->
            val p = androidPermFor(r)
            p != null && isGranted(p)
        }
        if (ok.isEmpty()) req.deny() else req.grant(ok.toTypedArray())
    }

    private fun configJson(): String = org.json.JSONObject()
        .put("out", prefs.getString("c_out", "") ?: "")
        .put("inn", prefs.getString("c_in", "") ?: "")
        .put("bg", prefs.getString("c_bg", "") ?: "")
        .put("simple", prefs.getBoolean("simple", true))
        .toString()

    private fun buildScript() =
        assets.open("inject.js").bufferedReader().use { it.readText() }
            .replace("__CONFIG__", configJson())

    private fun askBatteryExemptionOnce() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (prefs.getBoolean("asked_battery", false) || pm.isIgnoringBatteryOptimizations(packageName)) return
        prefs.edit().putBoolean("asked_battery", true).apply()
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Notifier.ensureChannels(this)
        script = buildScript()

        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = desktopUA
            // Use the phone's real width so WhatsApp Web shows a readable single-pane layout
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.addJavascriptInterface(Bridge(applicationContext), "AndroidBridge")

        docStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStartSupported) {
            WebViewCompat.addDocumentStartJavaScript(web, script, setOf("https://web.whatsapp.com"))
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: return true
                return if (host.endsWith("whatsapp.com") || host.endsWith("whatsapp.net")) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!docStartSupported) view.evaluateJavascript(script, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                CookieManager.getInstance().flush()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needed = request.resources
                        .mapNotNull { androidPermFor(it) }
                        .filter { !isGranted(it) }
                        .distinct()
                    if (needed.isEmpty()) {
                        grantWhatIsAllowed(request)
                    } else {
                        pendingWebPermission = request
                        permLauncher.launch(needed.toTypedArray())
                    }
                }
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    fileLauncher.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        // Back never closes the app: it goes back inside WhatsApp or sends the app to the background
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
            }
        })

        // Keeps the connection alive in the background so notifications keep arriving
        ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))

        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { !isGranted(it) }
        if (wanted.isEmpty()) askBatteryExemptionOnce() else upfrontLauncher.launch(wanted.toTypedArray())

        web.loadUrl("https://web.whatsapp.com/", mapOf("Accept-Language" to "es-AR,es;q=0.9"))
    }

    override fun onResume() {
        super.onResume()
        // Apply the chat colors chosen in the "Colores" screen
        web.evaluateJavascript("window.__applyConfig && window.__applyConfig(" + configJson() + ")", null)
    }
}
