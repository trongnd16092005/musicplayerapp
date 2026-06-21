package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.mpa23itb234.databinding.ActivityPlayerBinding
import com.example.mpa23itb234.databinding.AudioBoosterBinding

/**
 * Màn hình phát nhạc toàn màn hình.
 *
 * Activity hiển thị thông tin bài hát và chuyển các lệnh phát, tạm dừng,
 * chuyển bài, tua nhạc sang [MusicService].
 */
class PlayerActivity : AppCompatActivity(), ServiceConnection {

    companion object {
        lateinit var musicListPA: ArrayList<Music>
        var songPosition: Int = 0
        var isPlaying: Boolean = false
        var isPrepared: Boolean = false
        var musicService: MusicService? = null
        @SuppressLint("StaticFieldLeak")
        lateinit var binding: ActivityPlayerBinding
        var repeat: Boolean = false
        var min15: Boolean = false
        var min30: Boolean = false
        var min60: Boolean = false
        var nowPlayingId: String = ""
        var isFavourite: Boolean = false
        var fIndex: Int = -1
        lateinit var loudnessEnhancer: LoudnessEnhancer
    }

    private val equalizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Không cần xử lý kết quả, panel Equalizer hệ thống tự áp dụng hiệu ứng.
    }

    // region Khởi tạo và sự kiện giao diện

    /** Khởi tạo giao diện Player và đăng ký toàn bộ thao tác điều khiển nhạc. */
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Thiết lập theme từ MainActivity
        setTheme(MainActivity.ACTIVE_THEME)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Xử lý nếu phát nhạc từ file được chọn ngoài app
        if (intent.data?.scheme.contentEquals("content")) {
            songPosition = 0
            val intentService = Intent(this, MusicService::class.java)
            bindService(intentService, this, BIND_AUTO_CREATE)
            startService(intentService)
            musicListPA = ArrayList()
            val externalSong = getMusicDetails(intent.data!!) ?: run {
                finish()
                return
            }
            musicListPA.add(externalSong)
            val currentSong = currentPlayerSongOrNull() ?: return
            // Hiển thị ảnh và tiêu đề bài hát
            Glide.with(this)
                .load(getImgArt(currentSong.path))
                .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
                .into(binding.songImgPA)
            binding.songNamePA.text = currentSong.title
        } else initializeLayout()

        // Nút Audio Booster: điều chỉnh LoudnessEnhancer
        binding.boosterBtnPA.setOnClickListener {
            val customDialogB = LayoutInflater.from(this).inflate(R.layout.audio_booster, binding.root, false)
            val bindingB = AudioBoosterBinding.bind(customDialogB)
            val dialogB = MaterialAlertDialogBuilder(this).setView(customDialogB)
                .setOnCancelListener { playMusic() }
                .setPositiveButton(getString(R.string.ok)) { self, _ ->
                    loudnessEnhancer.setTargetGain(bindingB.verticalBar.progress * 100)
                    playMusic()
                    self.dismiss()
                }
                .setBackground(ColorDrawable(0x803700B3.toInt()))
                .create()
            dialogB.show()

            // Cập nhật giao diện dialog
            bindingB.verticalBar.progress = loudnessEnhancer.targetGain.toInt() / 100
            bindingB.progressText.text = getString(R.string.audio_boost_value, loudnessEnhancer.targetGain.toInt() / 10)
            bindingB.verticalBar.setOnProgressChangeListener {
                bindingB.progressText.text = getString(R.string.audio_boost_value, it * 10)
            }
            setDialogBtnBackground(this, dialogB)
        }

        // Các nút điều khiển: quay lại, play/pause, trước/sau
        binding.backBtnPA.setOnClickListener { finish() }
        binding.playPauseBtnPA.setOnClickListener { if (isPlaying) pauseMusic() else playMusic() }
        binding.previousBtnPA.setOnClickListener { prevNextSong(increment = false) }
        binding.nextBtnPA.setOnClickListener { prevNextSong(increment = true) }

        // SeekBar để tua bài hát
        binding.seekBarPA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isPrepared) {
                    try {
                        musicService?.mediaPlayer?.seekTo(progress)
                        musicService?.showNotification(if (isPlaying) R.drawable.pause_icon else R.drawable.play_icon)
                    } catch (_: IllegalStateException) {
                        isPrepared = false
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Nút lặp lại bài
        binding.repeatBtnPA.setOnClickListener {
            if (!repeat) {
                repeat = true
                binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
            } else {
                repeat = false
                binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.cool_pink))
            }
        }

        // Nút Equalizer: mở panel hệ thống
        binding.equalizerBtnPA.setOnClickListener {
            try {
                val player = musicService?.mediaPlayer
                if (!isPrepared || player == null) {
                    Toast.makeText(this, getString(R.string.song_loading), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                eqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, baseContext.packageName)
                eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                equalizerLauncher.launch(eqIntent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.equalizer_not_supported), Toast.LENGTH_SHORT).show()
            }
        }

        // Nút hẹn giờ dừng nhạc
        binding.timerBtnPA.setOnClickListener {
            val timerOn = min15 || min30 || min60
            if (!timerOn) showBottomSheetDialog()
            else {
                val builder = MaterialAlertDialogBuilder(this)
                builder.setTitle(getString(R.string.stop_timer))
                    .setMessage(getString(R.string.stop_timer_message))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        // Reset hẹn giờ
                        min15 = false
                        min30 = false
                        min60 = false
                        binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.cool_pink))
                    }
                    .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
                val customDialog = builder.create()
                customDialog.show()
                setDialogBtnBackground(this, customDialog)
            }
        }

        // Nút chia sẻ bài hát
        binding.shareBtnPA.setOnClickListener {
            val currentSong = currentPlayerSongOrNull() ?: return@setOnClickListener
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(currentSong.path))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_music_file)))
        }

        // Nút yêu thích
        binding.favouriteBtnPA.setOnClickListener {
            val currentSong = currentPlayerSongOrNull() ?: return@setOnClickListener
            fIndex = favouriteChecker(currentSong)
            if (isFavourite) {
                isFavourite = false
                binding.favouriteBtnPA.setImageResource(R.drawable.favourite_empty_icon)
                if (fIndex >= 0) FavouriteActivity.favouriteSongs.removeAt(fIndex)
            } else {
                isFavourite = true
                binding.favouriteBtnPA.setImageResource(R.drawable.favourite_icon)
                FavouriteActivity.favouriteSongs.add(currentSong)
            }
            FavouriteActivity.favouritesChanged = true
            UserLibraryStore.saveFavourites(this)
            FirebaseLibraryStore.saveFavourite(currentSong, isFavourite)
        }
    }

    // endregion

    // region Khởi tạo nguồn phát

    /** Chọn danh sách phát dựa trên nguồn mở Player được truyền qua Intent. */
    private fun initializeLayout() {
        songPosition = intent.getIntExtra(PlayerNavigation.EXTRA_INDEX, 0)
        when (intent.getStringExtra(PlayerNavigation.EXTRA_SOURCE)) {
            PlayerNavigation.SOURCE_NOW_PLAYING -> {
                setLayout()
                // Cập nhật SeekBar và thời gian
                if (isPrepared) {
                    val current = musicService?.getCurrentPosition() ?: 0
                    val duration = musicService?.getDuration() ?: 0
                    binding.tvSeekBarStart.text = formatDuration(current.toLong())
                    binding.tvSeekBarEnd.text = formatDuration(duration.toLong())
                    binding.seekBarPA.progress = current
                    binding.seekBarPA.max = duration
                } else {
                    setPlayerLoading(true)
                }
                binding.playPauseBtnPA.setIconResource(if (isPlaying) R.drawable.pause_icon else R.drawable.play_icon)
            }
            // Các trường hợp khởi tạo playlist từ các Adapter khác nhau
            PlayerNavigation.SOURCE_MUSIC_SEARCH -> initServiceAndPlaylist(MainActivity.musicListSearch, shuffle = false)
            PlayerNavigation.SOURCE_MUSIC_ADAPTER -> initServiceAndPlaylist(MainActivity.MusicListMA, shuffle = false)
            PlayerNavigation.SOURCE_FAVOURITE -> initServiceAndPlaylist(FavouriteActivity.favouriteSongs, shuffle = false)
            PlayerNavigation.SOURCE_MAIN_SHUFFLE -> initServiceAndPlaylist(MainActivity.MusicListMA, shuffle = true)
            PlayerNavigation.SOURCE_FAVOURITE_SHUFFLE -> initServiceAndPlaylist(FavouriteActivity.favouriteSongs, shuffle = true)
            PlayerNavigation.SOURCE_PLAYLIST -> initServiceAndPlaylist(
                PlaylistDetails.currentPlaylistOrNull()?.playlist ?: run {
                    finish()
                    return
                },
                shuffle = false
            )
            PlayerNavigation.SOURCE_PLAYLIST_SHUFFLE -> initServiceAndPlaylist(
                PlaylistDetails.currentPlaylistOrNull()?.playlist ?: run {
                    finish()
                    return
                },
                shuffle = true
            )
            PlayerNavigation.SOURCE_PLAY_NEXT -> initServiceAndPlaylist(PlayNext.playNextList, shuffle = false, playNext = true)
        }
    }

    /** Hiển thị ảnh, tên, trạng thái yêu thích và màu nền của bài hiện tại. */
    private fun setLayout() {
        val currentSong = currentPlayerSongOrNull() ?: return
        fIndex = favouriteChecker(currentSong)
        Glide
            .with(applicationContext)
            .load(currentSong.artUri)
            .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
            .into(binding.songImgPA)
        binding.songNamePA.text = currentSong.title
        // Cập nhật trạng thái nút repeat và timer
        if (repeat) binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(applicationContext, R.color.purple_500))
        if (min15 || min30 || min60) binding.timerBtnPA.setColorFilter(ContextCompat.getColor(applicationContext, R.color.purple_500))
        binding.favouriteBtnPA.setImageResource(if (isFavourite) R.drawable.favourite_icon else R.drawable.favourite_empty_icon)

        // Tạo gradient nền theo màu chính của ảnh
        val img = getImgArt(currentSong.path)
        val image = if (img != null) BitmapFactory.decodeByteArray(img, 0, img.size) else BitmapFactory.decodeResource(resources, R.drawable.music_player_icon_slash_screen)
        val bgColor = getMainColor(image)
        val gradient = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xFFFFFF, bgColor))
        binding.root.background = gradient
        window?.statusBarColor = bgColor
    }

    /** Yêu cầu MusicService chuẩn bị MediaPlayer cho bài hát hiện tại. */
    private fun createMediaPlayer() {
        musicService?.createMediaPlayer()
    }

    /** Yêu cầu service phát nhạc nếu MediaPlayer đã chuẩn bị xong. */
    private fun playMusic() {
        if (!isPrepared) {
            Toast.makeText(this, getString(R.string.song_loading), Toast.LENGTH_SHORT).show()
            return
        }
        musicService?.play()
    }

    /** Yêu cầu service tạm dừng bài hát hiện tại. */
    private fun pauseMusic() {
        if (!isPrepared) return
        musicService?.pause()
    }

    /** Cập nhật vị trí bài hát, giao diện và chuẩn bị bài mới. */
    private fun prevNextSong(increment: Boolean) {
        setSongPosition(increment)
        setLayout()
        createMediaPlayer()
    }

    /** Khóa điều khiển và đặt UI về trạng thái chờ khi bài hát đang tải. */
    private fun setPlayerLoading(loading: Boolean) {
        isPrepared = !loading
        isPlaying = false
        binding.playPauseBtnPA.isEnabled = !loading
        binding.seekBarPA.isEnabled = !loading
        binding.tvSeekBarStart.text = getString(R.string.start_tv)
        binding.tvSeekBarEnd.text = if (loading) getString(R.string.end_tv) else binding.tvSeekBarEnd.text
        binding.seekBarPA.progress = 0
        binding.playPauseBtnPA.setIconResource(R.drawable.play_icon)
    }

    /** Nhận MusicService, yêu cầu audio focus và bắt đầu chuẩn bị bài hát. */
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (musicService == null) {
            val binder = service as MusicService.MyBinder
            musicService = binder.currentService()
            val currentService = musicService ?: return
            currentService.audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            currentService.audioManager.requestAudioFocus(currentService, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        createMediaPlayer()
        musicService?.seekBarSetup()
    }

    /** Xóa tham chiếu service khi kết nối bị ngắt ngoài ý muốn. */
    override fun onServiceDisconnected(name: ComponentName?) {
        musicService = null
    }

    // endregion

    // region Tiện ích Player

    /** Hiển thị các mốc hẹn giờ dừng nhạc 15, 30 và 60 phút. */
    private fun showBottomSheetDialog() {
        val dialog = BottomSheetDialog(this@PlayerActivity)
        dialog.setContentView(R.layout.bottom_sheet_dialog)
        dialog.show()
        dialog.findViewById<LinearLayout>(R.id.min_15)?.setOnClickListener {
            Toast.makeText(baseContext, getString(R.string.music_stop_15), Toast.LENGTH_SHORT).show()
            binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
            min15 = true
            Thread {
                Thread.sleep((15 * 60000).toLong())
                if (min15) binding.root.post { exitApplication(this) }
            }.start()
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_30)?.setOnClickListener {
            Toast.makeText(baseContext, getString(R.string.music_stop_30), Toast.LENGTH_SHORT).show()
            binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
            min30 = true
            Thread {
                Thread.sleep((30 * 60000).toLong())
                if (min30) binding.root.post { exitApplication(this) }
            }.start()
            dialog.dismiss()
        }
        dialog.findViewById<LinearLayout>(R.id.min_60)?.setOnClickListener {
            Toast.makeText(baseContext, getString(R.string.music_stop_60), Toast.LENGTH_SHORT).show()
            binding.timerBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple_500))
            min60 = true
            Thread {
                Thread.sleep((60 * 60000).toLong())
                if (min60) binding.root.post { exitApplication(this) }
            }.start()
            dialog.dismiss()
        }
    }

    /** Đọc metadata khi người dùng mở một file âm thanh từ ứng dụng khác. */
    private fun getMusicDetails(contentUri: Uri): Music? {
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val cursor: Cursor? = runCatching {
            contentResolver.query(contentUri, projection, null, null, null)
        }.getOrNull()

        cursor?.use {
            if (it.moveToFirst()) {
                val dataIndex = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val durationIndex = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val path = if (dataIndex >= 0) it.getString(dataIndex).orEmpty() else ""
                val duration = if (durationIndex >= 0) it.getLong(durationIndex) else 0L
                val title = if (nameIndex >= 0) it.getString(nameIndex).orEmpty() else ""
                val playablePath = path.ifBlank { contentUri.toString() }

                return Music(
                    id = "Unknown",
                    title = title.ifBlank { playablePath },
                    album = "Unknown",
                    artist = "Unknown",
                    duration = duration,
                    artUri = "Unknown",
                    path = playablePath
                )
            }
        }

        return Music(
            id = "Unknown",
            title = contentUri.lastPathSegment ?: contentUri.toString(),
            album = "Unknown",
            artist = "Unknown",
            duration = 0L,
            artUri = "Unknown",
            path = contentUri.toString()
        )
    }

    /** Dọn phiên phát file ngoài khi Player bị hủy và bài hát đã dừng. */
    override fun onDestroy() {
        super.onDestroy()
        // Nếu phát từ file ngoài và đã dừng, thoát app
        if (currentPlayerSongOrNull()?.id == "Unknown" && !isPlaying) exitApplication(this)
    }

    /** Sao chép danh sách nguồn, áp dụng shuffle và kết nối MusicService. */
    private fun initServiceAndPlaylist(playlist: ArrayList<Music>, shuffle: Boolean, playNext: Boolean = false) {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, this, BIND_AUTO_CREATE)
        startService(intent)
        musicListPA = ArrayList()
        musicListPA.addAll(playlist)
        if (musicListPA.isEmpty()) {
            finish()
            return
        }
        if (shuffle) musicListPA.shuffle()
        songPosition = songPosition.coerceIn(0, musicListPA.lastIndex)
        setLayout()
        if (!playNext) PlayNext.playNextList = ArrayList()
    }

    // endregion
}
