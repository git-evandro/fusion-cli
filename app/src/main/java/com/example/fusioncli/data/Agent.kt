package com.example.fusioncli.data

/**
 * Events emitted while the agent works on the workspace. The UI turns these into chat lines and
 * the workspace screen reacts to the file changes they cause.
 */
sealed interface AgentEvent {
    /** A chunk of assistant text. */
    data class Text(val delta: String) : AgentEvent

    /** The model asked to run a tool; [summary] is a short human-readable description. */
    data class ToolStarted(val name: String, val summary: String) : AgentEvent

    /** A tool finished. [result] is a short human-readable result (or the error). */
    data class ToolFinished(val name: String, val ok: Boolean, val result: String) : AgentEvent
}

/** Result of executing one tool call: the text returned to the model plus whether it worked. */
data class ToolResult(val ok: Boolean, val output: String)
