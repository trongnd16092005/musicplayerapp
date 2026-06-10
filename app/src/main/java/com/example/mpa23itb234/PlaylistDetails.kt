package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.mpa23itb234.databinding.ActivityPlaylistDetailsBinding

class PlaylistDetails : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistDetailsBinding
    private lateinit var adapter: MusicAdapter

    companion object{
        var currentPlaylistPos: Int = -1

        fun currentPlaylistOrNull(): Playlist? {
            return PlaylistActivity.musicPlaylist.ref.getOrNull(currentPlaylistPos)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.currentTheme[MainActivity.themeIndex])
        binding = ActivityPlaylistDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        currentPlaylistPos = intent.getIntExtra("index", -1)
        val currentPlaylist = currentPlaylistOrNull() ?: run {
            finish()
            return
        }
        currentPlaylist.playlist = runCatching {
            checkPlaylist(playlist = currentPlaylist.playlist)
        }.getOrElse {
            ArrayList()
        }
        binding.playlistDetailsRV.setItemViewCacheSize(10)
        binding.playlistDetailsRV.setHasFixedSize(true)
        binding.playlistDetailsRV.layoutManager = LinearLayoutManager(this)
        adapter = MusicAdapter(this, currentPlaylist.playlist, playlistDetails = true)
        binding.playlistDetailsRV.adapter = adapter
        binding.backBtnPD.setOnClickListener { finish() }
        binding.shuffleBtnPD.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "PlaylistDetailsShuffle")
            startActivity(intent)
        }
        binding.addBtnPD.setOnClickListener {
            startActivity(Intent(this, SelectionActivity::class.java))
        }
        binding.removeAllPD.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(getString(R.string.remove))
                .setMessage(getString(R.string.remove_all_playlist_message))
                .setPositiveButton(getString(R.string.yes)){ dialog, _ ->
                    currentPlaylistOrNull()?.playlist?.clear()
                    UserLibraryStore.saveAll(this)
                    adapter.refreshPlaylist()
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.no)){dialog, _ ->
                    dialog.dismiss()
                }
            val customDialog = builder.create()
            customDialog.show()

            setDialogBtnBackground(this, customDialog)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        val currentPlaylist = currentPlaylistOrNull() ?: run {
            finish()
            return
        }
        binding.playlistNamePD.text = currentPlaylist.name
        binding.moreInfoPD.text = getString(R.string.playlist_song_count, adapter.itemCount) + "\n\n" +
                "${getString(R.string.playlist_created_on)}\n${currentPlaylist.createdOn}\n\n" +
                "  -- ${currentPlaylist.createdBy}"
        if(adapter.itemCount > 0)
        {
            Glide.with(this)
                .load(currentPlaylist.playlist.getOrNull(0)?.artUri)
                .apply(RequestOptions().placeholder(R.drawable.music_player_icon_slash_screen).centerCrop())
                .into(binding.playlistImgPD)
            binding.shuffleBtnPD.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
        UserLibraryStore.saveAll(this)
    }
}
