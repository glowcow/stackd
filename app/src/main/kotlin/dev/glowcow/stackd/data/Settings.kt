package dev.glowcow.stackd.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val maxBrightness: Boolean = true,
    /** Fetch fresh copies of the passes in the background, every [updateHours]. */
    val autoUpdate: Boolean = false,
    val updateHours: Int = 6,
    /** Look for a new version of the app once a week. */
    val appUpdate: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val brightnessKey = booleanPreferencesKey("max_brightness")
    private val autoUpdateKey = booleanPreferencesKey("auto_update")
    private val updateHoursKey = intPreferencesKey("update_hours")
    private val appUpdateKey = booleanPreferencesKey("app_update")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            maxBrightness = p[brightnessKey] ?: true,
            autoUpdate = p[autoUpdateKey] ?: false,
            updateHours = p[updateHoursKey] ?: 6,
            appUpdate = p[appUpdateKey] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }

    suspend fun setMaxBrightness(on: Boolean) = context.dataStore.edit { it[brightnessKey] = on }

    suspend fun setAutoUpdate(on: Boolean) = context.dataStore.edit { it[autoUpdateKey] = on }

    suspend fun setUpdateHours(hours: Int) = context.dataStore.edit { it[updateHoursKey] = hours }

    suspend fun setAppUpdate(on: Boolean) = context.dataStore.edit { it[appUpdateKey] = on }

    /** True the first time it is called for [task]. */
    suspend fun firstRun(task: String): Boolean {
        val key = booleanPreferencesKey("done_$task")
        var first = false
        context.dataStore.edit {
            first = it[key] != true
            it[key] = true
        }
        return first
    }
}
