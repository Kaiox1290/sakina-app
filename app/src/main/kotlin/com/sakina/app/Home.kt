package com.sakina.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@Composable fun rememberDao(): AppDao { val c = LocalContext.current; return remember { AppDb.get(c).dao() } }

@Composable fun Countdown() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val next = remember(now / 30_000) { PrayerSettings.next(now) }
    val left = ((next.second - now) / 1000).coerceAtLeast(0)
    Glass(Modifier.fillMaxWidth(), pad = 18) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Sub(tr("الصلاة القادمة", "Next prayer"))
                Text(prayerName(next.first), style = MaterialTheme.typography.headlineMedium, color = GoldT)
                Sub(numS(fmtTimeMs(next.second)))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(numS(String.format(java.util.Locale.US, "%02d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60)), style = MaterialTheme.typography.headlineMedium)
                Sub(tr("متبقي", "remaining"))
            }
        }
    }
}

@Composable fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    Text(numS(fmtTimeMs(now)), style = MaterialTheme.typography.displayMedium)
}

@Composable fun MiniStat(label: String, value: String, progress: Float?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Glass(modifier, onClick = onClick, pad = 14) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (progress != null) Ring(progress, 52, 6, label = value) else Text(value, style = MaterialTheme.typography.titleLarge, color = EmeraldT)
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        }
    }
}

