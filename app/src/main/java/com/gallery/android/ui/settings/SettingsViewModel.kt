package com.gallery.android.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDarkTheme: Boolean = false,
    val useDynamicColor: Boolean = false,
    val gridColumns: Int = 3,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    private val GRID_COLUMNS_KEY = intPreferencesKey("grid_columns")

    val uiState: StateFlow<SettingsUiState> = dataStore.data.map { prefs ->
        SettingsUiState(
            isDarkTheme = prefs[DARK_THEME_KEY] ?: false,
            useDynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: false,
            gridColumns = prefs[GRID_COLUMNS_KEY] ?: 3,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[DARK_THEME_KEY] = enabled }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            dataStore.edit { it[GRID_COLUMNS_KEY] = columns }
        }
    }
}
