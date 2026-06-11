package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.mpa23itb234.databinding.FragmentNowPlayingBinding
import kotlin.math.abs
class NowPlaying : Fragment() {

    private var touchStartX = 0f
    private var touchStartY = 0f

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var binding: FragmentNowPlayingBinding  // Binding layout fragment

        fun updateIfReady(context: Context, song: Music, playing: Boolean) {
            try {
                binding.songNameNP.isSelected = true
                Glide.with(context)
                    .load(song.artUri)
                    .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
                    .into(binding.songImgNP)
                binding.songNameNP.text = song.title
                binding.playPauseBtnNP.setIconResource(if (playing) R.drawable.pause_icon else R.drawable.play_icon)
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Áp theme hiện tại
        requireContext().theme.applyStyle(MainActivity.currentTheme[MainActivity.themeIndex], true)
        val rootView = inflater.inflate(R.layout.fragment_now_playing, container, false)
        binding = FragmentNowPlayingBinding.bind(rootView)
        binding.root.visibility = View.INVISIBLE  // Ẩn fragment lúc đầu

        // Nút play/pause
        binding.playPauseBtnNP.setOnClickListener {
            if (PlayerActivity.isPlaying) pauseMusic() else playMusic()
        }

        // Nút next bài hát
        binding.nextBtnNP.setOnClickListener {
            setSongPosition(true)
            PlayerActivity.musicService?.createMediaPlayer()
            // Cập nhật ảnh và tên bài hát mới
            currentPlayerSongOrNull()?.let { updateIfReady(requireContext(), it, PlayerActivity.isPlaying) }
        }

        // Click vào fragment mở PlayerActivity
        binding.root.setOnClickListener {
            openPlayer()
        }
        binding.root.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    val swipeThreshold = 96f * resources.displayMetrics.density
                    if (abs(deltaX) >= swipeThreshold && abs(deltaX) > abs(deltaY) * 1.4f) {
                        closeMiniPlayer()
                    } else {
                        touchedView.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        return rootView
    }

    override fun onResume() {
        super.onResume()
        if (PlayerActivity.musicService != null) {
            binding.root.visibility = View.VISIBLE   // Hiện fragment
            currentPlayerSongOrNull()?.let { updateIfReady(requireContext(), it, PlayerActivity.isPlaying) }
        }
    }

    private fun playMusic() {
        if (!PlayerActivity.isPrepared) return
        val service = PlayerActivity.musicService ?: return
        service.play()
        binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon)
    }

    private fun pauseMusic() {
        if (!PlayerActivity.isPrepared) return
        val service = PlayerActivity.musicService ?: return
        service.pause()
        binding.playPauseBtnNP.setIconResource(R.drawable.play_icon)
    }

    private fun openPlayer() {
        val intent = Intent(requireContext(), PlayerActivity::class.java)
        intent.putExtra("index", PlayerActivity.songPosition)
        intent.putExtra("class", "NowPlaying")
        ContextCompat.startActivity(requireContext(), intent, null)
    }

    private fun closeMiniPlayer() {
        stopMusicPlayback()
        context?.stopService(Intent(requireContext(), MusicService::class.java))
        binding.root.visibility = View.GONE
    }
}
