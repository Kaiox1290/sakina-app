package com.sakina.app

/** Pure logic (no Android dependencies) so it can be unit tested. */

fun List<TaskE>.on(d: Long): List<TaskE> = filter { it.occursOn(d) }.sortedWith(compareBy({ it.startMin < 0 }, { it.startMin }, { -it.priority }))

data class Interval(val start: Int, val end: Int)

/** Earliest gap of [dur] minutes at or after [from] and ending by [limit], avoiding [busy]. Returns start or null. */
fun findGap(busy: List<Interval>, from: Int, limit: Int, dur: Int): Int? {
    var t = from
    val sorted = busy.sortedBy { it.start }
    var guard = 0
    while (t + dur <= limit && guard++ < 1000) {
        val clash = sorted.firstOrNull { it.start < t + dur && it.end > t }
        if (clash == null) return t
        t = clash.end
    }
    return null
}

data class PlanInput(
    val wake: Int, val bed: Int, val workStart: Int = -1, val workEnd: Int = -1,
    val studyMin: Int = 0, val learnMin: Int = 0, val sportMin: Int = 0, val restMin: Int = 30,
    val prayers: List<Pair<String, Int>> = emptyList(), val prayerBlock: Int = 20
)
data class Block(val title: String, val category: String, val start: Int, val end: Int, val kind: String)
data class PlanResult(val blocks: List<Block>, val unplaced: List<String>, val sleepMinutes: Int, val warnings: List<String>)

fun buildPlan(p: PlanInput): PlanResult {
    val bedAbs = if (p.bed <= p.wake) p.bed + 1440 else p.bed
    val sleepMin = 1440 - (bedAbs - p.wake)
    val warnings = ArrayList<String>()
    if (sleepMin < 7 * 60) warnings.add("sleep")
    val busy = ArrayList<Interval>(); val blocks = ArrayList<Block>(); val unplaced = ArrayList<String>()
    for ((k, m) in p.prayers) {
        if (m < p.wake || m >= bedAbs) continue
        busy.add(Interval(m, m + p.prayerBlock)); blocks.add(Block(k, "worship", m, m + p.prayerBlock, "prayer"))
    }
    val windDown = 45
    val limit = bedAbs - windDown
    if (p.workStart >= 0 && p.workEnd > p.workStart) { busy.add(Interval(p.workStart, p.workEnd)); blocks.add(Block("work", "study", p.workStart, p.workEnd, "work")) }
    val afterWork = if (p.workEnd > 0) p.workEnd else p.wake + 60
    fun prayer(k: String) = p.prayers.firstOrNull { it.first == k }?.second
    fun place(title: String, cat: String, kind: String, dur: Int, pref: Int, gapAfter: Int = 0) {
        if (dur <= 0) return
        val s = findGap(busy, maxOf(pref, p.wake), limit, dur) ?: findGap(busy, p.wake, limit, dur)
        if (s == null) { unplaced.add(kind); return }
        busy.add(Interval(s, s + dur + gapAfter)); blocks.add(Block(title, cat, s, s + dur, kind))
    }
    if (p.restMin > 0) place("rest", "personal", "rest", p.restMin, afterWork)
    // study in chunks of at most 90 minutes with a short break between chunks
    var left = p.studyMin; var pref = afterWork + p.restMin
    while (left > 0) {
        val chunk = minOf(left, 90); place("study", "study", "study", chunk, pref, 15); pref += chunk + 15; left -= chunk
    }
    val asr = prayer("asr") ?: (afterWork + 120)
    place("sport", "sport", "sport", p.sportMin, asr + p.prayerBlock + 10)
    val maghrib = prayer("maghrib") ?: (asr + 180)
    var learnLeft = p.learnMin; var lp = (prayer("isha") ?: maghrib + 90) + p.prayerBlock
    if (learnLeft > 0) lp = minOf(lp, maghrib + p.prayerBlock + 30).let { maxOf(it, maghrib + p.prayerBlock) }
    while (learnLeft > 0) { val chunk = minOf(learnLeft, 90); place("learn", "coding", "learn", chunk, lp, 15); lp += chunk + 15; learnLeft -= chunk }
    return PlanResult(blocks.sortedBy { it.start }, unplaced.distinct(), sleepMin, warnings)
}

