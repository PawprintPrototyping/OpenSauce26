package org.pawprint.gachapaw.model

data class LogLine(
    val timestamp: String,
    val message: String,
    val severity: LogSeverity = LogSeverity.INFO
)
