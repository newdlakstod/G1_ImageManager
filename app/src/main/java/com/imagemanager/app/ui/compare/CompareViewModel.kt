package com.imagemanager.app.ui.compare

import androidx.lifecycle.ViewModel
import com.imagemanager.app.data.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Holds the comparison progress so it survives screen rotation. [photos] needs 2+ items. */
class CompareViewModel(photos: List<Photo>) : ViewModel() {
    private val _session = MutableStateFlow(CompareSession.start(photos))
    val session: StateFlow<CompareSession<Photo>> = _session

    fun delete() { _session.value = _session.value.delete() }
    fun hold() { _session.value = _session.value.hold() }
    fun promote() { _session.value = _session.value.promote() }
    fun undo() { if (_session.value.canUndo) _session.value = _session.value.undo() }
}
