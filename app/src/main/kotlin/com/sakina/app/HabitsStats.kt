package com.sakina.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable fun HabitsScreen(back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope(); val td = today()
    val habits by dao.habits().collectAsState(emptyList())
    val logs by dao.habitLogs(td - 60, td).collectAsState(emptyList())
    val acts by dao.activities().collectAsState(emptyList())
    var tab by remember { mutableStateOf("habits") }
    var dlg by remember { mutableStateOf<Habit?>(null) }; var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var rem by remember { mutableIntStateOf(-1) }
    var aName by remember { mutableStateOf("") }; var aMin by remember { mutableStateOf("30") }; var addAct by remember { mutableStateOf(false) }
    var planAdded by remember { mutableStateOf(false) }
    Screen(tr("العادات والرياضة", "Habits & sport"), back, fab = { if (tab == "habits") { name = ""; rem = -1; adding = true } else { aName = ""; addAct = true } }) {
        ChipRow(listOf("habits" to tr("العادات", "Habits"), "sport" to tr("الرياضة", "Exercise")), tab) { tab = it }
        if (tab == "habits") {
            if (habits.isEmpty()) Empty(tr("ابدأ بعادة صغيرة واحدة، مثل شرب الماء أو المشي.", "Start with one small habit, like drinking water or walking."), tr("أضف عادة", "Add habit")) { name = ""; rem = -1; adding = true }
            habits.forEach { h ->
                val days = logs.filter { it.habitId == h.id }.map { it.day }.toSet(); val doneToday = td in days
                Glass(Modifier.fillMaxWidth(), pad = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(doneToday, { v -> io(scope) { if (v) dao.putHabitLog(HabitLog(h.id, td)) else dao.delHabitLog(HabitLog(h.id, td)) } }, enabled = h.active)
                        Column(Modifier.weight(1f)) {
                            Text(h.name, style = MaterialTheme.typography.titleSmall)
                            Sub(numS(tr("التتابع: ${streak(days, td)} يوم", "Streak: ${streak(days, td)} d")) + (if (h.remindMin >= 0) " · ⏰ " + numS(fmtMin(h.remindMin)) else "") + (if (!h.active) " · " + tr("متوقفة", "paused") else ""))
                        }
                        TextButton({ dlg = h; name = h.name; rem = h.remindMin }) { Text(tr("تعديل", "Edit")) }
                    }
                }
            }
            Sub(tr("لا بأس إن فاتك يوم. المهم أن تعود.", "Missing a day is fine. What matters is coming back."))
        } else {
            Glass(Modifier.fillMaxWidth()) {
                Text(tr("جدول معتدل", "A gentle plan"), style = MaterialTheme.typography.titleMedium)
                Sub(tr("٣ أيام في الأسبوع، ٣٠ دقيقة مشي أو تمارين خفيفة، وباقي الأيام راحة. بدون إجهاد.", "3 days a week, 30 minutes of walking or light exercise, with rest days in between. No overload."))
                Btn(if (planAdded) tr("تمت الإضافة", "Added") else tr("أضفه لمهامي", "Add to my tasks"), {
                    planAdded = true
                    io(scope) { val base = LocalDate.now(); for (off in listOf(0, 2, 4)) dao.insertTask(TaskE(title = tr("رياضة خفيفة", "Light exercise"), category = "sport", day = base.plusDays(off.toLong()).toEpochDay(), startMin = 17 * 60 + 30, endMin = 18 * 60, repeat = 2, remind = true))
                        Notify.refreshAsync(c) }
                }, Modifier.fillMaxWidth(), enabled = !planAdded)
            }
            val week = acts.filter { it.day > td - 7 }
            Body(tr("نشاط هذا الأسبوع: ${num(week.sumOf { it.minutes })} دقيقة", "This week: ${week.sumOf { it.minutes }} min"))
            if (acts.isEmpty()) Empty(tr("سجّل نشاطك الرياضي.", "Log your activity."), tr("سجّل نشاطًا", "Log activity")) { aName = ""; addAct = true }
            acts.take(30).forEach { a -> Glass(Modifier.fillMaxWidth(), pad = 12) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(a.name); Sub(dayLabel(a.day) + " · " + numS(fmtDur(a.minutes))) }; TextButton({ io(scope) { dao.delActivity(a.id) } }) { Text(tr("حذف", "Del")) } } } }
            if (week.sumOf { it.minutes } > 420) Sub(tr("نشاطك كبير هذا الأسبوع، خذ يوم راحة.", "Plenty of activity this week. Take a rest day."))
        }
    }
    if (adding || dlg != null) FormDialog(if (dlg == null) tr("عادة جديدة", "New habit") else tr("تعديل العادة", "Edit habit"), { adding = false; dlg = null }, onSave = {
        if (name.isNotBlank()) { val h = dlg; val n = name.trim(); val r = rem; adding = false; dlg = null
            io(scope) { if (h == null) dao.addHabit(Habit(name = n, remindMin = r, createdDay = td)) else dao.updateHabit(h.copy(name = n, remindMin = r)); Notify.refreshAsync(c) } }
    }) {
        Field(name, { name = it }, tr("اسم العادة", "Habit name"))
        TimeField(tr("تذكير يومي", "Daily reminder"), rem, { rem = it })
        if (rem >= 0) TextButton({ rem = -1 }) { Text(tr("بدون تذكير", "No reminder")) }
        dlg?.let { h ->
            TextButton({ val n = h.copy(active = !h.active); dlg = null; io(scope) { dao.updateHabit(n); Notify.refreshAsync(c) } }) { Text(if (h.active) tr("إيقاف العادة مؤقتًا", "Pause habit") else tr("تفعيل العادة", "Resume habit")) }
            TextButton({ dlg = null; io(scope) { dao.delHabitLogs(h.id); dao.delHabit(h.id); Notify.refreshAsync(c) } }) { Text(tr("حذف العادة", "Delete habit"), color = DangerT) }
        }
    }
    if (addAct) FormDialog(tr("نشاط رياضي", "Activity"), { addAct = false }, onSave = { val m = aMin.toIntOrNull() ?: 0; if (aName.isNotBlank() && m > 0) { val n = aName.trim(); addAct = false; io(scope) { dao.addActivity(Activity(day = td, name = n, minutes = m)) } } }) {
        Field(aName, { aName = it }, tr("النشاط (مشي، جري…)", "Activity (walk, run…)")); Field(aMin, { aMin = it.filter { ch -> ch.isDigit() }.take(3) }, tr("الدقائق", "Minutes"), number = true)
    }
}

