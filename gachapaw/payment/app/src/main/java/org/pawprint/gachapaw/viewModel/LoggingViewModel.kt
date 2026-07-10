package org.pawprint.gachapaw.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.pawprint.gachapaw.PawApplication
import org.pawprint.gachapaw.model.LogLine
import org.pawprint.gachapaw.model.LogSeverity

class LoggingViewModel(application: Application) : AndroidViewModel(application) {
    private val loggingRepository = (application as PawApplication).loggingRepository
    val logState: StateFlow<List<LogLine>> = loggingRepository.logs

    fun addLog(message: String, severity: LogSeverity = LogSeverity.INFO) {
        loggingRepository.addLog(message, severity)
    }

    fun clearLogs() {
        loggingRepository.clearLogs()
    }
}
