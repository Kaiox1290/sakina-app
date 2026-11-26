package com.sakina.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun FormDialog(title: String, onDismiss: () -> Unit, saveLabel: String = tr("حفظ", "Save"), onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { TextButton(onSave) { Text(saveLabel) } }, dismissButton = { TextButton(onDismiss) { Text(tr("إلغاء", "Cancel")) } })

@Composable fun StudyScreen(back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope(); val td = today()
    var tab by remember { mutableStateOf("subjects") }
    val subjects by dao.subjects().collectAsState(emptyList())
    val slots by dao.slots().collectAsState(emptyList())
    val assigns by dao.assignments().collectAsState(emptyList())
    val exams by dao.exams().collectAsState(emptyList())
    val sessions by dao.sessions(td - 6, td).collectAsState(emptyList())
    var dlg by remember { mutableStateOf("") }
    var pickSubject by remember { mutableLongStateOf(0L) }
    var txt by remember { mutableStateOf("") }; var txt2 by remember { mutableStateOf("") }
    var dd by remember { mutableLongStateOf(td + 1) }; var wd by remember { mutableIntStateOf(6) }; var s1 by remember { mutableIntStateOf(480) }; var e1 by remember { mutableIntStateOf(540) }
    fun subName(id: Long) = subjects.firstOrNull { it.id == id }?.name ?: "-"
    fun open(k: String) { dlg = k; txt = ""; txt2 = ""; pickSubject = subjects.firstOrNull()?.id ?: 0 }
    val needSubject = { if (subjects.isEmpty()) { dlg = "needsub" } }
    Screen(tr("الدراسة", "Study"), back, fab = when (tab) { "subjects" -> ({ open("subject") }); "assign" -> ({ if (subjects.isEmpty()) dlg = "needsub" else open("assign") }); "exams" -> ({ if (subjects.isEmpty()) dlg = "needsub" else open("exam") }); else -> null }) {
        ChipRow(listOf("subjects" to tr("المواد", "Subjects"), "assign" to tr("الواجبات", "Homework"), "exams" to tr("الامتحانات", "Exams"), "focus" to tr("مؤقت التركيز", "Focus timer"), "stats" to tr("إحصائيات", "Stats")), tab) { tab = it }
        when (tab) {
            "subjects" -> {
                if (subjects.isEmpty()) Empty(tr("أضف موادك الدراسية لتبني جدولك.", "Add your subjects to build your timetable."), tr("أضف مادة", "Add subject")) { open("subject") }
                subjects.forEach { s ->
                    Glass(Modifier.fillMaxWidth()) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        slots.filter { it.subjectId == s.id }.forEach { sl -> Row(verticalAlignment = Alignment.CenterVertically) { Sub((if (Settings.isAr) weekdaysAr else weekdaysEn)[sl.weekday - 1] + " " + numS(fmtMin(sl.startMin) + "-" + fmtMin(sl.endMin))); TextButton({ io(scope) { dao.delSlot(sl.id) } }) { Text(tr("حذف", "Del")) } } }
                        if (s.notes.isNotBlank()) Body(s.notes)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton({ pickSubject = s.id; dlg = "slot" }) { Text(tr("+ حصة", "+ Class")) }
                            TextButton({ pickSubject = s.id; txt = s.notes; dlg = "notes" }) { Text(tr("ملاحظات", "Notes")) }
                            TextButton({ pickSubject = s.id; dlg = "delsub" }) { Text(tr("حذف", "Delete"), color = DangerT) }
                        }
                    }
                }
            }
            "assign" -> {
                if (assigns.isEmpty()) Empty(tr("لا واجبات. استمتع بالهدوء!", "No homework. Enjoy the calm!"))
                assigns.forEach { a -> Glass(Modifier.fillMaxWidth(), pad = 12) { Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(a.done, { io(scope) { dao.updateAssignment(a.copy(done = it)) } })
                    Column(Modifier.weight(1f)) { Text(a.title, style = MaterialTheme.typography.titleSmall); Sub(subName(a.subjectId) + " · " + dayLabel(a.dueDay)) }
                    TextButton({ io(scope) { dao.delAssignment(a) } }) { Text(tr("حذف", "Del")) } } } }
            }
            "exams" -> {
                if (exams.isEmpty()) Empty(tr("لا امتحانات مسجلة.", "No exams recorded."))
                exams.forEach { e -> val left = (e.day - td).toInt(); Glass(Modifier.fillMaxWidth(), pad = 12) { Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(e.title, style = MaterialTheme.typography.titleSmall); Sub(subName(e.subjectId) + " · " + dayLabel(e.day) + if (e.timeMin >= 0) " " + numS(fmtMin(e.timeMin)) else "") }
                    Text(if (left < 0) tr("انتهى", "past") else numS(tr("بعد $left يوم", "in $left d")), color = if (left in 0..3) Danger else Emerald)
                    TextButton({ io(scope) { dao.delExam(e) } }) { Text(tr("حذف", "Del")) } } } }
            }
            "focus" -> FocusTimer()
            else -> {
                val days = (td - 6..td).toList()
                val mins = days.map { d -> sessions.filter { it.day == d && it.kind != "learn" }.sumOf { it.minutes } }
                Body(tr("ساعات المذاكرة هذا الأسبوع: ${numS(String.format(java.util.Locale.US, "%.1f", mins.sum() / 60f))} س", "Study hours this week: ${String.format(java.util.Locale.US, "%.1f", mins.sum() / 60f)} h"))
                BarChart(mins.map { it / 60f }, days.map { numS(dayOf(it).dayOfMonth.toString()) }, unit = "h")
                Sub(tr("الواجبات المتبقية: ${num(assigns.count { !it.done })}", "Homework remaining: ${assigns.count { !it.done }}"))
            }
        }
    }
    when (dlg) {
        "needsub" -> AlertDialog({ dlg = "" }, confirmButton = { TextButton({ dlg = ""; tab = "subjects"; open("subject") }) { Text(tr("أضف مادة", "Add subject")) } }, title = { Text(tr("أضف مادة أولًا", "Add a subject first")) })
        "subject" -> FormDialog(tr("مادة جديدة", "New subject"), { dlg = "" }, onSave = { if (txt.isNotBlank()) { val n = txt.trim(); dlg = ""; io(scope) { dao.addSubject(Subject(name = n)) } } }) { Field(txt, { txt = it }, tr("اسم المادة", "Subject name")) }
        "notes" -> FormDialog(tr("ملاحظات المادة", "Subject notes"), { dlg = "" }, onSave = { val s = subjects.first { it.id == pickSubject }; val n = txt; dlg = ""; io(scope) { dao.updateSubject(s.copy(notes = n)) } }) { Field(txt, { txt = it }, tr("ملاحظات", "Notes"), singleLine = false) }
        "delsub" -> Confirm(tr("حذف المادة", "Delete subject"), tr("سيُحذف كل ما يخصها (حصص، واجبات، امتحانات).", "Its classes, homework and exams are deleted too."), { dlg = ""; val id = pickSubject; io(scope) { dao.delSlotsOf(id); dao.delAssignOf(id); dao.delExamsOf(id); dao.delSubject(id) } }, { dlg = "" })
        "slot" -> FormDialog(tr("حصة جديدة", "New class"), { dlg = "" }, onSave = { if (e1 > s1) { val id = pickSubject; val w = wd; val a = s1; val b = e1; dlg = ""; io(scope) { dao.addSlot(ClassSlot(subjectId = id, weekday = w, startMin = a, endMin = b)) } } }) {
            ChipRow((1..7).map { it.toString() to (if (Settings.isAr) weekdaysAr else weekdaysEn)[it - 1] }, wd.toString()) { wd = it.toInt() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TimeField(tr("من", "From"), s1, { s1 = it }, modifier = Modifier.weight(1f)); TimeField(tr("إلى", "To"), e1, { e1 = it }, modifier = Modifier.weight(1f)) }
            if (e1 <= s1) Text(tr("النهاية يجب أن تكون بعد البداية", "End must be after start"), color = DangerT)
        }
        "assign" -> FormDialog(tr("واجب جديد", "New homework"), { dlg = "" }, onSave = { if (txt.isNotBlank()) { val t = txt.trim(); val sid = pickSubject; val d = dd; dlg = ""; io(scope) { dao.addAssignment(Assignment(subjectId = sid, title = t, dueDay = d)) } } }) {
            ChipRow(subjects.map { it.id.toString() to it.name }, pickSubject.toString()) { pickSubject = it.toLong() }
            Field(txt, { txt = it }, tr("العنوان", "Title")); DateField(tr("التسليم", "Due"), dd, { dd = it }, Modifier.fillMaxWidth())
        }
        "exam" -> FormDialog(tr("امتحان جديد", "New exam"), { dlg = "" }, onSave = { if (txt.isNotBlank()) { val t = txt.trim(); val sid = pickSubject; val d = dd; dlg = ""; io(scope) { dao.addExam(Exam(subjectId = sid, title = t, day = d)) } } }) {
            ChipRow(subjects.map { it.id.toString() to it.name }, pickSubject.toString()) { pickSubject = it.toLong() }
            Field(txt, { txt = it }, tr("عنوان الامتحان", "Exam title")); DateField(tr("التاريخ", "Date"), dd, { dd = it }, Modifier.fillMaxWidth())
        }
    }
}

