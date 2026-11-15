package com.nexora.fit

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.os.Build
import org.json.JSONObject
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import android.database.Cursor
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import java.util.concurrent.Executors
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val filePickerRequest = 4107
    private val backupSaveRequest = 4108
    private var pendingBackupContent: String? = null
    private lateinit var assetLoader: WebViewAssetLoader
    private val bridgeExecutor = Executors.newSingleThreadExecutor()
    private val appOrigin = "https://appassets.androidplatform.net"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        assetLoader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.loadsImagesAutomatically = true
            settings.databaseEnabled = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
            

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                private fun handleNavigation(uri: Uri, isMainFrame: Boolean = true): Boolean {
                    // Leave iframe/resource navigation inside WebView; only route top-level links externally.
                    if (!isMainFrame) return false
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "https" && uri.host.equals("appassets.androidplatform.net", ignoreCase = true)) return false
                    if (scheme == "file" || scheme == "content") return true
                    if (scheme == "about" && uri.toString() == "about:blank") return false
                    // Keep the canonical NEXORA GitHub Pages site inside the app.
                    if (scheme == "https" && uri.host.equals("cedrickdavebausinbajar-afk.github.io", ignoreCase = true)) return false
                    if (scheme == "https" || scheme == "http") {
                        return try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                            true
                        } catch (_: Exception) {
                            true
                        }
                    }
                    return true
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val uri = url?.let { Uri.parse(it) } ?: return true
                    return handleNavigation(uri)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = handleNavigation(request.url, request.isForMainFrame)
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
        }

        setContentView(webView)
        WebViewCompat.addWebMessageListener(webView, "nexoraBridge", setOf(appOrigin)) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            if (!isMainFrame || sourceOrigin.toString() != appOrigin || !isTrustedAppPage()) return@addWebMessageListener
            val request = try { JSONObject(message.data ?: return@addWebMessageListener) } catch (_: Exception) { return@addWebMessageListener }
            val requestId = request.optString("id")
            val action = request.optString("action")
            val args = request.optJSONObject("args") ?: JSONObject()
            bridgeExecutor.execute {
                val result: Any? = try {
                    val bridge = BackupBridge()
                    when (action) {
                        "getInstalledAppVersion" -> bridge.getInstalledAppVersion()
                        "getUpdateMetadata" -> bridge.getUpdateMetadata()
                        "getUpdateStatus" -> bridge.getUpdateStatus()
                        "downloadUpdate" -> bridge.downloadUpdate(args.optString("url"), args.optString("version"))
                        "installUpdate" -> bridge.installUpdate()
                        "exportBackup" -> { bridge.exportBackup(args.optString("fileName"), args.optString("content")); true }
                        else -> JSONObject().put("error", "Unsupported native action").toString()
                    }
                } catch (_: Exception) { JSONObject().put("error", "Native request failed").toString() }
                val response = JSONObject().put("id", requestId).put("result", result)
                runOnUiThread { if (!isFinishing && ::webView.isInitialized && isTrustedAppPage()) replyProxy.postMessage(response.toString()) }
            }
        }
        if (savedInstanceState == null) webView.loadUrl("$appOrigin/assets/fit/index.html")
        else {
            webView.restoreState(savedInstanceState)
            if (webView.url.isNullOrBlank() || !webView.url!!.startsWith("$appOrigin/")) webView.loadUrl("$appOrigin/assets/fit/index.html")
        }
        if (Build.VERSION.SDK_INT >= 35) {
            webView.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            webView.requestApplyInsets()
        }
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

    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun downloadedApkMatchesInstalledSigner(apkUri: Uri): Boolean {
        val tempApk = File.createTempFile("nexora-fit-check-", ".apk", cacheDir)
        return try {
            contentResolver.openInputStream(apkUri)?.use { input ->
                tempApk.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val installed = packageManager.getPackageInfo(packageName, flags)
            val candidate = packageManager.getPackageArchiveInfo(tempApk.absolutePath, flags)
                ?: return false
            candidate.packageName == packageName &&
                signingDigests(installed).isNotEmpty() &&
                signingDigests(installed) == signingDigests(candidate)
        } catch (_: Exception) {
            false
        } finally {
            tempApk.delete()
        }
    }

    private fun isTrustedAppPage(): Boolean {
        if (!::webView.isInitialized) return false
        val current = Uri.parse(webView.url ?: return false)
        return current.scheme == "https" && current.host == "appassets.androidplatform.net" && current.path == "/assets/fit/index.html"
    }

    private inner class BackupBridge {
        
        fun getInstalledAppVersion(): String {
            return try {
                val info = packageManager.getPackageInfo(packageName, 0)
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
                JSONObject().put("versionName", info.versionName ?: "unknown").put("versionCode", code).toString()
            } catch (_: Exception) {
                JSONObject().put("error", "Installed app version could not be read").toString()
            }
        }

        
        fun getUpdateMetadata(): String {
            var connection: HttpURLConnection? = null
            return try {
                val endpoint = URL("https://raw.githubusercontent.com/cedrickdavebausinbajar-afk/nexora-official-website/main/android-studio-project/version.json")
                connection = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("User-Agent", "NEXORA-FIT-Android-Updater")
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Update service returned HTTP ${connection.responseCode}")
                }
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 65536) throw IllegalStateException("Update metadata is too large")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                String(bytes, Charsets.UTF_8)
            } catch (_: Exception) {
                JSONObject().put("error", "Update metadata could not be reached").toString()
            } finally {
                connection?.disconnect()
            }
        }

        
        fun getUpdateStatus(): String {
            val prefs = getSharedPreferences("nexora_fit_updater", Context.MODE_PRIVATE)
            val id = prefs.getLong("download_id", -1L)
            val version = prefs.getString("download_version", "") ?: ""
            if (id < 0) return JSONObject(mapOf("status" to "none", "ready" to false, "version" to version)).toString()
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(id)
            try {
                manager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = when (state) {
                            DownloadManager.STATUS_PENDING -> "pending"
                            DownloadManager.STATUS_RUNNING -> "running"
                            DownloadManager.STATUS_PAUSED -> "paused"
                            DownloadManager.STATUS_SUCCESSFUL -> "complete"
                            DownloadManager.STATUS_FAILED -> "failed"
                            else -> "unknown"
                        }
                        val result = JSONObject().apply {
                            put("status", status)
                            put("ready", state == DownloadManager.STATUS_SUCCESSFUL)
                            put("version", version)
                            put("downloaded", downloaded)
                            put("total", total)
                            put("percent", if (total > 0) (downloaded * 100 / total).coerceIn(0, 100) else 0)
                        }
                        return result.toString()
                    }
                }
            } catch (_: Exception) { }
            return JSONObject(mapOf("status" to "missing", "ready" to false, "version" to version)).toString()
        }

        
        fun downloadUpdate(url: String, version: String): Boolean {
            val uri = Uri.parse(url)
            val validVersion = version.matches(Regex("""[0-9]+\.[0-9]+\.[0-9]+"""))
            val validReleaseAsset = uri.scheme == "https" &&
                uri.host == "github.com" &&
                uri.path.orEmpty().matches(Regex("""^/cedrickdavebausinbajar-afk/nexora-official-website/releases/download/nexora-fit-build-[0-9]+/NEXORA-FIT\.apk$"""))
            if (!validReleaseAsset || !validVersion) return false
            return try {
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val prefs = getSharedPreferences("nexora_fit_updater", Context.MODE_PRIVATE)
                val oldId = prefs.getLong("download_id", -1L)
                val buildCode = uri.path.orEmpty()
                    .substringAfterLast("/nexora-fit-build-")
                    .substringBefore("/")
                val request = DownloadManager.Request(uri)
                    .setTitle("NEXORA FIT $version")
                    .setDescription("Downloading your NEXORA FIT update")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    // Keep the previous download intact until Android accepts this new one.
                    .setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, "NEXORA-FIT-update-$buildCode.apk")
                val id = manager.enqueue(request)
                prefs.edit().putLong("download_id", id).putString("download_version", version).apply()
                // Only retire the previous download after the replacement was successfully queued.
                if (oldId >= 0 && oldId != id) manager.remove(oldId)
                true
            } catch (_: Exception) { false }
        }

        
        fun installUpdate(): Boolean {
            val prefs = getSharedPreferences("nexora_fit_updater", Context.MODE_PRIVATE)
            val id = prefs.getLong("download_id", -1L)
            if (id < 0) return false
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val apk = try { manager.getUriForDownloadedFile(id) } catch (_: Exception) { null } ?: return false
            if (!downloadedApkMatchesInstalledSigner(apk)) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "This APK does not match the installed NEXORA FIT signing certificate. Installation blocked to protect your app and data.", Toast.LENGTH_LONG).show()
                }
                return false
            }
            runOnUiThread {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                        !packageManager.canRequestPackageInstalls()) {
                        val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName"))
                        startActivity(settings)
                        Toast.makeText(this@MainActivity, "Allow NEXORA FIT to install updates, then return and tap Install Update.", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    val install = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apk, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(install)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Android could not open the installer. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
            return true
        }

        
        fun exportBackup(fileName: String, content: String) {
            runOnUiThread {
                if (!::webView.isInitialized || !isTrustedAppPage()) {
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
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
        bridgeExecutor.shutdownNow()
        super.onDestroy()
    }
}
