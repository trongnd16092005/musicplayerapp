package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.mpa23itb234.databinding.FragmentNowPlayingBinding

class NowPlaying : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!
    companion object {
        var bindingInstance: FragmentNowPlayingBinding? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        requireContext().theme.applyStyle(
            MainActivity.currentTheme[MainActivity.themeIndex],
            true
        )

        val view = inflater.inflate(R.layout.fragment_now_playing, container, false)
        _binding = FragmentNowPlayingBinding.bind(view)
        bindingInstance = _binding

        binding.root.visibility = View.INVISIBLE

        // PLAY / PAUSE
        binding.playPauseBtnNP.setOnClickListener {
            val service = PlayerActivity.musicService ?: return@setOnClickListener
            val player = service.mediaPlayer ?: return@setOnClickListener

            if (PlayerActivity.isPlaying) {
                PlayerActivity.isPlaying = false
                player.pause()
                binding.playPauseBtnNP.setIconResource(R.drawable.play_icon)
                service.showNotification(R.drawable.play_icon)
            } else {
                PlayerActivity.isPlaying = true
                player.start()
                binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon)
                service.showNotification(R.drawable.pause_icon)
            }
        }

        // NEXT SONG
        binding.nextBtnNP.setOnClickListener {
            val service = PlayerActivity.musicService ?: return@setOnClickListener

            if (PlayerActivity.musicListPA.isEmpty()) return@setOnClickListener

            setSongPosition(true)

            service.createMediaPlayer()

            try {
                Glide.with(requireContext())
                    .load(PlayerActivity.musicListPA[PlayerActivity.songPosition].artUri)
                    .apply(
                        RequestOptions()
                            .placeholder(R.drawable.music_player_icon_slash_screen)
                            .centerCrop()
                    )
                    .into(binding.songImgNP)

                binding.songNameNP.text =
                    PlayerActivity.musicListPA[PlayerActivity.songPosition].title
            } catch (_: Exception) {}

            service.showNotification(R.drawable.pause_icon)

            val player = service.mediaPlayer ?: return@setOnClickListener
            PlayerActivity.isPlaying = true
            player.start()
        }

        // OPEN PLAYER ACTIVITY
        binding.root.setOnClickListener {
            if (!isAdded) return@setOnClickListener

            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("index", PlayerActivity.songPosition)
            intent.putExtra("class", "NowPlaying")
            ContextCompat.startActivity(requireContext(), intent, null)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val service = PlayerActivity.musicService ?: return

        binding.root.visibility = View.VISIBLE
        binding.songNameNP.isSelected = true

        if (PlayerActivity.musicListPA.isEmpty()) return

        try {
            Glide.with(requireContext())
                .load(PlayerActivity.musicListPA[PlayerActivity.songPosition].artUri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.music_player_icon_slash_screen)
                        .centerCrop()
                )
                .into(binding.songImgNP)

            binding.songNameNP.text =
                PlayerActivity.musicListPA[PlayerActivity.songPosition].title
        } catch (_: Exception) {}

        if (PlayerActivity.isPlaying)
            binding.playPauseBtnNP.setIconResource(R.drawable.pause_icon)
        else
            binding.playPauseBtnNP.setIconResource(R.drawable.play_icon)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}