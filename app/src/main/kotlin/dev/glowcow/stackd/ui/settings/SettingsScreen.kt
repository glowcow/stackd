package dev.glowcow.stackd.ui.settings

import android.Manifest
import android.app.LocaleManager
import android.os.LocaleList
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glowcow.stackd.R
import dev.glowcow.stackd.StackdApp
import dev.glowcow.stackd.data.AppSettings
import dev.glowcow.stackd.data.Palette
import dev.glowcow.stackd.data.SettingsStore
import dev.glowcow.stackd.data.ThemeMode
import dev.glowcow.stackd.ui.components.BottomBar
import dev.glowcow.stackd.ui.components.Group
import dev.glowcow.stackd.ui.components.GroupDivider
import dev.glowcow.stackd.ui.components.GroupRow
import dev.glowcow.stackd.ui.components.GroupSheet
import dev.glowcow.stackd.ui.components.TopTab
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.update.AppRelease
import dev.glowcow.stackd.update.AppUpdateState
import dev.glowcow.stackd.update.AppUpdater
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore, val updater: AppUpdater) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
    fun setPalette(palette: Palette) = viewModelScope.launch { store.setPalette(palette) }
    fun setMaxBrightness(on: Boolean) = viewModelScope.launch { store.setMaxBrightness(on) }
    fun setAutoUpdate(on: Boolean) = viewModelScope.launch { store.setAutoUpdate(on) }
    fun setUpdateHours(hours: Int) = viewModelScope.launch { store.setUpdateHours(hours) }
    fun setAppUpdate(on: Boolean) = viewModelScope.launch { store.setAppUpdate(on) }
}

