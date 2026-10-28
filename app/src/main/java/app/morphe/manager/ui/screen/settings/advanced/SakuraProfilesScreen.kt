/*
 * SakuraPatcher named profiles - validated per-app selections and patch options.
 * Runs against the original patch repositories, not a simulated patch engine.
 */
package app.morphe.manager.ui.screen.settings.advanced

import android.content.Context
import android.util.AtomicFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.manager.domain.repository.PatchSelectionRepository
import app.morphe.manager.domain.repository.PatchOptionsRepository
import app.morphe.manager.domain.repository.PatchBundleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import java.io.File
import java.util.UUID

@Serializable
private data class SakuraProfile(
    val id: String,
    val name: String,
    val packageName: String,
    val savedAt: Long,
    val selections: Map<Int, List<String>>,
    val options: Map<Int, Map<String, Map<String, String>>>,
    val sourceNames: Map<Int, String>
)

@Serializable
private data class SakuraProfileList(
    val version: Int = 1,
    val profiles: List<SakuraProfile> = emptyList()
)

private object SakuraProfilesStorage {
    private const val name = "sakura_profiles.json"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private const val maxChars = 1048576

    fun load(context: Context): List<SakuraProfile> {
        val file = File(context.filesDir, name)
        if (!file.exists()) return emptyList()
        require(file.length() <= maxChars) { "Saved profile file is too large" }
        return parse(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
    }
    fun serialize(value: List<SakuraProfile>): String = json.encodeToString(SakuraProfileList(profiles = value))
    fun parse(content: String): List<SakuraProfile> {
        require(content.length <= maxChars) { "Profile archive exceeds 1 MiB" }
        val archive = json.decodeFromString<SakuraProfileList>(content)
        require(archive.version == 1) { "Unsupported profile version" }
        require(archive.profiles.size <= 200) { "Too many profiles" }
        require(archive.profiles.all { it.name.isNotBlank() && it.packageName.isNotBlank() }) {
            "Invalid profile name or target package"
        }
        return archive.profiles
    }
    fun save(context: Context, profiles: List<SakuraProfile>) {
        require(profiles.size <= 200) { "Maximum 200 profiles" }
        val data = serialize(profiles)
        require(data.length <= maxChars) { "Profiles exceed 1 MiB" }
        val atomic = AtomicFile(File(context.filesDir, name))
        val output = atomic.startWrite()
        try {
            output.write(data.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
    }
}

@Composable
fun SakuraProfilesScreen(
    selectionRepo: PatchSelectionRepository = koinInject(),
    optionsRepo: PatchOptionsRepository = koinInject(),
    bundleRepo: PatchBundleRepository = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(emptyList<SakuraProfile>()) }
    var target by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var includeOptions by remember { mutableStateOf(true) }
    var confirmApply by remember { mutableStateOf<SakuraProfile?>(null) }
    var confirmDelete by remember { mutableStateOf<SakuraProfile?>(null) }

    LaunchedEffect(Unit) {
        try { profiles = withContext(Dispatchers.IO) { SakuraProfilesStorage.load(context) } }
        catch (e: Exception) { status = "Profiles unavailable: " + (e.message ?: "Unknown error") }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("Unable to read selected document")
                    val text = input.bufferedReader().use { it.readText() }
                    SakuraProfilesStorage.parse(text)
                }
                val merged = (profiles + items).distinctBy { it.id }
                withContext(Dispatchers.IO) { SakuraProfilesStorage.save(context, merged) }
                profiles = merged
                status = "Imported " + items.size + " profile(s)"
            } catch (e: Exception) { status = "Import failed: " + (e.message ?: "Invalid JSON") }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Unable to save file")
                    output.bufferedWriter().use { it.write(SakuraProfilesStorage.serialize(profiles)) }
                }
                status = "Exported " + profiles.size + " profiles"
            } catch (e: Exception) { status = "Export failed: " + (e.message ?: "Unknown error") }
        }
    }

    confirmDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete saved profile?") },
            text = { Text("Remove " + profile.name + "? Your active patch selections are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        try {
                            val next = profiles.filterNot { it.id == profile.id }
                            withContext(Dispatchers.IO) { SakuraProfilesStorage.save(context, next) }
                            profiles = next
                            status = "Profile deleted"
                        } catch (e: Exception) { status = e.message ?: "Delete failed" }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }

    confirmApply?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmApply = null },
            title = { Text("Restore " + profile.name + "?") },
            text = { Text("The app's saved selections will change only if all patch sources and patch names are still available. Other apps stay untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmApply = null
                    scope.launch {
                        busy = true
                        try {
                            val outcome = withContext(Dispatchers.IO) {
                                val available = bundleRepo.allBundlesInfoFlow.first()
                                require(profile.selections.isNotEmpty()) { "No patch selections in this profile" }
                                // Validate every source and selected patch before writing anything.
                                val valid = profile.selections.mapValues { (uid, selected) ->
                                    val bundle = available[uid] ?: error("Missing source ID " + uid)
                                    val nameAtSave = profile.sourceNames[uid]
                                    require(nameAtSave == null || nameAtSave == bundle.name) {
                                        "Source ID " + uid + " now points to a different repository"
                                    }
                                    val patchNames = bundle.forPackage(profile.packageName, null).patches
                                        .map { it.name }.toSet()
                                    val missing = selected.filterNot { it in patchNames }
                                    require(missing.isEmpty()) {
                                        "Source " + bundle.name + " is missing " + missing.size + " patches"
                                    }
                                    selected.toSet()
                                }
                                selectionRepo.updateSelection(profile.packageName, valid, scope = valid.keys)
                                if (includeOptions) {
                                    for ((uid, raw) in profile.options) {
                                        if (uid !in valid) continue
                                        optionsRepo.importOptionsForBundle(
                                            profile.packageName, uid,
                                            raw.filterKeys { it in valid[uid].orEmpty() }
                                        )
                                    }
                                }
                                "Restored selections for " + profile.packageName + ". Review options before patching."
                            }
                            status = outcome
                        } catch (e: Exception) {
                            status = "Restore cancelled: " + (e.message ?: "Unknown error")
                        } finally { busy = false }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmApply = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sakura Profiles", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Named snapshots of real patch selections and options. Matching sources are validated before restore.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(80) },
            label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = target, onValueChange = { target = it.trim().take(255) },
            label = { Text("Target package ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Button(
            enabled = !busy && title.isNotBlank() && target.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            val active = selectionRepo.getAllSelectionsForPackage(target)
                            require(active.isNotEmpty()) { "This app has no saved patch selections" }
                            val sources = bundleRepo.allBundlesInfoFlow.first()
                            SakuraProfile(
                                id = UUID.randomUUID().toString(),
                                name = title.trim(), packageName = target,
                                savedAt = System.currentTimeMillis(),
                                selections = active.mapValues { it.value.sorted() },
                                options = active.mapValues { (uid, _) ->
                                    optionsRepo.exportOptionsForBundle(target, uid)
                                },
                                sourceNames = active.keys.mapNotNull { uid ->
                                    sources[uid]?.name?.let { uid to it }
                                }.toMap()
                            )
                        }
                        val next = profiles + snapshot
                        withContext(Dispatchers.IO) { SakuraProfilesStorage.save(context, next) }
                        profiles = next
                        status = "Saved " + snapshot.name
                        title = ""
                    } catch (e: Exception) { status = "Save failed: " + (e.message ?: "Unknown error") }
                    finally { busy = false }
                }
            }
        ) { Text("Save current patch configuration") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, enabled = !busy) {
                Text("Import JSON")
            }
            OutlinedButton(onClick = { exportLauncher.launch("sakura-profiles.json") },
                enabled = !busy && profiles.isNotEmpty()) { Text("Export JSON") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Checkbox(checked = includeOptions, onCheckedChange = { includeOptions = it })
            Text("Restore saved patch options", modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        Text("Profiles (" + profiles.size + ")", style = MaterialTheme.typography.titleMedium)
        if (profiles.isEmpty()) Text("No saved profiles yet. Choose patches, then save a named snapshot.")
        profiles.sortedByDescending { it.savedAt }.forEach { profile ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(profile.packageName, style = MaterialTheme.typography.bodySmall)
                    Text(profile.selections.values.sumOf { it.size }.toString() +
                        " patches from " + profile.selections.size + " sources",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmApply = profile }, enabled = !busy) { Text("Restore") }
                        TextButton(onClick = { confirmDelete = profile }, enabled = !busy) { Text("Delete") }
                    }
                }
            }
        }
    }
}
