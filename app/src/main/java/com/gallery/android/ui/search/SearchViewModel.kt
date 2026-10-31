package com.gallery.android.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.data.local.database.dao.OcrMetadataDao
import com.gallery.android.domain.model.MediaCategory
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.usecase.GetMediaUseCase
import com.gallery.android.domain.usecase.SearchMediaUseCase
import com.gallery.android.worker.OcrScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchMediaUseCase: SearchMediaUseCase,
    getMediaUseCase: GetMediaUseCase,
    ocrMetadataDao: OcrMetadataDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<MediaItem>>(emptyList())
    val results: StateFlow<List<MediaItem>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow<MediaCategory?>(null)
    val selectedCategory: StateFlow<MediaCategory?> = _selectedCategory.asStateFlow()

    private val _categoryResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val categoryResults: StateFlow<List<MediaItem>> = _categoryResults.asStateFlow()

    val pendingIndexCount: StateFlow<Int> = ocrMetadataDao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val categories: List<MediaCategory> = MediaCategory.entries.toList()

    val moments: StateFlow<List<MediaItem>> = getMediaUseCase.getRecent(18)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank() && _selectedCategory.value == null) {
                        _results.value = emptyList()
                    } else if (q.isNotBlank()) {
                        _isLoading.value = true
                        _results.value = searchMediaUseCase(q)
                        _isLoading.value = false
                    }
                }
        }
    }

    fun ensureIndexing() {
        OcrScanWorker.enqueue(context)
    }

    fun onQueryChange(query: String) {
        _query.value = query
        if (query.isNotBlank()) {
            _selectedCategory.value = null
            _categoryResults.value = emptyList()
        }
    }

    fun clearSearch() {
        _query.value = ""
        _results.value = emptyList()
        _selectedCategory.value = null
        _categoryResults.value = emptyList()
        _isLoading.value = false
    }

    fun clearQuery() = clearSearch()

    fun selectCategory(category: MediaCategory) {
        if (_selectedCategory.value == category) {
            clearSearch()
            return
        }
        _selectedCategory.value = category
        _query.value = ""
        _results.value = emptyList()
        viewModelScope.launch {
            _isLoading.value = true
            _categoryResults.value = searchMediaUseCase.byCategory(category)
            _isLoading.value = false
        }
    }
}
