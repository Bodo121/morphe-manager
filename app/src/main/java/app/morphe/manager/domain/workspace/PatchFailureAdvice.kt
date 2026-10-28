package app.morphe.manager.domain.workspace

/** Advice is diagnostic guidance, never an automatic change to a patch selection. */
fun patchFailureAdvice(message: String): String = when {
    message.contains("fingerprint", true) -> "A patch could not find the expected code. Check the exact APK version and source version, and try an unmodified original APK. If patches pass individually but fail together, disable one group and repeat the dry run. A fingerprint failure alone does not prove a patch conflict."
    message.contains("dependency", true) -> "A patch dependency failed. Review the first failing patch in the log, update its source, and rerun the dry run. Required dependencies are executed even when they are not explicitly selected."
    message.contains("OutOfMemory", true) || message.contains("heap", true) -> "Memory was exhausted. Close other heavy apps, use fewer patches, and retry. Raising a heap limit cannot create physical RAM."
    message.contains("killed", true) || message.contains("process ended", true) -> "Android ended the patcher process. On HyperOS, check the app's battery restrictions and keep the app visible for another attempt. Recover the saved recipe from Build workspace; partial output is not installable."
    message.contains("space", true) || message.contains("ENOSPC", true) -> "Free storage and retry. The build needs room for the original APK, temporary resources, and the finished APK."
    else -> "Review the first error and failing patch in the log. Confirm the APK version and source versions, then repeat the dry run with a smaller selection. Build workspace retains the recipe and diagnostic log."
}
