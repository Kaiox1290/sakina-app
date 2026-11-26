package com.sakina.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate

@Composable fun TaskDialog(initial: TaskE?, day: Long, onDismiss: () -> Unit, onSave: (TaskE) -> Unit, onDelete: (() -> Unit)? = null) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "personal") }
    var prio by remember { mutableIntStateOf(initial?.priority ?: 1) }
    var d by remember { mutableLongStateOf(initial?.day ?: day) }
    var st by remember { mutableIntStateOf(initial?.startMin ?: -1) }
    var en by remember { mutableIntStateOf(initial?.endMin ?: -1) }
    var rep by remember { mutableIntStateOf(initial?.repeat ?: 0) }
    var rem by remember { mutableStateOf(initial?.remind ?: false) }
    var before by remember { mutableIntStateOf(initial?.remindBefore ?: 0) }
    var err by remember { mutableStateOf("") }
    Dialog(onDismiss) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Title(if (initial == null) tr("مهمة جديدة", "New task") else tr("تعديل المهمة", "Edit task"))
                Field(title, { title = it; err = "" }, tr("عنوان المهمة", "Title"))
                Field(notes, { notes = it }, tr("ملاحظات (اختياري)", "Notes (optional)"), singleLine = false)
                Text(tr("التصنيف", "Category"), style = MaterialTheme.typography.titleSmall)
                ChipRow(categories.map { it to catName(it) }, cat) { cat = it }
                Text(tr("الأولوية", "Priority"), style = MaterialTheme.typography.titleSmall)
                ChipRow(listOf("0" to tr("منخفضة", "Low"), "1" to tr("عادية", "Normal"), "2" to tr("عالية", "High")), prio.toString()) { prio = it.toInt() }
                DateField(tr("التاريخ", "Date"), d, { d = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(tr("البداية", "Start"), st, { st = it; if (en in 0..st) en = -1 }, modifier = Modifier.weight(1f))
                    TimeField(tr("النهاية", "End"), en, { en = it }, modifier = Modifier.weight(1f))
                }
                if (st >= 0) TextButton({ st = -1; en = -1; rem = false }) { Text(tr("بدون وقت", "No time")) }
                Text(tr("التكرار", "Repeat"), style = MaterialTheme.typography.titleSmall)
                ChipRow(listOf("0" to tr("مرة واحدة", "Once"), "1" to tr("يوميًا", "Daily"), "2" to tr("أسبوعيًا", "Weekly")), rep.toString()) { rep = it.toInt() }
                RowSwitch(tr("تذكير", "Reminder"), rem, { rem = it }, if (st < 0) tr("حدد وقت البداية أولًا", "Set a start time first") else null)
                if (rem && st >= 0) ChipRow(listOf("0" to tr("عند البدء", "At start"), "10" to tr("قبل ١٠ د", "10 min before"), "30" to tr("قبل ٣٠ د", "30 min before"), "60" to tr("قبل ساعة", "1 hour before")), before.toString()) { before = it.toInt() }
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(tr("حفظ", "Save"), {
                        if (title.isBlank()) err = tr("اكتب عنوان المهمة", "Enter a title")
                        else if (st >= 0 && en in 0 until st) err = tr("وقت النهاية قبل البداية", "End is before start")
                        else onSave((initial ?: TaskE(title = "", day = d)).copy(title = title.trim(), notes = notes.trim(), category = cat, priority = prio, day = d, startMin = st, endMin = en, repeat = rep, remind = rem && st >= 0, remindBefore = before, auto = initial?.auto ?: false))
                    }, Modifier.weight(1f))
                    Btn(tr("إلغاء", "Cancel"), onDismiss, Modifier.weight(1f), outlined = true)
                }
                if (onDelete != null) TextButton(onDelete) { Text(tr("حذف المهمة", "Delete task"), color = DangerT) }
            }
        }
    }
}

@Composable fun TasksScreen(go: Go) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope()
    var sel by remember { mutableLongStateOf(today()) }
    var filter by remember { mutableStateOf("all") }
    val tasks by dao.tasks().collectAsState(emptyList())
    val done by dao.taskDone(sel, sel).collectAsState(emptyList())
    var editing by remember { mutableStateOf<TaskE?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf<TaskE?>(null) }
    val doneIds = done.map { it.taskId }.toSet()
    val list = tasks.on(sel).filter { filter == "all" || it.category == filter }
    LazyScreen(tr("المهام", "Tasks"), fab = { adding = true }, actions = { TextButton({ go("calendar") }) { Text(tr("التقويم", "Calendar")) }; TextButton({ go("planner") }) { Text(tr("جدول يومي", "Planner")) } }) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ sel-- }) { Text("‹") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(dayLabel(sel), style = MaterialTheme.typography.titleMedium); Sub(hijriText(dayOf(sel))) }
                TextButton({ sel++ }) { Text("›") }
            }
        }
        item { ChipRow(listOf("all" to tr("الكل", "All")) + categories.map { it to catName(it) }, filter) { filter = it } }
        item { val total = list.size; val dn = list.count { it.id in doneIds }
            LinearProgressIndicator(progress = { if (total == 0) 0f else dn.toFloat() / total }, Modifier.fillMaxWidth(), color = EmeraldT); Sub(numS("$dn/$total") + " " + tr("مكتملة", "done")) }
        if (list.isEmpty()) item { Empty(tr("لا مهام لهذا اليوم.", "No tasks for this day."), tr("أضف مهمة", "Add a task")) { adding = true } }
        items(list, key = { it.id }) { t -> TaskRow(t, t.id in doneIds, { io(scope) { if (t.id in doneIds) dao.unsetTaskDone(TaskDone(t.id, sel)) else dao.setTaskDone(TaskDone(t.id, sel)) } }) { editing = t } }
    }
    if (adding) TaskDialog(null, sel, { adding = false }, { t -> adding = false; io(scope) { dao.insertTask(t.copy(id = 0)); Notify.refreshAsync(c) } })
    editing?.let { t -> TaskDialog(t, sel, { editing = null }, { n -> editing = null; io(scope) { dao.updateTask(n); Notify.refreshAsync(c) } }, { editing = null; confirmDel = t }) }
    confirmDel?.let { t -> Confirm(tr("حذف المهمة", "Delete task"), tr("حذف \"${t.title}\"؟" + if (t.repeat != 0) " سيُحذف كل تكرارها." else "", "Delete \"${t.title}\"?" + if (t.repeat != 0) " All repeats will be removed." else ""), { confirmDel = null; io(scope) { dao.deleteTask(t.id); dao.deleteTaskDone(t.id); Notify.refreshAsync(c) } }, { confirmDel = null }) }
}

