package hofjs.hofandroid.helpers

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.HtmlCompat

class NotificationHelper(val context: Context) {
    fun makeIntentNotification(title: String, message: String, intent: Intent,
                                 smallIcon: Int = android.R.mipmap.sym_def_app_icon,
                                 largeIcon: Bitmap? = ContextCompat.getDrawable(context, smallIcon)?.toBitmap(),
                                 category: String = context.applicationInfo.loadLabel(context.packageManager).toString(),
                                 id: Int = (System.nanoTime() % Integer.MAX_VALUE).toInt()) =
        makeNotification(title, message, smallIcon, largeIcon, category, id, PendingIntent.getActivity(
            context, 0, intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

    fun makeUrlNotification(title: String, message: String, url: String,
                            smallIcon: Int = android.R.mipmap.sym_def_app_icon,
                            largeIcon: Bitmap? = ContextCompat.getDrawable(context, smallIcon)?.toBitmap(),
                            category: String = context.applicationInfo.loadLabel(context.packageManager).toString(),
                            id: Int = (System.nanoTime() % Integer.MAX_VALUE).toInt()) =
        makeNotification(title, message, smallIcon, largeIcon, category, id, PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

    fun makeNotification(title: String, message: String,
                         smallIcon: Int = android.R.mipmap.sym_def_app_icon,
                         largeIcon: Bitmap? = ContextCompat.getDrawable(context, smallIcon)?.toBitmap(),
                         category: String = context.applicationInfo.loadLabel(context.packageManager).toString(),
                         id: Int = (System.nanoTime() % Integer.MAX_VALUE).toInt(),
                         contentIntent: PendingIntent) {
        // Make a channel if necessary (updates it, if already existing)
        createNotificationChannel(category)

        // Create notification
        val notification = createNotification(title, message, contentIntent, smallIcon, largeIcon, category)

        // Show the notification
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun createNotification(title: String, message: String, contentIntent: PendingIntent,
                                   smallIcon: Int, largeIcon: Bitmap?, category: String): Notification {
        // Create formatted representation of html text
        val text = HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY)

        // Create the notification
        return NotificationCompat.Builder(context, category)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(LongArray(0))
            .build()
    }

    private fun createNotificationChannel(category: String) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(category, category, importance)

        // Add the channel
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

        notificationManager?.createNotificationChannel(channel)
    }
}