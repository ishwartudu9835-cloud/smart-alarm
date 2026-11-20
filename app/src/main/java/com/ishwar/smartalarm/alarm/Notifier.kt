package com.ishwar.smartalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ishwar.smartalarm.R
import com.ishwar.smartalarm.data.Occurrence
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.ui.MainActivity

object Notifier {
    const val CH_ALARM = "alarm_v1"
    const val CH_MISSED = "missed_v1"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_ALARM) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALARM, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Schedule / reminder alarm"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        if (nm.getNotificationChannel(CH_MISSED) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_MISSED, "Missed tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Miss hue tasks ki jaankari"
                }
            )
        }
    }

    fun missed(ctx: Context, o: Occurrence) {
        createChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 5, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_MISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = tr(
            "Your \"${o.title}\" task was not completed. It has been marked as missed.",
            "आपका \"${o.title}\" वाला Task पूरा नहीं हुआ है। यह Task Miss हो गया है।"
        )
        val n = NotificationCompat.Builder(ctx, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(tr("Missed Task Alert", "Missed Task Alert"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify((100000 + o.id % 100000).toInt(), n)
        } catch (e: SecurityException) {
            // notification permission nahi hai
        }
    }
}