@Composable fun FocusTimer() {
    val c = LocalContext.current; val scope = rememberCoroutineScope()
    var minutes by remember { mutableIntStateOf(25) }
    var kind by remember { mutableStateOf("focus") }
    var endAt by remember { mutableLongStateOf(Settings.sp.getLong("focus_end", 0L)) }
    var left by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf("") }
    val running = endAt > 0
    LaunchedEffect(endAt) {
        while (endAt > 0) {
            left = ((endAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (left <= 0L) {
                val d = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Notify.finishFocus(c) }
                vibrate(c, 400); if (d > 0) saved = tr("تم حفظ ${num(d)} دقيقة", "Saved $d min"); endAt = 0; break
            }
            delay(500)
        }
    }
    Glass(Modifier.fillMaxWidth()) {
        if (!running) {
            ChipRow(listOf("focus" to tr("تركيز", "Focus"), "study" to tr("مذاكرة", "Study")), kind) { kind = it }
            ChipRow(listOf(15, 25, 45, 60).map { it.toString() to numS("$it ${tr("د", "m")}") }, minutes.toString()) { minutes = it.toInt() }
            Spacer(Modifier.height(10.dp))
            Btn(tr("ابدأ", "Start"), { Notify.startFocus(c, minutes, kind); endAt = Settings.sp.getLong("focus_end", 0L); saved = "" }, Modifier.fillMaxWidth())
            if (saved.isNotEmpty()) Body(saved)
        } else {
            Text(numS(String.format(java.util.Locale.US, "%02d:%02d", left / 60, left % 60)), Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Btn(tr("إيقاف وحفظ ما أنجزته", "Stop and save progress"), {
                scope.launch {
                    val d = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Notify.finishFocus(c) }
                    endAt = 0
                    saved = if (d >= 1) tr("تم حفظ ${num(d)} دقيقة", "Saved $d min") else tr("أقل من دقيقة، لم يُحفظ شيء", "Under a minute, nothing saved")
                }
            }, Modifier.fillMaxWidth(), outlined = true)
            Sub(tr("المؤقت يستمر ويُحفظ حتى لو أغلقت التطبيق، وسيصلك إشعار عند الانتهاء.", "The timer keeps running and is saved even if you close the app. You get a notification when it ends."))
        }
    }
}

