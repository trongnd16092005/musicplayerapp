package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var seekBarRunnable: Runnable? = null
    lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(baseContext, "My Music")
    }

    override fun onBind(intent: Intent?): IBinder {
        if (!::mediaSession.isInitialized) {
            mediaSession = MediaSessionCompat(baseContext, "My Music")
        }
        return myBinder
    }

    inner class MyBinder : Binder() {
        fun currentService(): MusicService {
            return this@MusicService
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    fun showNotification(playPauseBtn: Int) {
        val song = currentPlayerSongOrNull() ?: return
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

        val imgArt = getImgArt(song.path)
        val image = if (imgArt != null) {
            BitmapFactory.decodeByteArray(imgArt, 0, imgArt.size)
        } else {
            BitmapFactory.decodeResource(resources, R.drawable.music_player_icon_slash_screen)
        }

        val notification =
            androidx.core.app.NotificationCompat.Builder(baseContext, ApplicationClass.CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSmallIcon(R.drawable.music_icon).setLargeIcon(image)
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.previous_icon, getString(R.string.previous), prevPendingIntent)
                .addAction(playPauseBtn, getString(R.string.play_pause), playPendingIntent)
                .addAction(R.drawable.next_icon, getString(R.string.next), nextPendingIntent)
                .addAction(R.drawable.exit_icon, getString(R.string.exit), exitPendingIntent)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            mediaSession.setMetadata(
                MediaMetadataCompat.Builder().putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION, (safeDuration() ?: 0).toLong()
                ).build()
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
                    seekTo(pos.toInt())

                    mediaSession.setPlaybackState(getPlayBackState())
                }
            })
        }

        startForeground(13, notification)
    }

    fun createMediaPlayer() {
        try {
            val song = currentPlayerSongOrNull() ?: return
            stopSeekBarUpdates()
            PlayerActivity.isPrepared = false
            PlayerActivity.isPlaying = false
            updatePlayerLoadingUi()
            if (mediaPlayer == null) mediaPlayer = MediaPlayer()
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(song.path)
            mediaPlayer?.setOnErrorListener { _, _, _ ->
                PlayerActivity.isPrepared = false
                PlayerActivity.isPlaying = false
                stopSeekBarUpdates()
                updatePlayerStoppedUi()
                true
            }
            mediaPlayer?.setOnCompletionListener {
                prevNextSong(increment = true, context = baseContext)
            }
            mediaPlayer?.setOnPreparedListener {
                PlayerActivity.isPrepared = true
                updatePlayerPreparedUi(it)
                PlayerActivity.nowPlayingId = song.id
                PlayerActivity.loudnessEnhancer = LoudnessEnhancer(it.audioSessionId)
                PlayerActivity.loudnessEnhancer.enabled = true
                playMusic()
                seekBarSetup()
            }
            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            PlayerActivity.isPrepared = false
            updatePlayerStoppedUi()
            return
        }
    }

    fun seekBarSetup() {
        stopSeekBarUpdates()
        seekBarRunnable = Runnable {
            val current = safeCurrentPosition()
            if (PlayerActivity.isPrepared && current != null) {
                try {
                    PlayerActivity.binding.tvSeekBarStart.text = formatDuration(current.toLong())
                    PlayerActivity.binding.seekBarPA.progress = current
                } catch (_: Exception) {
                }
            }
            seekBarRunnable?.let { mainHandler.postDelayed(it, 200) }
        }
        seekBarRunnable?.let { mainHandler.postDelayed(it, 0) }
    }

    fun stopSeekBarUpdates() {
        seekBarRunnable?.let { mainHandler.removeCallbacks(it) }
        seekBarRunnable = null
    }

    fun getPlayBackState(): PlaybackStateCompat {
        val playbackSpeed = if (PlayerActivity.isPlaying) 1F else 0F

        return PlaybackStateCompat.Builder().setState(
            if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            (safeCurrentPosition() ?: 0).toLong(), playbackSpeed)
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build()
    }

    fun handlePlayPause() {
        if (!PlayerActivity.isPrepared) return
        if (PlayerActivity.isPlaying) pauseMusic()
        else playMusic()

        //update playback state for notification
        mediaSession.setPlaybackState(getPlayBackState())
    }

    fun play() {
        if (!PlayerActivity.isPlaying) playMusic()
    }

    fun pause() {
        if (PlayerActivity.isPlaying) pauseMusic()
    }

    fun setPlaylist(list: List<Music>, position: Int) {
        PlayerActivity.musicListPA = ArrayList(list)
        PlayerActivity.songPosition = if (PlayerActivity.musicListPA.isEmpty()) {
            0
        } else {
            position.coerceIn(0, PlayerActivity.musicListPA.lastIndex)
        }
    }

    fun prepare() {
        createMediaPlayer()
    }

    fun next() {
        prevNextSong(increment = true, context = baseContext)
    }

    fun previous() {
        prevNextSong(increment = false, context = baseContext)
    }

    fun seekTo(pos: Int) {
        if (PlayerActivity.isPrepared) {
            try {
                mediaPlayer?.seekTo(pos)
            } catch (_: IllegalStateException) {
                PlayerActivity.isPrepared = false
            }
        }
    }

    fun getCurrentSong(): Music? {
        return currentPlayerSongOrNull()
    }

    fun getCurrentPosition(): Int {
        return safeCurrentPosition() ?: 0
    }

    fun getDuration(): Int {
        return if (PlayerActivity.isPrepared) safeDuration() ?: 0 else 0
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }



    private fun prevNextSong(increment: Boolean, context: Context){

        setSongPosition(increment = increment)

        PlayerActivity.musicService?.createMediaPlayer()
        val song = currentPlayerSongOrNull() ?: return
        updatePlayerSongUi(context, song)
        NowPlaying.updateIfReady(context, song, PlayerActivity.isPlaying)
        PlayerActivity.fIndex = favouriteChecker(song)
        updatePlayerFavouriteUi()

        //update playback state for notification
        if (PlayerActivity.isPrepared) mediaSession.setPlaybackState(getPlayBackState())
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange <= 0) {
            pauseMusic()
        }
