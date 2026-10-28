package app.morphe.manager.ui.screen.home

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** A direct HTTPS APK/archive download; never fetches a store page or silently installs a file. */
@Composable
internal fun SakuraDirectApkDownload(
    url: String,
    requestedVersion: String?,
    onDownloaded: (Uri) -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    var taskId by remember(url) { mutableStateOf<Long?>(null) }
    var progress by remember(url) { mutableStateOf("") }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val downloadUri = remember(url) { runCatching { Uri.parse(url) }.getOrNull() }
    val suffix = remember(url) {
        downloadUri?.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
    }
    val allowed = downloadUri?.scheme.equals("https", ignoreCase = true) &&
        suffix in setOf("apk", "apks", "apkm", "xapk") &&
        !downloadUri?.host.isNullOrBlank()

    LaunchedEffect(taskId, url) {
        val id = taskId ?: return@LaunchedEffect
        while (true) {
            delay(400)
            val snapshot = withContext(Dispatchers.IO) {
                manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    Triple(status, received, total)
                }
            }
            if (snapshot == null) {
                error = "Download was removed."
                taskId = null
                break
            }
            when (snapshot.first) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = manager.getUriForDownloadedFile(id)
                    taskId = null
                    if (file == null) error = "Downloaded file could not be opened."
                    else onDownloaded(file) // Existing APK validation and patch-flow checks apply.
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    error = "Download failed. Retry, or open the source in your browser."
                    taskId = null
                    break
                }
                else -> progress = if (snapshot.third > 0)
                    (snapshot.second * 100L / snapshot.third).coerceIn(0L, 100L).toString() + "% downloaded"
                else "Downloading APK in Sakura..."
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Download directly in Sakura", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("The verified HTTPS file link is saved by Android Download Manager. Sakura checks the APK before patching; no automatic installation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (!allowed) {
                Text("Only direct HTTPS APK/APKM/APKS/XAPK links can download here.",
                    style = MaterialTheme.typography.bodySmall)
            } else if (taskId != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(progress, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = {
                    taskId?.let { manager.remove(it) }
                    taskId = null
                    progress = ""
                }) { Text("Cancel download") }
            } else {
                Button(onClick = {
                    try {
                        val ext = suffix
                        val version = (requestedVersion ?: "latest")
                            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(30)
                        val name = "SakuraPatcher-" + version + "-" +
                            System.currentTimeMillis().toString() + "." + ext
                        val request = DownloadManager.Request(downloadUri)
                            .setTitle("SakuraPatcher APK")
                            .setDescription("Downloading " + (requestedVersion ?: "APK"))
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
                        taskId = manager.enqueue(request)
                        progress = "Preparing download..."
                        error = null
                    } catch (e: Exception) {
                        error = "Cannot start download: " + e.message.orEmpty()
                    }
                }) { Text("Download APK in Sakura") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
        }
    }
}
