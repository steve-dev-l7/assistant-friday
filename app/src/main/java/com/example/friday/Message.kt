package com.example.friday

data class Message(
    var text: String,
    val isFromBot: Boolean,
    val time: String
)