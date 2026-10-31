package com.gallery.android.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

data class SettingsUiState(
    val themeMode: String = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val gridColumns: Int = 3,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val gridColumnsKey = intPreferencesKey("grid_columns")

    val uiState: StateFlow<SettingsUiState> = dataStore.data.map { prefs ->
        SettingsUiState(
            themeMode = prefs[themeModeKey] ?: when (prefs[darkThemeKey]) {
                true -> ThemeMode.DARK
                false -> ThemeMode.LIGHT
                null -> ThemeMode.SYSTEM
            },
            useDynamicColor = prefs[dynamicColorKey] ?: false,
            gridColumns = prefs[gridColumnsKey] ?: 3,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            dataStore.edit { it[themeModeKey] = mode }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[dynamicColorKey] = enabled }
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            dataStore.edit { it[gridColumnsKey] = columns }
        }
    }
}
