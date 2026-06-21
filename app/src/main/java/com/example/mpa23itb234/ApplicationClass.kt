package com.example.mpa23itb234

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/** Khởi tạo tài nguyên dùng chung ngay khi tiến trình ứng dụng được tạo. */
class ApplicationClass:Application() {
    companion object{
        const val CHANNEL_ID = "MusicNotification"
        const val PLAY = "play"
        const val NEXT = "next"
        const val PREVIOUS = "previous"
        const val EXIT = "exit"
    }
    /** Tạo notification channel dành cho foreground music service. */
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val notificationChannel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH)
            notificationChannel.description = getString(R.string.notification_channel_description)

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}
