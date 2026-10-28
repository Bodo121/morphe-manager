package app.morphe.manager.patcher

import app.morphe.manager.patcher.logger.*
import app.morphe.manager.patcher.split.SplitPreparationEvent
import app.morphe.patcher.patch.bytecodePatch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.test.*

class DryRunRequestTest {
    private fun request(dryRun: Boolean = false) = PatchRequest("test.app", inputFile = "/missing.apk", outputFile = "/unused.apk",
        mergedApkFile = "/merged.apk", temporaryDir = "/tmp", workspaceDir = "/tmp",
        bundles = listOf(PatchRequest.Bundle(1, "/source.jar", setOf("Removed patch"), emptyMap())),
        stripUnusedNativeLibs = false, device = DeviceConfig(listOf("x86_64"), listOf("en"), 320), dryRun = dryRun)
    @Test fun dryRunSurvivesProcessSerialization() {
        val req = request(true)
        assertEquals(req, Json.decodeFromString<PatchRequest>(Json.encodeToString(req)))
        assertFalse(request().dryRun)
    }
    @Test fun missingSelectedPatchFailsBeforeReadingInput() = runBlocking {
        val logger = object : Logger() { override fun log(level: LogLevel, message: String) {} }
        val listener = object : PatchingListener {
            override suspend fun stepCompleted() {}
            override suspend fun splitPreparation(event: SplitPreparationEvent) {}
            override suspend fun patchesStarted() {}
            override suspend fun patchSucceeded(name: String) {}
            override suspend fun patchFailed(name: String?, stackTrace: String) {}
            override suspend fun patchesFinished(count: Int) {}
        }
        val error = assertFailsWith<IllegalArgumentException> {
            PatchPipeline(request(true), logger, listener) { listOf(bytecodePatch(name = "Available patch") {}) }.run()
        }
        assertTrue(error.message.orEmpty().contains("Selected patches are missing"))
    }
}
