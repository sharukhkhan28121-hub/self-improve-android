package com.selfimprove.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Bg = Color(0xFF050907)
private val SurfaceDark = Color(0xFF101914)
private val SurfaceSoft = Color(0xFF17231B)
private val Neon = Color(0xFF39F51D)
private val NeonSoft = Color(0xFF9BFF73)
private val TextMain = Color(0xFFF5FFF2)
private val TextMuted = Color(0xFF9BA99C)
private val prayerList = listOf("Fajr", "Dhuhr", "Asr (Hanafi)", "Maghrib", "Isha")
private val habitList = listOf("Exercise", "Study", "Sleep on time", "Daily journal")
private const val NOTICE_CHANNEL = "self_improve_updates"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val summary = DailyRollover.rollover(this)
        if (summary != null) DailyRollover.showSummaryNotification(this, summary)
        DailyRollover.scheduleNextMidnight(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
        }
        setContent { SelfImproveApp(this) }
    }
}

@Composable
private fun SelfImproveApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("self_improve", Context.MODE_PRIVATE) }
    val view = LocalView.current
    val today = LocalDate.now().toString()
    val storedDay = remember { prefs.getString("day", null) }
    val firstLaunch = storedDay == null
    val dayChanged = storedDay != null && storedDay != today
    val oldPrayers = remember { readFlags(prefs, "prayers", prayerList.size) }
    val openingPenalty = if (dayChanged) oldPrayers.count { !it } * 20 else 0
    var xp by remember { mutableIntStateOf((prefs.getInt("xp", 0) - openingPenalty).coerceAtLeast(0)) }
    var prayerDone by remember { mutableStateOf(if (dayChanged || firstLaunch) List(prayerList.size) { false } else oldPrayers) }
    var habitDone by remember { mutableStateOf(if (dayChanged || firstLaunch) List(habitList.size) { false } else readFlags(prefs, "habits", habitList.size)) }
    var journal by remember { mutableStateOf(if (dayChanged || firstLaunch) "" else prefs.getString("journal_$today", "") ?: "") }
    var journalDraft by remember { mutableStateOf(journal) }
    var showJournal by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var mood by remember { mutableStateOf(prefs.getString("mood_$today", "") ?: "") }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("sound_on", true)) }
    var notificationsOn by remember { mutableStateOf(prefs.getBoolean("notifications_on", true)) }
    var focusSeconds by remember { mutableIntStateOf(25 * 60) }
    var focusRunning by remember { mutableStateOf(false) }

    fun saveXp(value: Int) {
        xp = value.coerceAtLeast(0)
        prefs.edit().putInt("xp", xp).apply()
    }
    fun saveFlags(key: String, values: List<Boolean>) {
        prefs.edit().putString(key, JSONArray().apply { values.forEach { put(it) } }.toString()).apply()
    }
    fun feedback(title: String, detail: String) {
        if (soundOn) {
            try {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 130)
                Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 250)
            } catch (_: Exception) { }
        }
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        if (notificationsOn) postNotice(context, title, detail)
    }
    fun setPrayer(index: Int, checked: Boolean) {
        if (prayerDone[index] == checked) return
        val updated = prayerDone.toMutableList().also { it[index] = checked }
        prayerDone = updated
        saveFlags("prayers", updated)
        saveXp(xp + if (checked) 20 else -20)
        feedback(if (checked) "Namaz completed" else "Namaz updated", prayerList[index] + if (checked) " • +20 XP" else " • -20 XP")
    }
    fun setHabit(index: Int, checked: Boolean) {
        if (habitDone[index] == checked) return
        val updated = habitDone.toMutableList().also { it[index] = checked }
        habitDone = updated
        saveFlags("habits", updated)
        saveXp(xp + if (checked) 10 else -10)
        feedback(if (checked) "Habit completed" else "Habit updated", habitList[index] + if (checked) " • +10 XP" else "Progress adjusted")
    }
    fun resetDay(newDay: String) {
        val summary = DailyRollover.rollover(context, newDay)
        if (summary != null && notificationsOn) DailyRollover.showSummaryNotification(context, summary)
        DailyRollover.scheduleNextMidnight(context)
        xp = prefs.getInt("xp", xp)
        prayerDone = List(prayerList.size) { false }
        habitDone = List(habitList.size) { false }
        journal = ""
        journalDraft = ""
        mood = ""
        focusSeconds = 25 * 60
    }

    LaunchedEffect(Unit) {
        if (dayChanged) resetDay(today)
        else if (firstLaunch) {
            prefs.edit().putString("day", today).apply()
            saveFlags("prayers", prayerDone)
            saveFlags("habits", habitDone)
        }
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
            val currentDay = now.toLocalDate().toString()
            if (prefs.getString("day", currentDay) != currentDay) resetDay(currentDay)
        }
    }
    LaunchedEffect(focusRunning) {
        while (focusRunning && focusSeconds > 0) {
            delay(1000)
            focusSeconds--
        }
        if (focusSeconds == 0) {
            focusRunning = false
            feedback("Focus session complete", "Great work. Take a short break.")
        }
    }

    val (level, xpInLevel) = calculateLevel(xp)
    val progress = if (level >= 100) 1f else (xpInLevel.toFloat() / (level * 60).toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, tween(700), label = "xpProgress")
    val dateLabel = now.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH))
    val timeLabel = now.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH))
    val completed = prayerDone.count { it } + habitDone.count { it }
    val total = prayerList.size + habitList.size

    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = SurfaceDark, primary = Neon, secondary = NeonSoft,
        onPrimary = Bg, onBackground = TextMain, onSurface = TextMain
    )) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark, contentColor = Neon) {
                    val labels = listOf("Home", "Namaz", "Habits", "Progress", "Settings")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Mosque, Icons.Default.CheckCircle, Icons.Default.Insights, Icons.Default.Settings)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index },
                            icon = { Icon(icons[index], contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Neon, selectedTextColor = Neon,
                                indicatorColor = Color(0xFF20351F), unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Neon, Color(0xFF087A21)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.TrendingUp, null, tint = Bg, modifier = Modifier.size(30.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("SELF IMPROVE", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text("BUILD A BETTER YOU • EVERY DAY", color = Neon, fontSize = 9.sp, letterSpacing = 1.2.sp)
                        }
                        Icon(Icons.Default.EmojiEvents, null, tint = NeonSoft, modifier = Modifier.size(26.dp))
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(dateLabel, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(timeLabel, color = Neon, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text("YOUR DAILY RESET • 12:00 AM", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                        }
                    }
                }
                when (tab) {
                    0 -> {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = SurfaceSoft), shape = RoundedCornerShape(22.dp)) {
                                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text("WELCOME BACK", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                                            Text("LEVEL $level", color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.Black)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("$xp XP", color = Neon, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                                            Text("TOTAL EXPERIENCE", color = TextMuted, fontSize = 9.sp)
                                        }
                                    }
                                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape), color = Neon, trackColor = Color(0xFF304033))
                                    Text(if (level == 100) "MAX LEVEL REACHED" else "$xpInLevel / ${level * 60} XP TO LEVEL ${level + 1}", color = NeonSoft, fontSize = 11.sp)
                                }
                            }
                        }
                        item { SectionHeader("TODAY'S OVERVIEW", "Your consistency, one day at a time") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricCard("NAMAZ", "${prayerDone.count { it }}/5", Icons.Default.Mosque, Modifier.weight(1f))
                                MetricCard("HABITS", "${habitDone.count { it }}/4", Icons.Default.CheckCircle, Modifier.weight(1f))
                                MetricCard("DONE", "${(completed * 100 / total)}%", Icons.Default.Bolt, Modifier.weight(1f))
                            }
                        }
                        item {
                            InfoPanel("DAILY JOURNAL", "Write your thoughts") {
                                Text(if (journal.isBlank()) "A few lines today can help you reflect tomorrow." else journal, color = TextMuted, maxLines = 2, fontSize = 12.sp)
                                GreenButton("OPEN JOURNAL", { journalDraft = journal; showJournal = true })
                            }
                        }
                        item {
                            InfoPanel("MOOD CHECK-IN", "How are you feeling?") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Great", "Good", "Okay", "Low").forEach { option ->
                                        val selected = mood == option
                                        FilterChip(selected, { mood = option; prefs.edit().putString("mood_$today", option).apply() },
                                            label = { Text(option, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon, selectedLabelColor = Bg))
                                    }
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text("MADE BY ", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                                Text("AR BADDI", color = Neon, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                            }
                        }
                    }
                    1 -> {
                        item { SectionHeader("DAILY NAMAZ", "Hanafi Asr • 20 XP for each completed prayer") }
                        items(prayerList.size) { i -> TrackerRow(prayerList[i], if (i == 2) "Hanafi calculation" else "Daily prayer", prayerDone[i], 20) { setPrayer(i, it) } }
                    }
                    2 -> {
                        item { SectionHeader("DAILY HABITS", "Build discipline through small actions • 10 XP each") }
                        items(habitList.size) { i -> TrackerRow(habitList[i], "Daily personal goal", habitDone[i], 10) { setHabit(i, it) } }
                        item {
                            InfoPanel("FOCUS TIMER", "Deep work session") {
                                Text(String.format(Locale.ENGLISH, "%02d:%02d", focusSeconds / 60, focusSeconds % 60), color = Neon, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GreenButton(if (focusRunning) "PAUSE" else "START 25 MIN", { focusRunning = !focusRunning })
                                    OutlinedButton(onClick = { focusRunning = false; focusSeconds = 25 * 60 }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon)) { Text("RESET") }
                                }
                            }
                        }
                        item {
                            InfoPanel("DAILY JOURNAL", "Keep a record") {
                                Text(if (journal.isBlank()) "No entry saved for today." else journal, color = TextMuted, maxLines = 2)
                                GreenButton("WRITE ENTRY", { journalDraft = journal; showJournal = true })
                            }
                        }
                    }
                    3 -> {
                        item {
                            InfoPanel("YOUR PROGRESS", "LEVEL $level • $xp XP") {
                                LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Neon, trackColor = SurfaceSoft)
                                Text(if (level == 100) "Maximum level" else "${(level * 60 - xpInLevel).coerceAtLeast(0)} XP remaining for next level", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        item { SectionHeader("TODAY'S SCORE", "Keep showing up") }
                        item { MetricCard("NAMAZ XP", "${prayerDone.count { it } * 20}", Icons.Default.Mosque, Modifier.fillMaxWidth()) }
                        item { MetricCard("HABIT XP", "${habitDone.count { it } * 10}", Icons.Default.Bolt, Modifier.fillMaxWidth()) }
                        item { SectionHeader("RECENT HISTORY", "Your saved daily records") }
                        (1L..30L).forEach { daysAgo ->
                            val historyDate = LocalDate.now().minusDays(daysAgo).toString()
                            val record = prefs.getString("history_$historyDate", null)
                            if (record != null) item { HistoryCard(historyDate, record) }
                        }
                    }
                    else -> {
                        item { SectionHeader("APP PREFERENCES", "Make the experience yours") }
                        item {
                            SettingRow("Sound effects", "Subtle sound on completed actions", soundOn) {
                                soundOn = it; prefs.edit().putBoolean("sound_on", it).apply()
                            }
                        }
                        item {
                            SettingRow("Notifications", "Completion feedback and daily summary", notificationsOn) {
                                notificationsOn = it; prefs.edit().putBoolean("notifications_on", it).apply()
                                if (it && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && context is ComponentActivity) {
                                    context.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
                                }
                            }
                        }
                        item {
                            InfoPanel("DAILY RESET", "Automatic day change") {
                                Text("Your daily Namaz and habit checklists reset on the next date. XP carries forward and the previous day is saved in history.", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        item {
                            InfoPanel("ABOUT", "SELF IMPROVE") {
                                Text("A personal routine tracker for faith, habits, focus and progress.", color = TextMuted, fontSize = 12.sp)
                                Text("Made by AR BADDI", color = Neon, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showJournal) {
        AlertDialog(
            onDismissRequest = { showJournal = false },
            containerColor = SurfaceDark,
            title = { Text("Daily journal", color = Neon) },
            text = { OutlinedTextField(value = journalDraft, onValueChange = { journalDraft = it }, label = { Text("Today's reflection") }, minLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    journal = journalDraft
                    prefs.edit().putString("journal_$today", journal).apply()
                    showJournal = false
                    feedback("Journal saved", "Today's reflection has been saved.")
                }) { Text("SAVE", color = Neon) }
            },
            dismissButton = { TextButton(onClick = { showJournal = false }) { Text("CANCEL", color = TextMuted) } }
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Neon, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 1.sp)
        Text(subtitle, color = TextMuted, fontSize = 11.sp)
    }
}
@Composable
private fun InfoPanel(title: String, heading: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Neon, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(heading, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Neon, modifier = Modifier.size(19.dp))
            Text(value, color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(title, color = TextMuted, fontSize = 9.sp, letterSpacing = .7.sp)
        }
    }
}
@Composable
private fun TrackerRow(title: String, subtitle: String, checked: Boolean, points: Int, onChecked: (Boolean) -> Unit) {
    val animated by animateColorAsState(if (checked) Color(0xFF17351B) else SurfaceDark, tween(250), label = "trackerColor")
    Card(colors = CardDefaults.cardColors(containerColor = animated), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (checked) Neon else SurfaceSoft), contentAlignment = Alignment.Center) {
                Icon(if (checked) Icons.Default.Check else Icons.Default.Add, null, tint = if (checked) Bg else Neon, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(title, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(if (checked) "Completed • +$points XP" else subtitle, color = if (checked) NeonSoft else TextMuted, fontSize = 10.sp)
            }
            Switch(checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Neon, uncheckedThumbColor = TextMuted, uncheckedTrackColor = SurfaceSoft))
        }
    }
}
@Composable
private fun GreenButton(label: String, onClick: () -> Unit) {
    Button(onClick, colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Bg), shape = RoundedCornerShape(12.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}
@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextMain, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 10.sp)
            }
            Switch(checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Bg, checkedTrackColor = Neon))
        }
    }
}
@Composable
private fun HistoryCard(date: String, raw: String) {
    val record = remember(raw) { JSONObject(raw) }
    val pa = record.optJSONArray("prayers") ?: JSONArray()
    val ha = record.optJSONArray("habits") ?: JSONArray()
    val pc = (0 until pa.length()).count { pa.optBoolean(it, false) }
    val hc = (0 until ha.length()).count { ha.optBoolean(it, false) }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, color = Neon, fontWeight = FontWeight.Bold)
            Text("Namaz: $pc/5  •  Habits: $hc/4", color = TextMain, fontSize = 12.sp)
            Text("XP after day-end: ${record.optInt("xpAfterRollover", 0)}", color = TextMuted, fontSize = 10.sp)
            val note = record.optString("journal", "")
            if (note.isNotBlank()) Text(note, color = TextMuted, maxLines = 2, fontSize = 11.sp)
        }
    }
}
private fun readFlags(prefs: android.content.SharedPreferences, key: String, count: Int): List<Boolean> {
    val raw = prefs.getString(key, null) ?: return List(count) { false }
    return try { val array = JSONArray(raw); List(count) { i -> array.optBoolean(i, false) } }
    catch (_: Exception) { List(count) { false } }
}
private fun calculateLevel(totalXp: Int): Pair<Int, Int> {
    var remaining = totalXp.coerceAtLeast(0)
    var level = 1
    while (level < 100 && remaining >= level * 60) { remaining -= level * 60; level++ }
    return level to remaining
}
private fun postNotice(context: Context, title: String, detail: String) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel(NOTICE_CHANNEL, "Progress updates", NotificationManager.IMPORTANCE_DEFAULT))
    }
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, NOTICE_CHANNEL) else {
        @Suppress("DEPRECATION") Notification.Builder(context)
    }
    manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
        builder.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("SELF IMPROVE • $title")
            .setContentText(detail).setAutoCancel(true).build())
}
