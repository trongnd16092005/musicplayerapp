package com.example.mpa23itb234

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

/**
 * Lưu phần thư viện cục bộ của từng tài khoản bằng SharedPreferences.
 *
 * Yêu thích/playlist chỉ chứa bài local; phần online được giao cho
 * [FirebaseLibraryStore] để có thể đồng bộ giữa các thiết bị.
 */
object UserLibraryStore {
    private const val PREF_NAME = "USER_LIBRARY"

    private val gson = GsonBuilder().create()

    /** Nạp yêu thích và playlist local của uid hiện tại vào bộ nhớ runtime. */
    fun load(context: Context) {
        FavouriteActivity.favouriteSongs = loadFavourites(context)
        PlaylistActivity.musicPlaylist = loadPlaylists(context)
        ensurePlaylistIds()
    }

    /** Xóa dữ liệu runtime khi đăng xuất nhưng không xóa dữ liệu đã lưu. */
    fun clearRuntime() {
        FavouriteActivity.favouriteSongs = ArrayList()
        PlaylistActivity.musicPlaylist = MusicPlaylist()
        PlayNext.playNextList = ArrayList()
    }

    /** Lưu các bài yêu thích local theo khóa gắn với uid. */
    fun saveFavourites(context: Context) {
        val localFavourites = FavouriteActivity.favouriteSongs
            .filter { !it.isOnlineSong() }
        val json = gson.toJson(localFavourites)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(favouritesKey(), json)
            .apply()
    }

    /** Lưu cấu trúc playlist và phần bài hát local trên thiết bị. */
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

    /** Lưu đồng thời thư viện local và yêu cầu Firebase lưu phần online. */
    fun saveAll(context: Context) {
        saveFavourites(context)
        savePlaylists(context)
        FirebaseLibraryStore.saveOnlineLibrary()
    }

    /** Giải mã JSON danh sách yêu thích; trả danh sách rỗng nếu dữ liệu lỗi. */
    private fun loadFavourites(context: Context): ArrayList<Music> {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(favouritesKey(), null)
            ?: return ArrayList()
        val type = object : TypeToken<ArrayList<Music>>() {}.type
        return runCatching {
            gson.fromJson<ArrayList<Music>>(json, type) ?: ArrayList()
        }.getOrDefault(ArrayList())
    }

    /** Giải mã JSON playlist; trả model rỗng nếu chưa có dữ liệu. */
    private fun loadPlaylists(context: Context): MusicPlaylist {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = playlistsKey()
        val json = prefs.getString(key, null)
            ?: return MusicPlaylist()
        return runCatching {
            gson.fromJson(json, MusicPlaylist::class.java) ?: MusicPlaylist()
        }.getOrDefault(MusicPlaylist())
    }

    /** Bổ sung id/danh sách rỗng cho dữ liệu playlist được lưu từ phiên bản cũ. */
    private fun ensurePlaylistIds() {
        PlaylistActivity.musicPlaylist.ref.forEach { playlist ->
            if (playlist.id.isBlank()) playlist.id = playlistIdFromName(playlist.name)
            runCatching { playlist.playlist }.getOrElse {
                playlist.playlist = ArrayList()
            }
        }
    }

    /** Tạo id playlist dễ đọc từ tên và timestamp để tránh trùng. */
    fun playlistIdFromName(name: String): String {
        val cleanName = name.trim().ifBlank { "playlist" }
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return "${cleanName}_${System.currentTimeMillis()}"
    }

    /** Tạo khóa SharedPreferences cho danh sách yêu thích của uid hiện tại. */
    private fun favouritesKey(): String = "favourites_${currentUid()}"

    /** Tạo khóa SharedPreferences cho playlist của uid hiện tại. */
    private fun playlistsKey(): String = "playlists_${currentUid()}"

    /** Trả uid hiện tại; dùng guest khi chưa có phiên Firebase Auth. */
    private fun currentUid(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
    }
}
