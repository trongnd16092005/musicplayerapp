package com.example.mpa23itb234

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.example.mpa23itb234.MusicAdapter.MyHolder
import com.example.mpa23itb234.databinding.DetailsViewBinding
import com.example.mpa23itb234.databinding.MoreFeaturesBinding
import com.example.mpa23itb234.databinding.MusicViewBinding

/** Adapter hiển thị bài hát và xử lý menu thao tác của từng bài. */
class MusicAdapter(private val context: Context, private var musicList: ArrayList<Music>, private val playlistDetails: Boolean = false,
private val selectionActivity: Boolean = false)
    : RecyclerView.Adapter<MyHolder>() {

    class MyHolder(binding: MusicViewBinding) : RecyclerView.ViewHolder(binding.root) {
        val title = binding.songNameMV
        val album = binding.songAlbumMV
        val image = binding.imageMV
        val duration = binding.songDuration
        val root = binding.root
    }

    /** Tạo ViewHolder từ layout music_view. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return MyHolder(MusicViewBinding.inflate(LayoutInflater.from(context), parent, false))
    }

    /** Gắn metadata, ảnh bìa và sự kiện click/nhấn giữ cho một bài hát. */
    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val song = musicList[position]
        holder.title.text = song.title
        holder.album.text = displaySubtitle(song)
        holder.duration.text = formatDuration(song.duration)
        val artUri = song.artUri
        if (!artUri.isNullOrEmpty()) {
            Glide.with(context)
                .load(artUri)
                .apply(RequestOptions()
                    .placeholder(R.drawable.music_player_icon_slash_screen)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL))
                .into(holder.image)
        } else {
            holder.image.setImageResource(R.drawable.music_player_icon_slash_screen)
        }
        // Nhấn giữ để mở các thao tác hàng chờ, thông tin, sửa và xóa.
        if(!selectionActivity)
            holder.root.setOnLongClickListener {
                val customDialog = LayoutInflater.from(context).inflate(R.layout.more_features, holder.root, false)
                val bindingMF = MoreFeaturesBinding.bind(customDialog)
                val dialog = MaterialAlertDialogBuilder(context).setView(customDialog)
                    .create()
                dialog.show()
                dialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
                val selectedSong = musicList[position]
                val mainActivity = context as? MainActivity
                val isOwner = mainActivity?.isCurrentUserOwner(selectedSong) == true
                bindingMF.editSongBtn.visibility = if (isOwner) View.VISIBLE else View.GONE
                bindingMF.deleteSongBtn.visibility = if (isOwner) View.VISIBLE else View.GONE

                bindingMF.AddToPNBtn.setOnClickListener {
                    val added = addToPlayNext(selectedSong)
                    val message = if (added) {
                        context.getString(R.string.added_to_play_next)
                    } else {
                        context.getString(R.string.already_in_play_next)
                    }
                    Snackbar.make(holder.root, message, Snackbar.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                bindingMF.infoBtn.setOnClickListener {
                    dialog.dismiss()
                    val detailsDialog = LayoutInflater.from(context).inflate(R.layout.details_view, bindingMF.root, false)
                    val binder = DetailsViewBinding.bind(detailsDialog)
                    binder.detailsTV.setTextColor(Color.WHITE)
                    binder.root.setBackgroundColor(Color.TRANSPARENT)
                    val dDialog = MaterialAlertDialogBuilder(context)
                        .setView(detailsDialog)
                        .setPositiveButton(context.getString(R.string.close_dialog)){self, _ -> self.dismiss()}
                        .setCancelable(false)
                        .create()
                    dDialog.show()
                    dDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
                    setDialogBtnBackground(context, dDialog)
                    dDialog.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
                    val str = SpannableStringBuilder().bold { append(context.getString(R.string.details_title)).append("\n\n").append(context.getString(R.string.details_name)) }
                        .append(musicList[position].title)
                        .bold { append("\n\n").append(context.getString(R.string.details_duration)) }.append(DateUtils.formatElapsedTime(musicList[position].duration/1000))
                        .bold { append("\n\n").append(context.getString(R.string.details_uploaded_by)) }.append(musicList[position].ownerUsername.ifBlank { "Unknown" })
                        .bold { append("\n\n").append(context.getString(R.string.details_location)) }.append(musicList[position].path)
                    binder.detailsTV.text = str
                }

                bindingMF.editSongBtn.setOnClickListener {
                    dialog.dismiss()
                    mainActivity?.showEditSongDialog(selectedSong)
                }

                bindingMF.deleteSongBtn.setOnClickListener {
                    dialog.dismiss()
                    mainActivity?.confirmDeleteSong(selectedSong)
                }

                return@setOnLongClickListener true
            }

        when{
            playlistDetails ->{
                holder.root.setOnClickListener {
                        sendIntent(ref = PlayerNavigation.SOURCE_PLAYLIST, pos = position)
                }
            }
            selectionActivity ->{
                holder.root.setOnClickListener {
                    if(addSong(musicList[position]))
                        holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.cool_pink))
                    else
                        holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.white))

                }
            }
            else ->{
                holder.root.setOnClickListener {
                when{
                    MainActivity.search -> sendIntent(ref = PlayerNavigation.SOURCE_MUSIC_SEARCH, pos = position)
                    musicList[position].id == PlayerActivity.nowPlayingId ->
                        sendIntent(ref = PlayerNavigation.SOURCE_NOW_PLAYING, pos = PlayerActivity.songPosition)
                    else->sendIntent(ref = PlayerNavigation.SOURCE_MUSIC_ADAPTER, pos = position) } }
        }

         }
    }

    /** Trả số bài hát đang hiển thị. */
    override fun getItemCount(): Int {
        return musicList.size
    }

    /** Tạo dòng phụ ưu tiên tên người tải lên, sau đó mới dùng nghệ sĩ. */
    private fun displaySubtitle(song: Music): String {
        val artist = cleanSubtitleValue(song.artist)
        return if (song.ownerUsername.isNotBlank()) {
            "Đăng bởi ${song.ownerUsername}"
        } else {
            artist.ifBlank { "Unknown" }
        }
    }

    /** Loại bỏ các giá trị placeholder không nên hiển thị. */
    private fun cleanSubtitleValue(value: String): String {
        return if (value.isBlank() || value == "None" || value == "null" || value == "Unknown") "" else value
    }

    /** Thay dữ liệu adapter bằng một bản sao của danh sách mới. */
    fun updateMusicList(searchList : ArrayList<Music>){
        musicList = ArrayList()
        musicList.addAll(searchList)
        notifyDataSetChanged()
    }
    /** Mở PlayerActivity với nguồn danh sách và vị trí được chọn. */
    private fun sendIntent(ref: String, pos: Int){
        val intent = Intent(context, PlayerActivity::class.java)
        intent.putExtra(PlayerNavigation.EXTRA_INDEX, pos)
        intent.putExtra(PlayerNavigation.EXTRA_SOURCE, ref)
        ContextCompat.startActivity(context, intent, null)
    }

    /** Thêm bài vào hàng chờ và giữ bài đang phát ở đầu khi cần. */
    private fun addToPlayNext(song: Music): Boolean {
        if (PlayNext.playNextList.isEmpty()) {
            currentPlayingSong()?.let { currentSong ->
                PlayNext.playNextList.add(currentSong)
                PlayerActivity.songPosition = 0
            }
        }

        if (PlayNext.playNextList.any { it.id == song.id && it.path == song.path }) {
            syncPlayerQueue()
            return false
        }

        PlayNext.playNextList.add(song)
        syncPlayerQueue()
        return true
    }

    /** Lấy bài đang phát hiện tại để tạo hàng chờ nhất quán. */
    private fun currentPlayingSong(): Music? {
        return try {
            if (PlayerActivity.musicService != null) {
                PlayerActivity.musicListPA.getOrNull(PlayerActivity.songPosition)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Đồng bộ PlayNext vào hàng phát runtime khi service đang hoạt động. */
    private fun syncPlayerQueue() {
        if (PlayerActivity.musicService == null) return
        PlayerActivity.musicListPA = ArrayList(PlayNext.playNextList)
    }

    /** Thêm hoặc loại bài hát khỏi playlist đang chỉnh sửa. */
    private fun addSong(song: Music): Boolean{
        val playlist = PlaylistDetails.currentPlaylistOrNull()?.playlist ?: return false
        playlist.forEachIndexed { index, music ->
            if(song.id == music.id){
                playlist.removeAt(index)
                UserLibraryStore.saveAll(context)
                return false
            }
        }
        playlist.add(song)
        UserLibraryStore.saveAll(context)
        return true
    }
    /** Nạp lại playlist hiện tại vào adapter. */
    fun refreshPlaylist(){
        musicList = ArrayList()
        musicList = PlaylistDetails.currentPlaylistOrNull()?.playlist ?: ArrayList()
        notifyDataSetChanged()
    }
}
