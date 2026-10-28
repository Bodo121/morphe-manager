package app.morphe.manager.domain.workspace
import kotlin.test.*
class PatchFailureAdviceTest {
    @Test fun fingerprintErrorsDoNotClaimAProvenConflict() {
        assertTrue(patchFailureAdvice("Fingerprint not found").contains("does not prove"))
    }
    @Test fun processDeathOffersRecipeRecovery() {
        assertTrue(patchFailureAdvice("process ended").contains("Build workspace"))
    }
    @Test fun memoryAdviceDoesNotPromiseMoreRam() {
        assertTrue(patchFailureAdvice("OutOfMemoryError").contains("cannot create physical RAM"))
    }
}
