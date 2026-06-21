package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mpa23itb234.databinding.ActivityFavouriteBinding

/** Màn hình hiển thị và phát các bài hát yêu thích của tài khoản hiện tại. */
class FavouriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavouriteBinding
    private lateinit var adapter: FavouriteAdapter

    companion object {
        var favouriteSongs: ArrayList<Music> = ArrayList()
        var favouritesChanged: Boolean = false
    }

    /** Nạp danh sách yêu thích hợp lệ và khởi tạo lưới bài hát. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(MainActivity.ACTIVE_THEME)
        binding = ActivityFavouriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favouriteSongs = checkPlaylist(favouriteSongs)
        UserLibraryStore.saveFavourites(this)

        binding.backBtnFA.setOnClickListener { finish() }

        binding.favouriteRV.setHasFixedSize(true)
        binding.favouriteRV.setItemViewCacheSize(13)
        binding.favouriteRV.layoutManager = GridLayoutManager(this, 4)
        adapter = FavouriteAdapter(this, favouriteSongs)
        binding.favouriteRV.adapter = adapter

        favouritesChanged = false

        if(favouriteSongs.size < 1) binding.shuffleBtnFA.visibility = View.INVISIBLE

        if(favouriteSongs.isNotEmpty()) binding.instructionFV.visibility = View.GONE

        binding.shuffleBtnFA.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra(PlayerNavigation.EXTRA_INDEX, 0)
            intent.putExtra(PlayerNavigation.EXTRA_SOURCE, PlayerNavigation.SOURCE_FAVOURITE_SHUFFLE)
            startActivity(intent)
        }
    }

    /** Cập nhật adapter khi trạng thái yêu thích thay đổi từ màn Player. */
    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        if(favouritesChanged) {
            adapter.updateFavourites(favouriteSongs)
            favouritesChanged = false
        }
    }
}
