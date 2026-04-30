package com.example.mpa23itb234

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val currentSong: Music? = null
)