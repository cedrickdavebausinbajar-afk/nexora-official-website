package com.nexora.fit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val filePickerRequest = 4107
    private val backupSaveRequest = 4108
    private var pendingBackupContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.loadsImagesAutomatically = true
            settings.databaseEnabled = true
            addJavascriptInterface(BackupBridge(), "NexoraNative")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "file" || scheme == "about") return false
                    if (scheme == "https" && uri.host == "cedrickdavebausinbajar-afk.github.io") {
                        return false
                    }
                    if (scheme == "https" || scheme == "http") {
                        return try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    val acceptedTypes = fileChooserParams?.acceptTypes
                        ?.filter { it.isNotBlank() }
                        ?.toTypedArray()
                    val mimeType = acceptedTypes
                        ?.firstOrNull()
                        ?.substringBefore(';')
                        ?.takeIf { it.contains('/') } ?: "*/*"

                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mimeType
                        if (acceptedTypes != null && acceptedTypes.size > 1) {
                            putExtra(Intent.EXTRA_MIME_TYPES, acceptedTypes)
                            type = "*/*"
                        }
                    }

                    return try {
                        startActivityForResult(
                            Intent.createChooser(intent, "Choose a file"),
                            filePickerRequest
                        )
                        true
                    } catch (_: Exception) {
                        this@MainActivity.filePathCallback?.onReceiveValue(null)
                        this@MainActivity.filePathCallback = null
                        false
                    }
                }
            }

            if (savedInstanceState == null) {
                loadUrl("file:///android_asset/fit/index.html")
            } else {
                restoreState(savedInstanceState)
                if (url.isNullOrBlank()) loadUrl("file:///android_asset/fit/index.html")
            }
        }
        setContentView(webView)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == filePickerRequest) {
            val result = if (resultCode == RESULT_OK) {
                data?.data?.let { arrayOf(it) }
            } else null
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        } else if (requestCode == backupSaveRequest) {
            val content = pendingBackupContent
            pendingBackupContent = null
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (content != null && uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open the selected file")
                    Toast.makeText(this, "NEXORA FIT backup saved", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Could not save backup. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inner class BackupBridge {
        @JavascriptInterface
        fun exportBackup(fileName: String, content: String) {
            runOnUiThread {
                if (!::webView.isInitialized || webView.url?.startsWith("file:///android_asset/fit/") != true) {
                    Toast.makeText(this@MainActivity, "Backup export is available inside NEXORA FIT", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                pendingBackupContent = content
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, fileName.ifBlank { "nexora-fit-backup.json" })
                }
                try {
                    startActivityForResult(intent, backupSaveRequest)
                } catch (_: Exception) {
                    pendingBackupContent = null
                    Toast.makeText(this@MainActivity, "No file picker is available", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("NexoraNative")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }
}
