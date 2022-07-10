package hofjs.hofandroid.helpers

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CalendarHelper(val context: Context) {
    var requestPermissionLauncher: ActivityResultLauncher<String>
    var action: () -> Unit

    init {
        action = { }

        requestPermissionLauncher = (context as ActivityResultCaller)
            .registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    isGranted: Boolean -> if (isGranted) action()
            }
    }

    fun showAccountChooser(title: String, resultAction: (entriesImported: Long?) -> Unit) {
        requestPermission(Manifest.permission.WRITE_CALENDAR) {
            queryCalendars { foundAccounts ->
                if (foundAccounts.isEmpty())
                    resultAction(null) // No account found
                else {
                    val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1,
                        foundAccounts.map { account -> account.second })

                    with(AlertDialog.Builder(context)) {
                        setTitle(title)
                        setAdapter(adapter) { _, which ->
                            resultAction(foundAccounts[which].first) // Selected account
                        }
                        setNegativeButton(android.R.string.cancel) { _, which ->
                            resultAction(-1) // No account selected
                        }
                        show()
                    }
                }
            }
        }
    }

    fun importCalendar(calID: Long, icalData: String, idSuffix: String? = null,
                       resultAction: (entriesImported: Int, entriesDeleted: Int) -> Unit) {
        // Clear events
        val deletedCalendarEvents = clearCalendarEvents(calID, idSuffix)

        // Add events
        val calendarEvents = getCalendarEvents(icalData)
        calendarEvents.forEach {
            addCalendarEvent(calID, it)
        }

        // Return success result
        resultAction(calendarEvents.size, deletedCalendarEvents)
    }

    private fun getCalendarEvents(icalData: String): List<CalendarEvent> {
        val calendarEvents = mutableListOf<CalendarEvent>()

        var calendarEvent = CalendarEvent()
        var calendarReminder = CalendarReminder()
        var alarmMode = false
        for (icalLine in icalData.split("\n")) {
            val icalEntry = icalLine.trim()

            if (icalEntry.startsWith("BEGIN:VEVENT"))
                calendarEvent = CalendarEvent().also { alarmMode = false }
            else if (icalLine.startsWith("END:VEVENT"))
                calendarEvents += calendarEvent
            else if (icalEntry.startsWith("BEGIN:VALARM"))
                calendarReminder = CalendarReminder().also { alarmMode = true }
            else if (icalEntry.startsWith("END:VALARM"))
                calendarEvent.reminders += calendarReminder

            // Process event
            if (!alarmMode) {
                if (icalEntry.startsWith("UID:"))
                    calendarEvent.uid = getCalendarEntryValue(icalEntry)
                else if (icalLine.startsWith("SUMMARY:"))
                    calendarEvent.title = getCalendarEntryValue(icalEntry)
                else if (icalLine.startsWith("DESCRIPTION:"))
                    calendarEvent.description = getCalendarEntryValue(icalEntry)
                else if (icalLine.startsWith("DTSTART;"))
                    calendarEvent.icalStartDate = getCalendarEntryValue(icalEntry)
                else if (icalLine.startsWith("DTEND;"))
                    calendarEvent.icalEndDate = getCalendarEntryValue(icalEntry)
                else if (icalEntry.startsWith("LOCATION:"))
                    calendarEvent.location = getCalendarEntryValue(icalEntry)
                else if (icalLine.startsWith("RDATE:"))
                    calendarEvent.rdate = getCalendarEntryValue(icalEntry)
            }
            else {
                // Process alarm
                if (icalLine.startsWith("DESCRIPTION:"))
                    calendarReminder.description = getCalendarEntryValue(icalEntry)
                else if (icalLine.startsWith("TRIGGER:"))
                    calendarReminder.trigger = getCalendarEntryValue(icalEntry)
            }
        }

        return calendarEvents
    }

    private fun getCalendarEntryValue(icalEntry: String) = icalEntry.substring(icalEntry.indexOf(":") + 1)

    data class CalendarEvent(var uid: String = "", var title: String = "", var description: String = "",
                             var icalStartDate: String = "", var icalEndDate: String = "",
                             var location: String = "", var rdate: String = "",
                             var reminders: List<CalendarReminder> = listOf())

    data class CalendarReminder(var description: String = "", var trigger: String = "")

    fun requestPermission(permission: String, action: () -> Unit) {
        this.action = action

        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
            this.action()
        else
            requestPermissionLauncher.launch(permission)
    }

    fun queryCalendars(accountsFoundCallback: (List<Pair<Long, String>>) -> Unit) {
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? and deleted != ?", arrayOf("com.google", "1"), null)

        val accounts = mutableListOf<Pair<Long, String>>()
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val calID: Long = cursor.getLong(0)
                val displayName: String = cursor.getString(1)

                accounts += calID to displayName
            }

            accountsFoundCallback(accounts)
        }

        cursor?.close()
    }

    fun queryCalendarEvents(calID: Long, idSuffix: String? = null) {
        val cursor = if (idSuffix != null)
            context.contentResolver.query(CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.UID_2445,
                    CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                    CalendarContract.Events.RRULE, CalendarContract.Events.EXDATE),
                "${CalendarContract.Events.CALENDAR_ID} = ? and ${CalendarContract.Events.UID_2445} like ? and deleted != ?", arrayOf(calID.toString(), "%$idSuffix", "1"), null)
        else
            context.contentResolver.query(CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID, CalendarContract.Events.UID_2445,
                    CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                    CalendarContract.Events.RRULE, CalendarContract.Events.EXDATE),
                "${CalendarContract.Events.CALENDAR_ID} = ? and deleted != ?", arrayOf(calID.toString(), "1"), null)

        if (cursor != null)
            while (cursor.moveToNext()) {
                val eventId = cursor.getLong(0)
                val uid = cursor.getString(1)
                val title = cursor.getString(2)
                val description = cursor.getString(3)
                val dtStart = cursor.getString(4)
                val dtEnd = cursor.getString(5)
                val rrule = cursor.getString(6)
                val exdate = cursor.getString(7)

                Log.e("fuchs", "- Event $eventId ($uid): $title - $description ($dtStart -  $dtEnd) [$rrule - $exdate]")
            }

        cursor?.close()
    }

    fun addCalendarEvent(calID: Long, calendarEvent: CalendarEvent) = addCalendarEvent(
        calID, calendarEvent.uid, calendarEvent.title, calendarEvent.description,
        calendarEvent.icalStartDate, calendarEvent.icalEndDate,
        calendarEvent.location, calendarEvent.rdate,
        calendarEvent.reminders)

    fun addCalendarEvent(calID: Long, uid: String, title: String, description: String,
                         icalStartDate: String, icalEndDate: String,
                         location: String, rdate: String,
                         reminders: List<CalendarReminder> = listOf()): Long? {
        val dtStart = convertFromIcalDate(icalStartDate)
        val dtEnd = convertFromIcalDate(icalEndDate)

        // Add event
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calID)
            put(CalendarContract.Events.UID_2445, uid)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, dtStart)
            put(CalendarContract.Events.DTEND, dtEnd)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Berlin")
            put(CalendarContract.Events.RDATE, rdate)
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = uri?.lastPathSegment?.toLong()

        // Add reminders
        if (eventId != null)
            reminders.forEach {
                addCalendarReminder(eventId, it)
            }

        return eventId
    }

    fun addCalendarReminder(eventId: Long, calendarReminder: CalendarReminder) = addCalendarReminder(
        eventId, calendarReminder.trigger)

    fun addCalendarReminder(eventId: Long, trigger: String): Long? {
        val minutes = if (trigger.startsWith("-PT") && trigger.endsWith("M"))
            trigger.substring(3, trigger.length - 1) else return null

        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        val uri = context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)

        return uri?.lastPathSegment?.toLong()
    }

    fun clearCalendarEvents(calID: Long, idSuffix: String? = null): Int {
        // Set the delete flag and dont delete directly, because this leads to unexpected results
        val values = ContentValues().apply {
            put(CalendarContract.Events.DELETED, "1")
        }

        if (idSuffix != null) // Only delete calendar entries with uid suffix
            return context.contentResolver.update(
                CalendarContract.Events.CONTENT_URI,
                values,
                "${CalendarContract.Events.CALENDAR_ID} = ? and ${CalendarContract.Events.UID_2445} like ?",
                arrayOf(calID.toString(), "%$idSuffix")
            )
        else // Delete all calendar entries
            return context.contentResolver.update(
                CalendarContract.Events.CONTENT_URI,
                values,
                "${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(calID.toString())
            )
    }

    fun deleteCalendarEvent(eventId: Long): Int {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DELETED, "1")
        }

        return context.contentResolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,
                eventId), values, null, null
        )
    }

    private fun convertFromIcalDate(icalDate: String): Long {
        return ZonedDateTime.parse(icalDate, DateTimeFormatter.ofPattern( "uuuuMMdd'T'HHmmss'Z'" )
            .withZone(ZoneId.systemDefault())).toInstant().toEpochMilli()
    }

    private fun convertToIcalDate(instant: Instant): String {
        return instant.toString()
            .replace("-", "")
            .replace(":", "")
            .replace(".", "")
            .substring(0, 15) + "Z"
    }
}