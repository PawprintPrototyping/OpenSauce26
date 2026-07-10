package org.pawprint.gachapaw.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.pawprint.gachapaw.model.LogLine
import org.pawprint.gachapaw.model.LogSeverity
import java.time.Instant

class LoggingRepository {
    companion object {
        private const val MAX_LOGS = 1000
    }

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    fun addLog(message: String, severity: LogSeverity = LogSeverity.INFO) {
        val newLog = LogLine(
            timestamp = Instant.now().toString(),
            message = message,
            severity = severity
        )
        _logs.update { currentLogs ->
            (currentLogs + newLog).takeLast(MAX_LOGS)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