/** Reflow late tasks: returns taskId -> new start minute for tasks that could be re-placed after [nowMin]. */
fun reflowTasks(late: List<TaskE>, others: List<TaskE>, nowMin: Int, bed: Int, prayerBusy: List<Interval>): Map<Long, Int> {
    val busy = ArrayList<Interval>(prayerBusy)
    others.filter { it.startMin >= 0 }.forEach { busy.add(Interval(it.startMin, if (it.endMin > it.startMin) it.endMin else it.startMin + 30)) }
    val res = LinkedHashMap<Long, Int>()
    val limit = if (bed <= nowMin) 1440 else bed
    for (t in late.sortedByDescending { it.priority }) {
        val dur = if (t.endMin > t.startMin && t.startMin >= 0) t.endMin - t.startMin else 30
        val s = findGap(busy, nowMin + 5, limit, dur) ?: continue
        busy.add(Interval(s, s + dur + 10)); res[t.id] = s
    }
    return res
}

/** Streak: number of consecutive days up to and including [today] present in [days] (today may be missing without breaking the streak). */
fun streak(days: Set<Long>, today: Long): Int {
    var d = if (days.contains(today)) today else today - 1
    var n = 0
    while (days.contains(d)) { n++; d-- }
    return n
}

/** Local, rule-based breakdown of a big goal into small weekly tasks. */
fun splitGoal(goal: String, weeks: Int, perWeek: Int): List<String> {
    val w = weeks.coerceIn(1, 52); val n = perWeek.coerceIn(1, 7)
    val out = ArrayList<String>()
    for (wk in 1..w) for (i in 1..n) out.add("$goal - W$wk/${i}")
    return out
}

// ---- v1.1: khatma plan, points and levels (pure, unit-tested) ----
data class KhatmaPlan(val dayIndex: Int, val days: Int, val perDay: Int, val targetEnd: Int, val read: Int, val remaining: Int, val daysLeft: Int, val catchUpPerDay: Int, val finished: Boolean, val behindBy: Int, val aheadBy: Int, val overdue: Boolean)

fun khatmaPlan(start: Long, days: Int, today: Long, readPages: Int, total: Int = 604): KhatmaPlan {
    val d = days.coerceAtLeast(1)
    val raw = (today - start).toInt()
    val idx = raw.coerceIn(0, d - 1)
    val targetEnd = Math.ceil(total.toDouble() * (idx + 1) / d).toInt().coerceAtMost(total)
    val read = readPages.coerceIn(0, total)
    val remaining = total - read
    val daysLeft = (d - idx).coerceAtLeast(1)
    val catchUp = Math.ceil(remaining.toDouble() / daysLeft).toInt()
    return KhatmaPlan(idx, d, Math.ceil(total.toDouble() / d).toInt(), targetEnd, read, remaining, daysLeft, catchUp, remaining == 0,
        (targetEnd - read).coerceAtLeast(0), (read - targetEnd).coerceAtLeast(0), raw > d - 1)
}

data class PointsIn(val adhkarDone: Int = 0, val tasks: Int = 0, val habits: Int = 0, val studyMin: Int = 0, val pages: Int = 0, val tasbih: Int = 0, val prayers: Int = 0)
fun pointsOf(p: PointsIn): Int = p.adhkarDone * 5 + p.tasks * 3 + p.habits * 2 + p.studyMin / 10 + p.pages + p.tasbih / 100 + p.prayers * 2
/** Level L (1-based) starts at 50*L*(L-1) points. Returns level, points at its start, points at the next level. */
fun levelOf(points: Int): Triple<Int, Int, Int> {
    var l = 1
    while (50 * (l + 1) * l <= points) l++
    return Triple(l, 50 * l * (l - 1), 50 * (l + 1) * l)
}

// ---- v1.1: badges, Ramadan, suggestions ----
data class Badge(val id: String, val ar: String, val en: String, val descAr: String, val descEn: String, val have: Int, val need: Int) { val unlocked get() = have >= need }

