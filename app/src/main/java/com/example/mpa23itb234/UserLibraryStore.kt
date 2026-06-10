package com.example.mpa23itb234

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object UserLibraryStore {
    private const val PREF_NAME = "USER_LIBRARY"

    private val gson = GsonBuilder().create()

    fun load(context: Context) {
        FavouriteActivity.favouriteSongs = loadFavourites(context)
        PlaylistActivity.musicPlaylist = loadPlaylists(context)
        ensurePlaylistIds()
    }

    fun clearRuntime() {
        FavouriteActivity.favouriteSongs = ArrayList()
        PlaylistActivity.musicPlaylist = MusicPlaylist()
        PlayNext.playNextList = ArrayList()
    }

    fun saveFavourites(context: Context) {
        val localFavourites = FavouriteActivity.favouriteSongs
            .filter { !it.isOnlineSong() }
        val json = gson.toJson(localFavourites)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(favouritesKey(), json)
            .apply()
    }

    fun savePlaylists(context: Context) {
        ensurePlaylistIds()
        val localOnly = MusicPlaylist().apply {
            ref = ArrayList(PlaylistActivity.musicPlaylist.ref.map { playlist ->
                Playlist().apply {
                    id = playlist.id
                    name = playlist.name
                    createdBy = playlist.createdBy
                    createdOn = playlist.createdOn
                    this.playlist = ArrayList(playlist.playlist.filter { !it.isOnlineSong() })
                }
            })
        }
        val json = gson.toJson(localOnly)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(playlistsKey(), json)
            .apply()
    }

    fun saveAll(context: Context) {
        saveFavourites(context)
        savePlaylists(context)
        FirebaseLibraryStore.saveOnlineLibrary()
    }

    private fun loadFavourites(context: Context): ArrayList<Music> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(favouritesKey(), null)
            ?: return ArrayList()
        val type = object : TypeToken<ArrayList<Music>>() {}.type
        return runCatching {
            gson.fromJson<ArrayList<Music>>(json, type) ?: ArrayList()
        }.getOrDefault(ArrayList())
    }

    private fun loadPlaylists(context: Context): MusicPlaylist {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = playlistsKey()
        val json = prefs.getString(key, null)
            ?: return MusicPlaylist()
        return runCatching {
            gson.fromJson(json, MusicPlaylist::class.java) ?: MusicPlaylist()
        }.getOrDefault(MusicPlaylist())
    }

    private fun ensurePlaylistIds() {
        PlaylistActivity.musicPlaylist.ref.forEach { playlist ->
            if (playlist.id.isBlank()) playlist.id = playlistIdFromName(playlist.name)
            runCatching { playlist.playlist }.getOrElse {
                playlist.playlist = ArrayList()
            }
        }
    }

    fun playlistIdFromName(name: String): String {
        val cleanName = name.trim().ifBlank { "playlist" }
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return "${cleanName}_${System.currentTimeMillis()}"
    }

    private fun favouritesKey(): String = "favourites_${currentUid()}"

    private fun playlistsKey(): String = "playlists_${currentUid()}"

    private fun currentUid(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
    }
}
