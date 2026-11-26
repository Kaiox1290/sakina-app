package com.sakina.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.widget.RemoteViews
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime

// ================= Forty Hadith of an-Nawawi =================
data class NawawiItem(val n: Int, val ar: String, val en: String)
object Nawawi {
    @Volatile private var cache: List<NawawiItem>? = null
    fun load(c: Context): List<NawawiItem> = cache ?: synchronized(this) {
        cache ?: parse(c.assets.open("nawawi.json").bufferedReader().use { it.readText() }).also { cache = it }
    }
    fun parse(json: String): List<NawawiItem> { val a = JSONArray(json); return (0 until a.length()).map { a.getJSONObject(it).let { o -> NawawiItem(o.getInt("n"), o.getString("ar"), o.optString("en")) } } }
    const val SOURCE_AR = "الأربعون النووية للإمام النووي (نص الحديث كما في الكتاب، مع عزو كل حديث لمن رواه). الترجمة الإنجليزية من مشروع hadith-api المفتوح."
    const val SOURCE_EN = "Forty Hadith of Imam an-Nawawi (text as in the book, each with its narrators/sources). English translation from the open hadith-api project."
}

@Composable fun NawawiScreen(back: () -> Unit) {
    val c = LocalContext.current
    var items by remember { mutableStateOf<List<NawawiItem>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var open by remember { mutableIntStateOf(0) }
    var learned by remember { mutableStateOf((Settings.sp.getString("nawawi_learned", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()) }
    LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { Nawawi.load(c) } }
    val nq = normAr(q)
    LazyScreen(tr("الأربعون النووية", "Forty Hadith (Nawawi)"), back) {
        item { Sub(tr(Nawawi.SOURCE_AR, Nawawi.SOURCE_EN)); Sub(tr("المحفوظ: ${num(learned.size)} من ${num(items.size)}", "Learned: ${learned.size} of ${items.size}")); Field(q, { q = it }, tr("ابحث في الأحاديث", "Search hadith")) }
        items(items.filter { nq.isEmpty() || normAr(it.ar).contains(nq) || it.en.contains(q, true) }) { h ->
            Glass(Modifier.fillMaxWidth(), onClick = { open = if (open == h.n) 0 else h.n }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("الحديث ${num(h.n)}", "Hadith ${h.n}"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = GoldT)
                    if (h.n.toString() in learned) Text("✓", color = EmeraldT)
                }
                if (open == h.n) {
                    Text(h.ar, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Amiri, fontSize = 20.sp, lineHeight = 36.sp), modifier = Modifier.padding(top = 8.dp))
                    if (!Settings.isAr && h.en.isNotEmpty()) Body(h.en, Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        TextButton({
                            learned = if (h.n.toString() in learned) learned - h.n.toString() else learned + h.n.toString()
                            Settings.sp.edit().putString("nawawi_learned", learned.joinToString(",")).apply()
                        }) { Text(if (h.n.toString() in learned) tr("إلغاء علامة الحفظ", "Unmark learned") else tr("علّمته محفوظًا", "Mark as learned")) }
                        TextButton({ c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, h.ar + "\n\n" + tr("الأربعون النووية، الحديث ${h.n}", "Forty Hadith of an-Nawawi, no. ${h.n}")), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(tr("مشاركة", "Share")) }
                    }
                } else Text(h.ar.take(90) + "…", style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ================= Khatma plan =================
@Composable fun KhatmaScreen(go: Go, back: () -> Unit) {
    val dao = rememberDao(); val scope = rememberCoroutineScope(); val td = today()
    var start by remember { mutableIntStateOf(Settings.int("khatma_start", -1)) }
    var days by remember { mutableIntStateOf(Settings.int("khatma_days", 30)) }
    val read by dao.pagesRead(if (start < 0) td else start.toLong(), td).collectAsState(emptyList())
    val readCount = read.map { it.page }.toSet().size
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }
    Screen(tr("خطة الختمة", "Khatma plan"), back) {
        if (start < 0) {
            Glass(Modifier.fillMaxWidth()) {
                Body(tr("اختر مدة ختمتك. نقسم ٦٠٤ صفحة على الأيام، وتُحسب الصفحات من قراءتك داخل التطبيق.", "Pick how long your khatma takes. 604 pages are split across the days, counted from your reading in the app."))
                ChipRow(listOf(7, 15, 30, 60, 90).map { it.toString() to numS("$it ${tr("يوم", "d")}") }, days.toString()) { days = it.toInt() }
                Sub(tr("≈ ${num(Math.ceil(604.0 / days).toInt())} صفحة في اليوم", "≈ ${Math.ceil(604.0 / days).toInt()} pages a day"))
                Btn(tr("ابدأ الختمة اليوم", "Start today"), { start = td.toInt(); Settings.put("khatma_start", start); Settings.put("khatma_days", days) }, Modifier.fillMaxWidth())
            }
        } else {
            val p = khatmaPlan(start.toLong(), days, td, readCount)
            Glass(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Ring(p.read / 604f, 84, 8, label = numS("${p.read * 100 / 604}%"))
                    Column {
                        Title(tr("اليوم ${num(p.dayIndex + 1)} من ${num(p.days)}", "Day ${p.dayIndex + 1} of ${p.days}"))
                        Body(tr("قرأت ${num(p.read)} من ٦٠٤ صفحة", "${p.read} of 604 pages read"))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Body(when {
                    p.finished -> tr("ما شاء الله! أتممت الختمة. تقبّل الله منك.", "Mashallah! You completed the khatma. May Allah accept it.")
                    p.overdue -> tr("تجاوزنا المدة التي اخترتها، ولا بأس. الباقي ${num(p.remaining)} صفحة. تقدر تبدأ خطة جديدة بوقت أطول.", "The period you chose has passed, and that's okay. ${p.remaining} pages remain. You can restart with a longer plan.")
                    p.behindBy == 0 -> tr("أنت على الخطة اليوم. حتى صفحة ${num(p.targetEnd)}.", "You're on track. Aim for page ${p.targetEnd}.")
                    else -> tr("هدف اليوم: حتى صفحة ${num(p.targetEnd)}. باقي ${num(p.behindBy)} صفحة لتلحق الخطة، أو وزّع الباقي ${num(p.catchUpPerDay)} صفحة يوميًا.", "Today's target: up to page ${p.targetEnd}. ${p.behindBy} pages to catch up, or spread the rest at ${p.catchUpPerDay} pages a day.")
                })
                if (p.aheadBy > 0) Sub(tr("متقدم عن الخطة بـ${num(p.aheadBy)} صفحة.", "${p.aheadBy} pages ahead of plan."))
                Spacer(Modifier.height(8.dp))
                Btn(tr("تابع القراءة", "Continue reading"), { go("quran/${Settings.int("last_s", 1).coerceAtLeast(1)}?a=${Settings.int("last_a", 1).coerceAtLeast(1)}") }, Modifier.fillMaxWidth())
            }
            Glass(Modifier.fillMaxWidth()) {
                Text(tr("قرأت من المصحف الورقي؟", "Read from a paper mushaf?"), style = MaterialTheme.typography.titleSmall)
                Sub(tr("سجّل الصفحات من - إلى لتُحسب في الختمة.", "Enter pages from - to to count them."))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Field(from, { from = it.filter(Char::isDigit) }, tr("من", "From"), Modifier.weight(1f), number = true); Field(to, { to = it.filter(Char::isDigit) }, tr("إلى", "To"), Modifier.weight(1f), number = true) }
                Btn(tr("سجّل", "Log pages"), {
                    val a = from.toIntOrNull(); val b = to.toIntOrNull()
                    if (a != null && b != null && a in 1..604 && b in a..604) { io(scope) { (a..b).forEach { dao.addPageRead(PageRead(td, it)) } }; from = ""; to = "" }
                }, Modifier.fillMaxWidth(), outlined = true)
            }
            TextButton({ Settings.put("khatma_start", -1); start = -1 }) { Text(tr("ابدأ خطة جديدة", "Start a new plan")) }
        }
    }
}

// ================= Points, levels, badges =================
suspend fun loadPoints(dao: AppDao) = PointsIn(dao.cntAdhkar(), dao.cntTasks(), dao.cntHabits(), dao.sumStudy(), dao.cntPages(), dao.sumTasbih(), prayerLogTotal())
fun prayerLogTotal(): Int = Settings.sp.all.entries.filter { it.key.startsWith("pl_") }.sumOf { parsePrayerLog(it.value as? String ?: "").size }

private val levelNamesAr = listOf("بذرة", "برعم", "غصن", "شجرة", "ثمرة", "نخلة", "واحة", "نور", "سكينة", "فردوس")
private val levelNamesEn = listOf("Seed", "Sprout", "Branch", "Tree", "Fruit", "Palm", "Oasis", "Light", "Serenity", "Firdaws")

@Composable fun PointsCard(go: Go) {
    val dao = rememberDao(); var p by remember { mutableStateOf<PointsIn?>(null) }
    LaunchedEffect(Unit) { p = withContext(Dispatchers.IO) { runCatching { loadPoints(dao) }.getOrNull() } }
    val pi = p ?: return
    val pts = pointsOf(pi); val (lvl, lo, hi) = levelOf(pts)
    val name = tr(levelNamesAr[(lvl - 1).coerceAtMost(9)], levelNamesEn[(lvl - 1).coerceAtMost(9)])
    Glass(Modifier.fillMaxWidth(), onClick = { go("levels") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(tr("المستوى ${num(lvl)} · $name", "Level $lvl · $name"), style = MaterialTheme.typography.titleSmall, color = GoldT); Sub(tr("${num(pts)} نقطة", "$pts points")) }
            Text(numS("${badgesOf(pi).count { it.unlocked }}/${badgesOf(pi).size}") + " 🏅")
        }
        LinearProgressIndicator(progress = { ((pts - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 8.dp), color = GoldT)
    }
}

@Composable fun LevelsScreen(back: () -> Unit) {
    val dao = rememberDao(); var p by remember { mutableStateOf<PointsIn?>(null) }
    LaunchedEffect(Unit) { p = withContext(Dispatchers.IO) { runCatching { loadPoints(dao) }.getOrNull() } }
    val pi = p ?: PointsIn()
    val pts = pointsOf(pi); val (lvl, lo, hi) = levelOf(pts); val badges = badgesOf(pi)
    val name = Settings.str("name")
    Screen(tr("الإنجازات والمستويات", "Achievements & levels"), back) {
        Glass(Modifier.fillMaxWidth()) {
            if (name.isNotBlank()) Sub(tr("ما شاء الله يا $name", "Well done, $name"))
            Title(tr("المستوى ${num(lvl)} · ${levelNamesAr[(lvl - 1).coerceAtMost(9)]}", "Level $lvl · ${levelNamesEn[(lvl - 1).coerceAtMost(9)]}"))
            Body(tr("${num(pts)} نقطة. الباقي ${num(hi - pts)} للمستوى التالي.", "$pts points. ${hi - pts} to the next level."))
            LinearProgressIndicator(progress = { ((pts - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 8.dp), color = GoldT)
            Sub(tr("النقاط تزيد ولا تنقص أبدًا. لا عقوبة على يوم فاتك.", "Points only go up. No penalty for a missed day."), Modifier.padding(top = 6.dp))
        }
        Glass(Modifier.fillMaxWidth()) {
            Text(tr("كيف تُحسب النقاط", "How points work"), style = MaterialTheme.typography.titleSmall)
            Sub(tr("ذكر مكتمل ٥ · مهمة منجزة ٣ · عادة مسجلة ٢ · كل ١٠ دقائق مذاكرة ١ · صفحة قرآن ١ · كل ١٠٠ تسبيحة ١", "Completed dhikr 5 · finished task 3 · logged habit 2 · each 10 study min 1 · Quran page 1 · each 100 tasbih 1"))
        }
        SectionLabel(tr("الأوسمة (${num(badges.count { it.unlocked })}/${num(badges.size)})", "Badges (${badges.count { it.unlocked }}/${badges.size})"))
        badges.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    Glass(Modifier.weight(1f), pad = 12) {
                        Text((if (b.unlocked) "🏅 " else "🔒 ") + tr(b.ar, b.en), style = MaterialTheme.typography.titleSmall, color = if (b.unlocked) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                        Sub(tr(b.descAr, b.descEn))
                        if (!b.unlocked) { LinearProgressIndicator(progress = { b.have.toFloat() / b.need }, Modifier.fillMaxWidth().padding(top = 4.dp), color = EmeraldT); Sub(numS("${b.have}/${b.need}")) }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ================= Ramadan countdown =================
@Composable fun SkyEffect(modifier: Modifier = Modifier.fillMaxWidth().height(110.dp)) {
    val t by rememberInfiniteTransition(label = "sky").animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "t")
    val sway by rememberInfiniteTransition(label = "sway").animateFloat(-1f, 1f, infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val stars = remember { val r = java.util.Random(7); List(26) { Triple(r.nextFloat(), r.nextFloat() * 0.8f, r.nextFloat()) } }
    Canvas(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xFF0B1F18))) {
        stars.forEach { (x, y, ph) ->
            val a = 0.25f + 0.75f * Math.abs(Math.sin(2 * Math.PI * (t + ph))).toFloat()
            drawCircle(Color(0xFFFFF3C4).copy(alpha = a), radius = 1.5f + 2.5f * ph, center = Offset(x * size.width, y * size.height))
        }
        val c = Offset(size.width * 0.5f, size.height * 0.5f + sway * 4f); val r = size.height * 0.34f
        drawCircle(Gold.copy(alpha = 0.10f + 0.06f * (sway + 1)), radius = r * 1.9f, center = c)
        val moon = Path.combine(PathOperation.Difference, Path().apply { addOval(Rect(c, r)) }, Path().apply { addOval(Rect(Offset(c.x + r * 0.45f, c.y - r * 0.1f), r * 0.85f)) })
        drawPath(moon, Gold)
    }
}

@Composable fun RamadanCard(go: Go) {
    val info = remember(today()) { ramadanInfo(LocalDate.now(), Settings.sp.getInt("hijri_adj", 0)) }
    if (info.daysUntil < 0) return
    Glass(Modifier.fillMaxWidth(), onClick = { go("ramadan") }) {
        SkyEffect(Modifier.fillMaxWidth().height(80.dp))
        if (info.daysUntil == 0) Text(tr("رمضان مبارك · اليوم ${num(info.ramadanDay)}", "Ramadan Mubarak · day ${info.ramadanDay}"), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = GoldT, textAlign = TextAlign.Center)
        else Text(tr("باقي ≈${num(info.daysUntil)} يوم على رمضان (تقدير)", "≈${info.daysUntil} days to Ramadan (estimate)"), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = GoldT, textAlign = TextAlign.Center)
    }
}

@Composable fun RamadanScreen(back: () -> Unit) {
    val adj = Settings.sp.getInt("hijri_adj", 0)
    val info = remember { ramadanInfo(LocalDate.now(), adj) }
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pp")
    Screen(tr("رمضان", "Ramadan"), back) {
        Glass(Modifier.fillMaxWidth()) {
            SkyEffect(Modifier.fillMaxWidth().height(170.dp))
            if (info.daysUntil == 0) {
                Text(tr("رمضان مبارك", "Ramadan Mubarak"), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, color = GoldT, textAlign = TextAlign.Center)
                Text(tr("اليوم ${num(info.ramadanDay)} من ${num(info.ramadanLength)}", "Day ${info.ramadanDay} of ${info.ramadanLength}"), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                LinearProgressIndicator(progress = { info.ramadanDay.toFloat() / info.ramadanLength.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(top = 10.dp), color = GoldT)
            } else if (info.daysUntil > 0) {
                Text(numS("${info.daysUntil}"), Modifier.fillMaxWidth(), style = MaterialTheme.typography.displayLarge.copy(fontSize = (64 * pulse).sp), color = GoldT, textAlign = TextAlign.Center)
                Text(tr("يوم تقريبًا على رمضان (تقدير)", "days until Ramadan (estimate)"), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Sub(tr("المتوقع بداية رمضان: ${gregText(info.startsOn)}", "Expected start: ${gregText(info.startsOn)}"), Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
        OccasionsCard()
        Glass(Modifier.fillMaxWidth()) {
            Sub(tr("هذا تقدير فلكي (تقويم أم القرى) ويتوافق مع توقع المعهد القومي للبحوث الفلكية في مصر: الاثنين ٨ فبراير ٢٠٢٧. الموعد الرسمي تعلنه دار الإفتاء المصرية بعد رؤية الهلال، وقد يختلف يومًا. تقدر تعدّل التقويم الهجري من الإعدادات.", "This is an astronomical estimate (Umm al-Qura). It matches the expectation of Egypt's National Research Institute of Astronomy and Geophysics: Monday, Feb 8, 2027. The official start is announced by Egypt's Dar al-Ifta after moon sighting and may differ by a day. You can adjust the Hijri calendar in Settings."))
        }
    }
}

// ================= What to do now / what to avoid =================
@Composable fun SuggestCard(go: Go) {
    val c = LocalContext.current; val dao = rememberDao(); val td = today()
    val tasks by dao.tasks().collectAsState(emptyList()); val done by dao.taskDone(td, td).collectAsState(emptyList())
    val adh by dao.adhkarProg(td).collectAsState(emptyList()); val pages by dao.pagesRead(td, td).collectAsState(emptyList())
    val sessions by dao.sessions(td, td).collectAsState(emptyList()); val habits by dao.habits().collectAsState(emptyList()); val hl by dao.habitLogs(td, td).collectAsState(emptyList())
    val now = LocalTime.now(); val minNow = now.hour * 60 + now.minute
    val next = remember(minNow) { NextPrayerWidget.next(System.currentTimeMillis()) }
    val tot = remember { listOf("morning", "evening", "sleep").associateWith { Adhkar.items(c, Adhkar.group(it)).size } }
    fun gd(k: String) = adh.count { it.done && it.key.startsWith("$k|") } >= (tot[k] ?: 1)
    val ids = done.map { it.taskId }.toSet()
    val left = tasks.on(td).filter { it.id !in ids }
    val nt = left.firstOrNull { it.startMin < 0 || it.startMin >= minNow - 30 }
    val out = remember(minNow, left.size, adh.size, pages.size, sessions.size, hl.size, habits.size) {
        suggest(SugIn(minNow, next?.let { prayerName(it.first) }, next?.first, next?.let { ((it.second - System.currentTimeMillis()) / 60000).toInt() } ?: 999,
            gd("morning"), gd("evening"), gd("sleep"), nt?.title, left.size, Settings.sp.getInt("wake_t", 360), Settings.sp.getInt("bed_t", 1380),
            sessions.sumOf { it.minutes }, pages.size, Settings.sp.getInt("wird_pages", 2), habits.count { it.active } - hl.size))
    }
    Glass(Modifier.fillMaxWidth()) {
        Text(tr("افعل الآن", "Do now"), style = MaterialTheme.typography.titleSmall, color = EmeraldT)
        out.doNow.forEach { s -> Text("• " + tr(s.ar, s.en), Modifier.fillMaxWidth().clickable(enabled = s.route.isNotEmpty()) { go(s.route) }.padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(6.dp))
        Text(tr("تجنّب الآن", "Better to avoid"), style = MaterialTheme.typography.titleSmall, color = GoldT)
        out.avoid.forEach { s -> Text("• " + tr(s.ar, s.en), Modifier.fillMaxWidth().padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium) }
        Sub(tr("اقتراحات عامة للوقت والصحة، وليست فتوى.", "General time and wellbeing tips, not a religious ruling."))
    }
}

// ================= Privacy policy =================
@Composable fun PrivacyScreen(back: () -> Unit) {
    Screen(tr("سياسة الخصوصية", "Privacy policy"), back) {
        Glass(Modifier.fillMaxWidth()) {
            Body(tr(PRIVACY_AR, PRIVACY_EN))
        }
    }
}
const val PRIVACY_AR = "سَكِينَة تطبيق يعمل على جهازك. نحترم خصوصيتك:\n\n• كل بياناتك (مهام، عادات، تقدم، إعدادات) محفوظة على جهازك فقط. لا حسابات ولا خوادم ولا إعلانات ولا تحليلات ولا تتبّع.\n• الموقع: نستخدم موقعًا تقريبيًا مرة عند طلبك لحساب مواقيت الصلاة والقبلة، ويُحفظ على جهازك فقط، ولا يُرسل لأي جهة.\n• الإنترنت: يُستخدم فقط لتشغيل تلاوة الآيات من everyayah.com ولمساعد رفيق إن فعّلته بمفتاحك. عند استخدام رفيق يُرسل نص رسالتك للمزوّد الذي اخترته.\n• مفتاح رفيق يُخزَّن مشفرًا على جهازك.\n• الإشعارات والمنبهات: لتذكيرك بالصلاة والأذكار والمهام فقط.\n• النسخ الاحتياطي: ملف تحفظه أنت في المكان الذي تختاره. النسخ التلقائي يكتب في مجلد تحدده أنت فقط، ولا يوجد نقل سحابي تلقائي من التطبيق. إحداثيات موقعك لا تُضمَّن في النسخ إلا إذا فعّلت ذلك بنفسك، ومفتاح رفيق لا يُضمَّن أبدًا. إيقاف النسخ الاحتياطي التلقائي من نظام أندرويد مُعطّل لهذا التطبيق.\n• الحذف: من الإعدادات يمكنك حذف كل بياناتك في أي وقت، وحذف التطبيق يمسح كل شيء.\n• المحتوى الديني من مصادر موثوقة معلنة، ولا يقدّم التطبيق فتاوى.\n\nإن غيّرنا هذه السياسة سنعلنها في التطبيق."
const val PRIVACY_EN = "Sakina runs on your device. We respect your privacy:\n\n• All your data (tasks, habits, progress, settings) stays on your device. No accounts, servers, ads, analytics or tracking.\n• Location: approximate location is used once, when you ask, to calculate prayer times and Qibla. It stays on your device and is not sent anywhere.\n• Internet: only used to stream Quran recitation from everyayah.com and for the Rafiq assistant if you enable it with your own key. When you use Rafiq, your message text is sent to the provider you chose.\n• The Rafiq key is stored encrypted on your device.\n• Notifications and alarms: only to remind you of prayers, adhkar and tasks.\n• Backups: a file you save where you choose. Auto backup writes only to a folder you pick, and the app does no automatic cloud transfer. Your location coordinates are left out of backups unless you turn that on, and the Rafiq key is never included. Android system cloud backup is disabled for this app.\n• Deletion: you can delete all your data from Settings at any time, and uninstalling removes everything.\n• Religious content comes from stated trusted sources, and the app issues no fatwas.\n\nIf this policy changes, we will announce it in the app."

// ================= Home screen widget =================
class NextPrayerWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { update(c) }
    override fun onReceive(c: Context, i: Intent) { super.onReceive(c, i); if (i.action == TICK) update(c) }
    companion object {
        const val TICK = "com.sakina.app.WIDGET_TICK"
        fun next(now: Long): Pair<String, Long>? {
            val d0 = java.time.Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
            for (off in 0..1) {
                val d = d0.plusDays(off.toLong()); val tm = PrayerSettings.timesFor(d)
                for (k in listOf("fajr", "dhuhr", "asr", "maghrib", "isha")) { val ms = PrayerSettings.millis(d, tm, k); if (ms > now) return k to ms }
            }
            return null
        }
        fun update(c: Context) {
            try {
                Settings.init(c)
                val mgr = AppWidgetManager.getInstance(c)
                val ids = mgr.getAppWidgetIds(ComponentName(c, NextPrayerWidget::class.java))
                if (ids.isEmpty()) return
                val now = System.currentTimeMillis(); val n = next(now)
                val rv = RemoteViews(c.packageName, R.layout.widget_next_prayer)
                rv.setTextViewText(R.id.w_label, tr("الصلاة القادمة", "Next prayer"))
                if (n != null) {
                    rv.setTextViewText(R.id.w_name, prayerName(n.first)); rv.setTextViewText(R.id.w_time, fmtTimeMs(n.second))
                    rv.setChronometerCountDown(R.id.w_chrono, true)
                    rv.setChronometer(R.id.w_chrono, SystemClock.elapsedRealtime() + (n.second - now), null, true)
                    val am = c.getSystemService(android.app.AlarmManager::class.java)
                    val tick = android.app.PendingIntent.getBroadcast(c, 7701, Intent(c, NextPrayerWidget::class.java).setAction(TICK), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                    am.set(android.app.AlarmManager.RTC, n.second + 2000, tick)
                }
                val open = android.app.PendingIntent.getActivity(c, 7702, Intent(c, MainActivity::class.java).putExtra("route", "prayer"), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                rv.setOnClickPendingIntent(R.id.w_root, open)
                ids.forEach { mgr.updateAppWidget(it, rv) }
            } catch (e: Exception) { }
        }
    }
}

// ================= Auto backup to a folder (works with Google Drive when the Drive app is installed) =================
object AutoBackup {
    /** Returns null on success, or a short error. */
    fun run(c: Context): String? {
        Settings.init(c)
        val s = Settings.sp.getString("ab_uri", "") ?: ""
        if (s.isEmpty()) return tr("لم يُختر مجلد", "No folder chosen")
        return try {
            val tree = Uri.parse(s); val docId = DocumentsContract.getTreeDocumentId(tree)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
            val doc = DocumentsContract.createDocument(c.contentResolver, parent, "application/json", "sakina-auto-$stamp.json") ?: return tr("تعذّر إنشاء الملف", "Could not create the file")
            c.contentResolver.openOutputStream(doc)!!.use { it.write(Backup.export(c).toByteArray()) }
            val newId = DocumentsContract.getDocumentId(doc)
            val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            c.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
                val old = ArrayList<String>()
                while (cur.moveToNext()) if (cur.getString(1).startsWith("sakina-auto-") && cur.getString(0) != newId) old.add(cur.getString(0))
                old.drop(0).forEach { runCatching { DocumentsContract.deleteDocument(c.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, it)) } }
            }
            Settings.sp.edit().putLong("ab_last", System.currentTimeMillis()).apply(); null
        } catch (e: Exception) { e.message ?: "error" }
    }
}

class AutoBackupWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        Settings.init(applicationContext)
        val sp = Settings.sp
        if (!sp.getBoolean("ab_on", false)) return Result.success()
        val gap = sp.getInt("ab_days", 1) * 24L * 3600_000L - 3600_000L
        if (System.currentTimeMillis() - sp.getLong("ab_last", 0L) >= gap) AutoBackup.run(applicationContext)
        return Result.success()
    }
}

@Composable fun AutoBackupCard() {
    val c = LocalContext.current; val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(Settings.sp.getBoolean("ab_on", false)) }
    var days by remember { mutableIntStateOf(Settings.sp.getInt("ab_days", 1)) }
    var uri by remember { mutableStateOf(Settings.sp.getString("ab_uri", "") ?: "") }
    var msg by remember { mutableStateOf("") }
    var last by remember { mutableLongStateOf(Settings.sp.getLong("ab_last", 0L)) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            runCatching { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            uri = u.toString(); Settings.sp.edit().putString("ab_uri", uri).apply()
        }
    }
    Glass(Modifier.fillMaxWidth()) {
        Text(tr("نسخ احتياطي تلقائي", "Automatic backup"), style = MaterialTheme.typography.titleSmall)
        Sub(tr("اختر مجلدًا ليُحفظ فيه ملف النسخة تلقائيًا. لو اخترت مجلدًا من Google Drive (يلزم تطبيق Drive على الهاتف) فستصل النسخة لدرايفك. نحتفظ بآخر نسخة فقط.", "Pick a folder and a backup file is saved there automatically. Choose a Google Drive folder (needs the Drive app installed) to send it to your Drive. Only the latest copy is kept."))
        RowSwitch(tr("تضمين الموقع في النسخ", "Include location in backups"), Settings.bool("backup_location", false), { Settings.put("backup_location", it) }, tr("افتراضيًا لا تُحفظ إحداثيات موقعك في أي نسخة احتياطية.", "By default your coordinates are not saved in any backup."))
        RowSwitch(tr("تفعيل", "Enable"), on, { on = it; Settings.sp.edit().putBoolean("ab_on", it).apply() })
        ChipRow(listOf("1" to tr("يوميًا", "Daily"), "7" to tr("أسبوعيًا", "Weekly")), days.toString()) { days = it.toInt(); Settings.sp.edit().putInt("ab_days", days).apply() }
        Btn(if (uri.isEmpty()) tr("اختيار المجلد", "Choose folder") else tr("تغيير المجلد", "Change folder"), { pick.launch(null) }, Modifier.fillMaxWidth(), outlined = true)
        if (uri.isNotEmpty()) Btn(tr("انسخ الآن", "Back up now"), { io(scope) { val r = AutoBackup.run(c); msg = r ?: tr("تم الحفظ", "Saved"); last = Settings.sp.getLong("ab_last", 0L) } }, Modifier.fillMaxWidth())
        if (last > 0) Sub(tr("آخر نسخة: ", "Last backup: ") + fmtTimeMs(last) + " · " + gregText(java.time.Instant.ofEpochMilli(last).atZone(zone()).toLocalDate()))
        if (msg.isNotEmpty()) Body(msg)
    }
}

@Composable fun PrayerLogCard() {
    val td = today(); val key = "pl_$td"
    var csv by remember { mutableStateOf(Settings.sp.getString(key, "") ?: "") }
    val done = parsePrayerLog(csv)
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("صلواتي اليوم", "My prayers today"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(numS("${done.size}/5"), color = GoldT)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dailyPrayers.forEach { k -> Box(Modifier.weight(1f)) { Chip(prayerName(k), k in done) { csv = togglePrayerLog(csv, k); Settings.sp.edit().putString(key, csv).apply() } } }
        }
        Sub(tr("للمتابعة الشخصية فقط، لا محاسبة ولا عدّاد خسارة.", "For your own tracking only. No judgment, no penalty."))
    }
}

@Composable fun OccasionsCard() {
    val list = remember(today()) { occasions(LocalDate.now(), Settings.sp.getInt("hijri_adj", 0)) }
    Glass(Modifier.fillMaxWidth()) {
        Text(tr("المناسبات الإسلامية القادمة (تقدير)", "Upcoming Islamic dates (estimate)"), style = MaterialTheme.typography.titleSmall, color = GoldT)
        list.forEach { o -> Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(tr(o.ar, o.en), Modifier.weight(1f))
            Text(if (o.daysUntil == 0) tr("اليوم", "Today") else numS("${o.daysUntil} ${tr("يوم", "d")}") + " · " + gregText(o.date), style = MaterialTheme.typography.bodyMedium)
        } }
        Sub(tr("تقويم أم القرى. الموعد الرسمي يُعلن برؤية الهلال وقد يختلف يومًا.", "Umm al-Qura calendar. Official dates follow moon sighting and may differ by a day."))
    }
}
