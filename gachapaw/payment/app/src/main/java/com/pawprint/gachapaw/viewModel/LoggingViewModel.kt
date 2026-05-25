package com.pawprint.gachapaw.viewModel

import androidx.lifecycle.ViewModel
import com.pawprint.gachapaw.model.LogLine
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.collections.emptyList

class LoggingViewModel : ViewModel() {
    companion object {
        const val LOGGING_SIZE_LIMIT = 50
    }
    private val _logState = MutableStateFlow<List<LogLine>>(emptyList())
    val logState: StateFlow<List<LogLine>> = _logState.asStateFlow()

    fun addLog(logLine: String) {
        val line = LogLine(
            Instant.now().toString(),
            logLine
        )
        _logState.update { lines ->
            val newList = lines + line
            newList.takeLast(LOGGING_SIZE_LIMIT)
        }
    }
}