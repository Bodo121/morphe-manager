package app.morphe.manager.domain.workspace

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Read off the UI thread; Android still makes the final installation decision. */
object InstallDiagnostics {
    private val flags get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    fun archive(context: Context, file: File): PackageInfo? = context.packageManager.getPackageArchiveInfo(file.path,
        flags or PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
    fun signatures(info: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val certs = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return certs.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }
    fun version(info: PackageInfo): Long {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    fun requireOwnUpdate(context: Context, file: File) {
        val candidate = archive(context, file) ?: error("Cannot read the downloaded update APK")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        require(candidate.packageName == context.packageName) { "Update belongs to a different application" }
        require(signatures(candidate).isNotEmpty() && signatures(candidate) == signatures(installed)) { "Update signing certificate does not match this SakuraPatcher installation" }
        require(version(candidate) > version(installed)) { "Update is not newer than the installed build" }
    }
    fun inspect(context: Context, file: File): List<String> {
        val apk = archive(context, file) ?: return listOf("Unreadable APK manifest. Select a standalone APK; split archives must be merged by the patcher.")
        return buildList {
            if ((apk.applicationInfo?.minSdkVersion ?: 0) > Build.VERSION.SDK_INT) add("This APK requires a newer Android version.")
            if (apk.splitNames?.isNotEmpty() == true) add("Split package: install the complete set or patch the original split archive.")
            val installed = runCatching { context.packageManager.getPackageInfo(apk.packageName, flags) }.getOrNull()
            if (installed != null) {
                if (version(apk) < version(installed)) add("Version downgrade: Android normally rejects installing this over the newer installed app.")
                if (signatures(apk).isEmpty() || signatures(apk) != signatures(installed)) add("Signing certificate differs from the installed app. A normal update cannot replace it. Back up app data before considering uninstall; uninstalling deletes data.")
            }
            if (signatures(apk).isEmpty()) add("No signing certificate could be read. The file may be unsigned or invalid.")
            if (isEmpty()) add("No manifest, version, or certificate conflict found. Android still makes the final installation decision.")
        }
    }
}