@Composable
fun SettingsScreen(
    onTab: (TopTab) -> Unit,
    vm: SettingsViewModel = viewModel { (this[APPLICATION_KEY] as StackdApp).container.let { SettingsViewModel(it.settings, it.appUpdater) } },
) {
    val c = StackdTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val update by vm.updater.state.collectAsStateWithLifecycle()
    // The system keeps the per-app language, shared with Settings → Apps → App language.
    val locales = remember(context) { context.getSystemService(LocaleManager::class.java) }
    // Hebrew reports its old code on some system versions.
    var language by remember { mutableStateOf(locales.applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language?.let { if (it == "iw") "he" else it }) }
    // Background updates report through notifications, so turning them on asks for the permission.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun setAutoUpdate(on: Boolean) {
        vm.setAutoUpdate(on)
        if (on) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun setAppUpdate(on: Boolean) {
        vm.setAppUpdate(on)
        if (on) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    fun setLanguage(tag: String?) {
        language = tag
        locales.applicationLocales = tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }

    Column(Modifier.fillMaxSize().background(c.groupBg)) {
        Text(
            stringResource(R.string.tab_settings),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = c.text,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Group {
                GroupRow(
                    stringResource(R.string.settings_theme),
                    value = stringResource(THEMES.first { it.first == settings.theme }.second),
                    onClick = { picker = Picker.THEME },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_palette),
                    value = stringResource(PALETTES.first { it.first == settings.palette }.second),
                    onClick = { picker = Picker.PALETTE },
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_language),
                    value = LANGUAGES.firstOrNull { it.first == language }?.second ?: stringResource(R.string.language_system),
                    onClick = { picker = Picker.LANGUAGE },
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_brightness),
                    subtitle = stringResource(R.string.settings_brightness_hint),
                    onClick = { vm.setMaxBrightness(!settings.maxBrightness) },
                    trailing = {
                        Switch(
                            checked = settings.maxBrightness,
                            onCheckedChange = { vm.setMaxBrightness(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedTrackColor = c.chip, uncheckedBorderColor = c.line),
                        )
                    },
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_auto_update),
                    subtitle = stringResource(R.string.settings_auto_update_hint),
                    onClick = { setAutoUpdate(!settings.autoUpdate) },
                    trailing = {
                        Switch(
                            checked = settings.autoUpdate,
                            onCheckedChange = { setAutoUpdate(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedTrackColor = c.chip, uncheckedBorderColor = c.line),
                        )
                    },
                )
                if (settings.autoUpdate) {
                    GroupDivider()
                    GroupRow(
                        stringResource(R.string.settings_update_interval),
                        value = hoursLabel(settings.updateHours),
                        onClick = { picker = Picker.INTERVAL },
                    )
                }
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_version),
                    subtitle = when (val s = update) {
                        AppUpdateState.Idle -> if (vm.updater.supported) null else stringResource(R.string.app_update_dev)
                        AppUpdateState.Checking -> stringResource(R.string.app_update_checking)
                        AppUpdateState.UpToDate -> stringResource(R.string.app_update_latest)
                        AppUpdateState.Failed -> stringResource(R.string.app_update_failed)
                        is AppUpdateState.Available -> stringResource(R.string.app_update_available, s.release.version)
                        is AppUpdateState.Downloading -> stringResource(R.string.app_update_downloading, (s.progress * 100).toInt())
                    },
                    value = vm.updater.current,
                    onClick = when (val s = update) {
                        AppUpdateState.Checking, is AppUpdateState.Downloading -> null
                        is AppUpdateState.Available -> ({ release = s.release })
                        else -> if (vm.updater.supported) vm.updater::checkNow else null
                    },
                )
                if (vm.updater.supported) {
                    GroupDivider()
                    GroupRow(
                        stringResource(R.string.settings_app_update),
                        subtitle = stringResource(R.string.settings_app_update_hint),
                        onClick = { setAppUpdate(!settings.appUpdate) },
                        trailing = {
                            Switch(
                                checked = settings.appUpdate,
                                onCheckedChange = { setAppUpdate(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, uncheckedTrackColor = c.chip, uncheckedBorderColor = c.line),
                            )
                        },
                    )
                }
                GroupDivider()
                GroupRow(stringResource(R.string.settings_font), value = "Arimo · SIL OFL 1.1")
            }
        }
        BottomBar(TopTab.SETTINGS, onTab)
    }

    release?.let { r ->
        GroupSheet(stringResource(R.string.app_update_available, r.version), onDismiss = { release = null }) { pick ->
            if (r.notes.isNotEmpty()) {
                Text(
                    r.notes,
                    color = c.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
                )
            }
            Group { GroupRow(stringResource(R.string.app_update_install), onClick = { pick { vm.updater.install(r) } }) }
        }
    }

    when (picker) {
        Picker.THEME -> ChoiceSheet(
            title = stringResource(R.string.settings_theme),
            options = THEMES.map { (mode, label) -> mode to stringResource(label) },
            selected = settings.theme,
            onSelect = { vm.setTheme(it) },
            onDismiss = { picker = null },
        )
        Picker.PALETTE -> ChoiceSheet(
            title = stringResource(R.string.settings_palette),
            options = PALETTES.map { (palette, label) -> palette to stringResource(label) },
            selected = settings.palette,
            onSelect = { vm.setPalette(it) },
            onDismiss = { picker = null },
        )
        Picker.LANGUAGE -> ChoiceSheet(
            title = stringResource(R.string.settings_language),
            options = listOf<Pair<String?, String>>(null to stringResource(R.string.language_system)) + LANGUAGES,
            selected = language,
            onSelect = ::setLanguage,
            onDismiss = { picker = null },
        )
        Picker.INTERVAL -> ChoiceSheet(
            title = stringResource(R.string.settings_update_interval),
            options = UPDATE_HOURS.map { it to hoursLabel(it) },
            selected = settings.updateHours,
            onSelect = { vm.setUpdateHours(it) },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

private enum class Picker { THEME, PALETTE, LANGUAGE, INTERVAL }

private val UPDATE_HOURS = listOf(1, 3, 6, 12, 24)

@Composable
private fun hoursLabel(hours: Int) = pluralStringResource(R.plurals.hours, hours, hours)

private val THEMES = listOf(
    ThemeMode.SYSTEM to R.string.theme_system,
    ThemeMode.LIGHT to R.string.theme_light,
    ThemeMode.DARK to R.string.theme_dark,
)

private val PALETTES = listOf(
    Palette.CLASSIC to R.string.palette_classic,
    Palette.WARM to R.string.palette_warm,
)

/** Language tags with names written in that language. */
private val LANGUAGES = listOf(
    "en" to "English",
    "be" to "Беларуская",
    "de" to "Deutsch",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "pl" to "Polski",
    "ru" to "Русский",
    "sr" to "Српски",
    "he" to "עברית",
)

/** Single choice in a bottom sheet; the current option carries a check mark. */
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) = GroupSheet(title, onDismiss) { pick ->
    val accent = StackdTheme.colors.accent
    Group {
        options.forEachIndexed { i, (value, label) ->
            if (i > 0) GroupDivider()
            GroupRow(label, onClick = { pick { onSelect(value) } }, trailing = {
                if (value == selected) Icon(StackdIcons.Check, null, tint = accent, modifier = Modifier.size(20.dp))
            })
        }
    }
}
