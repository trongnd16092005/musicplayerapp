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

/**
 * Foreground Service quản lý duy nhất vòng đời của MediaPlayer.
 *
 * Service chuẩn bị/phát nhạc, xử lý audio focus, notification media và cung
 * cấp trạng thái phát cho PlayerActivity cùng mini player.
 */
class MusicService : Service(), AudioManager.OnAudioFocusChangeListener {
    private var myBinder = MyBinder()
    var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val mainHandler = Handler(Looper.getMainLooper())
    private var seekBarRunnable: Runnable? = null
    lateinit var audioManager: AudioManager

    // region Vòng đời Service và notification

    /** Khởi tạo MediaSession khi service được tạo. */
    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(baseContext, "My Music")
    }

    /** Trả Binder để Activity điều khiển trực tiếp MusicService. */
    override fun onBind(intent: Intent?): IBinder {
        if (!::mediaSession.isInitialized) {
            mediaSession = MediaSessionCompat(baseContext, "My Music")
        }
        return myBinder
    }

    inner class MyBinder : Binder() {
        /** Cung cấp thể hiện MusicService hiện tại cho Activity đã bind. */
        fun currentService(): MusicService {
            return this@MusicService
        }
    }

    /** Tạo/cập nhật notification với các nút Previous, Play/Pause, Next và Exit. */
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

                // Phát nhạc từ notification hoặc thiết bị media.
                override fun onPlay() {
                    super.onPlay()
                    handlePlayPause()
                }

                // Tạm dừng nhạc từ notification hoặc thiết bị media.
                override fun onPause() {
                    super.onPause()
                    handlePlayPause()
                }

                // Chuyển đến bài tiếp theo.
                override fun onSkipToNext() {
                    super.onSkipToNext()
                    prevNextSong(increment = true, context = baseContext)
                }

                // Quay lại bài trước.
                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    prevNextSong(increment = false, context = baseContext)
                }

                // Xử lý nút media trên tai nghe hoặc thiết bị Bluetooth.
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    handlePlayPause()
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }

                // Tua bài hát từ media control của hệ thống.
                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    seekTo(pos.toInt())

                    mediaSession.setPlaybackState(getPlayBackState())
                }
            })
        }

        startForeground(13, notification)
    }

    // endregion

    // region Chuẩn bị và điều khiển phát nhạc

    /** Reset MediaPlayer, nạp bài hiện tại bất đồng bộ và bắt đầu phát khi sẵn sàng. */
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
                PlayerActivity.releaseLoudnessEnhancer()
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

    /** Cập nhật thời gian và seek bar của PlayerActivity theo chu kỳ 200 ms. */
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

    /** Dừng Runnable cập nhật seek bar để tránh callback sau khi service kết thúc. */
    fun stopSeekBarUpdates() {
        seekBarRunnable?.let { mainHandler.removeCallbacks(it) }
        seekBarRunnable = null
    }

    /** Tạo PlaybackState dùng cho MediaSession và notification hệ thống. */
    fun getPlayBackState(): PlaybackStateCompat {
        val playbackSpeed = if (PlayerActivity.isPlaying) 1F else 0F

        return PlaybackStateCompat.Builder().setState(
            if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            (safeCurrentPosition() ?: 0).toLong(), playbackSpeed)
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build()
    }

    /** Đảo trạng thái phát/tạm dừng từ MediaSession. */
    fun handlePlayPause() {
        if (!PlayerActivity.isPrepared) return
        if (PlayerActivity.isPlaying) pauseMusic()
        else playMusic()

        // Đồng bộ trạng thái cho notification.
        mediaSession.setPlaybackState(getPlayBackState())
    }

    /** Phát nhạc nếu service chưa ở trạng thái playing. */
    fun play() {
        if (!PlayerActivity.isPlaying) playMusic()
    }

    /** Tạm dừng nhạc nếu service đang phát. */
    fun pause() {
        if (PlayerActivity.isPlaying) pauseMusic()
    }

    /** Thay hàng phát hiện tại và chuẩn hóa vị trí bài hát. */
    fun setPlaylist(list: List<Music>, position: Int) {
        PlayerActivity.musicListPA = ArrayList(list)
        PlayerActivity.songPosition = if (PlayerActivity.musicListPA.isEmpty()) {
            0
        } else {
            position.coerceIn(0, PlayerActivity.musicListPA.lastIndex)
        }
    }

    /** Chuẩn bị MediaPlayer cho bài hiện tại. */
    fun prepare() {
        createMediaPlayer()
    }

    /** Chuyển đến bài tiếp theo trong hàng phát. */
    fun next() {
        prevNextSong(increment = true, context = baseContext)
    }

    /** Quay lại bài trước trong hàng phát. */
    fun previous() {
        prevNextSong(increment = false, context = baseContext)
    }

    /** Tua đến vị trí mili giây yêu cầu khi player đã sẵn sàng. */
    fun seekTo(pos: Int) {
        if (PlayerActivity.isPrepared) {
            try {
                mediaPlayer?.seekTo(pos)
            } catch (_: IllegalStateException) {
                PlayerActivity.isPrepared = false
            }
        }
    }

    /** Trả về bài hát hiện tại hoặc null nếu hàng phát không hợp lệ. */
    fun getCurrentSong(): Music? {
        return currentPlayerSongOrNull()
    }

    /** Đọc vị trí phát hiện tại theo cách an toàn với trạng thái MediaPlayer. */
    fun getCurrentPosition(): Int {
        return safeCurrentPosition() ?: 0
    }

    /** Đọc tổng thời lượng bài hiện tại nếu player đã chuẩn bị. */
    fun getDuration(): Int {
        return if (PlayerActivity.isPrepared) safeDuration() ?: 0 else 0
    }

    /** Kiểm tra trạng thái phát thực tế từ MediaPlayer. */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }



    /** Đổi vị trí bài hát, chuẩn bị bài mới và cập nhật các giao diện liên quan. */
    private fun prevNextSong(increment: Boolean, context: Context){

        setSongPosition(increment = increment)

        PlayerActivity.musicService?.createMediaPlayer()
        val song = currentPlayerSongOrNull() ?: return
        updatePlayerSongUi(context, song)
        NowPlaying.updateIfReady(context, song, PlayerActivity.isPlaying)
        PlayerActivity.fIndex = favouriteChecker(song)
        updatePlayerFavouriteUi()

        // Đồng bộ trạng thái cho notification.
        if (PlayerActivity.isPrepared) mediaSession.setPlaybackState(getPlayBackState())
    }

    /** Tạm dừng nhạc khi ứng dụng mất audio focus. */
    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange <= 0) {
            pauseMusic()
        }
    }

    /** Bắt đầu phát và đồng bộ PlayerActivity, mini player, notification. */
    private fun playMusic(){
        if (!PlayerActivity.isPrepared) return
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

    /** Tạm dừng phát và đồng bộ toàn bộ giao diện điều khiển. */
    private fun pauseMusic(){
        if (!PlayerActivity.isPrepared) return
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




    // endregion

    // region Vòng đời và truy cập MediaPlayer an toàn

    /** Không tự tạo lại service sau khi tiến trình bị hệ thống kết thúc. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    /** Giải phóng toàn bộ tài nguyên phát nhạc khi service bị hủy. */
    override fun onDestroy() {
        stopSeekBarUpdates()
        PlayerActivity.isPlaying = false
        PlayerActivity.isPrepared = false
        PlayerActivity.nowPlayingId = ""
        runCatching { audioManager.abandonAudioFocus(this) }
        runCatching { stopForeground(true) }
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        PlayerActivity.releaseLoudnessEnhancer()
        runCatching { mediaSession.release() }
        if (PlayerActivity.musicService === this) PlayerActivity.musicService = null
        super.onDestroy()
    }

    /** Đọc currentPosition và chuyển lỗi trạng thái thành null thay vì crash. */
    private fun safeCurrentPosition(): Int? {
        return try {
            mediaPlayer?.currentPosition
        } catch (_: IllegalStateException) {
            PlayerActivity.isPrepared = false
            null
        }
    }

    /** Đọc duration và chuyển lỗi trạng thái thành null thay vì crash. */
    private fun safeDuration(): Int? {
        return try {
            mediaPlayer?.duration
        } catch (_: IllegalStateException) {
            PlayerActivity.isPrepared = false
            null
        }
    }

    // endregion

    // region Đồng bộ giao diện Player

    /** Đưa PlayerActivity về trạng thái đang tải nếu màn hình còn tồn tại. */
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

    /** Bật điều khiển và hiển thị thời lượng sau khi MediaPlayer sẵn sàng. */
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

    /** Đưa nút Play/Pause về trạng thái đã dừng khi phát lỗi. */
    private fun updatePlayerStoppedUi() {
        try {
            PlayerActivity.binding.playPauseBtnPA.isEnabled = true
            PlayerActivity.binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
        } catch (_: Exception) {
        }
    }

    /** Cập nhật ảnh và tên bài hát trên PlayerActivity nếu màn hình còn tồn tại. */
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

    /** Đồng bộ biểu tượng yêu thích của bài đang phát. */
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

    /** Đồng bộ icon Play/Pause trên PlayerActivity. */
    private fun updatePlayerPlayPauseUi(icon: Int) {
        try {
            PlayerActivity.binding.playPauseBtnPA.setIconResource(icon)
        } catch (_: Exception) {
        }
    }

    // endregion
}
