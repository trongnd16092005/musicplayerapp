package com.example.mpa23itb234

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Nhận thao tác điều khiển nhạc từ notification media. */
class NotificationReceiver:BroadcastReceiver() {
    /** Chuyển action notification thành lệnh tương ứng cho MusicService. */
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action){
            // Chỉ chuyển bài khi hàng phát có nhiều hơn một bài hát.
            ApplicationClass.PREVIOUS -> if((currentPlayerListOrNull()?.size ?: 0) > 1) PlayerActivity.musicService?.previous()
            ApplicationClass.PLAY -> if(PlayerActivity.isPlaying) PlayerActivity.musicService?.pause() else PlayerActivity.musicService?.play()
            ApplicationClass.NEXT -> if((currentPlayerListOrNull()?.size ?: 0) > 1) PlayerActivity.musicService?.next()
            ApplicationClass.EXIT ->{
                exitApplication()
            }
        }
    }
}