val codeLangs = listOf("JavaScript", "Python", "C#", "C++", "Godot", "Kotlin", "Java", "HTML/CSS")

@Composable fun CodingScreen(back: () -> Unit) {
    val dao = rememberDao(); val scope = rememberCoroutineScope(); val td = today()
    var tab by remember { mutableStateOf("goals") }
    val goals by dao.codeGoals().collectAsState(emptyList())
    val projects by dao.codeProjects().collectAsState(emptyList())
    val ach by dao.achievements().collectAsState(emptyList())
    val sessions by dao.sessions(td - 6, td).collectAsState(emptyList())
    var dlg by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("Python") }; var txt by remember { mutableStateOf("") }; var lvl by remember { mutableIntStateOf(0) }
    var total by remember { mutableStateOf("20") }; var wh by remember { mutableStateOf("5") }; var logMin by remember { mutableStateOf("30") }
    val lv = listOf(tr("مبتدئ", "Beginner"), tr("متوسط", "Intermediate"), tr("متقدم", "Advanced"))
    val learn = sessions.filter { it.kind == "learn" }
    Screen(tr("تعلّم البرمجة", "Learn coding"), back, fab = { dlg = when (tab) { "goals" -> "goal"; "projects" -> "project"; else -> "ach" }; txt = "" }) {
        ChipRow(listOf("goals" to tr("الأهداف", "Goals"), "projects" to tr("المشاريع", "Projects"), "ach" to tr("الإنجازات", "Wins")), tab) { tab = it }
        val wk = learn.sumOf { it.minutes }
        Glass(Modifier.fillMaxWidth()) {
            Body(tr("هذا الأسبوع: ${numS(fmtDur(wk))} من ${num(goals.sumOf { it.weeklyHours })} ساعة مخططة", "This week: ${fmtDur(wk)} of ${goals.sumOf { it.weeklyHours }} h planned"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(logMin, { logMin = it.filter { ch -> ch.isDigit() }.take(3) }, tr("دقائق", "Minutes"), Modifier.weight(1f), number = true)
                Btn(tr("سجّل جلسة", "Log session"), { val m = logMin.toIntOrNull() ?: 0; if (m > 0) io(scope) { dao.addSession(StudySession(day = td, minutes = m, kind = "learn", tag = goals.firstOrNull()?.lang ?: "")) } })
            }
        }
        when (tab) {
            "goals" -> {
                if (goals.isEmpty()) Empty(tr("اختر لغة وحدد هدفك الأسبوعي.", "Pick a language and set a weekly goal."), tr("أضف هدفًا", "Add a goal")) { dlg = "goal" }
                goals.forEach { g -> Glass(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(g.lang, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Sub(lv[g.level.coerceIn(0, 2)]) }
                    LinearProgressIndicator(progress = { g.lessonsDone.toFloat() / g.lessonsTotal.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(vertical = 6.dp), color = EmeraldT)
                    Sub(numS("${g.lessonsDone}/${g.lessonsTotal} ") + tr("درس", "lessons") + " · " + numS("${g.weeklyHours}") + tr(" س أسبوعيًا", " h/week"))
                    Row { TextButton({ if (g.lessonsDone > 0) io(scope) { dao.updateCodeGoal(g.copy(lessonsDone = g.lessonsDone - 1)) } }) { Text("−") }
                        TextButton({ if (g.lessonsDone < g.lessonsTotal) io(scope) { dao.updateCodeGoal(g.copy(lessonsDone = g.lessonsDone + 1)); if (g.lessonsDone + 1 == g.lessonsTotal) dao.addAchievement(Achievement(day = td, text = tr("أنهيت أهداف ${g.lang}", "Finished ${g.lang} goal"), area = "coding")) } }) { Text(tr("+ درس مكتمل", "+ lesson done")) }
                        Spacer(Modifier.weight(1f)); TextButton({ io(scope) { dao.delCodeGoal(g) } }) { Text(tr("حذف", "Delete"), color = DangerT) } }
                } }
            }
            "projects" -> {
                if (projects.isEmpty()) Empty(tr("لا مشاريع بعد.", "No projects yet."), tr("أضف مشروعًا", "Add project")) { dlg = "project"; txt = "" }
                val st = listOf(tr("فكرة", "Idea"), tr("قيد العمل", "In progress"), tr("منتهي", "Done"))
                projects.forEach { p -> Glass(Modifier.fillMaxWidth()) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium); Sub(p.lang)
                    ChipRow(st.mapIndexed { i, s -> i.toString() to s }, p.status.toString()) { io(scope) { dao.updateCodeProject(p.copy(status = it.toInt())); if (it == "2") dao.addAchievement(Achievement(day = td, text = tr("أنهيت مشروع ${p.name}", "Finished project ${p.name}"), area = "coding")) } }
                    TextButton({ io(scope) { dao.delCodeProject(p) } }) { Text(tr("حذف", "Delete"), color = DangerT) }
                } }
            }
            else -> {
                if (ach.isEmpty()) Empty(tr("سجّل أول إنجاز لك.", "Log your first win."), tr("أضف إنجازًا", "Add a win")) { dlg = "ach"; txt = "" }
                ach.forEach { a -> Glass(Modifier.fillMaxWidth(), pad = 12) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("✓ ${a.text}"); Sub(dayLabel(a.day)) }; TextButton({ io(scope) { dao.delAchievement(a) } }) { Text(tr("حذف", "Del")) } } } }
            }
        }
    }
    when (dlg) {
        "goal" -> FormDialog(tr("هدف تعلم", "Learning goal"), { dlg = "" }, onSave = { val t = total.toIntOrNull() ?: 20; val w = wh.toIntOrNull() ?: 5; val l = lang; val v = lvl; dlg = ""; io(scope) { dao.addCodeGoal(CodeGoal(lang = l, level = v, lessonsTotal = t.coerceAtLeast(1), weeklyHours = w)) } }) {
            ChipRow(codeLangs.map { it to it }, lang) { lang = it }
            ChipRow(lv.mapIndexed { i, s -> i.toString() to s }, lvl.toString()) { lvl = it.toInt() }
            Field(total, { total = it.filter { ch -> ch.isDigit() }.take(4) }, tr("عدد الدروس المخطط", "Planned lessons"), number = true)
            Field(wh, { wh = it.filter { ch -> ch.isDigit() }.take(2) }, tr("ساعات أسبوعيًا", "Hours per week"), number = true)
        }
        "project" -> FormDialog(tr("مشروع جديد", "New project"), { dlg = "" }, onSave = { if (txt.isNotBlank()) { val n = txt.trim(); val l = lang; dlg = ""; io(scope) { dao.addCodeProject(CodeProject(name = n, lang = l)) } } }) { Field(txt, { txt = it }, tr("اسم المشروع", "Project name")); ChipRow(codeLangs.map { it to it }, lang) { lang = it } }
        "ach" -> FormDialog(tr("إنجاز جديد", "New win"), { dlg = "" }, onSave = { if (txt.isNotBlank()) { val n = txt.trim(); dlg = ""; io(scope) { dao.addAchievement(Achievement(day = td, text = n, area = "coding")) } } }) { Field(txt, { txt = it }, tr("ماذا أنجزت؟", "What did you achieve?")) }
    }
}
