//package com.example.mpa23itb234
//
//import androidx.lifecycle.ViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//
//class PlayerViewModel : ViewModel() {
//
//    private val _state = MutableStateFlow(PlayerState())
//    val state: StateFlow<PlayerState> = _state
//
//    private var musicService: MusicService? = null
//
//    fun setService(service: MusicService) {
//        musicService = service
//    }
//
//    fun play() {
//        musicService?.play()
//        updateState(isPlaying = true)
//    }
//
//    fun pause() {
//        musicService?.pause()
//        updateState(isPlaying = false)
//    }
//
//    fun playSong(list: List<Music>, position: Int) {
//        musicService?.setPlaylist(list, position)
//        musicService?.prepare()
//        musicService?.play()
//
//        updateState(
//            isPlaying = true,
//            currentSong = list[position]
//        )
//    }
//
//    fun next() {
//        musicService?.next()
//        updateFromService()
//    }
//
//    fun previous() {
//        musicService?.previous()
//        updateFromService()
//    }
//
//    fun seekTo(pos: Int) {
//        musicService?.seekTo(pos)
//    }
//
//    private fun updateFromService() {
//        musicService?.let {
//            _state.value = _state.value.copy(
//                currentSong = it.getCurrentSong(),
//                currentPosition = it.getCurrentPosition(),
//                duration = it.getDuration(),
//                isPlaying = it.isPlaying()
//            )
//        }
//    }
//
//    private fun updateState(
//        isPlaying: Boolean = _state.value.isPlaying,
//        currentSong: Music? = _state.value.currentSong
//    ) {
//        _state.value = _state.value.copy(
//            isPlaying = isPlaying,
//            currentSong = currentSong
//        )
//    }
//}