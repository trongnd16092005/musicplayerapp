package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.example.mpa23itb234.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var musicAdapter: MusicAdapter
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private var musicUri: Uri? = null
    private var imageUri: Uri? = null
    private var currentUploadField: EditText? = null
    private var currentUsername: String = ""
    private var isUploading = false
    private var loadSongsJob: Job? = null
    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        musicUri = uri
        if (uri != null) currentUploadField?.setText(R.string.music_selected)
    }
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        if (uri != null) currentUploadField?.setText(R.string.image_selected)
    }

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

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            openAuthActivity()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cấu hình Navigation Drawer (menu bên hông)
        toggle = ActionBarDrawerToggle(this, binding.root, R.string.open, R.string.close)
        binding.root.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        updateNavHeader()

        // Cảnh báo nếu dùng theme đen (black) mà không bật chế độ Dark Mode của hệ thống
        if (themeIndex == 4 && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO) {
            Toast.makeText(this, getString(R.string.black_theme_dark_mode), Toast.LENGTH_LONG).show()
        }

        // Khởi tạo kết nối Firebase Realtime Database tại node "songs"
        database = FirebaseDatabase.getInstance().getReference("songs")
        storage = FirebaseStorage.getInstance()
        UserLibraryStore.load(this)
        loadCurrentUserProfile()

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
        binding.uploadBtn.setOnClickListener {
            uploadMusicDialog()
        }

        // Xử lý item click của navigation drawer (Settings, About, Exit)
        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.navSettings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                R.id.navAllSongs -> showAllSongs()
                R.id.navMySongs -> showMySongs()
                R.id.navAbout -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                R.id.navLogout -> showLogoutDialog()
                R.id.navExit -> {
                    val builder = MaterialAlertDialogBuilder(this)
                    builder.setTitle(getString(R.string.exit))
                        .setMessage(getString(R.string.exit_message))
                        .setPositiveButton(getString(R.string.yes)) { _, _ -> exitApplication(this) }
                        .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
                    val customDialog = builder.create()
                    customDialog.show()
                    setDialogBtnBackground(this, customDialog)
                }
            }
            true
        }
    }

    private fun updateNavHeader() {
        val header = binding.navView.getHeaderView(0)
        header.findViewById<TextView>(R.id.navUserEmail)?.text = currentUsername.ifBlank {
            auth.currentUser?.email ?: getString(R.string.app_name)
        }
    }

    private fun loadCurrentUserProfile() {
        val user = auth.currentUser ?: return
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(user.uid)
        usersRef
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentUsername = snapshot.child("username").getValue(String::class.java).orEmpty()
                    if (currentUsername.isBlank()) {
                        currentUsername = user.email?.substringBefore("@").orEmpty()
                        val fallbackProfile = mapOf(
                            "uid" to user.uid,
                            "username" to currentUsername,
                            "email" to (user.email ?: ""),
                            "createdAt" to System.currentTimeMillis()
                        )
                        usersRef.setValue(fallbackProfile)
                    }
                    updateNavHeader()
                }

                override fun onCancelled(error: DatabaseError) {
                    currentUsername = user.email?.substringBefore("@").orEmpty()
                    updateNavHeader()
                }
            })
    }

    private fun showLogoutDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                loadSongsJob?.cancel()
                stopMusicPlayback()
                stopService(Intent(this, MusicService::class.java))
                UserLibraryStore.clearRuntime()
                FirebaseLibraryStore.resetSession()
                auth.signOut()
                openAuthActivity()
            }
            .setNegativeButton(getString(R.string.no)) { dialog, _ -> dialog.dismiss() }
        val customDialog = builder.create()
        customDialog.show()
        setDialogBtnBackground(this, customDialog)
    }

    private fun openAuthActivity() {
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
        musicListSearch = ArrayList()
        binding.musicRV.setHasFixedSize(true)
        binding.musicRV.setItemViewCacheSize(13)
        binding.musicRV.layoutManager = LinearLayoutManager(this)
        musicAdapter = MusicAdapter(this, MusicListMA)
        binding.musicRV.adapter = musicAdapter
        binding.totalSongs.text = getString(R.string.loading_songs)

        binding.refreshLayout.setOnRefreshListener {
            refreshMusicLibrary()
        }

        loadLocalSongsAsync(loadFirebaseAfterLocal = true)
    }

    private fun refreshMusicLibrary() {
        search = false
        MusicListMA.clear()
        musicListSearch = ArrayList()
        if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
        binding.totalSongs.text = getString(R.string.loading_songs)
        loadLocalSongsAsync(loadFirebaseAfterLocal = true, stopRefreshWhenDone = true)
    }

    private fun loadLocalSongsAsync(loadFirebaseAfterLocal: Boolean, stopRefreshWhenDone: Boolean = false) {
        loadSongsJob?.cancel()
        loadSongsJob = lifecycleScope.launch {
            val localSongs = withContext(Dispatchers.IO) {
                getAllAudio()
            }
            if (!::binding.isInitialized || isFinishing || isDestroyed) return@launch
            mergeSongs(localSongs)
            updateMusicListDisplay()
            if (loadFirebaseAfterLocal) {
                loadFirebaseSongsAfterRefresh(stopRefreshWhenDone)
            } else if (stopRefreshWhenDone) {
                binding.refreshLayout.isRefreshing = false
            }
        }
    }

    private fun mergeSongs(songs: List<Music>) {
        songs.forEach { song ->
            if (MusicListMA.none { it.id == song.id && it.path == song.path }) {
                MusicListMA.add(song)
            }
        }
    }

    private fun updateMusicListDisplay() {
        if (!::musicAdapter.isInitialized) return
        if (search) {
            musicAdapter.updateMusicList(musicListSearch)
        } else {
            musicAdapter.updateMusicList(MusicListMA)
        }
        binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
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
        loadSongsJob?.cancel()
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

    private fun uploadMusicDialog() {
        if (isUploading) {
            Toast.makeText(this, getString(R.string.upload_in_progress), Toast.LENGTH_SHORT).show()
            return
        }

        musicUri = null
        imageUri = null
        currentUploadField = null

        val customDialog = LayoutInflater.from(this).inflate(R.layout.activity_upload, binding.root, false)
        val songTitle = customDialog.findViewById<EditText>(R.id.songTitle)
        val songFile = customDialog.findViewById<EditText>(R.id.songFile)
        val songImage = customDialog.findViewById<EditText>(R.id.songImage)

        songFile.setOnClickListener {
            currentUploadField = songFile
            pickAudio()
        }

        songImage.setOnClickListener {
            currentUploadField = songImage
            pickImage()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.upload_music_title))
            .setView(customDialog)
            .setPositiveButton(getString(R.string.add), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = songTitle.text?.toString()?.trim().orEmpty()

                when {
                    title.isEmpty() -> Toast.makeText(this, getString(R.string.title_required), Toast.LENGTH_SHORT).show()
                    musicUri == null -> Toast.makeText(this, getString(R.string.music_file_required), Toast.LENGTH_SHORT).show()
                    else -> {
                        uploadToFirebase(title)
                        dialog.dismiss()
                    }
                }
            }
            setDialogBtnBackground(this, dialog)
        }

        dialog.show()
    }

    private fun pickAudio() {
        pickAudioLauncher.launch("audio/*")
    }

    private fun pickImage() {
        pickImageLauncher.launch("image/*")
    }

    private fun uploadToFirebase(title: String) {
        val selectedMusicUri = musicUri ?: return
        val uid = auth.currentUser?.uid ?: return
        val id = database.push().key ?: return
        val musicStoragePath = "users/$uid/music/$id.mp3"
        val imageStoragePath = "users/$uid/images/$id.jpg"
        val musicRef = storage.reference.child(musicStoragePath)
        val imageRef = storage.reference.child(imageStoragePath)
        val duration = getAudioDuration(selectedMusicUri)

        setUploadLoading(true)
        musicRef.putFile(selectedMusicUri)
            .addOnSuccessListener {
                musicRef.downloadUrl.addOnSuccessListener { musicUrl ->
                    val selectedImageUri = imageUri
                    if (selectedImageUri != null) {
                        imageRef.putFile(selectedImageUri)
                            .addOnSuccessListener {
                                imageRef.downloadUrl.addOnSuccessListener { imageUrl ->
                                    saveToDatabase(id, title, musicUrl.toString(), imageUrl.toString(), duration, musicStoragePath, imageStoragePath)
                                }
                            }
                            .addOnFailureListener { showUploadError(it) }
                    } else {
                        saveToDatabase(id, title, musicUrl.toString(), "", duration, musicStoragePath, "")
                    }
                }
                    .addOnFailureListener { showUploadError(it) }
            }
            .addOnFailureListener { showUploadError(it) }
    }

    private fun getAudioDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun saveToDatabase(
        id: String,
        title: String,
        musicUrl: String,
        imageUrl: String,
        duration: Long,
        musicStoragePath: String,
        imageStoragePath: String
    ) {
        val ownerUid = auth.currentUser?.uid ?: return
        val ownerName = currentUsername.ifBlank {
            auth.currentUser?.email?.substringBefore("@") ?: "Unknown"
        }
        val musicMap = mapOf(
            "id" to id,
            "title" to title,
            "artist" to ownerName,
            "album" to ownerName,
            "duration" to duration,
            "path" to musicUrl,
            "artUri" to imageUrl,
            "ownerUid" to ownerUid,
            "ownerUsername" to ownerName,
            "musicStoragePath" to musicStoragePath,
            "imageStoragePath" to imageStoragePath,
            "timestamp" to System.currentTimeMillis()
        )

        database.child(id).setValue(musicMap)
            .addOnSuccessListener {
                val music = Music(id, title, ownerName, ownerName, duration, musicUrl, imageUrl, ownerUid, ownerName, musicStoragePath, imageStoragePath)
                MusicListMA.add(music)
                if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
                binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
                setUploadLoading(false)
                Toast.makeText(this, getString(R.string.uploaded), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { showUploadError(it) }
    }

    private fun musicFromSnapshot(songSnap: DataSnapshot): Music {
        val ownerUid = cleanText(
            songSnap.child("ownerUid").value ?: songSnap.child("uploadedBy").value,
            ""
        )
        val ownerUsername = cleanText(
            songSnap.child("ownerUsername").value ?: songSnap.child("uploadedByName").value,
            ""
        )
        val album = cleanText(songSnap.child("album").value, ownerUsername.ifBlank { "Unknown" })
            .takeIf { it != "None" }
            ?: ownerUsername.ifBlank { "Unknown" }

        return Music(
            id = cleanText(songSnap.child("id").value, songSnap.key ?: ""),
            title = cleanText(songSnap.child("title").value, "Unknown"),
            album = album,
            artist = cleanText(songSnap.child("artist").value, ownerUsername.ifBlank { "Unknown" }),
            duration = parseDuration(songSnap.child("duration").value),
            path = cleanText(songSnap.child("path").value, ""),
            artUri = cleanText(songSnap.child("artUri").value, ""),
            ownerUid = ownerUid,
            ownerUsername = ownerUsername,
            musicStoragePath = cleanText(songSnap.child("musicStoragePath").value, ""),
            imageStoragePath = cleanText(songSnap.child("imageStoragePath").value, "")
        )
    }

    private fun cleanText(value: Any?, fallback: String): String {
        val text = value?.toString().orEmpty()
        return if (text.isBlank() || text == "null") fallback else text
    }

    private fun parseDuration(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Double -> value.toLong()
            is Int -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun showUploadError(error: Exception) {
        setUploadLoading(false)
        Toast.makeText(this, getString(R.string.upload_failed, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
    }

    private fun setUploadLoading(loading: Boolean) {
        isUploading = loading
        if (::binding.isInitialized) {
            binding.uploadBtn.isEnabled = !loading
            binding.uploadBtn.alpha = if (loading) 0.55f else 1f
        }
    }

    private fun showAllSongs() {
        search = false
        if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
        binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
        binding.root.closeDrawers()
    }

    private fun showMySongs() {
        val uid = auth.currentUser?.uid.orEmpty()
        musicListSearch = ArrayList(MusicListMA.filter { it.ownerUid == uid })
        search = true
        if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(musicListSearch)
        binding.totalSongs.text = getString(R.string.my_songs_count, musicListSearch.size)
        binding.root.closeDrawers()
    }

    fun isCurrentUserOwner(song: Music): Boolean {
        return song.ownerUid.isNotBlank() && song.ownerUid == auth.currentUser?.uid
    }

    fun showEditSongDialog(song: Music) {
        if (!isCurrentUserOwner(song)) {
            Toast.makeText(this, getString(R.string.edit_own_song_only), Toast.LENGTH_SHORT).show()
            return
        }

        imageUri = null
        currentUploadField = null
        val customDialog = LayoutInflater.from(this).inflate(R.layout.activity_upload, binding.root, false)
        val songTitle = customDialog.findViewById<EditText>(R.id.songTitle)
        val songFileLayout = customDialog.findViewById<View>(R.id.songFileLayout)
        val songImage = customDialog.findViewById<EditText>(R.id.songImage)

        songTitle.setText(song.title)
        songFileLayout.visibility = View.GONE
        songImage.setText(if (song.artUri.isNotBlank()) getString(R.string.current_image) else "")
        songImage.setOnClickListener {
            currentUploadField = songImage
            pickImage()
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.edit_music))
            .setView(customDialog)
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = songTitle.text?.toString()?.trim().orEmpty()
                if (title.isEmpty()) {
                    Toast.makeText(this, getString(R.string.title_required), Toast.LENGTH_SHORT).show()
                } else {
                    updateSong(song, title)
                    dialog.dismiss()
                }
            }
            setDialogBtnBackground(this, dialog)
        }
        dialog.show()
    }

    private fun updateSong(song: Music, newTitle: String) {
        val selectedImageUri = imageUri
        if (selectedImageUri != null) {
            val uid = auth.currentUser?.uid ?: return
            val imageStoragePath = song.imageStoragePath.ifBlank { "users/$uid/images/${song.id}.jpg" }
            val imageRef = storage.reference.child(imageStoragePath)
            imageRef.putFile(selectedImageUri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { imageUrl ->
                        updateSongMetadata(song, newTitle, imageUrl.toString(), imageStoragePath)
                    }
                }
                .addOnFailureListener { showUploadError(it) }
        } else {
            updateSongMetadata(song, newTitle, song.artUri, song.imageStoragePath)
        }
    }

    private fun updateSongMetadata(song: Music, newTitle: String, imageUrl: String, imageStoragePath: String) {
        val updates = mapOf<String, Any>(
            "title" to newTitle,
            "artUri" to imageUrl,
            "imageStoragePath" to imageStoragePath,
            "updatedAt" to System.currentTimeMillis()
        )

        database.child(song.id).updateChildren(updates)
            .addOnSuccessListener {
                val updatedSong = song.copy(title = newTitle, artUri = imageUrl, imageStoragePath = imageStoragePath)
                replaceSongInLists(updatedSong)
                Toast.makeText(this, getString(R.string.updated), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { showUploadError(it) }
    }

    fun confirmDeleteSong(song: Music) {
        if (!isCurrentUserOwner(song)) {
            Toast.makeText(this, getString(R.string.delete_own_song_only), Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_music))
            .setMessage(getString(R.string.delete_song_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> deleteSong(song) }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()
        setDialogBtnBackground(this, dialog)
    }

    private fun deleteSong(song: Music) {
        database.child(song.id).removeValue()
            .addOnSuccessListener {
                deleteStorageFile(song.musicStoragePath)
                deleteStorageFile(song.imageStoragePath)
                removeSongFromRuntimeLists(song)
                UserLibraryStore.saveAll(this)
                if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
                binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { showUploadError(it) }
    }

    private fun removeSongFromRuntimeLists(song: Music) {
        removeSong(MusicListMA, song)
        try {
            removeSong(musicListSearch, song)
        } catch (_: UninitializedPropertyAccessException) {
        }
        removeSong(FavouriteActivity.favouriteSongs, song)
        removeSong(PlayNext.playNextList, song)
        PlaylistActivity.musicPlaylist.ref.forEach { playlist ->
            runCatching { removeSong(playlist.playlist, song) }
        }
    }

    private fun deleteStorageFile(storagePath: String) {
        if (storagePath.isNotBlank()) {
            storage.reference.child(storagePath).delete()
        }
    }

    private fun replaceSongInLists(updatedSong: Music) {
        replaceSong(MusicListMA, updatedSong)
        try {
            replaceSong(musicListSearch, updatedSong)
        } catch (_: UninitializedPropertyAccessException) {
        }
        replaceSong(FavouriteActivity.favouriteSongs, updatedSong)
        replaceSong(PlayNext.playNextList, updatedSong)
        PlaylistActivity.musicPlaylist.ref.forEach { playlist ->
            runCatching { replaceSong(playlist.playlist, updatedSong) }
        }
        UserLibraryStore.saveAll(this)
        if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(if (search) musicListSearch else MusicListMA)
    }

    private fun replaceSong(list: ArrayList<Music>, updatedSong: Music) {
        val index = list.indexOfFirst { it.id == updatedSong.id }
        if (index >= 0) list[index] = updatedSong
    }

    private fun removeSong(list: ArrayList<Music>, song: Music) {
        list.removeAll { it.id == song.id && it.path == song.path }
    }

    private fun loadFirebaseSongsAfterRefresh(stopRefreshWhenDone: Boolean = true) {
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("firebase1", "Starting to fetch songs from Firebase...")
                Log.d("firebase2", "DataSnapshot received with ${snapshot.childrenCount} songs")

                for (songSnap in snapshot.children) {
                    val music = musicFromSnapshot(songSnap)
                    if (MusicListMA.none { it.id == music.id }) MusicListMA.add(music)
                }
                FirebaseLibraryStore.loadOnlineLibrary(MusicListMA) {
                    updateMusicListDisplay()
                    UserLibraryStore.savePlaylists(this@MainActivity)
                    if (stopRefreshWhenDone) binding.refreshLayout.isRefreshing = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, getString(R.string.firebase_connection_error, error.message), Toast.LENGTH_LONG).show()
                if (stopRefreshWhenDone) binding.refreshLayout.isRefreshing = false
            }
        })
    }
}
