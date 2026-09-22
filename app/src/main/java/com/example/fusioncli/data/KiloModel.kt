package com.example.fusioncli.data

data class KiloModel(
    val id: String,
    val name: String
) {
    val provider: String get() = id.substringBefore('/', id)
}
