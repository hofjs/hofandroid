package hofjs.hofandroid.workers

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import hofjs.hofandroid.helpers.NotificationHelper
import hofjs.hofandroid.helpers.RequestHelper

abstract class PullNotificationWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val requestHelper = RequestHelper(ctx)
    private val notificationHelper = NotificationHelper(ctx)

    // Save original context because applicationContext can differ from original context
    // in case of app restart and as a consequence shared prefs would not work
    private val context: Context

    init {
        this.context = ctx
    }

    companion object {
        const val NOTIFICATION_CATEGORY_PARAMETER_KEY = "category"
        const val NOTIFICATION_SMALLICON_PARAMETER_KEY = "smallIcon"
        const val NOTIFICATION_LARGEICON_PARAMETER_KEY = "largeIcon"
    }

    override fun doWork(): Result {
        return try {
            val entries = getUpdatedEntries() ?: return Result.failure()
            makeNotifications(entries)
            Result.success()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            Result.failure()
        }
    }

    open fun getUpdatedEntries(params: Data? = null,
                               currentContent: Set<String>? = makeRequests(params ?: inputData),
                               oldEntries: Set<NotificationEntry> = loadEntries()): Set<NotificationEntry>? {
        val currentEntries = parseContents(currentContent ?: return null, params ?: inputData)
        val updatedEntries = currentEntries.subtract(oldEntries)

        // Save current data if there are changes
        if (updatedEntries.isNotEmpty())
            saveEntries(currentEntries)

        // Return updated entries
        return updatedEntries
    }

    abstract fun makeRequests(params: Data): Set<String>?
    abstract fun parseContent(content: String, params: Data): Set<NotificationEntry>

    protected fun loadUrl(url: String, credentials: String? = null)
            = requestHelper.loadUrl(url, credentials)

    protected fun parseContents(contents: Set<String>, params: Data): Set<NotificationEntry> {
        val entries = mutableSetOf<NotificationEntry>()
        contents.forEach {
            entries += parseContent(it, params)
        }

        return entries
    }

    protected fun loadEntries(): Set<NotificationEntry> {
        val sharedPrefs = context.getSharedPreferences(
            this::class.java.name, Context.MODE_PRIVATE)

        val entries = mutableSetOf<NotificationEntry>()
        with(sharedPrefs) {
            var index = -1
            while (getString("TITLE_${++index}", null) != null) {
                entries.add(
                    NotificationEntry(
                        getString("TITLE_$index", null) ?: "-",
                        getString("TEXT_$index", null) ?: "-",
                        getString("DATA_$index", null) ?: ""
                    )
                )
            }
        }

        return entries
    }

    protected fun saveEntries(entries: Set<NotificationEntry>) {
        val sharedPrefs = context.getSharedPreferences(
            this::class.java.name, Context.MODE_PRIVATE)

        with (sharedPrefs.edit()) {
            clear()

            entries.forEachIndexed { index: Int, entry: NotificationEntry ->
                putString("TITLE_$index", entry.title)
                putString("TEXT_$index", entry.text)
                putString("DATA_$index", entry.data)
            }

            apply()
        }
    }

    protected fun makeNotifications(entries: Set<NotificationEntry>) {
        val category = inputData.getString(NOTIFICATION_CATEGORY_PARAMETER_KEY) ?:
        context.applicationInfo.loadLabel(context.packageManager).toString()
        val smallIcon = inputData.getInt(NOTIFICATION_SMALLICON_PARAMETER_KEY, android.R.mipmap.sym_def_app_icon)
        val largeIcon = inputData.getInt(NOTIFICATION_LARGEICON_PARAMETER_KEY, smallIcon)

        // Restrict notifications to last 3
        entries.reversed().takeLast(3).forEach { entry ->
            makeNotification(entry, category, smallIcon, largeIcon)
        }
    }

    protected fun makeNotification(entry: NotificationEntry, category: String, smallIcon: Int, largeIcon: Int) {
        if (entry.data.startsWith("http:") or entry.data.startsWith("https:"))
            notificationHelper.makeUrlNotification(entry.title, entry.text, entry.data,
                smallIcon, ContextCompat.getDrawable(context, largeIcon)?.toBitmap(), category)
        else if (entry.data.startsWith("intent:")) {
            val intent = Intent()

            var urlTokenIndex = entry.data.indexOf(",")
            var urlPart: String? = if (urlTokenIndex >= 0) entry.data.substring(urlTokenIndex+1) else null

            intent.setClassName(context, entry.data.substring(7,
                if (urlTokenIndex >= 0) urlTokenIndex else entry.data.length))
            urlPart?.let { intent.putExtra("url", urlPart) }

            notificationHelper.makeIntentNotification(entry.title, entry.text, intent, smallIcon,
                ContextCompat.getDrawable(context, largeIcon)?.toBitmap(), category)
        }
    }

    data class NotificationEntry(val title: String, val text: String, val data: String)
}