fun badgesOf(p: PointsIn, khatmaDone: Boolean = false): List<Badge> {
    val lvl = levelOf(pointsOf(p)).first
    fun b(id: String, ar: String, en: String, da: String, de: String, have: Int, need: Int) = Badge(id, ar, en, da, de, have, need)
    return listOf(
        b("a1", "أول ذكر", "First dhikr", "أكمل ذكرًا واحدًا", "Complete one dhikr", p.adhkarDone, 1),
        b("a2", "ذاكر", "Rememberer", "أكمل ٢٥ ذكرًا", "Complete 25 adhkar", p.adhkarDone, 25),
        b("a3", "لسان رطب", "Steady tongue", "أكمل ١٠٠ ذكر", "Complete 100 adhkar", p.adhkarDone, 100),
        b("t1", "بداية منظمة", "Organized start", "أنجز مهمة", "Finish one task", p.tasks, 1),
        b("t2", "منجز", "Achiever", "أنجز ٢٥ مهمة", "Finish 25 tasks", p.tasks, 25),
        b("t3", "قوة الإنجاز", "Powerhouse", "أنجز ١٠٠ مهمة", "Finish 100 tasks", p.tasks, 100),
        b("h1", "عادة تبدأ", "A habit begins", "سجّل عادة ٧ مرات", "Log habits 7 times", p.habits, 7),
        b("h2", "ثبات", "Consistency", "سجّل عادات ٦٠ مرة", "Log habits 60 times", p.habits, 60),
        b("s1", "ساعة تركيز", "One focused hour", "٦٠ دقيقة مذاكرة أو تركيز", "60 minutes of study or focus", p.studyMin, 60),
        b("s2", "طالب مجتهد", "Dedicated learner", "٦٠٠ دقيقة مذاكرة أو تركيز", "600 minutes of study or focus", p.studyMin, 600),
        b("q1", "قارئ", "Reader", "اقرأ ١٠ صفحات من القرآن", "Read 10 Quran pages", p.pages, 10),
        b("q2", "ورد منتظم", "Regular wird", "اقرأ ١٠٠ صفحة", "Read 100 Quran pages", p.pages, 100),
        b("q3", "ختمة", "Khatma", "أتمّ ٦٠٤ صفحات (مصحف المدينة)", "Read all 604 pages", p.pages, 604),
        b("b1", "مسبّح", "Tasbih", "٥٠٠ تسبيحة", "500 tasbih counts", p.tasbih, 500),
        b("b2", "مسبّح كثير", "Frequent tasbih", "٥٠٠٠ تسبيحة", "5000 tasbih counts", p.tasbih, 5000),
        b("p1", "على العهد", "Keeping it up", "سجّل ٢٥ صلاة في المتتبع", "Log 25 prayers in the tracker", p.prayers, 25),
        b("p2", "محافظ", "Steadfast", "سجّل ٢٥٠ صلاة", "Log 250 prayers", p.prayers, 250),
        b("l1", "المستوى ٥", "Level 5", "ابلغ المستوى ٥", "Reach level 5", lvl, 5)
    )
}

/** Days until 1 Ramadan (Umm al-Qura calendar, an astronomical estimate), or the current Ramadan day if it is Ramadan now. */
data class RamadanInfo(val daysUntil: Int, val ramadanDay: Int, val ramadanLength: Int, val startsOn: java.time.LocalDate)
fun ramadanInfo(today: java.time.LocalDate, adj: Int = 0): RamadanInfo {
    fun h(d: java.time.LocalDate) = java.time.chrono.HijrahDate.from(d.plusDays(adj.toLong()))
    val now = h(today)
    val month = now.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
    if (month == 9) {
        val day = now.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        return RamadanInfo(0, day, now.lengthOfMonth(), today.minusDays((day - 1).toLong()))
    }
    var d = today
    for (i in 1..420) {
        d = today.plusDays(i.toLong()); val x = h(d)
        if (x.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) == 9 && x.get(java.time.temporal.ChronoField.DAY_OF_MONTH) == 1) return RamadanInfo(i, 0, 0, d)
    }
    return RamadanInfo(-1, 0, 0, today)
}

data class Sug(val ar: String, val en: String, val route: String = "")
data class SugIn(val minNow: Int, val nextPrayerAr: String?, val nextPrayerEn: String?, val minsToPrayer: Int, val morningDone: Boolean, val eveningDone: Boolean, val sleepDone: Boolean,
                 val nextTask: String?, val tasksLeft: Int, val wakeMin: Int, val bedMin: Int, val studyToday: Int, val pagesToday: Int, val wirdGoal: Int, val habitsLeft: Int)
data class SugOut(val doNow: List<Sug>, val avoid: List<Sug>)

