package app.morphe.manager.domain.workspace

import android.content.Context
import android.util.AtomicFile
import app.morphe.manager.BuildConfig
import app.morphe.manager.domain.bundles.PatchBundleSource
import app.morphe.manager.domain.bundles.RemotePatchBundle
import app.morphe.manager.domain.manager.PreferencesManager
import app.morphe.manager.domain.repository.PatchBundleRepository
import app.morphe.manager.patcher.patch.optionValueToJson
import app.morphe.manager.patcher.patch.optionValueFromJson
import app.morphe.manager.ui.model.SelectedApp
import app.morphe.manager.ui.model.navigation.Patcher
import app.morphe.manager.util.Options
import app.morphe.manager.util.PatchSelection
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Persistent bounded archive. Call from Dispatchers.IO. */
class BuildWorkspace(private val context: Context) {
    private val root = File(context.filesDir, "build-workspace").apply { mkdirs() }
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private fun directory(id: String): File {
        require(Regex("[a-zA-Z0-9-]{1,80}").matches(id))
        return root.resolve(id)
    }
    fun readAll(): List<BuildRecipe> = root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
        runCatching { json.decodeFromString<BuildRecipe>(dir.resolve("recipe.json").readText()) }.getOrNull()
    }.map { if (it.status == "RUNNING" && it.sessionId != sessionId) it.copy(status = "INTERRUPTED", error = "The previous process ended. Restart from the retained input and recipe.") else it }.sortedByDescending { it.createdAt }
    fun input(recipe: BuildRecipe) = directory(recipe.id).resolve("input.apk")
    fun output(recipe: BuildRecipe) = directory(recipe.id).resolve("output.apk")
    fun pinnedSources(recipe: BuildRecipe): Map<Int, String> = recipe.sources.associate { source ->
        val uid = requireNotNull(source.sourceUid)
        uid to directory(recipe.id).resolve("source-$uid.jar").absolutePath
    }
    fun exportSources(recipe: BuildRecipe, output: java.io.OutputStream) {
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("recipe.json")); zip.write(export(recipe).toByteArray()); zip.closeEntry()
            recipe.sources.forEachIndexed { index, source ->
                val uid = source.sourceUid ?: return@forEachIndexed
                val file = directory(recipe.id).resolve("source-$uid.jar")
                require(file.isFile && sha256(file) == source.sha256) { "Archived source is missing or changed" }
                zip.putNextEntry(java.util.zip.ZipEntry("source-$index.jar")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }
    fun logs(recipe: BuildRecipe) = directory(recipe.id).resolve("build.log").takeIf { it.exists() }?.readText().orEmpty()
    fun export(recipe: BuildRecipe): String = json.encodeToString(recipe)
    fun save(recipe: BuildRecipe) {
        val dir = directory(recipe.id).apply { mkdirs() }
        val atomic = AtomicFile(dir.resolve("recipe.json")); val stream = atomic.startWrite()
        try { stream.write(export(recipe).toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun appendLog(id: String, message: String) = synchronized(logLock) {
        val file = directory(id).resolve("build.log")
        if (file.length() < 1024 * 1024) file.appendText(message.take(16000) + "\n")
    }
    fun delete(recipe: BuildRecipe) { directory(recipe.id).deleteRecursively() }
    suspend fun begin(input: File, packageName: String, version: String?, versionCode: Long?, selection: PatchSelection,
                      options: Options, repo: PatchBundleRepository, prefs: PreferencesManager, dryRun: Boolean): BuildRecipe {
        require(input.isFile && input.length() > 0) { "Input APK is missing" }
        require(root.usableSpace > input.length() * 3 + 128L * 1024 * 1024) { "Not enough storage to retain input and build safely" }
        readAll().filter { it.status != "RUNNING" }.drop(9).forEach(::delete)
        val sources = repo.sources.first()
        val id = UUID.randomUUID().toString(); val dir = directory(id).apply { mkdirs() }
        try {
            input.copyTo(dir.resolve("input.apk"))
            val recipe = BuildRecipe(id = id, createdAt = System.currentTimeMillis(), packageName = packageName,
                versionName = version, versionCode = versionCode, inputSha256 = sha256(dir.resolve("input.apk")),
                sources = selection.map { (uid, patches) ->
                    val source = sources.single { it.uid == uid }
                    val pinned = dir.resolve("source-$uid.jar")
                    source.patchesJarFile.copyTo(pinned)
                    pinned.setReadOnly()
                    val hash = sha256(pinned)
                    val identity = if (source is RemotePatchBundle) sourceIdentity(source) else "local:$hash"
                    RecipeSource(identity, source.name, app.morphe.manager.patcher.patch.PatchBundle(pinned.path).manifestAttributes?.version ?: source.version, hash, patches,
                        options[uid].orEmpty().mapValues { (_, values) -> values.mapValues { optionValueToJson(it.value) } }, sourceUid = uid)
                }, managerVersion = BuildConfig.VERSION_NAME, patcherVersion = BuildConfig.PATCHER_VERSION,
                allowCrossPackage = prefs.allowCrossPackagePatches.get(), stripNativeLibs = prefs.stripUnusedNativeLibs.get(), dryRun = dryRun, sessionId = sessionId)
            save(recipe); return recipe
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }
    fun finish(recipe: BuildRecipe, status: String, file: File?, error: String?) {
        val outputHash = file?.takeIf { it.isFile && status == "SUCCESS" }?.let {
            it.copyTo(output(recipe), overwrite = true); sha256(output(recipe))
        }
        save(recipe.copy(status = status, outputSha256 = outputHash, error = error?.take(12000), elapsedMs = System.currentTimeMillis() - recipe.createdAt))
    }
    fun importRecipe(text: String): BuildRecipe {
        require(text.length <= 1024 * 1024) { "Recipe exceeds 1 MiB" }
        val parsed = json.decodeFromString<BuildRecipe>(text)
        require(parsed.schema == 1 && parsed.packageName.isNotBlank() && parsed.sources.size in 1..100)
        require(Regex("[0-9a-fA-F]{64}").matches(parsed.inputSha256)) { "Invalid input hash" }
        require(parsed.sources.all { it.patches.size in 1..2000 && Regex("[0-9a-fA-F]{64}").matches(it.sha256) }) { "Invalid source hash or patch selection" }
        val imported = parsed.copy(id = UUID.randomUUID().toString(), createdAt = System.currentTimeMillis(), status = "IMPORTED", outputSha256 = null)
        save(imported); return imported
    }
    suspend fun prepare(recipe: BuildRecipe, apk: File, repo: PatchBundleRepository, prefs: PreferencesManager): Pair<Patcher.ViewModelParams, List<String>> {
        val info = InstallDiagnostics.archive(context, apk) ?: error("Select a standalone APK with a readable manifest")
        require(info.packageName == recipe.packageName) { "APK belongs to ${info.packageName}; recipe targets ${recipe.packageName}" }
        val sources = repo.sources.first(); val identities = sources.associate { it.uid to sourceIdentity(it) }
        val metadata = repo.allBundlesInfoFlow.first(); val changes = mutableListOf<String>()
        val selection = mutableMapOf<Int, Set<String>>(); val options = mutableMapOf<Int, Map<String, Map<String, Any?>>>()
        recipe.sources.forEach { saved ->
            val uid = resolveSourceIdentity(saved.identity, identities); val source = sources.single { it.uid == uid }
            require(source.enabled) { "Enable source ${source.name} first" }
            val scoped = metadata[uid]?.forPackage(recipe.packageName, info.versionName, InstallDiagnostics.version(info), allowAllPackages = recipe.allowCrossPackage) ?: error("Source ${saved.name} is not loaded")
            val names = scoped.patches.map { it.name }.toSet()
            changes += recipeChanges(saved, source.version, sha256(source.patchesJarFile), names)
            require(names.containsAll(saved.patches)) { "Missing patches in ${saved.name}: ${(saved.patches - names).joinToString()}. Review patch selection instead." }
            selection[uid] = saved.patches
            options[uid] = saved.options.mapValues { (_, values) -> values.mapValues { optionValueFromJson(it.value) } }
        }
        if (sha256(apk) != recipe.inputSha256) changes += "Input changed: ${recipe.versionName} → ${info.versionName}. Run a dry run before rebuilding."
        if (recipe.patcherVersion != BuildConfig.PATCHER_VERSION) changes += "Patcher engine changed: ${recipe.patcherVersion} → ${BuildConfig.PATCHER_VERSION}"
        require(recipe.allowCrossPackage == prefs.allowCrossPackagePatches.get() && recipe.stripNativeLibs == prefs.stripUnusedNativeLibs.get()) { "Patcher settings differ. Review cross-package and native-library settings before rebuilding." }
        return Patcher.ViewModelParams(SelectedApp.Local(info.packageName, info.versionName.orEmpty(), InstallDiagnostics.version(info), apk, temporary = apk.parentFile == context.cacheDir), selection, options, useRecipeOptions = true) to changes
    }
    companion object {
        private val logLock = Any()
        private val sessionId = UUID.randomUUID().toString()
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun sourceIdentity(source: PatchBundleSource): String = if (source is RemotePatchBundle) "remote:${source.endpoint.trim().trimEnd('/')}" else "local:${sha256(source.patchesJarFile)}"
    }
}
