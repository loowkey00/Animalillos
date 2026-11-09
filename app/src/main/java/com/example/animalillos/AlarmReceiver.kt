package com.example.animalillos

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PET_NAME = "extra_pet_name"
        const val EXTRA_DESCRIPTION = "extra_description"
        private const val CHANNEL_ID = "pet_reminder_channel"
        private const val CHANNEL_NAME = "Recordatorios de Mascotas"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val petName = intent.getStringExtra(EXTRA_PET_NAME) ?: "Mascota"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: "Es hora de alimentar a tu mascota"

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_popup_reminder)
            .setContentTitle("Recordatorio: $petName")
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