@Composable fun StatsScreen(back: () -> Unit) {
    val dao = rememberDao(); val td = today()
    var range by remember { mutableIntStateOf(7) }
    val from = td - 29
    val tasks by dao.tasks().collectAsState(emptyList())
    val done by dao.taskDone(from, td).collectAsState(emptyList())
    val adh by dao.adhkarRange(from, td).collectAsState(emptyList())
    val pages by dao.pagesRead(from, td).collectAsState(emptyList())
    val sess by dao.sessions(from, td).collectAsState(emptyList())
    val hab by dao.habitLogs(from, td).collectAsState(emptyList())
    val habits by dao.habits().collectAsState(emptyList())
    val tas by dao.tasbihRange(from, td).collectAsState(emptyList())
    fun rng(n: Int) = (td - n + 1..td).toList()
    fun taskPct(days: List<Long>): Int { val occ = days.sumOf { d -> tasks.on(d).size }; val dn = days.sumOf { d -> tasks.on(d).count { t -> done.any { it.taskId == t.id && it.day == d } } }; return if (occ == 0) 0 else dn * 100 / occ }
    val days = rng(range)
    val activeDays = (from..td).filter { d -> done.any { it.day == d } || adh.any { it.day == d && it.done } || pages.any { it.day == d } || hab.any { it.day == d } || tas.any { it.day == d && it.count > 0 } }.toSet()
    val lbl = days.map { numS(dayOf(it).dayOfMonth.toString()) }
    val prev = (td - 2 * range + 1..td - range).toList()
    fun studyH(ds: List<Long>) = sess.filter { it.day in ds && it.kind != "learn" }.sumOf { it.minutes } / 60f
    fun learnH(ds: List<Long>) = sess.filter { it.day in ds && it.kind == "learn" }.sumOf { it.minutes } / 60f
    Screen(tr("الإحصائيات", "Statistics"), back) {
        ChipRow(listOf("7" to tr("أسبوع", "Week"), "30" to tr("شهر", "Month")), range.toString()) { range = it.toInt() }
        Glass(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Ring(taskPct(days) / 100f, 76, 8)
                Column { Text(tr("إنجاز المهام", "Task completion"), style = MaterialTheme.typography.titleMedium); Sub(tr("أيام الالتزام: ${num(days.count { it in activeDays })} من ${num(days.size)}", "Active days: ${days.count { it in activeDays }} of ${days.size}")); Sub(tr("تتابع الأيام: ${num(streak(activeDays, td))}", "Current streak: ${streak(activeDays, td)}")) }
            }
        }
        SectionLabel(tr("الأذكار المكتملة يوميًا", "Adhkar completed per day")); BarChart(days.map { d -> adh.count { it.day == d && it.done }.toFloat() }, lbl)
        SectionLabel(tr("صفحات القرآن", "Quran pages")); BarChart(days.map { d -> pages.count { it.day == d }.toFloat() }, lbl, color = GoldT)
        SectionLabel(tr("ساعات الدراسة", "Study hours")); BarChart(days.map { d -> studyH(listOf(d)) }, lbl, unit = "h")
        SectionLabel(tr("ساعات التعلّم", "Learning hours")); BarChart(days.map { d -> learnH(listOf(d)) }, lbl, color = GoldT, unit = "h")
        val hd = hab.count { it.day in days }
        Body(tr("عادات مكتملة: ${num(hd)} · تسبيحات: ${num(tas.filter { it.day in days }.sumOf { it.count })}", "Habits completed: $hd · Tasbih: ${tas.filter { it.day in days }.sumOf { it.count }}"))
        SectionLabel(tr(if (range == 7) "مقارنة بالأسبوع السابق" else "مقارنة بالشهر السابق", if (range == 7) "Compared with last week" else "Compared with last month"))
        Glass(Modifier.fillMaxWidth()) {
            val a = taskPct(days); val b = if (range == 7) taskPct(prev) else 0
            Body(tr("المهام: ${num(a)}٪ (قبلها ${num(b)}٪)", "Tasks: $a% (before: $b%)"))
            Body(tr("الدراسة: ${numS(String.format(java.util.Locale.US, "%.1f", studyH(days)))} س (قبلها ${numS(String.format(java.util.Locale.US, "%.1f", studyH(prev)))})", "Study: ${String.format(java.util.Locale.US, "%.1f", studyH(days))} h (before ${String.format(java.util.Locale.US, "%.1f", studyH(prev))})"))
        }
        Sub(tr("الأرقام لمساعدتك على الاستمرار فقط. يوم الفوات ليس فشلًا، وغدًا بداية جديدة.", "These numbers are here to help you continue. A missed day is not a failure; tomorrow is a fresh start."))
    }
}
