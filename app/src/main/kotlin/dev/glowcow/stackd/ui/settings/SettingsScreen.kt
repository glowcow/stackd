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
import dev.glowcow.stackd.data.SettingsStore
import dev.glowcow.stackd.data.ThemeMode
import dev.glowcow.stackd.ui.components.BottomBar
import dev.glowcow.stackd.ui.components.Group
import dev.glowcow.stackd.ui.components.GroupDivider
import dev.glowcow.stackd.ui.components.GroupRow
import dev.glowcow.stackd.ui.components.GroupSheet
import dev.glowcow.stackd.ui.components.TopTab
import dev.glowcow.stackd.ui.theme.StackdIcons
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
    fun setMaxBrightness(on: Boolean) = viewModelScope.launch { store.setMaxBrightness(on) }
    fun setAutoUpdate(on: Boolean) = viewModelScope.launch { store.setAutoUpdate(on) }
    fun setUpdateHours(hours: Int) = viewModelScope.launch { store.setUpdateHours(hours) }
}

@Composable
fun SettingsScreen(
    onTab: (TopTab) -> Unit,
    vm: SettingsViewModel = viewModel { SettingsViewModel((this[APPLICATION_KEY] as StackdApp).container.settings) },
) {
    val c = StackdTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    // The system keeps the per-app language, shared with Settings → Apps → App language.
    val locales = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var language by remember { mutableStateOf(locales.applicationLocales.takeUnless { it.isEmpty }?.get(0)?.language) }
    // Background updates report through notifications, so turning them on asks for the permission.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun setAutoUpdate(on: Boolean) {
        vm.setAutoUpdate(on)
        if (on) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
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
                GroupRow(stringResource(R.string.settings_version), value = version)
                GroupDivider()
                GroupRow(stringResource(R.string.settings_font), value = "Instrument Sans · SIL OFL 1.1")
            }
        }
        BottomBar(TopTab.SETTINGS, onTab)
    }

    when (picker) {
        Picker.THEME -> ChoiceSheet(
            title = stringResource(R.string.settings_theme),
            options = THEMES.map { (mode, label) -> mode to stringResource(label) },
            selected = settings.theme,
            onSelect = { vm.setTheme(it) },
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

private enum class Picker { THEME, LANGUAGE, INTERVAL }

private val UPDATE_HOURS = listOf(1, 3, 6, 12, 24)

@Composable
private fun hoursLabel(hours: Int) = pluralStringResource(R.plurals.hours, hours, hours)

private val THEMES = listOf(
    ThemeMode.SYSTEM to R.string.theme_system,
    ThemeMode.LIGHT to R.string.theme_light,
    ThemeMode.DARK to R.string.theme_dark,
)

/** Language tags with names written in that language. */
private val LANGUAGES = listOf("en" to "English", "ru" to "Русский")

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
