package com.example.mpa23itb234

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Quản lý phần thư viện người dùng được lưu online trên Firebase.
 *
 * Chỉ id của bài hát online được đồng bộ; bài local vẫn do [UserLibraryStore]
 * lưu riêng trên thiết bị.
 */
object FirebaseLibraryStore {
    private var onlineLibraryLoaded = false
    private var loadedLibraryUid: String? = null

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    /** Tạo tham chiếu userLibraries/{uid} của tài khoản hiện tại. */
    private fun libraryRef() = FirebaseDatabase.getInstance()
        .getReference("userLibraries")
        .child(auth.currentUser?.uid ?: "guest")

    /** Tải yêu thích và playlist online rồi ánh xạ id về model trong [allSongs]. */
    fun loadOnlineLibrary(allSongs: List<Music>, onLoaded: () -> Unit = {}) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            resetSession()
            onLoaded()
            return
        }
        if (loadedLibraryUid != uid) {
            onlineLibraryLoaded = false
            loadedLibraryUid = uid
        }

        libraryRef().addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                mergeOnlineFavourites(snapshot.child("favorites").child("online"), allSongs)
                mergeOnlinePlaylists(snapshot.child("playlists"), allSongs)
                onlineLibraryLoaded = true
                loadedLibraryUid = uid
                onLoaded()
            }

            override fun onCancelled(error: DatabaseError) {
                onlineLibraryLoaded = true
                loadedLibraryUid = uid
                onLoaded()
            }
        })
    }

    /** Lưu toàn bộ phần online khi dữ liệu đúng tài khoản đã được tải xong. */
    fun saveOnlineLibrary() {
        val uid = auth.currentUser?.uid ?: return
        if (!onlineLibraryLoaded || loadedLibraryUid != uid) return
        saveOnlineFavourites()
        saveOnlinePlaylists()
    }

    /** Xóa cờ phiên để tài khoản mới không dùng dữ liệu của tài khoản trước. */
    fun resetSession() {
        onlineLibraryLoaded = false
        loadedLibraryUid = null
    }

    /** Thêm hoặc xóa một bài online khỏi danh sách yêu thích trên Firebase. */
    fun saveFavourite(song: Music, isFavourite: Boolean) {
        if (auth.currentUser == null || !song.isOnlineSong()) return
        val ref = libraryRef().child("favorites").child("online").child(song.id)
        if (isFavourite) ref.setValue(true) else ref.removeValue()
    }

    /** Ghi tập id bài online yêu thích tại favorites/online. */
    private fun saveOnlineFavourites() {
        val onlineFavourites = FavouriteActivity.favouriteSongs
            .filter { it.isOnlineSong() }
            .associate { it.id to true }
        libraryRef().child("favorites").child("online")
            .setValue(onlineFavourites.ifEmpty { null })
    }

    /** Ghi metadata playlist và tập id bài online tương ứng. */
    private fun saveOnlinePlaylists() {
        val onlinePlaylists = PlaylistActivity.musicPlaylist.ref.mapNotNull { playlist ->
            val playlistSongs = runCatching { playlist.playlist }.getOrNull() ?: return@mapNotNull null
            val onlineSongIds = playlistSongs
                .filter { it.isOnlineSong() }
                .associate { it.id to true }

            if (onlineSongIds.isEmpty()) {
                null
            } else {
                playlist.id to mapOf(
                    "id" to playlist.id,
                    "name" to playlist.name,
                    "createdBy" to playlist.createdBy,
                    "createdOn" to playlist.createdOn,
                    "songs" to onlineSongIds
                )
            }
        }.toMap()
        libraryRef().child("playlists").setValue(onlinePlaylists.ifEmpty { null })
    }

    /** Ghép các id yêu thích Firebase với danh sách Music đã tải. */
    private fun mergeOnlineFavourites(snapshot: DataSnapshot, allSongs: List<Music>) {
        val songsById = allSongs.associateBy { it.id }
        snapshot.children.forEach { child ->
            val song = songsById[child.key] ?: return@forEach
            if (FavouriteActivity.favouriteSongs.none { it.id == song.id }) {
                FavouriteActivity.favouriteSongs.add(song)
            }
        }
    }

    /** Khôi phục playlist online và tránh thêm trùng bài hát. */
    private fun mergeOnlinePlaylists(snapshot: DataSnapshot, allSongs: List<Music>) {
        val songsById = allSongs.associateBy { it.id }
        snapshot.children.forEach playlistLoop@{ playlistSnap ->
            val id = playlistSnap.child("id").getValue(String::class.java)
                ?: playlistSnap.key
                ?: return@playlistLoop
            val playlist = PlaylistActivity.musicPlaylist.ref.find { it.id == id }
                ?: Playlist().apply {
                    this.id = id
                    name = playlistSnap.child("name").getValue(String::class.java) ?: "Playlist"
                    createdBy = playlistSnap.child("createdBy").getValue(String::class.java) ?: ""
                    createdOn = playlistSnap.child("createdOn").getValue(String::class.java) ?: ""
                    playlist = ArrayList()
                    PlaylistActivity.musicPlaylist.ref.add(this)
                }

            playlistSnap.child("songs").children.forEach songLoop@{ songSnap ->
                val song = songsById[songSnap.key] ?: return@songLoop
                if (playlist.playlist.none { it.id == song.id }) {
                    playlist.playlist.add(song)
                }
            }
        }
    }
}
