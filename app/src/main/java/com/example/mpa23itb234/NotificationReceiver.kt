package com.example.mpa23itb234

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        val ctx = context ?: return

        when (intent?.action) {

            ApplicationClass.PREVIOUS -> {
                if (PlayerActivity.musicListPA.size > 1)
                    prevNextSong(increment = false, context = ctx)
            }

            ApplicationClass.PLAY -> {
                if (PlayerActivity.isPlaying) pauseMusic()
                else playMusic()
            }

            ApplicationClass.NEXT -> {
                if (PlayerActivity.musicListPA.size > 1)
                    prevNextSong(increment = true, context = ctx)
            }

            ApplicationClass.EXIT -> {
                exitApplication()
            }
        }
    }

    private fun playMusic() {
        val service = PlayerActivity.musicService ?: return
        val player = service.mediaPlayer ?: return

        PlayerActivity.isPlaying = true
        player.start()
        service.showNotification(R.drawable.pause_icon)

        try {
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        } catch (_: Exception) {}

        // FIX: dùng bindingInstance
        NowPlaying.bindingInstance?.playPauseBtnNP?.setIconResource(R.drawable.pause_icon)
    }

    private fun pauseMusic() {
        val service = PlayerActivity.musicService ?: return
        val player = service.mediaPlayer ?: return

        PlayerActivity.isPlaying = false
        player.pause()
        service.showNotification(R.drawable.play_icon)

        try {
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        } catch (_: Exception) {}

        // FIX
        NowPlaying.bindingInstance?.playPauseBtnNP?.setIconResource(R.drawable.play_icon)
    }

    private fun prevNextSong(increment: Boolean, context: Context) {

        val service = PlayerActivity.musicService ?: return

        if (PlayerActivity.musicListPA.isEmpty()) return

        setSongPosition(increment)

        service.createMediaPlayer()

        try {
            Glide.with(context)
                .load(PlayerActivity.musicListPA[PlayerActivity.songPosition].artUri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.music_player_icon_slash_screen)
                        .centerCrop()
                )
                .into(PlayerActivity.binding.songImgPA)

            PlayerActivity.binding.songNamePA.text =
                PlayerActivity.musicListPA[PlayerActivity.songPosition].title

        } catch (_: Exception) {}

        // FIX: NowPlaying an toàn
        NowPlaying.bindingInstance?.apply {

            Glide.with(context)
                .load(PlayerActivity.musicListPA[PlayerActivity.songPosition].artUri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.music_player_icon_slash_screen)
                        .centerCrop()
                )
                .into(songImgNP)

            songNameNP.text =
                PlayerActivity.musicListPA[PlayerActivity.songPosition].title
        }

        playMusic()

        PlayerActivity.fIndex =
            favouriteChecker(PlayerActivity.musicListPA[PlayerActivity.songPosition].id)

        try {
            if (PlayerActivity.isFavourite)
                PlayerActivity.binding.favouriteBtnPA.setImageResource(R.drawable.favourite_icon)
            else
                PlayerActivity.binding.favouriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
        } catch (_: Exception) {}
    }
}