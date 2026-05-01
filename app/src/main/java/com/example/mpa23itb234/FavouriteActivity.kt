package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mpa23itb234.databinding.ActivityFavouriteBinding

class FavouriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavouriteBinding  // ViewBinding để truy cập views
    private lateinit var adapter: FavouriteAdapter          // Adapter cho RecyclerView hiển thị bài hát yêu thích

    companion object {
        var favouriteSongs: ArrayList<Music> = ArrayList()
        var favouritesChanged: Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Đặt theme ứng dụng theo lựa chọn hiện tại trong MainActivity
        setTheme(MainActivity.currentTheme[MainActivity.themeIndex])
        // Khởi tạo ViewBinding
        binding = ActivityFavouriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Kiểm tra và loại bỏ các bài hát không còn tồn tại trong danh sách yêu thích
        favouriteSongs = checkPlaylist(favouriteSongs)
        Log.d("FAV_DEBUG", "after checkPlaylist size = ${favouriteSongs.size}")
        binding.backBtnFA.setOnClickListener { finish() }

        binding.favouriteRV.setHasFixedSize(true)
        binding.favouriteRV.setItemViewCacheSize(13)
        binding.favouriteRV.layoutManager = GridLayoutManager(this, 4)
        adapter = FavouriteAdapter(this, favouriteSongs)
        binding.favouriteRV.adapter = adapter

        favouritesChanged = false

        if(favouriteSongs.size < 1) binding.shuffleBtnFA.visibility = View.INVISIBLE

        if(favouriteSongs.isNotEmpty()) binding.instructionFV.visibility = View.GONE

        // Xử lý sự kiện nút shuffle
        binding.shuffleBtnFA.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("index", 0)                // Bắt đầu phát từ bài đầu tiên
            intent.putExtra("class", "FavouriteShuffle")  // Thông tin để PlayerActivity biết phát shuffle từ Favourite
            startActivity(intent)
        }
        Log.d("FAV_DEBUG", "final list size = ${favouriteSongs.size}")
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        // Nếu danh sách yêu thích đã thay đổi, cập nhật adapter
        if(favouritesChanged) {
            adapter.updateFavourites(favouriteSongs)
            favouritesChanged = false
        }
    }
}
