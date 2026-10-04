package dev.glowcow.stackd.ui

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.glowcow.stackd.barcode.BarcodeFormat
import dev.glowcow.stackd.data.CardSource
import dev.glowcow.stackd.ui.components.TopTab
import dev.glowcow.stackd.ui.edit.EditCardScreen
import dev.glowcow.stackd.ui.home.HomeScreen
import dev.glowcow.stackd.ui.scan.ScannerScreen
import dev.glowcow.stackd.ui.search.SearchScreen
import dev.glowcow.stackd.ui.settings.SettingsScreen
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute : NavKey
@Serializable data object SearchRoute : NavKey
@Serializable data object SettingsRoute : NavKey
/** Live barcode scanner, or with [photo] the camera that shoots a card cover. */
@Serializable data class ScannerRoute(val photo: Boolean = false) : NavKey
@Serializable data class EditRoute(val id: String? = null, val draft: CardDraft? = null) : NavKey

/** What the scanner hands to the editor for a new card. */
@Serializable
data class CardDraft(
    val source: CardSource,
    val value: String? = null,
    val format: BarcodeFormat? = null,
    val coverPath: String? = null,
)

@Composable
fun StackdRoot(openCard: Flow<String>, openSettings: Flow<Unit>) {
    val backStack = rememberNavBackStack(HomeRoute)
    // A card to open in the wallet on the home screen.
    var pendingOpen by rememberSaveable { mutableStateOf<String?>(null) }

    fun selectTab(tab: TopTab) {
        when (tab) {
            TopTab.SCANNER -> backStack.add(ScannerRoute())
            else -> {
                backStack.retainAll { it == HomeRoute }
                if (backStack.isEmpty()) backStack.add(HomeRoute)
                if (tab == TopTab.SEARCH) backStack.add(SearchRoute)
                if (tab == TopTab.SETTINGS) backStack.add(SettingsRoute)
            }
        }
    }

    fun showCard(id: String) {
        selectTab(TopTab.CARDS)
        pendingOpen = id
    }

    LaunchedEffect(openCard) { openCard.collect(::showCard) }
    LaunchedEffect(openSettings) { openSettings.collect { selectTab(TopTab.SETTINGS) } }

    // Status bar icons follow the theme, except over the always-dark scanner.
    val lightIcons = !StackdTheme.colors.isDark && backStack.lastOrNull() !is ScannerRoute
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightIcons
            isAppearanceLightNavigationBars = lightIcons
        }
    }

    val fade = fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS))
    // The scanner covers the screen from below and leaves the same way, following the back gesture.
    val slideDown = EnterTransition.None togetherWith slideOutVertically(tween(SHEET_MS)) { it }
    val sheet = NavDisplay.transitionSpec {
        slideInVertically(tween(SHEET_MS)) { it } togetherWith ExitTransition.KeepUntilTransitionsFinished
    } + NavDisplay.popTransitionSpec { slideDown } + NavDisplay.predictivePopTransitionSpec { slideDown }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { fade },
        popTransitionSpec = { fade },
        predictivePopTransitionSpec = { fade },
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    openRequest = pendingOpen,
                    onOpenHandled = { pendingOpen = null },
                    onScan = { backStack.add(ScannerRoute(photo = it)) },
                    onDraft = { backStack.add(EditRoute(draft = it)) },
                    onImported = ::showCard,
                    onEdit = { backStack.add(EditRoute(id = it)) },
                    onTab = ::selectTab,
                )
            }
            entry<SearchRoute> {
                SearchScreen(onOpen = ::showCard, onTab = ::selectTab)
            }
            entry<SettingsRoute> {
                SettingsScreen(onTab = ::selectTab)
            }
            entry<ScannerRoute>(metadata = sheet) { key ->
                ScannerScreen(
                    photo = key.photo,
                    onClose = { backStack.removeLastOrNull() },
                    onDraft = {
                        backStack.removeLastOrNull()
                        backStack.add(EditRoute(draft = it))
                    },
                )
            }
            entry<EditRoute> { key ->
                EditCardScreen(
                    route = key,
                    onClose = { backStack.removeLastOrNull() },
                    onSaved = { id -> if (key.id == null) showCard(id) else backStack.removeLastOrNull() },
                )
            }
        },
    )
}

private const val FADE_MS = 180
private const val SHEET_MS = 260
