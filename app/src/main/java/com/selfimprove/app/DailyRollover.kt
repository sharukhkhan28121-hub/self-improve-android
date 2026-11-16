package com.selfimprove.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

private val prayerNames = listOf("Fajr", "Dhuhr", "Asr (Hanafi)", "Maghrib", "Isha")
private val habitNames = listOf("Exercise", "Study", "Sleep on time", "Daily journal")

object DailyRollover {
    private const val CHANNEL_ID = "daily_progress"
    internal const val ACTION = "com.selfimprove.app.MIDNIGHT_RESET"

    @Synchronized
    fun rollover(context: Context, newDay: String = LocalDate.now().toString()): String? {
        val prefs = context.getSharedPreferences("self_improve", Context.MODE_PRIVATE)
        val oldDay = prefs.getString("day", null)
        if (oldDay == null) {
            prefs.edit().putString("day", newDay).apply()
            return null
        }
        if (oldDay == newDay) return null

        val prayerFlags = readFlags(prefs.getString("prayers", null), prayerNames.size)
        val habitFlags = readFlags(prefs.getString("habits", null), habitNames.size)
        val oldXp = prefs.getInt("xp", 0)
        val missed = prayerFlags.count { !it }
        val newXp = (oldXp - missed * 20).coerceAtLeast(0)
        val journal = prefs.getString("journal_$oldDay", "") ?: ""

        val record = JSONObject().apply {
            put("date", oldDay)
            put("prayers", JSONArray(prayerFlags))
            put("habits", JSONArray(habitFlags))
            put("journal", journal)
            put("xpBeforeRollover", oldXp)
            put("xpAfterRollover", newXp)
            put("missedPrayers", missed)
        }

        prefs.edit()
            .putString("history_$oldDay", record.toString())
            .putInt("xp", newXp)
            .putString("day", newDay)
            .putString("prayers", JSONArray().apply { repeat(prayerNames.size) { put(false) } }.toString())
            .putString("habits", JSONArray().apply { repeat(habitNames.size) { put(false) } }.toString())
            .putString("journal_$newDay", "")
            .apply()

        val completedPrayers = prayerNames.filterIndexed { i, _ -> prayerFlags[i] }
        val completedHabits = habitNames.filterIndexed { i, _ -> habitFlags[i] }
        val prayerText = "Namaz \${completedPrayers.size}/5" +
            if (completedPrayers.isEmpty()) "" else ": \${completedPrayers.joinToString(", ")}"
        val habitText = if (completedHabits.isEmpty()) "No habits marked complete" else
            "Completed: \${completedHabits.joinToString(", ")}"
        return "\$prayerText • \$habitText • XP: \$newXp"
    }

    fun scheduleNextMidnight(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyResetReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(
            context, 701, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnight, pending)
    }

    fun showSummaryNotification(context: Context, summary: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily progress", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("SELF IMPROVE • Daily summary")
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()
        manager.notify(702, notification)
    }

    private fun readFlags(raw: String?, count: Int): List<Boolean> {
        if (raw == null) return List(count) { false }
        return try {
            val array = JSONArray(raw)
            List(count) { i -> array.optBoolean(i, false) }
        } catch (_: Exception) {
            List(count) { false }
        }
    }
}

class DailyResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DailyRollover.ACTION) return
        val summary = DailyRollover.rollover(context)
        if (summary != null) DailyRollover.showSummaryNotification(context, summary)
        DailyRollover.scheduleNextMidnight(context)
    }
}

class RescheduleAlarmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val summary = DailyRollover.rollover(context)
        if (summary != null) DailyRollover.showSummaryNotification(context, summary)
        DailyRollover.scheduleNextMidnight(context)
    }
}
