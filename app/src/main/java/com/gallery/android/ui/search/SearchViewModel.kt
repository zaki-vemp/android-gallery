package com.gallery.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.android.domain.model.MediaItem
import com.gallery.android.domain.usecase.SearchMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMediaUseCase: SearchMediaUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<MediaItem>>(emptyList())
    val results: StateFlow<List<MediaItem>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _results.value = emptyList()
                    } else {
                        _isLoading.value = true
                        _results.value = searchMediaUseCase(q)
                        _isLoading.value = false
                    }
                }
        }
    }

    fun onQueryChange(query: String) { _query.value = query }
    fun clearQuery() { _query.value = "" }
}
