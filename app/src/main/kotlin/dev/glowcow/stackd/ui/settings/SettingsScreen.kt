package dev.glowcow.stackd.ui.settings

import android.Manifest
import android.app.LocaleManager
import android.os.LocaleList
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.glowcow.stackd.backup.Backup
import dev.glowcow.stackd.backup.RestoreOutcome
import dev.glowcow.stackd.ui.theme.AppFont
import java.time.LocalDate
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import dev.glowcow.stackd.ui.components.Group
import dev.glowcow.stackd.ui.components.GroupDivider
import dev.glowcow.stackd.ui.components.GroupField
import dev.glowcow.stackd.ui.components.GroupRow
import dev.glowcow.stackd.ui.components.ChoiceSheet
import dev.glowcow.stackd.ui.components.GroupSheet
import dev.glowcow.stackd.ui.components.SwitchRow
import dev.glowcow.stackd.ui.components.TabScreen
import dev.glowcow.stackd.ui.components.TopTab
import dev.glowcow.stackd.update.AppRelease
import dev.glowcow.stackd.update.AppUpdateState
import dev.glowcow.stackd.update.AppUpdater
import dev.glowcow.stackd.ui.theme.StackdTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val store: SettingsStore, val updater: AppUpdater, private val backup: Backup) : ViewModel() {
    /** A backup is being written or read. */
    var busy by mutableStateOf(false)
        private set

    fun saveBackup(uri: Uri, password: String, onDone: (Boolean) -> Unit) = work { onDone(backup.save(uri, password)) }

    /** Tells whether the file at [uri] wants a password; null when it cannot be read. */
    fun inspectBackup(uri: Uri, onDone: (Boolean?) -> Unit) = work { onDone(backup.isEncrypted(uri)) }

    fun restoreBackup(uri: Uri, password: String, overwrite: Boolean, onDone: (RestoreOutcome) -> Unit) = work {
        onDone(backup.restore(uri, password, overwrite))
    }

    private fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { store.setTheme(mode) }
    fun setPalette(palette: Palette) = viewModelScope.launch { store.setPalette(palette) }
    fun setMaxBrightness(on: Boolean) = viewModelScope.launch { store.setMaxBrightness(on) }
    fun setAutoUpdate(on: Boolean) = viewModelScope.launch { store.setAutoUpdate(on) }
    fun setUpdateHours(hours: Int) = viewModelScope.launch { store.setUpdateHours(hours) }
    fun setNotifyAllChanges(on: Boolean) = viewModelScope.launch { store.setNotifyAllChanges(on) }
    fun setAppUpdate(on: Boolean) = viewModelScope.launch { store.setAppUpdate(on) }
}

