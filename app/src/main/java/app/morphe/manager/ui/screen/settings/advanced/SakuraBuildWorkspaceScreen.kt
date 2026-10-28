package app.morphe.manager.ui.screen.settings.advanced

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.morphe.manager.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.domain.workspace.*
import app.morphe.manager.ui.model.navigation.Patcher
import kotlinx.coroutines.*
import org.koin.compose.koinInject
import java.io.File
import java.util.UUID
import java.text.DateFormat
import java.util.Date

@Composable
fun SakuraBuildWorkspaceScreen(onStartBuild: (Patcher.ViewModelParams) -> Unit,
    repo: PatchBundleRepository = koinInject(), prefs: PreferencesManager = koinInject()) {
    val context = LocalContext.current
    val store = remember { BuildWorkspace(context) }
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<BuildRecipe>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<BuildRecipe?>(null) }
    var exportText by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<BuildRecipe?>(null) }
    var details by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<Pair<Patcher.ViewModelParams, List<String>>?>(null) }
    suspend fun refresh() { records = withContext(Dispatchers.IO) { store.readAll() } }
    LaunchedEffect(Unit) { refresh() }
    fun prepare(record: BuildRecipe, file: File) {
        scope.launch {
            busy = true
            try { review = withContext(Dispatchers.IO) { store.prepare(record, file, repo, prefs) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = e.message.orEmpty() }
            finally { busy = false }
        }
    }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val record = picked
        if (uri != null && record != null) scope.launch {
            busy = true
            var staged: File? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "recipe-${UUID.randomUUID()}.apk").also { target ->
                        staged = target
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            target.outputStream().use { out ->
                                val buffer = ByteArray(65536); var bytes = 0L
                                while (true) { ensureActive(); val n = input.read(buffer); if (n < 0) break
                                    bytes += n; require(bytes <= 1024L * 1024 * 1024) { "APK exceeds 1 GiB" }; out.write(buffer, 0, n) }
                            }
                        }
                    }
                }
                review = withContext(Dispatchers.IO) { store.prepare(record, file, repo, prefs) }
            } catch (e: CancellationException) { staged?.delete(); throw e }
            catch (e: Exception) { staged?.delete(); status = e.message.orEmpty() }
            finally { busy = false }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(exportText) } }; status = "Exported" }
            catch (e: Exception) { status = e.message.orEmpty() }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                        val buffer = CharArray(1024 * 1024 + 1); var offset = 0
                        while (offset < buffer.size) { val n = reader.read(buffer, offset, buffer.size - offset); if (n < 0) break; offset += n }
                        require(offset <= 1024 * 1024) { "Recipe exceeds 1 MiB" }; String(buffer, 0, offset)
                    }
                    store.importRecipe(text)
                }
                refresh(); status = "Recipe imported. Select an APK and review sources before building."
            } catch (e: Exception) { status = e.message.orEmpty() }
            finally { busy = false }
        }
    }
    review?.let { (params, changes) ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text(stringResource(R.string.sakura_ui_review_rebuild)) },
            text = { Text((changes.ifEmpty { listOf("Input and source hashes match the saved recipe.") } +
                "The next screen offers a dry run before building. Patch options can contain private values; review any exported recipe before sharing.").joinToString("\n\n")) },
            confirmButton = { TextButton(onClick = { review = null; onStartBuild(params) }) { Text(stringResource(R.string.sakura_ui_continue)) } },
            dismissButton = { TextButton(onClick = { review = null }) { Text(stringResource(R.string.sakura_ui_cancel)) } })
    }
    delete?.let { record ->
        AlertDialog(onDismissRequest = { delete = null }, title = { Text(stringResource(R.string.sakura_ui_delete_archived_build)) },
            text = { Text(stringResource(R.string.sakura_ui_removes_its_saved_apks_recipe_and_log_installed_apps_and_patch_se)) },
            confirmButton = { TextButton(onClick = { delete = null; scope.launch { withContext(Dispatchers.IO) { store.delete(record) }; refresh() } }) { Text(stringResource(R.string.sakura_ui_delete)) } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.sakura_ui_cancel)) } })
    }
    details?.let { text -> AlertDialog(onDismissRequest = { details = null }, title = { Text(stringResource(R.string.sakura_ui_build_report)) },
        text = { androidx.compose.foundation.lazy.LazyColumn { item { Text(text) } } },
        confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.sakura_ui_close)) } }) }
    val shown = remember(records, query) { records.filter { query.isBlank() || it.packageName.contains(query, true) || it.status.contains(query, true) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Build workspace", style = MaterialTheme.typography.headlineSmall)
            Text("Recipes, logs, and the last ten completed builds stay on this device. Interrupted work restarts from the beginning; an APK is never silently installed.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.sakura_ui_search_app_or_result)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(stringResource(R.string.sakura_ui_import_recipe)) }
            SakuraPerformanceProbe()
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
            if (records.isEmpty()) Text(stringResource(R.string.sakura_ui_your_next_patch_or_dry_run_will_appear_here))
        }
        items(shown, key = { it.id }) { record ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.packageName, style = MaterialTheme.typography.titleMedium)
                    Text("${record.versionName.orEmpty()} · ${record.status} · ${DateFormat.getDateTimeInstance().format(Date(record.createdAt))}")
                    Text("${record.sources.sumOf { it.patches.size }} patches · ${record.sources.size} sources · ${record.elapsedMs / 1000}s")
                    if (record.error != null) Text(record.error.take(300), color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && record.status != "RUNNING", onClick = { prepare(record, store.input(record)) }) { Text(stringResource(R.string.sakura_ui_rebuild)) }
                        OutlinedButton(enabled = !busy && record.status != "RUNNING", onClick = { picked = record; apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }) { Text(stringResource(R.string.sakura_ui_new_apk)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { scope.launch { exportText = withContext(Dispatchers.IO) { store.export(record) }; exporter.launch("sakura-recipe-${record.id}.json") } }) { Text(stringResource(R.string.sakura_ui_recipe)) }
                        TextButton(onClick = { scope.launch { exportText = withContext(Dispatchers.IO) { store.logs(record) }; exporter.launch("sakura-build-${record.id}.log") } }) { Text(stringResource(R.string.sakura_ui_log)) }
                        TextButton(enabled = record.status != "RUNNING", onClick = { delete = record }) { Text(stringResource(R.string.sakura_ui_delete)) }
                    }
                    if (record.status == "SUCCESS") {
                        val apkExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
                            if (uri != null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { out -> store.output(record).inputStream().use { it.copyTo(out) } } }; status = "APK exported" } catch (e: Exception) { status = e.message.orEmpty() } }
                        }
                        OutlinedButton(onClick = { apkExporter.launch("${record.packageName}-patched.apk") }) { Text(stringResource(R.string.sakura_ui_export_apk)) }
                        TextButton(onClick = { scope.launch { details = withContext(Dispatchers.IO) { InstallDiagnostics.inspect(context, store.output(record)).joinToString("\n\n") } } }) { Text(stringResource(R.string.sakura_ui_check_installation)) }
                    }
                }
            }
        }
    }
}
