package com.example.mpa23itb234

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver:BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action){
            //only play next or prev song, when music list contains more than one song
            ApplicationClass.PREVIOUS -> if((currentPlayerListOrNull()?.size ?: 0) > 1) PlayerActivity.musicService?.previous()
            ApplicationClass.PLAY -> if(PlayerActivity.isPlaying) PlayerActivity.musicService?.pause() else PlayerActivity.musicService?.play()
            ApplicationClass.NEXT -> if((currentPlayerListOrNull()?.size ?: 0) > 1) PlayerActivity.musicService?.next()
            ApplicationClass.EXIT ->{
                exitApplication()
            }
        }
    }
}