@Composable
fun SettingsScreen(
    onTab: (TopTab) -> Unit,
    vm: SettingsViewModel = viewModel { (this[APPLICATION_KEY] as StackdApp).container.let { SettingsViewModel(it.settings, it.appUpdater, it.backup) } },
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
    // Where a backup goes and where it comes from is the user's pick in the system file picker.
    var sheet by rememberSaveable { mutableStateOf<BackupSheet?>(null) }
    var password by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<Uri?>(null) }
    var locked by remember { mutableStateOf(false) }
    var overwrite by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun restored(outcome: RestoreOutcome) {
        toast(
            when (outcome) {
                is RestoreOutcome.Restored -> resources.getString(R.string.backup_restored, outcome.cards)
                RestoreOutcome.WrongPassword -> resources.getString(R.string.backup_wrong_password)
                RestoreOutcome.Invalid -> resources.getString(R.string.backup_invalid)
            },
        )
        // A mistyped password is asked for again, for the same file.
        if (outcome == RestoreOutcome.WrongPassword) sheet = BackupSheet.RESTORE
    }
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.saveBackup(uri, password) { toast(resources.getString(if (it) R.string.backup_saved else R.string.backup_save_failed)) }
        password = ""
    }
    val restoreFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.inspectBackup(uri) { encrypted ->
                if (encrypted == null) return@inspectBackup restored(RestoreOutcome.Invalid)
                source = uri
                locked = encrypted
                overwrite = false
                sheet = BackupSheet.RESTORE
            }
        }
    }
    fun setLanguage(tag: String?) {
        language = tag
        locales.applicationLocales = tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }

    TabScreen(TopTab.SETTINGS, onTab) { top, bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = top, bottom = bottom + 16.dp),
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
                SwitchRow(stringResource(R.string.settings_brightness), stringResource(R.string.settings_brightness_hint), settings.maxBrightness, vm::setMaxBrightness)
            }
            Group {
                SwitchRow(stringResource(R.string.settings_auto_update), stringResource(R.string.settings_auto_update_hint), settings.autoUpdate, ::setAutoUpdate)
                if (settings.autoUpdate) {
                    GroupDivider()
                    GroupRow(
                        stringResource(R.string.settings_update_interval),
                        value = hoursLabel(settings.updateHours),
                        onClick = { picker = Picker.INTERVAL },
                    )
                    GroupDivider()
                    SwitchRow(stringResource(R.string.settings_notify_all), stringResource(R.string.settings_notify_all_hint), settings.notifyAllChanges, vm::setNotifyAllChanges)
                }
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_backup_save),
                    subtitle = stringResource(R.string.settings_backup_save_hint),
                    onClick = if (vm.busy) null else ({ sheet = BackupSheet.SAVE }),
                )
                GroupDivider()
                GroupRow(
                    stringResource(R.string.settings_backup_restore),
                    subtitle = stringResource(R.string.settings_backup_restore_hint),
                    onClick = if (vm.busy) null else ({ restoreFrom.launch(arrayOf("*/*")) }),
                )
            }
            Group {
                GroupRow(
                    stringResource(R.string.settings_version),
                    subtitle = when (val s = update) {
                        AppUpdateState.Idle -> stringResource(if (vm.updater.supported) R.string.app_update_check else R.string.app_update_dev)
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
                    SwitchRow(stringResource(R.string.settings_app_update), stringResource(R.string.settings_app_update_hint), settings.appUpdate, ::setAppUpdate)
                }
            }
            Group {
                GroupRow(stringResource(R.string.settings_licence), value = "GPL-3.0-or-later")
                GroupDivider()
                GroupRow(stringResource(R.string.settings_font), value = "Arimo · SIL OFL 1.1")
                GroupDivider()
                GroupRow(stringResource(R.string.settings_title_font), value = "Cormorant Garamond · SIL OFL 1.1")
            }
        }
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

    when (sheet) {
        BackupSheet.SAVE -> GroupSheet(stringResource(R.string.settings_backup_save), onDismiss = { sheet = null }) { pick ->
            Text(stringResource(R.string.backup_password_note), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp))
            PasswordField(password) { password = it }
            Group(Modifier.padding(top = 12.dp)) {
                GroupRow(stringResource(R.string.backup_save_pick), onClick = { pick { saveTo.launch(backupName()) } })
            }
        }
        BackupSheet.RESTORE -> GroupSheet(stringResource(R.string.settings_backup_restore), onDismiss = { sheet = null }) { pick ->
            var typed by remember { mutableStateOf("") }
            if (locked) {
                Text(stringResource(R.string.backup_encrypted), color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp))
                PasswordField(typed) { typed = it }
            }
            Group(Modifier.padding(top = if (locked) 12.dp else 0.dp)) {
                SwitchRow(stringResource(R.string.backup_overwrite), stringResource(R.string.backup_overwrite_hint), overwrite) { overwrite = it }
            }
            Group(Modifier.padding(top = 12.dp)) {
                GroupRow(
                    stringResource(R.string.backup_restore),
                    onClick = { pick { source?.let { vm.restoreBackup(it, typed, overwrite, ::restored) } } },
                )
            }
        }
        null -> Unit
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

private enum class BackupSheet { SAVE, RESTORE }

/** `stackd-2026-10-06.stackd` */
private fun backupName() = "stackd-${LocalDate.now()}.stackd"

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit) {
    Group { GroupField(stringResource(R.string.backup_password), value, onChange, secret = true) }
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
