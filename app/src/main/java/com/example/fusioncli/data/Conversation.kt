package com.example.fusioncli.data

data class Conversation(
    val id: String,
    val title: String = "Nova conversa",
    val messages: List<ChatMessage> = emptyList()
)
