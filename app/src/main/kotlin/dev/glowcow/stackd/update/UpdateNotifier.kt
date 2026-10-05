package dev.glowcow.stackd.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.glowcow.stackd.MainActivity
import dev.glowcow.stackd.R

/** One notification per pass with changes worth reporting; a tap opens the card. */
object UpdateNotifier {
    private const val CHANNEL = "pass_updates"
    private const val MAX_LINES = 4

    /** [all] adds the changes the issuer did not ask to announce. */
    fun notify(context: Context, changes: List<PassChange>, all: Boolean) {
        if (changes.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        for (change in changes) {
            val lines = if (all) change.announced + change.other else change.announced
            if (lines.isEmpty()) continue
            val id = change.card.id.hashCode()
            val text = lines.take(MAX_LINES).joinToString("\n")
            val open = PendingIntent.getActivity(
                context,
                id,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_CARD_ID, change.card.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                id,
                Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(change.card.bgColor)
                    .setContentTitle(change.card.name)
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
