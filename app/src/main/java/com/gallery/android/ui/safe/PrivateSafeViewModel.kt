package com.gallery.android.ui.safe

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.repository.MediaRepository
import com.gallery.android.utils.CryptoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SafeAuthState { LOCKED, SETTING_PIN, UNLOCKED }

data class SafeUiState(
    val authState: SafeAuthState = SafeAuthState.LOCKED,
    val pinHash: String = "",
    val safeMedia: List<MediaItem> = emptyList(),
    val errorMessage: String? = null,
    val hasPinSet: Boolean = false,
)

@HiltViewModel
class PrivateSafeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val PIN_HASH_KEY = stringPreferencesKey("safe_pin_hash")

    private val _uiState = MutableStateFlow(SafeUiState())
    val uiState: StateFlow<SafeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.map { prefs -> prefs[PIN_HASH_KEY] ?: "" }.collect { hash ->
                _uiState.update {
                    it.copy(
                        pinHash = hash,
                        hasPinSet = hash.isNotEmpty(),
                        authState = if (hash.isEmpty()) SafeAuthState.SETTING_PIN else SafeAuthState.LOCKED,
                    )
                }
            }
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            val hash = CryptoUtils.hashPin(pin)
            dataStore.edit { it[PIN_HASH_KEY] = hash }
            _uiState.update { it.copy(authState = SafeAuthState.UNLOCKED, errorMessage = null) }
            loadSafeMedia()
        }
    }

    fun unlockWithPin(pin: String) {
        val hash = CryptoUtils.hashPin(pin)
        if (hash == _uiState.value.pinHash) {
            _uiState.update { it.copy(authState = SafeAuthState.UNLOCKED, errorMessage = null) }
            loadSafeMedia()
        } else {
            _uiState.update { it.copy(errorMessage = "Incorrect PIN") }
        }
    }

    fun onBiometricSuccess() {
        _uiState.update { it.copy(authState = SafeAuthState.UNLOCKED) }
        loadSafeMedia()
    }

    fun lock() = _uiState.update { it.copy(authState = SafeAuthState.LOCKED) }

    private fun loadSafeMedia() {
        viewModelScope.launch {
            mediaRepository.getSafeMedia().collect { list ->
                _uiState.update { it.copy(safeMedia = list) }
            }
        }
    }

    fun moveToSafe(mediaId: Long) {
        viewModelScope.launch { mediaRepository.moveToSafe(mediaId, "") }
    }

    fun restoreFromSafe(mediaId: Long) {
        viewModelScope.launch { mediaRepository.restoreFromSafe(mediaId) }
    }
}
