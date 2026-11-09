package com.watchwave.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bounded in-memory diagnostics log. It intentionally never receives tokens, query
 * strings or video paths; callers log only lifecycle events and error classes.
 */
class AppLog(private val capacity: Int = 250) {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(message: String) {
        val safe = message
            .replace(Regex("(?i)(t|token|hostToken|sessionToken)=[^&\\s]+"), "$1=[redacted]")
            .take(400)
        val line = "${timeFormat.format(Date())}  $safe"
        _lines.update { current -> (current + line).takeLast(capacity) }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