/** General time-management and wellbeing suggestions. They are not religious rulings. */
fun suggest(s: SugIn): SugOut {
    val d = ArrayList<Sug>(); val a = ArrayList<Sug>()
    val nearPrayer = s.nextPrayerAr != null && s.minsToPrayer in 0..25
    if (nearPrayer) d.add(Sug("استعد لصلاة ${s.nextPrayerAr} (بعد ${s.minsToPrayer} دقيقة)", "Get ready for ${s.nextPrayerEn} (in ${s.minsToPrayer} min)", "prayer"))
    if (!s.morningDone && s.minNow in s.wakeMin..719) d.add(Sug("أذكار الصباح", "Morning adhkar", "adhkar/morning"))
    if (!s.eveningDone && s.minNow in 900..1259) d.add(Sug("أذكار المساء", "Evening adhkar", "adhkar/evening"))
    if (!s.sleepDone && s.minNow >= s.bedMin - 90 && s.minNow < s.bedMin + 120) d.add(Sug("أذكار النوم", "Before-sleep adhkar", "adhkar/sleep"))
    if (s.nextTask != null) d.add(Sug("المهمة التالية: ${s.nextTask}", "Next task: ${s.nextTask}", "tasks"))
    if (s.pagesToday < s.wirdGoal && s.minNow in 300..1380) d.add(Sug("وردك اليوم: باقي ${s.wirdGoal - s.pagesToday} صفحة", "Today's wird: ${s.wirdGoal - s.pagesToday} page(s) left", "quran"))
    if (s.habitsLeft > 0) d.add(Sug("عندك ${s.habitsLeft} عادة لم تسجلها اليوم", "${s.habitsLeft} habit(s) not logged today", "habits"))
    if (d.isEmpty()) d.add(Sug("كل شيء تمام الآن. خذ استراحة قصيرة.", "All set for now. Take a short break.", ""))
    if (nearPrayer && s.minsToPrayer <= 15) a.add(Sug("لا تبدأ عملًا طويلًا الآن، الصلاة قريبة.", "Don't start a long task now, prayer is close.", ""))
    if (s.minNow >= s.bedMin - 60 || s.minNow < s.wakeMin - 120) a.add(Sug("ابتعد عن الشاشات والمنبهات قبل النوم، ولا تسهر بلا داعٍ.", "Avoid screens and stimulants close to bedtime, and don't stay up needlessly.", ""))
    if (s.studyToday >= 180) a.add(Sug("لا تُجهد نفسك: خذ استراحة قبل أن تواصل المذاكرة.", "Don't overdo it: rest before studying more.", ""))
    if (s.tasksLeft >= 6) a.add(Sug("لا تحاول إنجاز كل شيء اليوم. اختر ٣ مهام فقط.", "Don't try to do everything today. Pick just 3 tasks.", "tasks"))
    if (a.isEmpty()) a.add(Sug("لا تقارن يومك بيوم غيرك. التقدم الصغير يكفي.", "Don't compare your day with others. Small progress is enough.", ""))
    return SugOut(d.take(3), a.take(2))
}

// ---- v1.2: Islamic occasions and daily prayer log ----
data class Occasion(val key: String, val ar: String, val en: String, val date: java.time.LocalDate, val daysUntil: Int)
/** Next date (today included) whose Umm al-Qura Hijri month/day match. Estimates only: the official date depends on moon sighting. */
fun nextHijri(today: java.time.LocalDate, month: Int, day: Int, adj: Int = 0): java.time.LocalDate? {
    for (i in 0..400) {
        val d = today.plusDays(i.toLong()); val h = java.time.chrono.HijrahDate.from(d.plusDays(adj.toLong()))
        if (h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) == month && h.get(java.time.temporal.ChronoField.DAY_OF_MONTH) == day) return d
    }
    return null
}
fun occasions(today: java.time.LocalDate, adj: Int = 0): List<Occasion> {
    val defs = listOf(Triple("ramadan", 9 to 1, "بداية رمضان" to "Start of Ramadan"), Triple("fitr", 10 to 1, "عيد الفطر" to "Eid al-Fitr"),
        Triple("arafah", 12 to 9, "يوم عرفة" to "Day of Arafah"), Triple("adha", 12 to 10, "عيد الأضحى" to "Eid al-Adha"), Triple("newyear", 1 to 1, "رأس السنة الهجرية" to "Islamic new year"))
    return defs.mapNotNull { (k, md, n) -> nextHijri(today, md.first, md.second, adj)?.let { Occasion(k, n.first, n.second, it, java.time.temporal.ChronoUnit.DAYS.between(today, it).toInt()) } }.sortedBy { it.daysUntil }
}
val dailyPrayers = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
fun parsePrayerLog(csv: String): Set<String> = csv.split(",").filter { it in dailyPrayers }.toSet()
fun togglePrayerLog(csv: String, k: String): String { val s = parsePrayerLog(csv); return dailyPrayers.filter { if (it == k) it !in s else it in s }.joinToString(",") }
