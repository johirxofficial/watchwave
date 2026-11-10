package com.watchwave.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bounded in-memory diagnostics log. It intentionally never receives tokens, query
 * strings or video paths; callers log only lifecycle events and error classes.
 */
class AppLog(private val capacity: Int = 250) {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    fun log(message: String) {
        val line = "${LocalTime.now().format(timeFormat)}  ${redact(message)}"
        _lines.update { current -> (current + line).takeLast(capacity) }
    }

    /** Token- and URL-safe text, shared by the log and the copyable diagnostics report. */
    fun redact(message: String): String = message
        .replace(Regex("(?i)(t|token|hostToken|sessionToken|key|secret)=[^&\\s]+"), "$1=[redacted]")
        .replace(Regex("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com"), "https://<quick-tunnel>.trycloudflare.com")
        .take(400)

    fun clear() {
        _lines.value = emptyList()
    }
}
