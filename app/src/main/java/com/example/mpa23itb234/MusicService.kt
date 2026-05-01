package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.*
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class MusicService : Service(), AudioManager.OnAudioFocusChangeListener {
    private var myBinder = MyBinder()
    var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var runnable: Runnable
    lateinit var audioManager: AudioManager

    override fun onBind(intent: Intent?): IBinder {
        mediaSession = MediaSessionCompat(baseContext, "My Music")
        return myBinder
    }

    inner class MyBinder : Binder() {
        fun currentService(): MusicService {
            return this@MusicService
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    fun showNotification(playPauseBtn: Int) {
        val intent = Intent(baseContext, MainActivity::class.java)

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentIntent = PendingIntent.getActivity(this, 0, intent, flag)

        val prevIntent = Intent(
            baseContext, NotificationReceiver::class.java
        ).setAction(ApplicationClass.PREVIOUS)
        val prevPendingIntent = PendingIntent.getBroadcast(baseContext, 0, prevIntent, flag)

        val playIntent =
            Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.PLAY)
        val playPendingIntent = PendingIntent.getBroadcast(baseContext, 0, playIntent, flag)

        val nextIntent =
            Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.NEXT)
        val nextPendingIntent = PendingIntent.getBroadcast(baseContext, 0, nextIntent, flag)

        val exitIntent =
            Intent(baseContext, NotificationReceiver::class.java).setAction(ApplicationClass.EXIT)
        val exitPendingIntent = PendingIntent.getBroadcast(baseContext, 0, exitIntent, flag)

        val list = PlayerActivity.musicListPA
        if (list.isEmpty()) return

        val song = list.getOrNull(PlayerActivity.songPosition) ?: return

        val image: Bitmap = if (song.artUri.startsWith("http")) {

            BitmapFactory.decodeResource(resources, R.drawable.music_player_icon_slash_screen)

        } else {

            val imgArt = try {
                if (!song.path.startsWith("http")) getImgArt(song.path)
                else null
            } catch (e: Exception) {
                null
            }
            if (imgArt != null) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4
                    }
                    BitmapFactory.decodeByteArray(imgArt, 0, imgArt.size, options)
                } catch (e: Exception) {
                    BitmapFactory.decodeResource(resources, R.drawable.music_player_icon_slash_screen)
                }
            } else {
                BitmapFactory.decodeResource(resources, R.drawable.music_player_icon_slash_screen)
            }
        }

        val notification =
            androidx.core.app.NotificationCompat.Builder(baseContext, ApplicationClass.CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setContentTitle(PlayerActivity.musicListPA[PlayerActivity.songPosition].title)
                .setContentText(PlayerActivity.musicListPA[PlayerActivity.songPosition].artist)
                .setSmallIcon(R.drawable.music_icon).setLargeIcon(image)
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.previous_icon, "Previous", prevPendingIntent)
                .addAction(playPauseBtn, "Play", playPendingIntent)
                .addAction(R.drawable.next_icon, "Next", nextPendingIntent)
                .addAction(R.drawable.exit_icon, "Exit", exitPendingIntent)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val player = mediaPlayer ?: return

            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION,
                        try {
                            player.duration.toLong()
                        } catch (e: Exception) {
                            0L
                        }
                    )
                    .build()
            )

            mediaSession.setPlaybackState(getPlayBackState())
            mediaSession.setCallback(object : MediaSessionCompat.Callback() {

                //called when play button is pressed
                override fun onPlay() {
                    super.onPlay()
                    handlePlayPause()
                }

                //called when pause button is pressed
                override fun onPause() {
                    super.onPause()
                    handlePlayPause()
                }

                //called when next button is pressed
                override fun onSkipToNext() {
                    super.onSkipToNext()
                    prevNextSong(increment = true, context = baseContext)
                }

                //called when previous button is pressed
                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    prevNextSong(increment = false, context = baseContext)
                }

                //called when headphones buttons are pressed
                //currently only pause or play music on button click
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    handlePlayPause()
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                //called when seekbar is changed
                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    mediaPlayer?.seekTo(pos.toInt())

                    mediaSession.setPlaybackState(getPlayBackState())
                }
            })
        }

        startForeground(13, notification)
    }

    fun createMediaPlayer() {
        try {
            if (mediaPlayer == null) mediaPlayer = MediaPlayer()
            mediaPlayer?.reset()

            val list = PlayerActivity.musicListPA
            if (list.isEmpty()) return

            val song = list.getOrNull(PlayerActivity.songPosition) ?: return

            mediaPlayer?.setDataSource(song.path)
            mediaPlayer?.prepareAsync()

            mediaPlayer?.setOnPreparedListener {
                it.start()
                showNotification(R.drawable.pause_icon)   // 🔥 CHỈ gọi khi ready
            }
            try {
                PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
            } catch (_: Exception) {}


            mediaPlayer?.let {
                PlayerActivity.binding.tvSeekBarStart.text =
                    formatDuration(it.currentPosition.toLong())
                PlayerActivity.binding.tvSeekBarEnd.text =
                    formatDuration(it.duration.toLong())
                PlayerActivity.binding.seekBarPA.progress = 0
                PlayerActivity.binding.seekBarPA.max = it.duration

                PlayerActivity.nowPlayingId = song.id
                PlayerActivity.loudnessEnhancer = LoudnessEnhancer(it.audioSessionId)
                PlayerActivity.loudnessEnhancer.enabled = true
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekBarSetup() {
        runnable = Runnable {

            val player = mediaPlayer ?: return@Runnable

            try {
                PlayerActivity.binding.tvSeekBarStart.text =
                    formatDuration(player.currentPosition.toLong())

                PlayerActivity.binding.seekBarPA.progress =
                    player.currentPosition
            } catch (_: Exception) {}

            Handler(Looper.getMainLooper()).postDelayed(runnable, 200)
        }

        Handler(Looper.getMainLooper()).post(runnable)
    }

    fun getPlayBackState(): PlaybackStateCompat {
        val player = mediaPlayer ?: return PlaybackStateCompat.Builder().build()

        val playbackSpeed = if (PlayerActivity.isPlaying) 1F else 0F

        return PlaybackStateCompat.Builder().setState(
            if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            player.currentPosition.toLong(),
            playbackSpeed
        )
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .build()
    }

    fun handlePlayPause() {
        if (mediaPlayer == null) return

        if (PlayerActivity.isPlaying) pauseMusic()
        else playMusic()

        mediaSession.setPlaybackState(getPlayBackState())
    }



    private fun prevNextSong(increment: Boolean, context: Context) {

        val list = PlayerActivity.musicListPA
        if (list.isEmpty()) return

        setSongPosition(increment)

        val service = PlayerActivity.musicService ?: return
        if (service.mediaPlayer?.isPlaying == true) {
            service.mediaPlayer?.stop()
        }
        service.createMediaPlayer()

        val song = list.getOrNull(PlayerActivity.songPosition) ?: return

        try {
            Glide.with(context)
                .load(song.artUri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.music_player_icon_slash_screen)
                        .centerCrop()
                        .override(300)
                )
                .into(PlayerActivity.binding.songImgPA)

            PlayerActivity.binding.songNamePA.text = song.title
        } catch (_: Exception) {}

        // FIX NowPlaying
        NowPlaying.bindingInstance?.apply {
            Glide.with(context)
                .load(song.artUri)
                .apply(
                    RequestOptions()
                        .placeholder(R.drawable.music_player_icon_slash_screen)
                        .centerCrop()
                        .override(300)
                )
                .into(songImgNP)

            songNameNP.text = song.title
        }

        playMusic()

        PlayerActivity.fIndex = favouriteChecker(song.id)

        try {
            if (PlayerActivity.isFavourite)
                PlayerActivity.binding.favouriteBtnPA.setImageResource(R.drawable.favourite_icon)
            else
                PlayerActivity.binding.favouriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
        } catch (_: Exception) {}

        mediaSession.setPlaybackState(getPlayBackState())
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange <= 0 && mediaPlayer != null) {
            pauseMusic()
        }
    }

    private fun playMusic() {
        val player = mediaPlayer ?: return

        PlayerActivity.isPlaying = true
        player.start()

        try {
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
        } catch (_: Exception) {}

        NowPlaying.bindingInstance?.playPauseBtnNP?.setIconResource(R.drawable.pause_icon)

//        showNotification(R.drawable.pause_icon)
    }

    private fun pauseMusic() {
        val player = mediaPlayer ?: return

        PlayerActivity.isPlaying = false
        player.pause()

        try {
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        } catch (_: Exception) {}

        NowPlaying.bindingInstance?.playPauseBtnNP?.setIconResource(R.drawable.play_icon)

        showNotification(R.drawable.play_icon)
    }




    //for making persistent
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

}