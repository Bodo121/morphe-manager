package app.morphe.manager.domain.workspace

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import app.morphe.manager.util.isNewerVersion

class RecipePolicyTest {
    @Test fun sourcesRemapWithoutDeviceIds() {
        assertEquals(902, resolveSourceIdentity("remote:https://example.test/bundle.json", mapOf(902 to "remote:https://example.test/bundle.json", 1 to "other")))
    }
    @Test fun ambiguityAndMissingSourcesFailClosed() {
        assertFailsWith<IllegalArgumentException> { resolveSourceIdentity("a", mapOf(1 to "a", 2 to "a")) }
        assertFailsWith<IllegalArgumentException> { resolveSourceIdentity("missing", mapOf(1 to "a")) }
    }
    @Test fun sourceDriftAndMissingPatchesAreReported() {
        val source = RecipeSource("id", "Patches", "1", "old", setOf("keep", "removed"))
        val changes = recipeChanges(source, "2", "new", setOf("keep"))
        assertEquals(2, changes.size)
        assertTrue(changes.last().contains("removed"))
        assertTrue(recipeChanges(source, "1", "old", source.patches).isEmpty())
    }
    @Test fun recipeRoundTripPreservesPatchOptionsAndProvenance() {
        val source = RecipeSource("remote:https://example.test", "Source", "1", "abc", setOf("Patch"), mapOf("Patch" to mapOf("enabled" to kotlinx.serialization.json.JsonPrimitive(true))))
        val recipe = BuildRecipe(id = "test", createdAt = 1, packageName = "test.app", versionName = "1", versionCode = 1,
            inputSha256 = "def", sources = listOf(source), managerVersion = "3.0.0-sakura.6", patcherVersion = "1", dryRun = true)
        assertEquals(recipe, Json.decodeFromString<BuildRecipe>(Json.encodeToString(recipe)))
    }
    @Test fun sakuraBuildNumbersSortNumerically() {
        assertTrue(isNewerVersion("3.0.0-sakura.9", "3.0.0-sakura.10"))
        assertFalse(isNewerVersion("3.0.0-sakura.10", "3.0.0-sakura.9"))
        assertTrue(isNewerVersion("2.2.0", "3.0.0-sakura.6"))
    }
}
