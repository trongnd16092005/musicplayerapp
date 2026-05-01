package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mpa23itb234.FavouriteActivity.Companion.favouriteSongs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.example.mpa23itb234.databinding.ActivityMainBinding
import java.io.File
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var musicAdapter: MusicAdapter
    private lateinit var database: DatabaseReference

    companion object {
        lateinit var MusicListMA: ArrayList<Music>
        lateinit var musicListSearch: ArrayList<Music>
        var search: Boolean = false
        var themeIndex: Int = 0

        val currentTheme = arrayOf(R.style.coolPink, R.style.coolBlue, R.style.coolPurple, R.style.coolGreen, R.style.coolBlack)
        val currentThemeNav = arrayOf(R.style.coolPinkNav, R.style.coolBlueNav, R.style.coolPurpleNav, R.style.coolGreenNav, R.style.coolBlackNav)
        val currentGradient = arrayOf(R.drawable.gradient_pink, R.drawable.gradient_blue, R.drawable.gradient_purple, R.drawable.gradient_green, R.drawable.gradient_black)
        var sortOrder: Int = 0
        val sortingList = arrayOf(MediaStore.Audio.Media.DATE_ADDED + " DESC", MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.SIZE + " DESC")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load theme người dùng chọn từ SharedPreferences
        val themeEditor = getSharedPreferences("THEMES", MODE_PRIVATE)
        themeIndex = themeEditor.getInt("themeIndex", 0)
        setTheme(currentThemeNav[themeIndex])

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cấu hình Navigation Drawer (menu bên hông)
        toggle = ActionBarDrawerToggle(this, binding.root, R.string.open, R.string.close)
        binding.root.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Cảnh báo nếu dùng theme đen (black) mà không bật chế độ Dark Mode của hệ thống
        if (themeIndex == 4 && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO) {
            Toast.makeText(this, "Black Theme Works Best in Dark Mode!!", Toast.LENGTH_LONG).show()
        }

        // Khởi tạo kết nối Firebase Realtime Database tại node "songs"
        database = FirebaseDatabase.getInstance().getReference("songs")

        // Yêu cầu quyền truy cập âm thanh ở runtime
        if (requestRuntimePermission()) {
            initializeLayout() // Nếu đã có quyền thì load giao diện và dữ liệu
        }

        // Các nút điều hướng đến các activity khác6+++++++++++++++++
        binding.shuffleBtn.setOnClickListener {
            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "MainActivity")
            startActivity(intent)
        }
        binding.favouriteBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, FavouriteActivity::class.java))
        }
        binding.playlistBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, PlaylistActivity::class.java))
        }
        binding.playNextBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, PlayNext::class.java))
        }

        // Xử lý item click của navigation drawer (Settings, About, Exit)
        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.navSettings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                R.id.navAbout -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                R.id.navExit -> {
                    val builder = MaterialAlertDialogBuilder(this)
                    builder.setTitle("Exit")
                        .setMessage("Do you want to close app?")
                        .setPositiveButton("Yes") { _, _ -> exitApplication() }
                        .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                    val customDialog = builder.create()
                    customDialog.show()
                    setDialogBtnBackground(this, customDialog)
                }
            }
            true
        }
        val json = getSharedPreferences("FAVOURITES", MODE_PRIVATE)
            .getString("FavouriteSongs", null)

        if (json != null) {
            val type = object : TypeToken<ArrayList<Music>>() {}.type
            FavouriteActivity.favouriteSongs =
                GsonBuilder().create().fromJson(json, type)
        }
        Log.d("FAV_DEBUG", "onCreate - before load size = ${favouriteSongs.size}")
    }

    // Yêu cầu quyền truy cập đọc file âm thanh tùy SDK
    private fun requestRuntimePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        else
            android.Manifest.permission.READ_MEDIA_AUDIO

        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 13)
            return false
        }
        return true
    }

    // Xử lý kết quả cấp quyền
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 13 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeLayout()
        }
    }

    // Xử lý nút trên toolbar (navigation drawer toggle)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) return true
        return super.onOptionsItemSelected(item)
    }

    // Hàm chính khởi tạo giao diện và dữ liệu
    private fun initializeLayout() {

        search = false
        MusicListMA = ArrayList()

        // 🔥 Setup RecyclerView TRƯỚC
        binding.musicRV.setHasFixedSize(true)
        binding.musicRV.layoutManager = LinearLayoutManager(this)
        musicAdapter = MusicAdapter(this, MusicListMA)
        binding.musicRV.adapter = musicAdapter

        // 🔥 Load local songs (nặng → chạy background)
        Thread {
            val localSongs = getAllAudio()

            runOnUiThread {
                MusicListMA.addAll(localSongs)
                musicAdapter.notifyDataSetChanged()
                binding.totalSongs.text = "Total Songs: ${musicAdapter.itemCount}"
            }
        }.start()

        // 🔥 Load Firebase (KHÔNG cần Thread nữa)
        database.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("firebase1", "Starting to fetch songs from Firebase...")
                Log.d("firebase2", "DataSnapshot received with ${snapshot.childrenCount} songs")
                val tempList = ArrayList<Music>()

                for (songSnap in snapshot.children) {

                    val songMap = songSnap.value as? Map<String, Any> ?: continue

                    val rawAlbum = songMap["album"]?.toString()
                    val album = if (rawAlbum.isNullOrEmpty() || rawAlbum == "null")
                        "Unknown" else rawAlbum

                    val duration = when (val d = songMap["duration"]) {
                        is Long -> d
                        is Double -> d.toLong()
                        is String -> d.toLongOrNull() ?: 0L
                        else -> 0L
                    }

                    val music = Music(
                        id = songMap["id"].toString(),
                        title = songMap["title"]?.toString() ?: "Unknown",
                        album = album,
                        artist = songMap["artist"]?.toString() ?: "Unknown",
                        duration = duration,
                        path = songMap["path"]?.toString() ?: "",
                        artUri = songMap["artUri"]?.toString() ?: ""
                    )

                    tempList.add(music) // ✅ đúng
                }

                // 🔥 update UI
                MusicListMA.addAll(tempList)
                musicAdapter.notifyDataSetChanged()
                binding.totalSongs.text = "Total Songs: ${musicAdapter.itemCount}"
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MainActivity,
                    "Lỗi Firebase: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        // 🔥 Refresh
        binding.refreshLayout.setOnRefreshListener {
            Thread {
                val songs = getAllAudio()

                runOnUiThread {
                    MusicListMA.clear()
                    MusicListMA.addAll(songs)
                    musicAdapter.notifyDataSetChanged()
                    binding.refreshLayout.isRefreshing = false
                }
            }.start()
        }
    }

    // Lấy danh sách bài hát từ bộ nhớ thiết bị
    @SuppressLint("Range")
    private fun getAllAudio(): ArrayList<Music> {
        val tempList = ArrayList<Music>()
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.MIME_TYPE + " LIKE 'audio/%'"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection,
            null, sortingList[sortOrder], null
        )

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                val id = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media._ID)) ?: "Unknown"
                val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                val path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
                val albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)).toString()
                val artUri = Uri.withAppendedPath(Uri.parse("content://media/external/audio/albumart"), albumId).toString()

                if (File(path).exists()) {
                    val music = Music(id, title, album, artist, duration, path, artUri)
                    tempList.add(music)
                }
            } while (cursor.moveToNext())
            cursor.close()
        }
        return tempList
    }

    // Hiện thanh Now Playing nếu có bài đang phát
    override fun onResume() {
        super.onResume()
        if (PlayerActivity.musicService != null) binding.nowPlaying.visibility = View.VISIBLE
    }

    // Khi thoát activity, nếu không còn bài phát, thoát app luôn
    override fun onDestroy() {
        super.onDestroy()
        if (!PlayerActivity.isPlaying && PlayerActivity.musicService != null) {
            exitApplication()
        }
    }

    // Tạo menu tìm kiếm trên toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_view_menu, menu)
        findViewById<LinearLayout>(R.id.linearLayoutNav)?.setBackgroundResource(currentGradient[themeIndex])
        val searchView = menu?.findItem(R.id.searchView)?.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                musicListSearch = ArrayList()
                newText?.lowercase()?.let { userInput ->
                    // Lọc danh sách bài hát theo tiêu đề dựa trên input tìm kiếm
                    MusicListMA.filterTo(musicListSearch) { it.title.lowercase().contains(userInput) }
                    search = true
                    musicAdapter.updateMusicList(musicListSearch)
                }
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }
}
