package com.selfimprove.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val Ink = Color(0xFF101014)
private val Panel = Color(0xFF1B1B22)
private val Gold = Color(0xFFE3B943)
private val Muted = Color(0xFFAAAAB4)
private val White = Color(0xFFF7F4EA)
private val prayers = listOf("Fajr", "Dhuhr", "Asr (Hanafi)", "Maghrib", "Isha")
private val habits = listOf("Exercise", "Study", "Sleep on time", "Daily journal")

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
    val today = LocalDate.now().toString()
    val storedDay = remember { prefs.getString("day", null) }
    val firstLaunch = storedDay == null
    val dayChanged = storedDay != null && storedDay != today
    val oldPrayers = remember { loadFlags(prefs, "prayers", prayers.size) }
    val openingPenalty = if (dayChanged) oldPrayers.count { !it } * 20 else 0

    var xp by remember { mutableIntStateOf((prefs.getInt("xp", 0) - openingPenalty).coerceAtLeast(0)) }
    var prayerDone by remember { mutableStateOf(if (dayChanged || firstLaunch) List(prayers.size) { false } else oldPrayers) }
    var habitDone by remember { mutableStateOf(if (dayChanged || firstLaunch) List(habits.size) { false } else loadFlags(prefs, "habits", habits.size)) }
    var journal by remember { mutableStateOf(if (dayChanged || firstLaunch) "" else prefs.getString("journal_$today", "") ?: "") }
    var journalDraft by remember { mutableStateOf(journal) }
    var showJournal by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    fun saveXp(value: Int) {
        xp = value.coerceAtLeast(0)
        prefs.edit().putInt("xp", xp).apply()
    }

    fun saveFlags(key: String, values: List<Boolean>) {
        val json = JSONArray()
        values.forEach { json.put(it) }
        prefs.edit().putString(key, json.toString()).apply()
    }

    fun setPrayer(index: Int, checked: Boolean) {
        if (prayerDone[index] == checked) return
        val updated = prayerDone.toMutableList().also { it[index] = checked }
        prayerDone = updated
        saveFlags("prayers", updated)
        saveXp(xp + if (checked) 20 else -20)
    }

    fun setHabit(index: Int, checked: Boolean) {
        if (habitDone[index] == checked) return
        val updated = habitDone.toMutableList().also { it[index] = checked }
        habitDone = updated
        saveFlags("habits", updated)
        saveXp(xp + if (checked) 10 else -10)
    }

    fun resetDay(newDay: String) {
        val summary = DailyRollover.rollover(context, newDay)
        if (summary != null) DailyRollover.showSummaryNotification(context, summary)
        DailyRollover.scheduleNextMidnight(context)
        xp = prefs.getInt("xp", xp)
        prayerDone = List(prayers.size) { false }
        habitDone = List(habits.size) { false }
        journal = ""
        journalDraft = ""
    }

    LaunchedEffect(Unit) {
        if (dayChanged) {
            resetDay(today)
        } else if (firstLaunch) {
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

    val (level, xpInLevel) = calculateLevel(xp)
    val dateLabel = now.format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH))
    val timeLabel = now.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH))

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Gold,
        onPrimary = Ink, onBackground = White, onSurface = White, secondary = Gold
    )) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(activeTab == 0, { activeTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(activeTab == 1, { activeTab = 1 }, icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("Namaz") })
                    NavigationBarItem(activeTab == 2, { activeTab = 2 }, icon = { Icon(Icons.Default.CheckCircle, null) }, label = { Text("Habits") })
                    NavigationBarItem(activeTab == 3, { activeTab = 3 }, icon = { Icon(Icons.Default.Star, null) }, label = { Text("Progress") })
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                item {
                    Text("SELF IMPROVE", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("BUILD A BETTER YOU • EVERY DAY", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(dateLabel, color = White, fontWeight = FontWeight.SemiBold)
                            Text(timeLabel, color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Device local time", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                when (activeTab) {
                    0 -> {
                        item {
                            InfoCard("YOUR LEVEL", "LEVEL $level", "$xp XP") {
                                if (level < 100) {
                                    LinearProgressIndicator(
                                        progress = { (xpInLevel.toFloat() / (level * 60).toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = Gold, trackColor = Color(0xFF39343A)
                                    )
                                    Text("$xpInLevel / ${level * 60} XP to Level ${level + 1}", color = Muted, fontSize = 12.sp)
                                } else Text("MAX LEVEL 100", color = Gold, fontWeight = FontWeight.Bold)
                                Text("Each next level requires level × 60 XP.", color = Muted, fontSize = 11.sp)
                            }
                        }
                        item { SectionTitle("TODAY AT A GLANCE") }
                        item { SummaryRow("Namaz completed", "${prayerDone.count { it }} / 5") }
                        item { SummaryRow("Habits completed", "${habitDone.count { it }} / ${habits.size}") }
                        item {
                            InfoCard("DAILY JOURNAL", "Today's reflection", "") {
                                Text(if (journal.isBlank()) "No entry saved today." else journal, color = Muted, maxLines = 3)
                                Button(onClick = { journalDraft = journal; showJournal = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Write journal") }
                            }
                        }
                        item {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text("Made by ", color = Muted, fontSize = 12.sp)
                                Text("AR BADDI", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            }
                        }
                    }
                    1 -> {
                        item { SectionTitle("DAILY NAMAZ • HANAFI ASR") }
                        item { Text("Each completed prayer earns 20 XP. Each prayer left incomplete at midnight deducts 20 XP.", color = Muted, fontSize = 12.sp) }
                        items(prayers.size) { i -> TrackerRow(prayers[i], if (i == 2) "Hanafi Asr" else "Daily prayer", prayerDone[i]) { setPrayer(i, it) } }
                    }
                    2 -> {
                        item { SectionTitle("DAILY HABITS") }
                        item { Text("Each completed habit earns 10 XP. Checklist resets automatically on the next date.", color = Muted, fontSize = 12.sp) }
                        items(habits.size) { i -> TrackerRow(habits[i], "+10 XP", habitDone[i]) { setHabit(i, it) } }
                        item {
                            InfoCard("DAILY JOURNAL", "Today's notes", "") {
                                Text(if (journal.isBlank()) "No entry saved today." else journal, color = Muted, maxLines = 3)
                                Button(onClick = { journalDraft = journal; showJournal = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Open journal") }
                            }
                        }
                    }
                    else -> {
                        item {
                            InfoCard("PROGRESS", "LEVEL $level", "$xp XP") {
                                Text(if (level == 100) "You reached the maximum level." else "${level * 60 - xpInLevel} XP needed for Level ${level + 1}.", color = Muted)
                                Text("Level 1 to 100 • XP total carries across days.", color = Muted, fontSize = 12.sp)
                            }
                        }
                        item { SectionTitle("TODAY'S STATS") }
                        item { SummaryRow("Namaz XP earned", "${prayerDone.count { it } * 20} XP") }
                        item { SummaryRow("Habit XP earned", "${habitDone.count { it } * 10} XP") }
                        item { SectionTitle("RECENT HISTORY") }
                        (1L..7L).forEach { daysAgo ->
                            val historyDate = LocalDate.now().minusDays(daysAgo).toString()
                            val historyJson = prefs.getString("history_$historyDate", null)
                            if (historyJson != null) {
                                item { HistoryCard(historyDate, historyJson) }
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
            title = { Text("Daily journal") },
            text = { OutlinedTextField(value = journalDraft, onValueChange = { journalDraft = it }, label = { Text("Today's reflection") }, minLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    journal = journalDraft
                    prefs.edit().putString("journal_$today", journal).apply()
                    showJournal = false
                }) { Text("Save", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { showJournal = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InfoCard(title: String, heading: String, trailing: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(heading, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                if (trailing.isNotBlank()) Text(trailing, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
private fun TrackerRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Muted, checkmarkColor = Ink))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = White, fontSize = 13.sp)
            Text(value, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

private fun calculateLevel(totalXp: Int): Pair<Int, Int> {
    var remaining = totalXp.coerceAtLeast(0)
    var level = 1
    while (level < 100 && remaining >= level * 60) {
        remaining -= level * 60
        level++
    }
    return level to remaining
}

private fun loadFlags(prefs: android.content.SharedPreferences, key: String, count: Int): List<Boolean> {
    val raw = prefs.getString(key, null) ?: return List(count) { false }
    return try {
        val json = JSONArray(raw)
        List(count) { index -> json.optBoolean(index, false) }
    } catch (_: Exception) {
        List(count) { false }
    }
}


@Composable
private fun HistoryCard(date: String, raw: String) {
    val record = remember(raw) { JSONObject(raw) }
    val prayerArray = record.optJSONArray("prayers") ?: JSONArray()
    val habitArray = record.optJSONArray("habits") ?: JSONArray()
    val prayersDone = (0 until prayerArray.length()).count { prayerArray.optBoolean(it, false) }
    val habitsDone = (0 until habitArray.length()).count { habitArray.optBoolean(it, false) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(date, color = Gold, fontWeight = FontWeight.Bold)
            Text("Namaz: $prayersDone/5  •  Habits: $habitsDone/4", color = White, fontSize = 13.sp)
            Text("XP after day-end: ${record.optInt("xpAfterRollover", 0)}", color = Muted, fontSize = 11.sp)
            val journalText = record.optString("journal", "")
            if (journalText.isNotBlank()) Text("Journal: $journalText", color = Muted, fontSize = 12.sp, maxLines = 2)
        }
    }
}
