package com.sakina.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

fun blockTitle(b: Block): String = when (b.kind) {
    "prayer" -> prayerName(b.title); "work" -> tr("المدرسة / العمل", "School / work"); "rest" -> tr("راحة", "Rest")
    "study" -> tr("مذاكرة", "Study"); "learn" -> tr("تعلّم البرمجة", "Learn coding"); "sport" -> tr("رياضة", "Exercise"); else -> b.title
}

@Composable fun PlannerScreen(back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope()
    var wake by remember { mutableIntStateOf(Settings.sp.getInt("wake_t", 360)) }
    var bed by remember { mutableIntStateOf(Settings.sp.getInt("bed_t", 1380)) }
    var ws by remember { mutableIntStateOf(Settings.sp.getInt("work_s", -1)) }
    var we by remember { mutableIntStateOf(Settings.sp.getInt("work_e", -1)) }
    var study by remember { mutableIntStateOf(Settings.sp.getInt("plan_study", 120)) }
    var learn by remember { mutableIntStateOf(Settings.sp.getInt("plan_learn", 60)) }
    var sport by remember { mutableIntStateOf(Settings.sp.getInt("plan_sport", 30)) }
    var rest by remember { mutableIntStateOf(Settings.sp.getInt("plan_rest", 30)) }
    var result by remember { mutableStateOf<PlanResult?>(null) }
    var removed by remember { mutableStateOf(setOf<Int>()) }
    var applied by remember { mutableStateOf(false) }
    var askApply by remember { mutableStateOf(false) }
    fun save() { Settings.put("wake_t", wake); Settings.put("bed_t", bed); Settings.put("work_s", ws); Settings.put("work_e", we); Settings.put("plan_study", study); Settings.put("plan_learn", learn); Settings.put("plan_sport", sport); Settings.put("plan_rest", rest) }
    Screen(tr("مساعد تنظيم الوقت", "Schedule assistant"), back) {
        Sub(tr("حدد يومك وسأبني جدولًا واقعيًا بدون تداخل، يراعي الصلاة والراحة والنوم. تقدر تعدّله قبل الاعتماد.", "Tell me about your day and I will build a realistic, non-overlapping schedule around prayers, rest and sleep. You can edit it before applying."))
        Glass(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TimeField(tr("الاستيقاظ", "Wake"), wake, { wake = it }, modifier = Modifier.weight(1f)); TimeField(tr("النوم", "Sleep"), bed, { bed = it }, modifier = Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TimeField(tr("بداية المدرسة/العمل", "School/work start"), ws, { ws = it }, modifier = Modifier.weight(1f)); TimeField(tr("نهايتها", "End"), we, { we = it }, modifier = Modifier.weight(1f)) }
            if (ws >= 0 || we >= 0) TextButton({ ws = -1; we = -1 }) { Text(tr("لا يوجد مدرسة/عمل اليوم", "No school/work")) }
            DurRow(tr("وقت الدراسة", "Study"), study, listOf(0, 60, 120, 180, 240)) { study = it }
            DurRow(tr("وقت تعلم البرمجة", "Learning/coding"), learn, listOf(0, 30, 60, 90, 120)) { learn = it }
            DurRow(tr("وقت الرياضة", "Exercise"), sport, listOf(0, 20, 30, 45, 60)) { sport = it }
            DurRow(tr("وقت الراحة", "Rest"), rest, listOf(0, 15, 30, 45, 60)) { rest = it }
        }
        Btn(tr("ابنِ جدول اليوم", "Build today's schedule"), {
            save(); applied = false; removed = emptySet()
            val tm = PrayerSettings.timesFor(LocalDate.now())
            val pr = listOf("fajr", "dhuhr", "asr", "maghrib", "isha").map { it to (tm.get(it) + 0.5).toInt() }
            result = buildPlan(PlanInput(wake, bed, ws, we, study, learn, sport, rest, pr))
        }, Modifier.fillMaxWidth())
        result?.let { r ->
            if ("sleep" in r.warnings) Glass(Modifier.fillMaxWidth()) { Text(tr("نومك ${fmtDur(r.sleepMinutes)} فقط. الأفضل ٧ ساعات أو أكثر، فكّر في تبكير النوم.", "You are planning only ${fmtDur(r.sleepMinutes)} of sleep. Aim for 7+ hours; consider an earlier bedtime."), color = GoldT) }
            else Sub(tr("مدة النوم: ${fmtDur(r.sleepMinutes)}", "Sleep: ${fmtDur(r.sleepMinutes)}"))
            if (r.unplaced.isNotEmpty()) Glass(Modifier.fillMaxWidth()) { Text(tr("لم يتّسع الوقت لـ: ${r.unplaced.joinToString("، ") { blockTitle(Block(it, "", 0, 0, it)) }}. قلّل المدد أو وسّع اليوم.", "No room for: ${r.unplaced.joinToString(", ") { blockTitle(Block(it, "", 0, 0, it)) }}. Shorten durations or widen your day."), color = DangerT) }
            r.blocks.forEachIndexed { i, b ->
                if (i !in removed) Glass(Modifier.fillMaxWidth(), pad = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(numS(fmtMin(b.start) + " - " + fmtMin(b.end)), Modifier.width(110.dp), color = GoldT, style = MaterialTheme.typography.labelLarge)
                        Text(blockTitle(b), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        if (b.kind != "prayer" && b.kind != "work") TextButton({ removed = removed + i }) { Text(tr("حذف", "Remove")) }
                    }
                }
            }
            Btn(tr("اعتمد الجدول (مهام يومية)", "Apply as daily tasks"), { askApply = true }, Modifier.fillMaxWidth(), enabled = !applied)
            if (applied) Sub(tr("تم الاعتماد. تجدها في المهام. تقدر تعدّل أي مهمة هناك.", "Applied. Find them in Tasks, where you can edit each one."))
        }
        Sub(tr("ملاحظة: يتأخر موعد الصلاة أو يتقدم يوميًا، لذا تُحسب كتل الصلاة لليوم فقط ولا تُحفظ كمهام.", "Prayer times shift daily, so prayer blocks are shown for today only and are not saved as tasks."))
    }
    if (askApply) Confirm(tr("اعتماد الجدول", "Apply schedule"), tr("سيحلّ هذا محل أي جدول سابق أنشأه المساعد. مهامك اليدوية لن تتغير.", "This replaces any schedule previously made by the assistant. Your own tasks are not touched."), {
        askApply = false
        val r = result
        if (r != null) io(scope) {
            dao.deleteAutoTasks()
            r.blocks.forEachIndexed { i, b -> if (i !in removed && b.kind != "prayer" && b.kind != "work")
                dao.insertTask(TaskE(title = blockTitle(b), category = b.category, day = today(), startMin = b.start % 1440, endMin = b.end % 1440, repeat = 1, remind = b.kind != "rest", remindBefore = 0, auto = true)) }
            Notify.refreshAsync(c)
            applied = true
        }
    }, { askApply = false })
}

@Composable fun DurRow(label: String, v: Int, options: List<Int>, onPick: (Int) -> Unit) {
    Spacer(Modifier.height(6.dp))
    Text(label, style = MaterialTheme.typography.titleSmall)
    ChipRow(options.map { it.toString() to (if (it == 0) tr("بدون", "None") else numS(fmtDur(it))) }, v.toString()) { onPick(it.toInt()) }
}
