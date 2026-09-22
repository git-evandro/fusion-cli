package com.example.fusioncli.data

sealed class CommandOutput {
    data class Stdout(val line: String) : CommandOutput()
    data class Stderr(val line: String) : CommandOutput()
    data class Error(val message: String) : CommandOutput()
}
