/*
 * SakuraPatcher native diagnostics. The original manager patch/install pipeline is not modified.
 */
package app.morphe.manager.ui.screen.settings.advanced

import android.content.Context
import app.morphe.manager.domain.workspace.DexMethodIndex
import app.morphe.manager.domain.workspace.InstallDiagnostics
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import app.morphe.manager.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.manager.ui.theme.SakuraMono
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.io.File
import java.io.IOException
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

private data class SakuraDexHeader(
    val name: String,
    val methodIds: Int,
    val classDefs: Int,
    val stringIds: Int
)

private data class SakuraApkScan(
    val fileName: String,
    val packageName: String,
    val version: String?,
    val versionCode: Long,
    val bytes: Long,
    val scanDurationMs: Long,
    val sha256: String,
    val entriesCount: Int,
    val entryIndex: Map<String, Pair<Long, Long>>,
    val dexCount: Int,
    val dexHeaders: List<SakuraDexHeader>,
    val nativeLibraryCount: Int,
    val permissions: List<String>,
    val hasManifest: Boolean,
    val hasResources: Boolean,
    val components: Set<String>,
    val certificates: Set<String>,
    val methods: Map<String, String>,
    val inspectionNotes: List<String>
)

private data class SakuraPatchCheck(
    val title: String,
    val source: String,
    val status: String,
    val target: String
)

/** Inspects a local APK; never executes its code or sends it to a server. */
@Suppress("DEPRECATION")
private suspend fun inspectSakuraApk(context: Context, uri: Uri): SakuraApkScan {
    val fileName = context.contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "Selected APK"

    val scanStartNs = System.nanoTime()
    val tmp = File.createTempFile("sakura-lab-", ".apk", context.cacheDir)
    try {
        var bytes = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        val source = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open this APK")
        source.use { input ->
            tmp.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    bytes += read
                    digest.update(buffer, 0, read)
                    if (bytes > 1024L * 1024L * 1024L) {
                        throw IOException("The APK exceeds the 1 GB inspection limit")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }

        val packageInfo = InstallDiagnostics.archive(context, tmp) ?: throw IOException("Android could not read the APK manifest. Choose a standalone .apk, not an .apkm or .apks bundle.")

        val methods = linkedMapOf<String, String>()
        val notes = mutableListOf<String>()
        val installChecks = InstallDiagnostics.inspect(context, tmp)
        var count = 0
        var dex = 0
        val dexHeaders = mutableListOf<SakuraDexHeader>()
        var libs = 0
        var hasManifest = false
        var hasResources = false
        val files = LinkedHashMap<String, Pair<Long, Long>>()
        ZipFile(tmp).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                count++
                if (files.size < 20000) files[entry.name] = entry.crc to entry.size
                if (entry.name.matches(Regex("classes(\\d*)\\.dex"))) {
                    dex++
                    if (methods.size < 50000 && entry.size in 112..(64L * 1024 * 1024)) {
                        runCatching {
                            val data = zip.getInputStream(entry).use { input ->
                                val out = java.io.ByteArrayOutputStream()
                                val block = ByteArray(65536)
                                while (true) {
                                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                    val n = input.read(block); if (n < 0) break
                                    require(out.size().toLong() + n <= 64L * 1024 * 1024) { "DEX exceeds inspection limit" }
                                    out.write(block, 0, n)
                                }
                                out.toByteArray()
                            }
                            methods.putAll(DexMethodIndex.read(data, 50000 - methods.size))
                        }.onFailure { notes += "Method inspection incomplete for ${entry.name}: ${it.message}" }
                    } else notes += "Method inspection limited for ${entry.name}"
                    // Only read the 112-byte DEX header; never inflate a full DEX just
                    // to display its class/method ID counts.
                    if (dexHeaders.size < 30) {
                        val header = ByteArray(112)
                        val valid = runCatching {
                            zip.getInputStream(entry).use { input ->
                                DataInputStream(input).readFully(header)
                            }
                            header[0] == 'd'.code.toByte() &&
                                header[1] == 'e'.code.toByte() &&
                                header[2] == 'x'.code.toByte() &&
                                header[3] == '\n'.code.toByte()
                        }.getOrDefault(false)
                        if (valid) {
                            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                            val methods = buffer.getInt(88)
                            val classes = buffer.getInt(96)
                            val strings = buffer.getInt(56)
                            if (methods >= 0 && classes >= 0 && strings >= 0) {
                                dexHeaders.add(SakuraDexHeader(entry.name, methods, classes, strings))
                            }
                        }
                    }
                }
                if (entry.name.startsWith("lib/") && entry.name.endsWith(".so")) libs++
                if (entry.name == "AndroidManifest.xml") hasManifest = true
                if (entry.name == "resources.arsc") hasResources = true
            }
        }

        return SakuraApkScan(
            fileName = fileName,
            packageName = packageInfo.packageName,
            version = packageInfo.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            },
            bytes = bytes,
            scanDurationMs = (System.nanoTime() - scanStartNs) / 1000000L,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            entriesCount = count,
            entryIndex = files,
            dexCount = dex,
            dexHeaders = dexHeaders,
            nativeLibraryCount = libs,
            permissions = packageInfo.requestedPermissions?.toList().orEmpty().sorted(),
            hasManifest = hasManifest,
            hasResources = hasResources,
            components = buildSet {
                packageInfo.activities.orEmpty().forEach { add("activity ${it.name} exported=${it.exported}") }
                packageInfo.services.orEmpty().forEach { add("service ${it.name} exported=${it.exported}") }
                packageInfo.receivers.orEmpty().forEach { add("receiver ${it.name} exported=${it.exported}") }
                packageInfo.providers.orEmpty().forEach { add("provider ${it.name} exported=${it.exported} authorities=${it.authority}") }
            },
            certificates = InstallDiagnostics.signatures(packageInfo), methods = methods,
            inspectionNotes = notes + installChecks + if (count > files.size) listOf("ZIP index limited to 20,000 paths; diff is partial") else emptyList()
        )
    } finally {
        tmp.delete()
    }
}

