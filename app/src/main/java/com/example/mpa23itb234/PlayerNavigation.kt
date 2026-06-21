package com.example.mpa23itb234

/**
 * Tập trung các khóa Intent và nguồn mở màn hình phát nhạc.
 *
 * Việc dùng hằng số giúp tránh sai chính tả khi truyền dữ liệu giữa Activity,
 * Fragment và Adapter.
 */
object PlayerNavigation {
    const val EXTRA_INDEX = "index"
    const val EXTRA_SOURCE = "class"

    const val SOURCE_NOW_PLAYING = "NowPlaying"
    const val SOURCE_MUSIC_ADAPTER = "MusicAdapter"
    const val SOURCE_MUSIC_SEARCH = "MusicAdapterSearch"
    const val SOURCE_MAIN_SHUFFLE = "MainActivity"
    const val SOURCE_FAVOURITE = "FavouriteAdapter"
    const val SOURCE_FAVOURITE_SHUFFLE = "FavouriteShuffle"
    const val SOURCE_PLAYLIST = "PlaylistDetailsAdapter"
    const val SOURCE_PLAYLIST_SHUFFLE = "PlaylistDetailsShuffle"
    const val SOURCE_PLAY_NEXT = "PlayNext"
}
