/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.shared

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.morphe.manager.SakuraCrashRecovery
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.manager.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.*
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** The places the main screen pages between, in the order its pager lays them out. */
enum class MainPage(@param:StringRes val titleRes: Int, val icon: ImageVector) {
    APPS(R.string.sakura_nav_home, MaterialSymbols.Rounded.Home),
    PATCHER(R.string.sakura_nav_patcher, MaterialSymbols.Rounded.Auto_fix_high),
    PATCH_LAB(R.string.sakura_nav_lab, MaterialSymbols.Rounded.Tune),
    SOURCES(R.string.sources, MaterialSymbols.Rounded.Folder_code),
    SETTINGS(R.string.settings, MaterialSymbols.Rounded.Settings)
}

/**
 * Turns the main pager to [page], sliding across one page at most. A page further away is jumped
 * next to first, so the pages between are never drawn on the way and the slide costs what a swipe does.
 */
suspend fun PagerState.turnTo(page: MainPage) {
    if (kotlin.math.abs(currentPage - page.ordinal) == 1) {
        animateScrollToPage(page.ordinal)
    } else {
        scrollToPage(page.ordinal)
    }
}

/**
 * The main screen: its [MainPage]s side by side, swiped through or picked on a pill floating over
 * their lower edge, or on a sidebar in landscape. Each page is handed the height of the pill, which
 * it leaves clear at the end of its scroll, and runs on under the status bar.
 *
 * Back on a side page returns to the apps, as leaving any other tab returns to the first one.
 *
 * @param navigationCovered Whether something docked over the bottom of a page stands in for the
 *   navigation, such as a selection's action bar. The pill fades out and swiping holds off. The
 *   landscape sidebar stays, as the bars dock beside it.
 * @param swipeEnabled Whether the pages follow a swipe, rather than only a pick on the navigation.
 * @param settingsIcon The settings page's icon, which marks expert mode.
 * @param settingsDescription What a screen reader says for the settings page, when its icon says more
 *   than its label.
 * @param onItemPositioned Where each page's navigation item sits, for the tour to point at.
 * @param overlay Drawn over the pages rather than in one, so it stays put as they are swiped.
 */
@Composable
fun MainPagerScaffold(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    navigationCovered: Boolean = false,
    swipeEnabled: Boolean = true,
    settingsIcon: ImageVector = MainPage.SETTINGS.icon,
    settingsDescription: String? = null,
    onItemPositioned: ((MainPage, Rect) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    page: @Composable (page: MainPage, bottomBarHeight: Dp) -> Unit
) {
    val scope = rememberCoroutineScope()
    val current = MainPage.entries[pagerState.currentPage]
    val select: (MainPage) -> Unit = { target ->
        scope.launch { pagerState.turnTo(target) }
    }
    var predictiveProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = current != MainPage.APPS) { events ->
        try {
            events.collect { event -> predictiveProgress = event.progress.coerceIn(0f, 1f) }
            pagerState.turnTo(MainPage.APPS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            predictiveProgress = 0f
        }
    }

    val reducedUi = SakuraCrashRecovery.safeMode(LocalContext.current)
    val visiblePages = if (reducedUi) {
        listOf(MainPage.APPS, MainPage.SOURCES, MainPage.SETTINGS)
    } else MainPage.entries
    val items = visiblePages.map { target ->
        MainNavItem(
            page = target,
            icon = if (target == MainPage.SETTINGS) settingsIcon else target.icon,
            label = stringResource(target.titleRes),
            contentDescription = settingsDescription.takeIf { target == MainPage.SETTINGS },
            modifier = onItemPositioned?.let { onPositioned ->
                Modifier.onGloballyPositioned { onPositioned(target, it.boundsInWindow()) }
            } ?: Modifier
        )
    }
    val userScrollEnabled = swipeEnabled && !navigationCovered
    val pager: @Composable (Modifier, FocusRequester?, Dp) -> Unit = { pagerModifier, startFocus, bottomBarHeight ->
        // A remote steps off a page onto the navigation beside it, if any, never into the next page
        val pageFocus = rememberPagerPageFocus(startFocus)
        Box(modifier = pagerModifier.graphicsLayer {
            val scale = 1f - predictiveProgress * 0.045f
            scaleX = scale
            scaleY = scale
        }) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = userScrollEnabled,
                key = { index -> MainPage.entries[index] }
            ) { index ->
                Box(modifier = Modifier.fillMaxSize().then(pageFocus)) { page(MainPage.entries[index], bottomBarHeight) }
            }
            overlay()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape()) {
            val sidebarFocus = remember { FocusRequester() }
            Row(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                MainNavigationSidebar(
                    items = items,
                    current = current,
                    onSelect = select,
                    currentFocus = sidebarFocus,
                    modifier = Modifier.statusBarsPadding()
                )
                VerticalDivider(modifier = Modifier.statusBarsPadding().padding(vertical = 20.dp))
                pager(Modifier.weight(1f).fillMaxHeight(), sidebarFocus, 0.dp)
            }
        } else {
            // Material3's NavigationBar owns its own window insets. Scaffold keeps it
            // separate from the pager, avoiding floating-bar measurement/focus conflicts.
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!navigationCovered) {
                        // The React reference uses a 64dp tall, five-item bottom bar,
                        // a 56×32dp active indicator and 11sp labels.
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items.forEach { item ->
                                    val selected = item.page == current
                                    val ink = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    Column(
                                        modifier = item.modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(role = Role.Tab, onClick = { select(item.page) }),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier.size(width = 56.dp, height = 32.dp)
                                                .background(
                                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .15f)
                                                    else Color.Transparent,
                                                    RoundedCornerShape(percent = 50)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                item.icon,
                                                contentDescription = item.contentDescription ?: item.label,
                                                tint = ink,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Text(
                                            item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ink,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                // The content is measured above navigation, so children need no
                // additional floating-bar bottom inset.
                pager(Modifier.fillMaxSize().padding(padding), null, 0.dp)
            }
        }
    }
}

/** One page's entry on the navigation, as the pill and the sidebar both draw it. */
private class MainNavItem(
    val page: MainPage,
    val icon: ImageVector,
    val label: String,
    val contentDescription: String?,
    val modifier: Modifier
)

/**
 * The navigation stacked down the side of a landscape screen, every label shown since there is the room.
 *
 * @param currentFocus Focuses the [current] page's item, where a remote stepping off the page lands.
 */
@Composable
private fun MainNavigationSidebar(
    items: List<MainNavItem>,
    current: MainPage,
    onSelect: (MainPage) -> Unit,
    currentFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(horizontal = Defaults.SpaceMedium, vertical = Defaults.SpaceExtraLarge),
        verticalArrangement = Arrangement.spacedBy(Defaults.SpaceMedium, Alignment.CenterVertically)
    ) {
        items.forEach { item ->
            GlassButton(
                icon = item.icon,
                label = item.label,
                contentDescription = item.contentDescription,
                selected = item.page == current,
                onClick = { onSelect(item.page) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (item.page == current) Modifier.focusRequester(currentFocus) else Modifier)
                    .then(item.modifier),
                hapticFeedback = true,
                showLabel = true
            )
        }
    }
}
