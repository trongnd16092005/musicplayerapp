package com.example.mpa23itb234

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.mpa23itb234.databinding.ActivityPlaylistBinding
import com.example.mpa23itb234.databinding.AddPlaylistDialogBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/** Màn hình hiển thị, tạo và mở các danh sách phát của người dùng. */
class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private lateinit var adapter: PlaylistViewAdapter

    companion object{
        var musicPlaylist: MusicPlaylist = MusicPlaylist()
    }

    /** Khởi tạo lưới playlist và sự kiện tạo playlist mới. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.ACTIVE_THEME)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBtnPlaylist.setOnClickListener { finish() }

        binding.playlistRV.setHasFixedSize(true)
        binding.playlistRV.setItemViewCacheSize(13)
        binding.playlistRV.layoutManager = GridLayoutManager(this@PlaylistActivity, 2)
        adapter = PlaylistViewAdapter(this, playlistList = musicPlaylist.ref)
        binding.playlistRV.adapter = adapter
        binding.addPlaylistBtn.setOnClickListener { showCreatePlaylistDialog() }

        if(musicPlaylist.ref.isNotEmpty()) binding.instructionPA.visibility = View.GONE
    }
    /** Hiển thị form nhập tên playlist và người tạo. */
    private fun showCreatePlaylistDialog(){
        val customDialog = LayoutInflater.from(this@PlaylistActivity).inflate(R.layout.add_playlist_dialog, binding.root, false)
        val binder = AddPlaylistDialogBinding.bind(customDialog)
        val builder = MaterialAlertDialogBuilder(this)
        val dialog = builder.setView(customDialog)
            .setTitle(getString(R.string.playlist_details_title))
            .setPositiveButton(getString(R.string.add)){ dialog, _ ->
                val playlistName = binder.playlistName.text
                val createdBy = binder.yourName.text
                if(playlistName != null && createdBy != null)
                    if(playlistName.isNotEmpty() && createdBy.isNotEmpty())
                    {
                        addPlaylist(playlistName.toString(), createdBy.toString())
                    }
                dialog.dismiss()
            }.create()
        dialog.show()
        setDialogBtnBackground(this, dialog)

    }
    /** Kiểm tra trùng tên, tạo playlist và lưu thư viện người dùng. */
    private fun addPlaylist(name: String, createdBy: String){
        var playlistExists = false
        for(i in musicPlaylist.ref) {
            if (name == i.name){
                playlistExists = true
                break
            }
        }
        if(playlistExists) Toast.makeText(this, getString(R.string.playlist_exists), Toast.LENGTH_SHORT).show()
        else {
            val tempPlaylist = Playlist()
            tempPlaylist.id = UserLibraryStore.playlistIdFromName(name)
            tempPlaylist.name = name
            tempPlaylist.playlist = ArrayList()
            tempPlaylist.createdBy = createdBy
            val calendar = Calendar.getInstance().time
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            tempPlaylist.createdOn = sdf.format(calendar)
            musicPlaylist.ref.add(tempPlaylist)
            UserLibraryStore.saveAll(this)
            adapter.refreshPlaylist()
        }
    }

    /** Làm mới lưới khi quay lại từ màn chi tiết playlist. */
    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }
}