@Composable
fun SakuraPatchLabScreen(
    repository: PatchBundleRepository = koinInject(),
    preferences: PreferencesManager = koinInject()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bundles by repository.enabledBundlesInfoFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val crossPackage by preferences.allowCrossPackagePatches.getAsState()

    var primary by remember { mutableStateOf<SakuraApkScan?>(null) }
    var comparison by remember { mutableStateOf<SakuraApkScan?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var labTab by rememberSaveable { mutableStateOf("Overview") }

    fun scan(uri: Uri, comparisonSlot: Boolean) {
        scope.launch {
            loading = true
            error = null
            try {
                val result = withContext(Dispatchers.IO) { inspectSakuraApk(context, uri) }
                if (comparisonSlot) comparison = result else {
                    primary = result
                    comparison = null
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                error = e.message ?: "APK inspection failed"
            } finally {
                loading = false
            }
        }
    }

    val primaryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scan(it, false) }
    }
    val comparePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scan(it, true) }
    }

    val checks = remember(primary, bundles) {
        val apk = primary
        if (apk == null) emptyList() else bundles.values.flatMap { bundle ->
            bundle.patches.map { patch ->
                val status = when {
                    patch.isUniversal -> "Universal"
                    !patch.compatibleWith(apk.packageName) -> "Other package"
                    patch.supports(apk.packageName, apk.version, apk.versionCode) -> "Package/version match"
                    else -> "Version mismatch"
                }
                SakuraPatchCheck(
                    title = patch.displayName,
                    source = bundle.name,
                    status = status,
                    target = patch.compatiblePackages?.mapNotNull { it.packageName }?.distinct()
                        ?.take(3)?.joinToString(", ").orEmpty()
                )
            }
        }.sortedWith(compareBy({ it.status != "Package/version match" }, { it.status != "Universal" }, { it.title }))
    }

    val shownChecks = remember(checks, filter, search) {
        checks.filter { check ->
            (filter == "All" || check.status == filter) &&
                (search.isBlank() || check.title.contains(search, true) ||
                    check.source.contains(search, true) || check.target.contains(search, true))
        }.take(80)
    }

    val report = buildString {
        val apk = primary
        appendLine("Sakura PatchLab - local preflight report")
        if (apk == null) {
            appendLine("No APK selected")
        } else {
            appendLine("APK: " + apk.fileName)
            appendLine("Package: " + apk.packageName)
            appendLine("Version: " + (apk.version ?: "unknown") + " (" + apk.versionCode + ")")
            appendLine("Size: " + apk.bytes + " bytes")
            appendLine("Scan duration: " + apk.scanDurationMs + " ms")
            appendLine("SHA-256: " + apk.sha256)
            appendLine("DEX: " + apk.dexCount + "; native .so: " + apk.nativeLibraryCount)
            appendLine("DEX method IDs: " + apk.dexHeaders.sumOf { it.methodIds })
            appendLine("DEX class definitions: " + apk.dexHeaders.sumOf { it.classDefs })
            apk.dexHeaders.forEach {
                appendLine("DEX " + it.name + ": method IDs " + it.methodIds +
                    ", classes " + it.classDefs + ", strings " + it.stringIds)
            }
            appendLine("ZIP entries: " + apk.entriesCount)
            appendLine("Manifest: " + apk.hasManifest + "; resources: " + apk.hasResources)
            appendLine("Requested permissions:")
            apk.permissions.forEach { appendLine(" - " + it) }
            appendLine("Signing certificates (SHA-256): " + apk.certificates.joinToString())
            apk.components.forEach { appendLine("Component: $it") }
            apk.inspectionNotes.forEach { appendLine("Inspection: $it") }
            appendLine("Indexed methods: " + apk.methods.size)
            appendLine("Metadata compatibility checks: " + checks.size)
            checks.forEach { appendLine(" - " + it.source + " / " + it.title + " / " + it.status) }
            comparison?.let { other ->
                val addedPaths = other.entryIndex.keys - apk.entryIndex.keys
                val removedPaths = apk.entryIndex.keys - other.entryIndex.keys
                val changedPaths = apk.entryIndex.keys.intersect(other.entryIndex.keys)
                    .count { apk.entryIndex[it] != other.entryIndex[it] }
                appendLine("Comparison APK: " + other.fileName)
                appendLine("Certificates changed: " + (apk.certificates != other.certificates))
                (other.components - apk.components).forEach { appendLine("+ Component: $it") }
                (apk.components - other.components).forEach { appendLine("- Component: $it") }
                val addedMethods = other.methods.keys - apk.methods.keys
                val removedMethods = apk.methods.keys - other.methods.keys
                val changedMethods = apk.methods.keys.intersect(other.methods.keys).filter { apk.methods[it] != other.methods[it] }
                appendLine("Methods: +${addedMethods.size} -${removedMethods.size}; changed instruction bytes: ${changedMethods.size}")
                addedMethods.take(200).forEach { appendLine("+ Method: $it") }
                removedMethods.take(200).forEach { appendLine("- Method: $it") }
                changedMethods.take(200).forEach { appendLine("~ Method: $it") }
                (addedPaths + removedPaths + apk.entryIndex.keys.intersect(other.entryIndex.keys).filter { apk.entryIndex[it] != other.entryIndex[it] })
                    .filter { it.startsWith("res/") || it == "resources.arsc" || it.startsWith("lib/") }.take(200).forEach { appendLine("Changed resource/native path: $it") }
                appendLine("Method index is capped at 50,000; instruction hashes are index-sensitive, not a semantic equivalence proof.")
                appendLine("Comparison SHA-256: " + other.sha256)
                appendLine("Added ZIP paths: " + addedPaths.size)
                appendLine("Removed ZIP paths: " + removedPaths.size)
                appendLine("Changed ZIP entries (CRC or size): " + changedPaths)
                appendLine("Added permissions:")
                (other.permissions.toSet() - apk.permissions.toSet()).forEach { appendLine(" + " + it) }
                appendLine("Removed permissions:")
                (apk.permissions.toSet() - other.permissions.toSet()).forEach { appendLine(" - " + it) }
            }
        }
        appendLine("Note: Metadata compatibility is not bytecode fingerprint verification.")
    }

    val reportExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { destination ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination)?.bufferedWriter()?.use {
                            it.write(report)
                        } ?: throw IOException("Cannot write the selected report file")
                    }
                } catch (e: Exception) {
                    error = "Report export failed: " + (e.message ?: "Unknown error")
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("🌸 Sakura PatchLab", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Local APK diagnostics · Patch compatibility · Archive inspection",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { primaryPicker.launch(arrayOf("*/*")) }, enabled = !loading) {
                Text(if (primary == null) "Choose APK" else "Replace APK")
            }
            if (primary != null && labTab == "Diff") OutlinedButton(
                onClick = { comparePicker.launch(arrayOf("*/*")) }, enabled = !loading
            ) { Text(stringResource(R.string.sakura_ui_compare_apk)) }
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Overview", "Patches", "Diff", "Report").forEach { title ->
                FilterChip(
                    selected = labTab == title,
                    onClick = { labTab = title },
                    label = { Text(title) }
                )
            }
        }

        if (primary == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ready for inspection", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Pick a standalone APK to display real on-device results. No upload or fake fingerprint confidence scores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("DEX", "Manifest", "Permissions").forEach { label ->
                            SuggestionChip(onClick = { primaryPicker.launch(arrayOf("*/*")) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Text("1  Choose APK    •    2  Analyze    •    3  Compare or export",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (labTab == "Patches") {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cross-package patch metadata", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Allow patches for other apps", modifier = Modifier.weight(1f))
                    Switch(
                        checked = crossPackage,
                        onCheckedChange = { enabled ->
                            scope.launch { preferences.allowCrossPackagePatches.update(enabled) }
                        }
                    )
                }
                Text(
                    "This switch also controls the manager's global patch override. " +
                        "A metadata match does not guarantee a working bytecode patch.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        }
        primary?.let { apk ->
            if (labTab == "Overview") {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(apk.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(apk.packageName, style = MaterialTheme.typography.bodySmall)
                    Text("Version: " + (apk.version ?: "unknown") + " (" + apk.versionCode + ")")
                    apk.inspectionNotes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text("APK size: " + (apk.bytes / 1024 / 1024) + " MB")
                    Text("Local scan time: " + apk.scanDurationMs + " ms")
                    Text("SHA-256: " + apk.sha256, style = MaterialTheme.typography.bodySmall, fontFamily = SakuraMono)
                    Text("DEX: " + apk.dexCount + "  •  Native libraries: " + apk.nativeLibraryCount)
                    Text("DEX method IDs: " + apk.dexHeaders.sumOf { it.methodIds })
                    Text("DEX class definitions: " + apk.dexHeaders.sumOf { it.classDefs })
                    Text("Archive entries: " + apk.entriesCount)
                    Text("Manifest: " + if (apk.hasManifest) "Present" else "Missing")
                    Text("Resources: " + if (apk.hasResources) "Present" else "Not present")
                    Text("Requested permissions: " + apk.permissions.size)
                    if (apk.permissions.isNotEmpty()) {
                        apk.permissions.take(12).forEach { Text("• " + it, style = MaterialTheme.typography.bodySmall) }
                        if (apk.permissions.size > 12) Text("+" + (apk.permissions.size - 12) + " more permissions")
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Patch compatibility", style = MaterialTheme.typography.titleMedium)
                    Text(
                        checks.count { it.status == "Package/version match" }.toString() + " version-matched • " +
                            checks.count { it.status == "Universal" } + " universal • " +
                            checks.count { it.status == "Other package" } + " other-package",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Compatibility is calculated from loaded source metadata, not actual fingerprints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            }
            if (labTab == "Patches") {
            if (checks.isNotEmpty()) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    label = { Text(stringResource(R.string.sakura_ui_find_a_patch_or_source)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Package/version match", "Universal", "Other package", "Version mismatch").forEach { kind ->
                        FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind) })
                    }
                }
                shownChecks.forEach { check ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(check.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(check.source + " • " + check.status, style = MaterialTheme.typography.bodySmall)
                            if (check.target.isNotBlank()) Text(
                                check.target, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (checks.size > shownChecks.size) Text(
                    "Showing up to 80 matching patches. Use search to narrow the list.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(stringResource(R.string.sakura_ui_no_loaded_patch_metadata_yet_add_or_update_patch_sources_in_the_s))
            }

            }
            if (labTab == "Diff") {
            if (comparison == null) {
                Card {
                    Text(
                        "Select Compare APK to compare components, permissions, certificates, method instruction hashes, resources, and native libraries.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            if (comparison != null) {
                val other = comparison!!
                val addedPermissions = other.permissions.toSet() - apk.permissions.toSet()
                val removedPermissions = apk.permissions.toSet() - other.permissions.toSet()
                val added = other.entryIndex.keys - apk.entryIndex.keys
                val removed = apk.entryIndex.keys - other.entryIndex.keys
                val changed = apk.entryIndex.keys.intersect(other.entryIndex.keys).count {
                    apk.entryIndex[it] != other.entryIndex[it]
                }
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sakura Diff - archive comparison", style = MaterialTheme.typography.titleMedium)
                        Text(apk.fileName + " ↔ " + other.fileName)
                        Text("Certificates changed: " + (apk.certificates != other.certificates))
                        Text("Components added: ${(other.components - apk.components).size}; removed: ${(apk.components - other.components).size}")
                        Text("Methods added: ${(other.methods.keys - apk.methods.keys).size}; removed: ${(apk.methods.keys - other.methods.keys).size}")
                        Text("Methods with changed instruction bytes: " + apk.methods.keys.intersect(other.methods.keys).count { apk.methods[it] != other.methods[it] })
                        Text("Method inspection is capped at 50,000. Instruction hashes can change when DEX indexes change. Export the report for method names and resource/native paths.", style = MaterialTheme.typography.bodySmall)
                        Text("Added paths: " + added.size)
                        Text("Removed paths: " + removed.size)
                        Text("Changed ZIP CRC / size: " + changed)
                        Text("DEX class-definition count change: " +
                            (other.dexHeaders.sumOf { it.classDefs } - apk.dexHeaders.sumOf { it.classDefs }))
                        Text("DEX method-ID count change: " +
                            (other.dexHeaders.sumOf { it.methodIds } - apk.dexHeaders.sumOf { it.methodIds }))
                        Text("New requested permissions: " + addedPermissions.size)
                        Text("Removed requested permissions: " + removedPermissions.size)
                        addedPermissions.take(6).forEach { Text("+ " + it, style = MaterialTheme.typography.bodySmall) }
                        removedPermissions.take(6).forEach { Text("- " + it, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "This compares ZIP entry metadata only. It is not a DEX semantic diff or security audit.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (apk.entriesCount > 20000 || other.entriesCount > 20000) {
                            Text(stringResource(R.string.sakura_ui_archive_index_capped_at_20_000_entries_per_apk))
                        }
                        Text("Example changes:", style = MaterialTheme.typography.labelLarge)
                        (added.take(5).map { "+ " + it } + removed.take(5).map { "- " + it })
                            .forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            }
            if (labTab == "Report") {
                Card {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Local diagnostic report", style = MaterialTheme.typography.titleMedium)
                        Text(report, style = MaterialTheme.typography.bodySmall, fontFamily = SakuraMono)
                        Text(
                            "These checks do not prove bytecode fingerprint compatibility.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Sakura PatchLab report", report))
                }) { Text(stringResource(R.string.sakura_ui_copy_diagnostic_report)) }
                OutlinedButton(onClick = {
                    reportExportLauncher.launch("sakura-patchlab-report.txt")
                }) { Text(stringResource(R.string.sakura_ui_export_report_txt)) }
            }
        }
    }
}
