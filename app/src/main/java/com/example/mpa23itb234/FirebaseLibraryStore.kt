package com.example.mpa23itb234

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirebaseLibraryStore {
    private var onlineLibraryLoaded = false
    private var loadedLibraryUid: String? = null

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private fun libraryRef() = FirebaseDatabase.getInstance()
        .getReference("userLibraries")
        .child(auth.currentUser?.uid ?: "guest")

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

    fun saveOnlineLibrary() {
        val uid = auth.currentUser?.uid ?: return
        if (!onlineLibraryLoaded || loadedLibraryUid != uid) return
        saveOnlineFavourites()
        saveOnlinePlaylists()
    }

    fun resetSession() {
        onlineLibraryLoaded = false
        loadedLibraryUid = null
    }

    fun saveFavourite(song: Music, isFavourite: Boolean) {
        if (auth.currentUser == null || !song.isOnlineSong()) return
        val ref = libraryRef().child("favorites").child("online").child(song.id)
        if (isFavourite) ref.setValue(true) else ref.removeValue()
    }

    private fun saveOnlineFavourites() {
        val onlineFavourites = FavouriteActivity.favouriteSongs
            .filter { it.isOnlineSong() }
            .associate { it.id to true }
        libraryRef().child("favorites").child("online")
            .setValue(onlineFavourites.ifEmpty { null })
    }

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

    private fun mergeOnlineFavourites(snapshot: DataSnapshot, allSongs: List<Music>) {
        val songsById = allSongs.associateBy { it.id }
        snapshot.children.forEach { child ->
            val song = songsById[child.key] ?: return@forEach
            if (FavouriteActivity.favouriteSongs.none { it.id == song.id }) {
                FavouriteActivity.favouriteSongs.add(song)
            }
        }
    }

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
