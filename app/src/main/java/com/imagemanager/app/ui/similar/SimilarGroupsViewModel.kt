package com.imagemanager.app.ui.similar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imagemanager.app.data.db.DuplicateGroupDao
import com.imagemanager.app.data.db.DuplicateGroupEntity
import com.imagemanager.app.domain.DuplicateScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SimilarGroupsViewModel(
    private val scanner: DuplicateScanner,
    duplicateGroupDao: DuplicateGroupDao,
) : ViewModel() {
    val groups: StateFlow<List<DuplicateGroupEntity>> = duplicateGroupDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    fun rescan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanner.scan()
            } finally {
                _isScanning.value = false
            }
        }
    }
}
