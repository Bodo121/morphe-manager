package app.morphe.manager.ui.screen.home

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * On-demand HTTPS source browser, shown only after the user requests a download.
 *
 * APKMirror and similar services return an HTML page rather than an APK. Displaying the actual
 * page lets the user choose the correct version/variant. A genuine archive download triggered
 * by that page is handed to Android DownloadManager, then to the existing APK validation path.
 * No scraping, bypassing download protections, automatic installation or background WebView.
 */
@Composable
internal fun SakuraApkSourceBrowser(
    url: String,
    appName: String,
    requestedVersion: String?,
    onDismiss: () -> Unit,
    onDownloaded: (Uri) -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadingPercent by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("APK source") }
    var canGoBack by remember { mutableStateOf(false) }
    var downloadId by rememberSaveable(url) { mutableLongStateOf(0L) }
    var received by remember(downloadId) { mutableLongStateOf(0L) }
    var expected by remember(downloadId) { mutableLongStateOf(-1L) }
    var error by remember { mutableStateOf<String?>(null) }
    val latestDownloaded by rememberUpdatedState(onDownloaded)

    fun openExternally() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: url)))
        }.onFailure { error = "No browser available for this source." }
    }

    fun enqueueFile(address: String, userAgent: String?, contentDisposition: String?, mime: String?) {
        try {
            val uri = Uri.parse(address)
            if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank()) {
                error = "This source did not provide a secure HTTPS file link."
                return
            }
            val dispositionName = Regex("""filename\*?=(?:UTF-8''|["']?)([^;"']+)""", RegexOption.IGNORE_CASE)
                .find(contentDisposition.orEmpty())?.groupValues?.get(1)
            val filename = (dispositionName ?: uri.lastPathSegment.orEmpty())
                .substringBefore('?').substringAfterLast('/').lowercase(Locale.ROOT)
            val extension = listOf("apk", "apkm", "apks", "xapk").firstOrNull {
                filename.endsWith(".$it")
            } ?: if (mime?.substringBefore(';')?.trim()?.equals(
                    "application/vnd.android.package-archive", ignoreCase = true
                ) == true) "apk" else null
            if (extension == null) {
                error = "The source did not offer an APK/archive. Choose its APK download option."
                return
            }
            if (downloadId != 0L) return
            val safeApp = appName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(35)
            val safeVersion = (requestedVersion ?: "latest").replace(Regex("[^A-Za-z0-9._-]"), "_").take(30)
            val fileName = "Sakura-$safeApp-$safeVersion-${System.currentTimeMillis()}.$extension"
            val request = DownloadManager.Request(uri)
                .setTitle("$appName · $safeVersion")
                .setDescription("Downloading original APK for SakuraPatcher")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            val cookies = CookieManager.getInstance().getCookie(address)
            if (!cookies.isNullOrBlank()) request.addRequestHeader("Cookie", cookies)
            if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
            webView?.url?.takeIf { it.startsWith("https://", true) }?.let {
                request.addRequestHeader("Referer", it)
            }
            downloadId = manager.enqueue(request)
            error = null
        } catch (e: Exception) {
            error = "Couldn't start download: ${e.localizedMessage ?: "Try your browser"}"
        }
    }

    LaunchedEffect(downloadId) {
        val id = downloadId
        if (id == 0L) return@LaunchedEffect
        while (true) {
            delay(550)
            val snapshot = withContext(Dispatchers.IO) {
                runCatching {
                    manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (!c.moveToFirst()) null else Triple(
                            c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        )
                    }
                }.getOrNull()
            }
            if (snapshot == null) {
                error = "Download is no longer available. Please retry."
                downloadId = 0L
                break
            }
            received = snapshot.second
            expected = snapshot.third
            when (snapshot.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = manager.getUriForDownloadedFile(id)
                    downloadId = 0L
                    if (file == null) error = "The downloaded APK cannot be opened."
                    else {
                        // The receiving patch flow dismisses this dialog after it accepts
                        // the file; dismissing first would drop the pending app session.
                        latestDownloaded(file)
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    downloadId = 0L
                    error = "Download failed. The source may require a browser or a different mirror."
                    break
                }
            }
        }
    }

    BackHandler(enabled = canGoBack && downloadId == 0L) {
        webView?.goBack()
    }

    Dialog(
        onDismissRequest = { if (downloadId == 0L) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f).padding(horizontal = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Download $appName", style = MaterialTheme.typography.titleMedium)
                        Text(
                            (requestedVersion?.let { "Required: $it · " } ?: "") + pageTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    TextButton(onClick = ::openExternally) { Text("Browser") }
                    TextButton(onClick = onDismiss, enabled = downloadId == 0L) { Text("Close") }
                }
                if (loadingPercent in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadingPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, progress: Int) {
                                    loadingPercent = progress
                                }
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    pageTitle = title?.take(65) ?: "APK source"
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?, request: WebResourceRequest?
                                ): Boolean {
                                    if (request?.isForMainFrame != true) return false
                                    if (!request.url.scheme.equals("https", true)) {
                                        error = "Only secure HTTPS pages can open inside Sakura."
                                        return true
                                    }
                                    return false
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    canGoBack = view?.canGoBack() == true
                                }
                            }
                            setDownloadListener(DownloadListener { address, agent, disposition, mime, _ ->
                                enqueueFile(address, agent, disposition, mime)
                            })
                            loadUrl(url)
                            webView = this
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (downloadId != 0L) {
                        Text("Downloading the original APK...", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(
                            progress = { if (expected > 0) (received.toFloat() / expected).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (expected > 0) "${(received * 100 / expected).coerceIn(0, 100)}%" else "Preparing download...",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = {
                            manager.remove(downloadId)
                            downloadId = 0L
                            error = "Download cancelled."
                        }) { Text("Cancel download") }
                    } else {
                        Text(
                            "Choose the correct variant on the source page. Tap its real download link; Sakura will save and verify the APK. Nothing is installed automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                setDownloadListener(null)
                destroy()
            }
            webView = null
        }
    }
}