//        else{
//            playMusic()
//        }
    }

    private fun playMusic(){
        if (!PlayerActivity.isPrepared) return
        //play music
        PlayerActivity.isPlaying = true
        try {
            mediaPlayer?.start()
        } catch (_: IllegalStateException) {
            PlayerActivity.isPrepared = false
            return
        }
        updatePlayerPlayPauseUi(R.drawable.pause_icon)
        currentPlayerSongOrNull()?.let { NowPlaying.updateIfReady(baseContext, it, true) }
        showNotification(R.drawable.pause_icon)
    }

    private fun pauseMusic(){
        if (!PlayerActivity.isPrepared) return
        //pause music
        PlayerActivity.isPlaying = false
        try {
            mediaPlayer?.pause()
        } catch (_: IllegalStateException) {
            PlayerActivity.isPrepared = false
            return
        }
        updatePlayerPlayPauseUi(R.drawable.play_icon)
        currentPlayerSongOrNull()?.let { NowPlaying.updateIfReady(baseContext, it, false) }
        showNotification(R.drawable.play_icon)
    }




    //for making persistent
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        stopSeekBarUpdates()
        super.onDestroy()
    }

    private fun safeCurrentPosition(): Int? {
        return try {
            mediaPlayer?.currentPosition
        } catch (_: IllegalStateException) {
            PlayerActivity.isPrepared = false
            null
        }
    }

    private fun safeDuration(): Int? {
        return try {
            mediaPlayer?.duration
        } catch (_: IllegalStateException) {
            PlayerActivity.isPrepared = false
            null
        }
    }

    private fun updatePlayerLoadingUi() {
        try {
            PlayerActivity.binding.playPauseBtnPA.isEnabled = false
            PlayerActivity.binding.seekBarPA.isEnabled = false
            PlayerActivity.binding.tvSeekBarStart.text = formatDuration(0)
            PlayerActivity.binding.tvSeekBarEnd.text = PlayerActivity.binding.root.context.getString(R.string.end_tv)
            PlayerActivity.binding.seekBarPA.progress = 0
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        } catch (_: Exception) {
        }
    }

    private fun updatePlayerPreparedUi(player: MediaPlayer) {
        try {
            PlayerActivity.binding.playPauseBtnPA.isEnabled = true
            PlayerActivity.binding.seekBarPA.isEnabled = true
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.pause_icon)
            PlayerActivity.binding.tvSeekBarStart.text = formatDuration(player.currentPosition.toLong())
            PlayerActivity.binding.tvSeekBarEnd.text = formatDuration(player.duration.toLong())
            PlayerActivity.binding.seekBarPA.progress = 0
            PlayerActivity.binding.seekBarPA.max = player.duration
        } catch (_: Exception) {
        }
    }

    private fun updatePlayerStoppedUi() {
        try {
            PlayerActivity.binding.playPauseBtnPA.isEnabled = true
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        } catch (_: Exception) {
        }
    }

    private fun updatePlayerSongUi(context: Context, song: Music) {
        try {
            Glide.with(context)
                .load(song.artUri)
                .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
                .into(PlayerActivity.binding.songImgPA)
            PlayerActivity.binding.songNamePA.text = song.title
        } catch (_: Exception) {
        }
    }

    private fun updatePlayerFavouriteUi() {
        try {
            if (PlayerActivity.isFavourite) {
                PlayerActivity.binding.favouriteBtnPA.setImageResource(R.drawable.favourite_icon)
            } else {
                PlayerActivity.binding.favouriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
            }
        } catch (_: Exception) {
        }
    }

    private fun updatePlayerPlayPauseUi(icon: Int) {
        try {
            PlayerActivity.binding.playPauseBtnPA.setIconResource(icon)
        } catch (_: Exception) {
        }
    }
}
