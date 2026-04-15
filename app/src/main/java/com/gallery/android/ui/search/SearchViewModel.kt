package com.gallery.android.ui.search

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.gallery.android.domain.model.MediaCategory
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.usecase.SearchMediaUseCase
import com.gallery.android.worker.OcrScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchMediaUseCase: SearchMediaUseCase,
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

    val categories: List<MediaCategory> = MediaCategory.entries.toList()

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

        enqueueOcrScan()
    }

    fun onQueryChange(query: String) {
        _query.value = query
        if (query.isNotBlank()) {
            _selectedCategory.value = null
        }
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun selectCategory(category: MediaCategory?) {
        _selectedCategory.value = category
        if (category != null) {
            _query.value = ""
            viewModelScope.launch {
                _isLoading.value = true
                _categoryResults.value = searchMediaUseCase.byCategory(category)
                _isLoading.value = false
            }
        } else {
            _categoryResults.value = emptyList()
        }
    }

    private fun enqueueOcrScan() {
        runCatching {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                OcrScanWorker.WORK_NAME + "_once",
                ExistingWorkPolicy.KEEP,
                OcrScanWorker.buildOneTimeRequest(),
            )
            workManager.enqueueUniquePeriodicWork(
                OcrScanWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                OcrScanWorker.buildPeriodicRequest(),
            )
        }
    }
}
