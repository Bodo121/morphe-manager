package app.morphe.manager.domain.workspace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RecipeSource(val identity: String, val name: String, val version: String?, val sha256: String,
    val patches: Set<String>, val options: Map<String, Map<String, JsonElement>> = emptyMap(), val sourceUid: Int? = null)

@Serializable
data class BuildRecipe(val schema: Int = 1, val id: String, val createdAt: Long, val packageName: String,
    val versionName: String?, val versionCode: Long?, val inputSha256: String, val sources: List<RecipeSource>,
    val managerVersion: String, val patcherVersion: String, val allowCrossPackage: Boolean = false,
    val stripNativeLibs: Boolean = false, val status: String = "RUNNING", val outputSha256: String? = null,
    val error: String? = null, val dryRun: Boolean = false, val elapsedMs: Long = 0,
    val sessionId: String = "")

/** Device-local numeric IDs never identify a portable source. */
fun resolveSourceIdentity(identity: String, available: Map<Int, String>): Int {
    val matches = available.filterValues { it == identity }.keys
    require(matches.size == 1) { if (matches.isEmpty()) "Required source is missing" else "Source identity is ambiguous; remove duplicate sources" }
    return matches.single()
}
fun recipeChanges(saved: RecipeSource, version: String?, hash: String, available: Set<String>): List<String> = buildList {
    if (saved.sha256 != hash) add("${saved.name}: bundle changed (${saved.version ?: "unknown"} → ${version ?: "unknown"})")
    val missing = saved.patches - available
    if (missing.isNotEmpty()) add("${saved.name}: missing patches: ${missing.sorted().joinToString()}")
}
