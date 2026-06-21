package com.example.mpa23itb234

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
import java.io.File
import java.util.concurrent.TimeUnit

data class Music(
    val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val duration: Long = 0,
    val path: String,
    val artUri: String,
    val ownerUid: String = "",
    val ownerUsername: String = "",
    val musicStoragePath: String = "",
    val imageStoragePath: String = ""
)

/** Model danh sách phát cùng metadata người tạo và thời điểm tạo. */
class Playlist {
    var id: String = ""
    lateinit var name: String
    lateinit var playlist: ArrayList<Music>
    lateinit var createdBy: String
    lateinit var createdOn: String
}

/** Model bao bọc toàn bộ playlist của người dùng hiện tại. */
class MusicPlaylist {
    var ref: ArrayList<Playlist> = ArrayList()
}

/** Kiểm tra bài hát có đường dẫn HTTP/HTTPS và được xem là bài online. */
fun Music.isOnlineSong(): Boolean {
    return path.startsWith("http://") || path.startsWith("https://")
}

/** Định dạng thời lượng mili giây thành chuỗi mm:ss. */
fun formatDuration(duration: Long): String {
    val minutes = TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS)
    val seconds = (TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS) -
            minutes * TimeUnit.SECONDS.convert(1, TimeUnit.MINUTES))
    return String.format("%02d:%02d", minutes, seconds)
}

/** Đọc ảnh bìa nhúng trong file local; bài online trả về null. */
fun getImgArt(path: String): ByteArray? {
    if (path.startsWith("http://") || path.startsWith("https://")) return null

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.embeddedPicture
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

/** Tăng/giảm vị trí bài hát và xử lý quay vòng đầu/cuối danh sách. */
fun setSongPosition(increment: Boolean) {
    val list = currentPlayerListOrNull()
    if (list.isNullOrEmpty()) return
    PlayerActivity.songPosition = PlayerActivity.songPosition.coerceIn(0, list.lastIndex)
    if (!PlayerActivity.repeat) {
        if (increment) {
            if (list.size - 1 == PlayerActivity.songPosition)
                PlayerActivity.songPosition = 0
            else ++PlayerActivity.songPosition
        } else {
            if (0 == PlayerActivity.songPosition)
                PlayerActivity.songPosition = list.size - 1
            else --PlayerActivity.songPosition
        }
    }
}

/** Trả hàng phát hiện tại hoặc null nếu Player chưa được khởi tạo. */
fun currentPlayerListOrNull(): ArrayList<Music>? {
    return try {
        PlayerActivity.musicListPA
    } catch (_: UninitializedPropertyAccessException) {
        null
    }
}

/** Trả bài hát tại vị trí phát hiện tại nếu dữ liệu hợp lệ. */
fun currentPlayerSongOrNull(): Music? {
    val list = currentPlayerListOrNull() ?: return null
    return list.getOrNull(PlayerActivity.songPosition)
}

/** Dừng player, notification, audio focus và xóa trạng thái phát runtime. */
fun stopMusicPlayback() {
    val service = PlayerActivity.musicService
    if (service != null) {
        service.stopSeekBarUpdates()
        runCatching { service.audioManager.abandonAudioFocus(service) }
        runCatching { service.stopForeground(true) }
        runCatching { service.mediaPlayer?.release() }
        service.mediaPlayer = null
        PlayerActivity.musicService = null
    }
    PlayerActivity.isPlaying = false
    PlayerActivity.isPrepared = false
    PlayerActivity.nowPlayingId = ""
}

/** Dừng nhạc và đóng toàn bộ Activity khi có context phù hợp. */
fun exitApplication(context: Context? = null) {
    stopMusicPlayback()
    (context as? Activity)?.finishAffinity()
}

/** Kiểm tra bài hiện tại trong yêu thích và trả về vị trí tìm thấy. */
fun favouriteChecker(song: Music): Int {
    PlayerActivity.isFavourite = false
    FavouriteActivity.favouriteSongs.forEachIndexed { index, music ->
        if (music.id == song.id && music.path == song.path) {
            PlayerActivity.isFavourite = true
            return index
        }
    }
    return -1
}

/** Loại các file local không còn tồn tại; giữ nguyên bài online. */
fun checkPlaylist(playlist: ArrayList<Music>): ArrayList<Music> {
    val indicesToRemove = mutableListOf<Int>()
    playlist.forEachIndexed { index, music ->
        val isRemoteSong = music.path.startsWith("http://") || music.path.startsWith("https://")
        if (!isRemoteSong && !File(music.path).exists()) indicesToRemove.add(index)
    }
    indicesToRemove.sortDescending()
    indicesToRemove.forEach { index -> playlist.removeAt(index) }
    return playlist
}

/** Áp màu nút dialog theo theme đang dùng. */
fun setDialogBtnBackground(context: Context, dialog: AlertDialog) {
    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(
        MaterialColors.getColor(context, R.attr.dialogTextColor, Color.WHITE)
    )
    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
        MaterialColors.getColor(context, R.attr.dialogTextColor, Color.WHITE)
    )
    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setBackgroundColor(
        MaterialColors.getColor(context, R.attr.dialogBtnBackground, Color.RED)
    )
    dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setBackgroundColor(
        MaterialColors.getColor(context, R.attr.dialogBtnBackground, Color.RED)
    )
}

/** Lấy màu đại diện bằng cách thu ảnh về một điểm ảnh. */
fun getMainColor(img: Bitmap): Int {
    val newImg = Bitmap.createScaledBitmap(img, 1, 1, true)
    val color = newImg.getPixel(0, 0)
    newImg.recycle()
    return color
}
