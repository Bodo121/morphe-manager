package app.morphe.manager.ui.screen.settings.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Sakura's document-tree explorer. Access is limited to the folder the user grants with SAF.
 * Binder I/O, file copies and metadata queries never run on the Compose/UI dispatcher.
 */
private data class SakuraFileEntry(
    val document: DocumentFile,
    val title: String,
    val directory: Boolean,
    val mime: String?,
    val size: Long
)

private const val MAX_LISTED_FILES = 500
private const val MAX_IMPORTED_BYTES = 1024L * 1024L * 1024L
private const val PREFS_FILES = "sakura_files"
private const val PREFS_TREE = "tree_uri"

@Composable
fun SakuraFileManagerScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_FILES, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val savedTree = remember { prefs.getString(PREFS_TREE, null) }
    var root by remember {
        mutableStateOf(savedTree?.let { text ->
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(text)) }.getOrNull()
        })
    }
    var current by remember { mutableStateOf(root) }
    var parents by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<SakuraFileEntry>>(emptyList()) }
    var truncated by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SakuraFileEntry?>(null) }
    var rename by remember { mutableStateOf<SakuraFileEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SakuraFileEntry?>(null) }
    var pendingExport by remember { mutableStateOf<SakuraFileEntry?>(null) }

    fun refresh() { refreshKey++ }

    BackHandler(enabled = parents.isNotEmpty()) {
        current = parents.last()
        parents = parents.dropLast(1)
        selected = null
        search = ""
    }

    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString(PREFS_TREE, uri.toString()).apply()
                root = DocumentFile.fromTreeUri(context, uri)
                current = root
                parents = emptyList()
                search = ""
                message = null
                refresh()
            } catch (e: SecurityException) {
                message = "This folder did not grant persistent access: " + e.message.orEmpty()
            }
        }
    }

    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val destination = current
        if (uri != null && destination != null && !busy) scope.launch {
            busy = true
            try {
                val title = withContext(Dispatchers.IO) {
                    val source = DocumentFile.fromSingleUri(context, uri)
                    val displayName = source?.name?.takeIf { it.isNotBlank() } ?: "imported-file"
                    require(destination.canWrite()) { "Folder is read-only" }
                    require(destination.findFile(displayName) == null) { "A file with that name already exists" }
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val created = destination.createFile(mime, displayName)
                        ?: error("Could not create file")
                    try {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Cannot read selected file" }
                            context.contentResolver.openOutputStream(created.uri, "w").use { output ->
                                requireNotNull(output) { "Cannot write to selected folder" }
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    require(total <= MAX_IMPORTED_BYTES) { "Import exceeds 1 GiB safety limit" }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        created.delete()
                        throw e
                    }
                    displayName
                }
                message = "Imported " + title
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Import failed: " + e.message.orEmpty() }
            finally { busy = false }
        }
    }

    val saveExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val file = pendingExport
        pendingExport = null
        if (uri != null && file != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(file.document.uri).use { input ->
                        requireNotNull(input) { "Source file cannot be read" }
                        context.contentResolver.openOutputStream(uri, "w").use { output ->
                            requireNotNull(output) { "Destination cannot be written" }
                            input.copyTo(output, 64 * 1024)
                        }
                    }
                }
                message = "Exported " + file.title
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Export failed: " + e.message.orEmpty() }
            finally { busy = false }
        }
    }

    LaunchedEffect(current?.uri, refreshKey) {
        val folder = current
        if (folder == null) {
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val list = withContext(Dispatchers.IO) {
                require(folder.isDirectory) { "Selected location is not a folder" }
                val all = folder.listFiles()
                val limited = all.take(MAX_LISTED_FILES)
                val rows = limited.map { document ->
                    SakuraFileEntry(
                        document = document,
                        title = document.name.orEmpty().ifBlank { "Unnamed document" },
                        directory = document.isDirectory,
                        mime = document.type,
                        size = if (document.isFile) document.length() else 0
                    )
                }.sortedWith(compareBy<SakuraFileEntry> { !it.directory }
                    .thenBy { it.title.lowercase(Locale.ROOT) })
                rows to (all.size > MAX_LISTED_FILES)
            }
            entries = list.first
            truncated = list.second
        } catch (e: Exception) {
            entries = emptyList()
            message = "Cannot read folder: " + e.message.orEmpty()
        } finally {
            loading = false
        }
    }

    val visible = remember(entries, search) {
        if (search.isBlank()) entries else entries.filter { it.title.contains(search.trim(), ignoreCase = true) }
    }

    fun openFile(item: SakuraFileEntry) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.document.uri, item.mime ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) { message = "No application can open this file" }
    }

    fun shareFile(item: SakuraFileEntry) {
        try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = item.mime ?: "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, item.document.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share " + item.title))
        } catch (e: Exception) { message = "Unable to share this file" }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Sakura Files", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Private, permission-based file management. Only folders you choose are accessible.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { pickTree.launch(null) }) {
                Icon(MaterialSymbols.Rounded.Folder, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (root == null) "Choose folder" else "Change folder")
            }
            if (current != null) {
                OutlinedButton(onClick = { pickImport.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text("Import")
                }
                IconButton(onClick = { refresh() }, enabled = !busy) {
                    Icon(MaterialSymbols.Rounded.Refresh, contentDescription = "Refresh folder")
                }
            }
        }
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (parents.isNotEmpty()) {
                    IconButton(onClick = {
                        current = parents.last()
                        parents = parents.dropLast(1)
                        search = ""
                    }) { Icon(MaterialSymbols.Rounded.Arrow_back, contentDescription = "Parent folder") }
                }
                Text(current?.name ?: "Selected folder", style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedTextField(value = search, onValueChange = { search = it },
                label = { Text("Search this folder") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
        }
        if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall) }
        if (truncated) Text("Showing the first 500 entries. Use Android Files for very large directories.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (root == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Text("Choose Downloads, Documents, or another folder to begin. Sakura does not request full-storage access.",
                    Modifier.fillMaxWidth().padding(20.dp))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 28.dp)) {
                items(visible, key = { it.document.uri.toString() }) { item ->
                    Card(onClick = {
                        if (item.directory) {
                            current?.let { parents = parents + it }
                            current = item.document
                            search = ""
                        } else selected = item
                    }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(if (item.directory) MaterialSymbols.Rounded.Folder else MaterialSymbols.Rounded.Insert_drive_file,
                                contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(if (item.directory) "Folder" else formatSakuraSize(item.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { selected = item }) {
                                Icon(MaterialSymbols.Rounded.More_vert,
                                    contentDescription = "Actions for " + item.title)
                            }
                        }
                    }
                }
                if (visible.isEmpty() && !loading) item {
                    Text("No files found in this folder.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    selected?.let { item ->
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(item.title, maxLines = 2) },
            text = {
                Column {
                    if (!item.directory) {
                        TextButton(onClick = { selected = null; openFile(item) }) { Text("Open") }
                        TextButton(onClick = {
                            selected = null
                            pendingExport = item
                            saveExport.launch(item.title)
                        }) { Text("Export a copy") }
                        TextButton(onClick = { selected = null; shareFile(item) }) { Text("Share") }
                    }
                    TextButton(onClick = { selected = null; rename = item; renameText = item.title }) { Text("Rename") }
                    TextButton(onClick = { selected = null; pendingDelete = item }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }

    rename?.let { item ->
        AlertDialog(onDismissRequest = { rename = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it.take(160) },
                singleLine = true) },
            confirmButton = {
                TextButton(enabled = renameText.trim().isNotEmpty() && !busy,
                    onClick = {
                        rename = null
                        scope.launch {
                            busy = true
                            try {
                                require(withContext(Dispatchers.IO) {
                                    item.document.renameTo(renameText.trim())
                                }) { "Rename was refused by the document provider" }
                                message = "Renamed successfully"
                                refresh()
                            } catch (e: Exception) { message = e.message }
                            finally { busy = false }
                        }
                    }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } })
    }

    pendingDelete?.let { item ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("Delete " + item.title + "?") },
            text = { Text(if (item.directory)
                "This removes the folder and its contents. This cannot be undone."
                else "This deletes the selected file. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        busy = true
                        try {
                            require(withContext(Dispatchers.IO) { item.document.delete() }) {
                                "Delete was refused by the document provider"
                            }
                            message = "Deleted " + item.title
                            refresh()
                        } catch (e: Exception) { message = e.message }
                        finally { busy = false }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } })
    }
}

private fun formatSakuraSize(bytes: Long): String = when {
    bytes < 1024L -> bytes.toString() + " B"
    bytes < 1024L * 1024L -> (bytes / 1024L).toString() + " KiB"
    else -> String.format(Locale.ROOT, "%.1f MiB", bytes.toDouble() / (1024.0 * 1024.0))
}
