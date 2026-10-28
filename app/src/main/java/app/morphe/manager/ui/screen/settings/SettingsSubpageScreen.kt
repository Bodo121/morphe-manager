/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.settings

import app.morphe.manager.R
import androidx.activity.ComponentActivity
import app.morphe.manager.ui.screen.settings.advanced.SakuraBuildWorkspaceScreen
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.morphe.manager.ui.screen.settings.advanced.PatchOptionsSection
import app.morphe.manager.ui.screen.settings.advanced.PatcherSettings
import app.morphe.manager.ui.screen.settings.advanced.SakuraPatchLabScreen
import app.morphe.manager.ui.screen.settings.advanced.SakuraProfilesScreen
import app.morphe.manager.ui.screen.settings.system.AboutSettings
import app.morphe.manager.ui.screen.settings.system.ApkDownloadHelpersSettings
import app.morphe.manager.ui.screen.settings.system.InstallerSettings
import app.morphe.manager.ui.screen.settings.system.NotificationsSettings
import app.morphe.manager.ui.screen.settings.system.StorageSettings
import app.morphe.manager.ui.screen.shared.SubpageScaffold
import app.morphe.manager.ui.viewmodel.HomeViewModel
import app.morphe.manager.ui.viewmodel.PatchOptionsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * One [subpage] of the settings, opened from its row on the settings page.
 *
 * @param navigation Where the page leads out to.
 */
@Composable
fun SettingsSubpageScreen(
    subpage: SettingsSubpage,
    homeViewModel: HomeViewModel,
    navigation: SettingsNavigation,
    onBack: () -> Unit
) {
    // PatchLab contains its own large scrollable diagnostic report. Avoid nesting that
    // scroll container inside SubpageScaffold's verticalScroll (infinite constraints).
    if (subpage == SettingsSubpage.PATCH_LAB || subpage == SettingsSubpage.PROFILES || subpage == SettingsSubpage.BUILD_WORKSPACE) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.sakura_ui_back_to_settings)) }
            Box(Modifier.weight(1f)) {
                if (subpage == SettingsSubpage.BUILD_WORKSPACE) SakuraBuildWorkspaceScreen(navigation.startBuild)
                else if (subpage == SettingsSubpage.PROFILES) SakuraProfilesScreen()
                else SakuraPatchLabScreen()
            }
        }
        return
    }
    SubpageScaffold(title = stringResource(subpage.titleRes), onBack = onBack) {
        SettingsSubpageContent(subpage, homeViewModel, navigation)
    }
}

/** What [subpage] holds, on its own page or among what a search of the settings page finds. */
@Composable
internal fun SettingsSubpageContent(
    subpage: SettingsSubpage,
    homeViewModel: HomeViewModel,
    navigation: SettingsNavigation
) {
    when (subpage) {
        SettingsSubpage.PATCH_OPTIONS -> PatchOptionsSettings(homeViewModel)
        SettingsSubpage.APPEARANCE -> AppearanceSettings()
        SettingsSubpage.NOTIFICATIONS -> NotificationsSettings()
        SettingsSubpage.INSTALLER -> InstallerSettings()
        SettingsSubpage.PATCHER -> PatcherSettings()
        SettingsSubpage.PATCH_LAB -> SakuraPatchLabScreen()
        SettingsSubpage.BUILD_WORKSPACE -> SakuraBuildWorkspaceScreen(navigation.startBuild)
        SettingsSubpage.PROFILES -> SakuraProfilesScreen()
        SettingsSubpage.STORAGE -> StorageSettings()
        SettingsSubpage.APK_DOWNLOAD_HELPERS -> ApkDownloadHelpersSettings()
        SettingsSubpage.ABOUT -> AboutSettings(
            onOpenLicenses = navigation.openLicenses,
            onStartTour = navigation.startTour
        )
    }
}

/** The options simple mode patches apps with, which expert mode picks patch by patch instead. */
@Composable
private fun PatchOptionsSettings(
    homeViewModel: HomeViewModel,
    patchOptionsViewModel: PatchOptionsViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    PatchOptionsSection(
        patchOptionsPrefs = patchOptionsViewModel.patchOptionsPrefs,
        patchOptionsViewModel = patchOptionsViewModel,
        homeViewModel = homeViewModel
    )
}