@Composable fun HomeScreen(go: Go) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope()
    val td = today(); val name = Settings.str("name")
    val tasks by dao.tasks().collectAsState(emptyList())
    val done by dao.taskDone(td, td).collectAsState(emptyList())
    val adh by dao.adhkarProg(td).collectAsState(emptyList())
    val pages by dao.pagesRead(td, td).collectAsState(emptyList())
    val sessions by dao.sessions(td, td).collectAsState(emptyList())
    val habits by dao.habits().collectAsState(emptyList())
    val hlogs by dao.habitLogs(td, td).collectAsState(emptyList())
    val ach by dao.achievements().collectAsState(emptyList())
    val todayTasks = tasks.on(td)
    val doneIds = done.map { it.taskId }.toSet()
    val tasksDone = todayTasks.count { it.id in doneIds }
    val wirdGoal = Settings.int("wird_pages", 2)
    val activeHabits = habits.filter { it.active }
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val late = todayTasks.filter { it.id !in doneIds && it.startMin >= 0 && (if (it.endMin > it.startMin) it.endMin else it.startMin + 30) < nowMin }
    var askReflow by remember { mutableStateOf(false) }
    val show = { k: String -> Settings.bool("home_$k", true) }
    val greetHour = LocalTime.now().hour
    val greet = if (greetHour < 12) tr("صباح الخير", "Good morning") else if (greetHour < 18) tr("مساء الخير", "Good afternoon") else tr("مساء النور", "Good evening")
    val quote = remember(td) { Quotes.forDay(c, td) }
    val groups = remember { Adhkar.groups.associate { it.key to Adhkar.items(c, it).size } }
    fun grpDone(k: String) = adh.count { it.done && it.key.startsWith("$k|") }

    Screen(greet + if (name.isNotBlank()) "، $name".let { if (Settings.isAr) it else ", $name" } else "", actions = {
        IconButton({ go("search") }) { Icon(Icons.Default.Search, tr("بحث", "Search")) }
        IconButton({ go("settings") }) { Icon(Icons.Default.Settings, tr("الإعدادات", "Settings")) }
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Body(gregText(LocalDate.now())); Sub(hijriText(LocalDate.now())) }
            Clock()
        }
        if (show("prayer")) Box(Modifier.clickable { go("prayer") }) { Countdown() }
        SuggestCard(go)
        PrayerLogCard()
        RamadanCard(go)
        PointsCard(go)
        if (show("quote")) Glass(Modifier.fillMaxWidth()) {
            Text(quote.first, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (quote.second.isNotEmpty()) Amiri else Tajawal, fontSize = if (quote.second.isNotEmpty()) 20.sp else 16.sp, lineHeight = 32.sp), color = GoldT, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            if (quote.second.isNotEmpty()) Sub(quote.second, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        if (late.isNotEmpty()) Glass(Modifier.fillMaxWidth()) {
            Text(tr("عندك ${num(late.size)} مهمة تأخرت. لا بأس، تقدر أرتّب لك بقية اليوم.", "${late.size} task(s) ran late. No worries, I can rearrange the rest of your day."), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp)); Btn(tr("أعد ترتيب بقية اليوم", "Rearrange my day"), { askReflow = true })
        }
        val row = Modifier.fillMaxWidth()
        if (show("wird") || show("tasks")) Row(row, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (show("wird")) MiniStat(tr("الورد اليومي", "Daily wird"), numS("${pages.size}/$wirdGoal"), pages.size.toFloat() / wirdGoal.coerceAtLeast(1), Modifier.weight(1f)) { go("quran") }
            if (show("tasks")) MiniStat(tr("مهام اليوم", "Today's tasks"), numS("$tasksDone/${todayTasks.size}"), if (todayTasks.isEmpty()) 0f else tasksDone.toFloat() / todayTasks.size, Modifier.weight(1f)) { go("tasks") }
        }
        if (show("adhkar")) Row(row, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(tr("أذكار الصباح", "Morning"), numS("${grpDone("morning")}/${groups["morning"]}"), grpDone("morning").toFloat() / (groups["morning"] ?: 1), Modifier.weight(1f)) { go("adhkar/morning") }
            MiniStat(tr("أذكار المساء", "Evening"), numS("${grpDone("evening")}/${groups["evening"]}"), grpDone("evening").toFloat() / (groups["evening"] ?: 1), Modifier.weight(1f)) { go("adhkar/evening") }
        }
        if (show("study") || show("coding")) Row(row, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val study = sessions.filter { it.kind != "learn" }.sumOf { it.minutes }; val learn = sessions.filter { it.kind == "learn" }.sumOf { it.minutes }
            if (show("study")) MiniStat(tr("مذاكرة اليوم", "Study today"), fmtDur(study).let { numS(it) }, null, Modifier.weight(1f)) { go("study") }
            if (show("coding")) MiniStat(tr("تعلم البرمجة", "Coding"), numS(fmtDur(learn)), null, Modifier.weight(1f)) { go("coding") }
        }
        if (show("habits")) MiniStat(tr("العادات", "Habits"), numS("${hlogs.count { l -> activeHabits.any { it.id == l.habitId } }}/${activeHabits.size}"),
            if (activeHabits.isEmpty()) 0f else hlogs.count { l -> activeHabits.any { it.id == l.habitId } }.toFloat() / activeHabits.size, row) { go("habits") }
        if (show("sleep")) Glass(row, onClick = { go("planner") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(tr("النوم والاستيقاظ", "Sleep & wake"), style = MaterialTheme.typography.titleSmall); Sub(numS(tr("استيقاظ ${fmtMin(Settings.int("wake_t", 360))} · نوم ${fmtMin(Settings.int("bed_t", 1380))}", "Wake ${fmtMin(Settings.int("wake_t", 360))} · Bed ${fmtMin(Settings.int("bed_t", 1380))}"))) }
                Icon(Icons.Default.Info, null, tint = GoldT)
            }
        }
        SectionLabel(tr("مهام اليوم", "Today"))
        if (todayTasks.isEmpty()) Empty(tr("ما في مهام اليوم. أضف مهمة أو اطلب من المساعد بناء جدول.", "No tasks today. Add one or let the planner build your day."), tr("أضف مهمة", "Add a task")) { go("tasks") }
        else todayTasks.take(5).forEach { t ->
            TaskRow(t, t.id in doneIds, { io(scope) { if (t.id in doneIds) dao.unsetTaskDone(TaskDone(t.id, td)) else dao.setTaskDone(TaskDone(t.id, td)) } }) { go("tasks") }
        }
        SectionLabel(tr("وصول سريع", "Quick access"))
        Row(row, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickTile(tr("الأذكار", "Adhkar"), Modifier.weight(1f)) { go("adhkar") }
            QuickTile(tr("المسبحة", "Tasbih"), Modifier.weight(1f)) { go("tasbih") }
            QuickTile(tr("رفيق", "Rafiq"), Modifier.weight(1f)) { go("assistant") }
        }
        if (ach.isNotEmpty() && show("ach")) { SectionLabel(tr("آخر الإنجازات", "Recent wins")); ach.take(2).forEach { Glass(row) { Body("✓ ${it.text}") } } }
    }
    if (askReflow) Confirm(tr("إعادة ترتيب اليوم", "Rearrange today"), tr("سأنقل المهام المتأخرة إلى أقرب وقت فارغ بعد الآن، بدون تداخل مع الصلاة أو مهامك الأخرى.", "I will move late tasks to the nearest free time after now, avoiding prayers and your other tasks."), {
        askReflow = false
        io(scope) {
            val tm = PrayerSettings.timesFor(LocalDate.now())
            val busy = listOf("fajr", "dhuhr", "asr", "maghrib", "isha").map { Interval((tm.get(it) + 0.5).toInt(), (tm.get(it) + 0.5).toInt() + 20) }
            val res = reflowTasks(late, todayTasks.filter { it !in late && it.id !in doneIds }, nowMin, Settings.sp.getInt("bed_t", 1380), busy)
            for ((id, s) in res) { val t = tasks.first { it.id == id }; val dur = if (t.endMin > t.startMin) t.endMin - t.startMin else 30
                if (t.repeat == 0) dao.updateTask(t.copy(startMin = s, endMin = s + dur)) }
            Notify.refreshAsync(c)
        }
    }, { askReflow = false })
}

@Composable fun QuickTile(label: String, modifier: Modifier, onClick: () -> Unit) =
    Glass(modifier, onClick = onClick, pad = 14) { Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

@Composable fun TaskRow(t: TaskE, done: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), onClick = onClick, pad = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(done, { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                val tm = if (t.startMin >= 0) numS(fmtMin(t.startMin) + if (t.endMin > t.startMin) "-" + fmtMin(t.endMin) else "") + " · " else ""
                Sub(tm + catName(t.category) + if (t.repeat == 1) " · " + tr("يوميًا", "daily") else if (t.repeat == 2) " · " + tr("أسبوعيًا", "weekly") else "")
            }
            if (t.priority == 2) Text("●", color = DangerT) else if (t.priority == 0) Text("○", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
