package me.ash.reader.domain.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SyncErrorHolder {

    private val _errorFlow = MutableStateFlow<SyncErrorReport?>(null)
    val errorFlow: StateFlow<SyncErrorReport?> = _errorFlow

    fun report(report: SyncErrorReport) {
        _errorFlow.value = report
    }

    fun clear() {
        _errorFlow.value = null
    }
}
