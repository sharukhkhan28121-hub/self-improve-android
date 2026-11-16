package com.selfimprove.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject

private val Ink = Color(0xFF101014)
private val Panel = Color(0xFF1B1B22)
private val Gold = Color(0xFFE3B943)
private val Muted = Color(0xFFAAAAB4)
private val White = Color(0xFFF7F4EA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SelfImproveApp(this) }
    }
}

@Composable
private fun SelfImproveApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("self_improve", Context.MODE_PRIVATE) }
    val prayers = listOf("Fajr", "Dhuhr", "Asr (Hanafi)", "Maghrib", "Isha")
    val habits = listOf("Exercise", "Study", "Sleep on time", "Daily journal")
    var xp by remember { mutableIntStateOf(prefs.getInt("xp", 0)) }
    var prayerDone by remember { mutableStateOf(loadFlags(prefs, "prayers", prayers.size)) }
    var habitDone by remember { mutableStateOf(loadFlags(prefs, "habits", habits.size)) }
    var journal by remember { mutableStateOf(prefs.getString("journal", "") ?: "") }
    var showJournal by remember { mutableStateOf(false) }
    var pendingMiss by remember { mutableStateOf<String?>(null) }

    val level = remember(xp) {
        var remaining = xp.coerceAtLeast(0)
        var current = 1
        while (current < 100 && remaining >= current * 60) {
            remaining -= current * 60
            current++
        }
        current
    }
    val xpIntoLevel = remember(xp) {
        var remaining = xp.coerceAtLeast(0)
        var current = 1
        while (current < 100 && remaining >= current * 60) {
            remaining -= current * 60
            current++
        }
        remaining
    }
    val levelTarget = if (level == 100) 0 else level * 60

    fun saveXp(value: Int) {
        xp = value.coerceAtLeast(0)
        prefs.edit().putInt("xp", xp).apply()
    }
    fun setFlag(key: String, index: Int, value: Boolean, list: List<Boolean>) {
        val updated = list.toMutableList()
        updated[index] = value
        if (key == "prayers") prayerDone = updated else habitDone = updated
        val json = JSONArray()
        updated.forEach { json.put(it) }
        prefs.edit().putString(key, json.toString()).apply()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Panel, primary = Gold,
            onPrimary = Ink, onBackground = White, onSurface = White,
            secondary = Gold
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("SELF IMPROVE", color = Gold, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("BUILD A BETTER YOU • EVERY DAY", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    Text("YOUR LEVEL", color = Muted, fontSize = 12.sp)
                                    Text("LEVEL $level", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("$xp XP", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            if (level < 100) {
                                LinearProgressIndicator(
                                    progress = { (xpIntoLevel.toFloat() / levelTarget.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(9.dp),
                                    color = Gold,
                                    trackColor = Color(0xFF39343A)
                                )
                                Text("$xpIntoLevel / $levelTarget XP to Level ${level + 1}", color = Muted, fontSize = 12.sp)
                            } else {
                                Text("ULTIMATE MASTER • MAX LEVEL", color = Gold, fontWeight = FontWeight.Bold)
                            }
                            Text("Level N requires ${60 * level} XP to advance. XP carries forward.", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                item { SectionTitle("DAILY NAMAZ", "Hanafi Asr") }
                items(prayers.size) { index ->
                    TrackerRow(
                        title = prayers[index],
                        subtitle = if (index == 2) "Hanafi calculation preference" else "Daily prayer",
                        checked = prayerDone[index],
                        onChecked = { checked ->
                            if (checked) {
                                setFlag("prayers", index, true, prayerDone)
                                saveXp(xp + 20)
                            } else pendingMiss = prayers[index]
                        }
                    )
                }
                item { SectionTitle("DAILY HABITS", "Build consistency") }
                items(habits.size) { index ->
                    TrackerRow(
                        title = habits[index],
                        subtitle = "Daily habit",
                        checked = habitDone[index],
                        onChecked = { checked ->
                            if (checked) {
                                setFlag("habits", index, true, habitDone)
                                saveXp(xp + 10)
                            } else pendingMiss = habits[index]
                        }
                    )
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("DAILY JOURNAL", color = Gold, fontWeight = FontWeight.Bold)
                            Text(if (journal.isBlank()) "Write a short reflection for today." else journal, color = Muted, fontSize = 13.sp, maxLines = 3)
                            Button(onClick = { showJournal = true }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                                Text("Open journal")
                            }
                        }
                    }
                }
                item {
                    Text("Note: Prayer times and automatic reminders are not enabled in this starter version.", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }

    if (pendingMiss != null) {
        AlertDialog(
            onDismissRequest = { pendingMiss = null },
            title = { Text("Confirm missed item?") },
            text = { Text("Mark ${pendingMiss} as missed? 20 XP will be deducted for Namaz, or 10 XP for a daily habit. No XP will be deducted until you confirm.") },
            confirmButton = {
                TextButton(onClick = {
                    val name = pendingMiss ?: return@TextButton
                    val pIndex = prayers.indexOf(name)
                    val hIndex = habits.indexOf(name)
                    if (pIndex >= 0) setFlag("prayers", pIndex, false, prayerDone)
                    if (hIndex >= 0) setFlag("habits", hIndex, false, habitDone)
                    saveXp(xp - if (pIndex >= 0) 20 else 10)
                    pendingMiss = null
                }) { Text("Confirm deduction", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { pendingMiss = null }) { Text("Cancel") } }
        )
    }
    if (showJournal) {
        var draft by remember { mutableStateOf(journal) }
        Dialog(onDismissRequest = { showJournal = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Daily journal", color = Gold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Today's reflection") }, minLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showJournal = false }) { Text("Cancel") }
                        Button(onClick = {
                            journal = draft
                            prefs.edit().putString("journal", journal).apply()
                            showJournal = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(detail, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TrackerRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Checkbox(checked = checked, onCheckedChange = onChecked, colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Muted, checkmarkColor = Ink))
        }
    }
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