@Composable fun CalendarScreen(back: () -> Unit) {
    val dao = rememberDao(); val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("month") }
    var sel by remember { mutableLongStateOf(today()) }
    val tasks by dao.tasks().collectAsState(emptyList())
    val range = if (mode == "month") dayOf(sel).withDayOfMonth(1).toEpochDay() - 7 to dayOf(sel).withDayOfMonth(1).plusMonths(1).toEpochDay() + 7 else sel - 7 to sel + 7
    val done by dao.taskDone(range.first, range.second).collectAsState(emptyList())
    val doneSet = done.map { it.key() }.toSet()
    Screen(tr("التقويم", "Calendar"), back) {
        ChipRow(listOf("day" to tr("يومي", "Daily"), "week" to tr("أسبوعي", "Weekly"), "month" to tr("شهري", "Monthly")), mode) { mode = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ sel = when (mode) { "month" -> dayOf(sel).minusMonths(1).toEpochDay(); "week" -> sel - 7; else -> sel - 1 } }) { Text("‹") }
            Text(if (mode == "month") numS(dayOf(sel).let { (if (Settings.isAr) monthsAr else monthsEn)[it.monthValue - 1] + " " + it.year }) else gregText(dayOf(sel)), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            TextButton({ sel = when (mode) { "month" -> dayOf(sel).plusMonths(1).toEpochDay(); "week" -> sel + 7; else -> sel + 1 } }) { Text("›") }
        }
        when (mode) {
            "month" -> {
                val first = dayOf(sel).withDayOfMonth(1); val len = first.lengthOfMonth()
                val lead = (first.dayOfWeek.value + 1) % 7
                val cells = (0 until lead).map { 0 } + (1..len)
                val heads = if (Settings.isAr) listOf("س", "ح", "ن", "ث", "ر", "خ", "ج") else listOf("Sa", "Su", "Mo", "Tu", "We", "Th", "Fr")
                Glass(Modifier.fillMaxWidth(), pad = 8) {
                    Row(Modifier.fillMaxWidth()) { heads.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldT, style = MaterialTheme.typography.labelMedium) } }
                    cells.chunked(7).forEach { wk -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { for (i in 0 until 7) {
                        val d = wk.getOrNull(i) ?: 0
                        if (d == 0) { Spacer(Modifier.weight(1f).height(44.dp)) } else {
                            val ed = first.plusDays((d - 1).toLong()).toEpochDay(); val n = tasks.on(ed).size; val isSel = ed == sel
                            Column(Modifier.weight(1f).height(44.dp).padding(2.dp).let { m -> if (isSel) m.then(Modifier) else m }, horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton({ sel = ed; mode = "day" }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth().height(36.dp)) {
                                    Text(numS(d.toString()), color = if (ed == today()) Emerald else MaterialTheme.colorScheme.onSurface, style = if (ed == today()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                                }
                                if (n > 0) Text(numS("•$n"), style = MaterialTheme.typography.labelSmall, color = GoldT)
                            }
                        }
                    } } }
                }
                Sub(tr("اضغط على يوم لعرض مهامه.", "Tap a day to see its tasks."))
            }
            "week" -> {
                val start = dayOf(sel).let { it.minusDays(((it.dayOfWeek.value + 1) % 7).toLong()) }.toEpochDay()
                (0..6).forEach { k -> val ed = start + k; val ts = tasks.on(ed)
                    Glass(Modifier.fillMaxWidth(), onClick = { sel = ed; mode = "day" }, pad = 12) {
                        Text(dayLabel(ed), style = MaterialTheme.typography.titleSmall, color = if (ed == today()) Emerald else MaterialTheme.colorScheme.onSurface)
                        if (ts.isEmpty()) Sub(tr("لا مهام", "No tasks")) else ts.forEach { Text((if ("${it.id}|$ed" in doneSet) "✓ " else "• ") + (if (it.startMin >= 0) numS(fmtMin(it.startMin)) + " " else "") + it.title, style = MaterialTheme.typography.bodyMedium) }
                    } }
            }
            else -> {
                val ts = tasks.on(sel)
                if (ts.isEmpty()) Empty(tr("لا مهام في هذا اليوم.", "Nothing on this day."))
                ts.forEach { t -> val dn = "${t.id}|$sel" in doneSet
                    TaskRow(t, dn, { io(scope) { if (dn) dao.unsetTaskDone(TaskDone(t.id, sel)) else dao.setTaskDone(TaskDone(t.id, sel)) } }) { } }
            }
        }
    }
}
