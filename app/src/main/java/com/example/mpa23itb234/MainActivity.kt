package com.example.mpa23itb234

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
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

/**
 * Màn hình chính của ứng dụng.
 *
 * Lớp chịu trách nhiệm hiển thị thư viện nhạc local/online, điều hướng các màn
 * chức năng và quản lý thao tác tải lên, sửa, xóa bài hát của người dùng.
 */
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
    private var uploadProgressDialog: AlertDialog? = null
    private var uploadProgressBar: ProgressBar? = null
    private var uploadProgressText: TextView? = null
    private var uploadProgressStatus: TextView? = null
    private var loadSongsJob: Job? = null
    private var loadGeneration = 0
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
        val ACTIVE_THEME = R.style.coolBlue
        val ACTIVE_NAV_THEME = R.style.coolBlueNav
        val ACTIVE_GRADIENT = R.drawable.gradient_blue
        var sortOrder: Int = 0
        val sortingList = arrayOf(MediaStore.Audio.Media.DATE_ADDED + " DESC", MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.SIZE + " DESC")
        private var cachedOnlineSongsUid: String? = null
        private var cachedOnlineSongsLoaded = false
        private var cachedOnlineSongs: ArrayList<Music> = ArrayList()
    }

    // region Khởi tạo và điều hướng

    /** Khởi tạo theme, Firebase, giao diện chính và các sự kiện điều hướng. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(ACTIVE_NAV_THEME)

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

        // Khởi tạo kết nối Firebase Realtime Database tại node "songs"
        database = FirebaseDatabase.getInstance().getReference("songs")
        storage = FirebaseStorage.getInstance()
        UserLibraryStore.load(this)
        loadCurrentUserProfile()

        // Yêu cầu quyền truy cập âm thanh ở runtime
        if (requestRuntimePermission()) {
            initializeLayout() // Nếu đã có quyền thì load giao diện và dữ liệu
        }

        // Các nút điều hướng nhanh trên màn hình chính.
        binding.shuffleBtn.setOnClickListener {
            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
            intent.putExtra(PlayerNavigation.EXTRA_INDEX, 0)
            intent.putExtra(PlayerNavigation.EXTRA_SOURCE, PlayerNavigation.SOURCE_MAIN_SHUFFLE)
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

    /** Hiển thị username hoặc email hiện tại trên phần đầu Navigation Drawer. */
    private fun updateNavHeader() {
        val header = binding.navView.getHeaderView(0)
        header.findViewById<TextView>(R.id.navUserEmail)?.text = currentUsername.ifBlank {
            auth.currentUser?.email ?: getString(R.string.app_name)
        }
    }

    /** Đọc hồ sơ người dùng từ node users/{uid} và tạo dữ liệu dự phòng khi cần. */
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

    /** Xác nhận đăng xuất, dừng nhạc và xóa dữ liệu runtime của tài khoản hiện tại. */
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

    /** Mở màn hình xác thực và xóa toàn bộ Activity cũ khỏi back stack. */
    private fun openAuthActivity() {
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // endregion

    // region Quyền truy cập và thư viện nhạc

    /** Yêu cầu quyền đọc file âm thanh tương ứng với phiên bản Android. */
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

    /** Bắt đầu tải thư viện sau khi người dùng cấp quyền đọc âm thanh. */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 13 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeLayout()
        }
    }

    /** Chuyển sự kiện nút Home trên ActionBar cho Navigation Drawer. */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) return true
        return super.onOptionsItemSelected(item)
    }

    /** Khởi tạo RecyclerView, thao tác kéo để tải lại và bắt đầu nạp bài hát. */
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

        loadMusicLibrary()
    }

    /** Xóa danh sách hiện tại và tải mới cả bài hát local lẫn Firebase. */
    private fun refreshMusicLibrary() {
        search = false
        MusicListMA.clear()
        musicListSearch = ArrayList()
        if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
        binding.totalSongs.text = getString(R.string.loading_songs)
        loadMusicLibrary(forceRemote = true, stopRefreshWhenDone = true)
    }

    /**
     * Tải thư viện local và online song song.
     * [forceRemote] bỏ qua cache online; [stopRefreshWhenDone] kết thúc hiệu ứng refresh.
     */
    private fun loadMusicLibrary(forceRemote: Boolean = false, stopRefreshWhenDone: Boolean = false) {
        loadSongsJob?.cancel()
        val generation = ++loadGeneration
        if (!forceRemote && mergeCachedOnlineSongs()) updateMusicListDisplay()
        loadFirebaseSongsAfterRefresh(stopRefreshWhenDone, generation, forceRemote)
        loadLocalSongsAsync(generation)
    }

    /** Quét MediaStore ở luồng IO và chỉ cập nhật kết quả thuộc lần tải mới nhất. */
    private fun loadLocalSongsAsync(generation: Int) {
        loadSongsJob = lifecycleScope.launch {
            val localSongs = withContext(Dispatchers.IO) {
                getAllAudio()
            }
            if (generation != loadGeneration || !::binding.isInitialized || isFinishing || isDestroyed) return@launch
            mergeSongs(localSongs)
            updateMusicListDisplay()
        }
    }

    /** Ghép cache online đúng tài khoản vào danh sách và đồng bộ thư viện người dùng. */
    private fun mergeCachedOnlineSongs(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        if (!cachedOnlineSongsLoaded || cachedOnlineSongsUid != uid) return false
        mergeSongs(cachedOnlineSongs)
        FirebaseLibraryStore.loadOnlineLibrary(MusicListMA) {
            UserLibraryStore.savePlaylists(this@MainActivity)
            updateMusicListDisplay()
        }
        return true
    }

    /** Ghép các bài hát chưa tồn tại vào danh sách chung, tránh trùng id và đường dẫn. */
    private fun mergeSongs(songs: List<Music>) {
        songs.forEach { song ->
            if (MusicListMA.none { it.id == song.id && it.path == song.path }) {
                MusicListMA.add(song)
            }
        }
    }

    /** Cập nhật adapter và tổng số bài hát theo chế độ tìm kiếm hiện tại. */
    private fun updateMusicListDisplay() {
        if (!::musicAdapter.isInitialized) return
        if (search) {
            musicAdapter.updateMusicList(musicListSearch)
        } else {
            musicAdapter.updateMusicList(MusicListMA)
        }
        binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
    }

    /** Đọc metadata các bài hát local từ MediaStore theo thứ tự người dùng chọn. */
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

    /** Hiển thị mini player khi MusicService đang tồn tại. */
    override fun onResume() {
        super.onResume()
        if (PlayerActivity.musicService != null) binding.nowPlaying.visibility = View.VISIBLE
    }

    /** Hủy tác vụ tải và giải phóng service khi Activity đóng mà nhạc không phát. */
    override fun onDestroy() {
        loadSongsJob?.cancel()
        dismissUploadProgress()
        super.onDestroy()
        if (!PlayerActivity.isPlaying && PlayerActivity.musicService != null) {
            exitApplication()
        }
    }

    /** Tạo ô tìm kiếm và lọc danh sách theo tên bài hát. */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_view_menu, menu)
        findViewById<LinearLayout>(R.id.linearLayoutNav)?.setBackgroundResource(ACTIVE_GRADIENT)
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

    // endregion

    // region Tải bài hát lên Firebase

    /** Hiển thị form chọn tên, file nhạc và ảnh trước khi tải lên. */
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

    /** Mở trình chọn file âm thanh của hệ thống. */
    private fun pickAudio() {
        pickAudioLauncher.launch("audio/*")
    }

    /** Mở trình chọn ảnh bìa của hệ thống. */
    private fun pickImage() {
        pickImageLauncher.launch("image/*")
    }

    /** Tải file nhạc và ảnh lên Firebase Storage, sau đó lưu metadata. */
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
        showUploadProgress()
        musicRef.putFile(selectedMusicUri)
            .addOnProgressListener { snapshot ->
                val stageEnd = if (imageUri != null) 85 else 98
                updateUploadProgress(
                    calculateStageProgress(snapshot.bytesTransferred, snapshot.totalByteCount, 0, stageEnd),
                    getString(R.string.uploading_audio)
                )
            }
            .addOnSuccessListener {
                musicRef.downloadUrl.addOnSuccessListener { musicUrl ->
                    val selectedImageUri = imageUri
                    if (selectedImageUri != null) {
                        imageRef.putFile(selectedImageUri)
                            .addOnProgressListener { snapshot ->
                                updateUploadProgress(
                                    calculateStageProgress(snapshot.bytesTransferred, snapshot.totalByteCount, 85, 98),
                                    getString(R.string.uploading_image)
                                )
                            }
                            .addOnSuccessListener {
                                updateUploadProgress(99, getString(R.string.saving_song))
                                imageRef.downloadUrl.addOnSuccessListener { imageUrl ->
                                    saveToDatabase(id, title, musicUrl.toString(), imageUrl.toString(), duration, musicStoragePath, imageStoragePath)
                                }
                            }
                            .addOnFailureListener { showUploadError(it) }
                    } else {
                        updateUploadProgress(99, getString(R.string.saving_song))
                        saveToDatabase(id, title, musicUrl.toString(), "", duration, musicStoragePath, "")
                    }
                }
                    .addOnFailureListener { showUploadError(it) }
            }
            .addOnFailureListener { showUploadError(it) }
    }

    /** Đọc thời lượng file âm thanh được chọn bằng MediaMetadataRetriever. */
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

    /** Lưu metadata bài hát đã tải lên node songs trong Realtime Database. */
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
                cacheOnlineSong(music)
                if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
                binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
                updateUploadProgress(100, getString(R.string.upload_complete))
                dismissUploadProgress()
                setUploadLoading(false)
                Toast.makeText(this, getString(R.string.uploaded), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { showUploadError(it) }
    }

    // endregion

    // region Chuyển đổi dữ liệu Firebase

    /** Chuyển một DataSnapshot Firebase thành model Music an toàn với dữ liệu cũ. */
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

    /** Chuẩn hóa dữ liệu text null/rỗng từ Firebase về giá trị dự phòng. */
    private fun cleanText(value: Any?, fallback: String): String {
        val text = value?.toString().orEmpty()
        return if (text.isBlank() || text == "null") fallback else text
    }

    /** Chuyển duration từ các kiểu dữ liệu Firebase thường gặp sang Long. */
    private fun parseDuration(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Double -> value.toLong()
            is Int -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    /** Kết thúc trạng thái tải và thông báo lỗi upload bằng tiếng Việt. */
    private fun showUploadError(error: Exception) {
        dismissUploadProgress()
        setUploadLoading(false)
        Toast.makeText(this, getString(R.string.upload_failed, error.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
    }

    /** Hiển thị tiến trình tải lên và khóa thao tác đóng giữa chừng. */
    private fun showUploadProgress() {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_upload_progress, binding.root, false)
        uploadProgressBar = content.findViewById(R.id.uploadProgressBar)
        uploadProgressText = content.findViewById(R.id.uploadProgressText)
        uploadProgressStatus = content.findViewById(R.id.uploadProgressStatus)
        uploadProgressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.upload_music_title))
            .setView(content)
            .setCancelable(false)
            .create()
            .also { it.show() }
        updateUploadProgress(0, getString(R.string.uploading_audio))
    }

    /** Cập nhật phần trăm và tên giai đoạn đang thực hiện. */
    private fun updateUploadProgress(progress: Int, status: String) {
        val safeProgress = progress.coerceIn(0, 100)
        uploadProgressBar?.progress = safeProgress
        uploadProgressText?.text = getString(R.string.upload_progress_value, safeProgress)
        uploadProgressStatus?.text = status
    }

    /** Quy đổi tiến trình một tệp vào khoảng phần trăm dành cho giai đoạn đó. */
    private fun calculateStageProgress(transferred: Long, total: Long, start: Int, end: Int): Int {
        if (total <= 0L) return start
        val ratio = transferred.toDouble() / total.toDouble()
        return start + ((end - start) * ratio).toInt()
    }

    /** Đóng dialog và bỏ tham chiếu view sau khi upload kết thúc. */
    private fun dismissUploadProgress() {
        uploadProgressDialog?.dismiss()
        uploadProgressDialog = null
        uploadProgressBar = null
        uploadProgressText = null
        uploadProgressStatus = null
    }

    /** Khóa/mở nút upload để ngăn người dùng gửi nhiều tác vụ cùng lúc. */
    private fun setUploadLoading(loading: Boolean) {
        isUploading = loading
        if (::binding.isInitialized) {
            binding.uploadBtn.isEnabled = !loading
            binding.uploadBtn.alpha = if (loading) 0.55f else 1f
        }
    }

    // endregion

    // region Lọc và quản lý bài hát

    /** Hiển thị lại toàn bộ thư viện nhạc. */
    private fun showAllSongs() {
        search = false
        if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
        binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
        binding.root.closeDrawers()
    }

    /** Chỉ hiển thị các bài online thuộc quyền sở hữu của tài khoản hiện tại. */
    private fun showMySongs() {
        val uid = auth.currentUser?.uid.orEmpty()
        musicListSearch = ArrayList(MusicListMA.filter { it.ownerUid == uid })
        search = true
        if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(musicListSearch)
        binding.totalSongs.text = getString(R.string.my_songs_count, musicListSearch.size)
        binding.root.closeDrawers()
    }

    /** Kiểm tra người dùng hiện tại có quyền sửa/xóa bài hát hay không. */
    fun isCurrentUserOwner(song: Music): Boolean {
        return song.ownerUid.isNotBlank() && song.ownerUid == auth.currentUser?.uid
    }

    /** Hiển thị form sửa tên và ảnh của bài hát thuộc người dùng hiện tại. */
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

    /** Tải ảnh mới nếu có rồi cập nhật metadata bài hát. */
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

    /** Cập nhật metadata trên Firebase và đồng bộ các danh sách runtime. */
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
                cacheOnlineSong(updatedSong)
                Toast.makeText(this, getString(R.string.updated), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { showUploadError(it) }
    }

    /** Kiểm tra quyền sở hữu và yêu cầu xác nhận trước khi xóa bài hát. */
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

    /** Xóa metadata, file Storage và mọi tham chiếu runtime của bài hát. */
    private fun deleteSong(song: Music) {
        database.child(song.id).removeValue()
            .addOnSuccessListener {
                deleteStorageFile(song.musicStoragePath)
                deleteStorageFile(song.imageStoragePath)
                removeSongFromRuntimeLists(song)
                removeCachedOnlineSong(song)
                UserLibraryStore.saveAll(this)
                if (::musicAdapter.isInitialized) musicAdapter.updateMusicList(MusicListMA)
                binding.totalSongs.text = getString(R.string.total_songs_count, MusicListMA.size)
                Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { showUploadError(it) }
    }

    /** Xóa bài hát khỏi danh sách chính, tìm kiếm, yêu thích, hàng chờ và playlist. */
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

    /** Xóa file Firebase Storage khi đường dẫn lưu trữ hợp lệ. */
    private fun deleteStorageFile(storagePath: String) {
        if (storagePath.isNotBlank()) {
            storage.reference.child(storagePath).delete()
        }
    }

    /** Thay model bài hát mới trong mọi danh sách đang giữ bản sao của bài hát. */
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

    /** Thay một bài hát trong danh sách dựa theo id. */
    private fun replaceSong(list: ArrayList<Music>, updatedSong: Music) {
        val index = list.indexOfFirst { it.id == updatedSong.id }
        if (index >= 0) list[index] = updatedSong
    }

    /** Xóa đúng bài hát dựa theo cặp id và đường dẫn. */
    private fun removeSong(list: ArrayList<Music>, song: Music) {
        list.removeAll { it.id == song.id && it.path == song.path }
    }

    /** Thêm hoặc cập nhật bài online trong cache của tài khoản hiện tại. */
    private fun cacheOnlineSong(song: Music) {
        val uid = auth.currentUser?.uid ?: return
        if (!song.isOnlineSong()) return
        if (cachedOnlineSongsUid != uid) {
            cachedOnlineSongsUid = uid
            cachedOnlineSongs = ArrayList()
        }
        cachedOnlineSongsLoaded = true
        val index = cachedOnlineSongs.indexOfFirst { it.id == song.id }
        if (index >= 0) cachedOnlineSongs[index] = song else cachedOnlineSongs.add(song)
    }

    /** Xóa bài hát khỏi cache online của phiên làm việc. */
    private fun removeCachedOnlineSong(song: Music) {
        cachedOnlineSongs.removeAll { it.id == song.id && it.path == song.path }
    }

    /** Tải danh sách songs từ Firebase và ghép với thư viện local hiện có. */
    private fun loadFirebaseSongsAfterRefresh(
        stopRefreshWhenDone: Boolean = true,
        generation: Int = loadGeneration,
        forceRemote: Boolean = false
    ) {
        val uid = auth.currentUser?.uid
        if (!forceRemote && cachedOnlineSongsLoaded && cachedOnlineSongsUid == uid) {
            if (stopRefreshWhenDone) binding.refreshLayout.isRefreshing = false
            return
        }

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (generation != loadGeneration || !::binding.isInitialized || isFinishing || isDestroyed) return
                Log.d("firebase1", "Starting to fetch songs from Firebase...")
                Log.d("firebase2", "DataSnapshot received with ${snapshot.childrenCount} songs")

                val onlineSongs = ArrayList<Music>()
                for (songSnap in snapshot.children) {
                    val music = musicFromSnapshot(songSnap)
                    onlineSongs.add(music)
                }
                cachedOnlineSongsUid = uid
                cachedOnlineSongsLoaded = true
                cachedOnlineSongs = onlineSongs
                mergeSongs(onlineSongs)
                FirebaseLibraryStore.loadOnlineLibrary(MusicListMA) {
                    if (generation != loadGeneration || !::binding.isInitialized || isFinishing || isDestroyed) return@loadOnlineLibrary
                    updateMusicListDisplay()
                    UserLibraryStore.savePlaylists(this@MainActivity)
                    if (stopRefreshWhenDone) binding.refreshLayout.isRefreshing = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (generation != loadGeneration || !::binding.isInitialized || isFinishing || isDestroyed) return
                Toast.makeText(this@MainActivity, getString(R.string.firebase_connection_error, error.message), Toast.LENGTH_LONG).show()
                if (stopRefreshWhenDone) binding.refreshLayout.isRefreshing = false
            }
        })
    }

    // endregion
}
