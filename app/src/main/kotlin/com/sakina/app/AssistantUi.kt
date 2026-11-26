package com.sakina.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class Msg(val mine: Boolean, val text: String)

private val rulingWords = listOf("حكم", "حلال", "حرام", "فتوى", "افتي", "يجوز", "هل يجوز", "مكروه", "بدعة", "تكفير", "halal", "haram", "fatwa", "ruling", "permissible", "is it allowed", "is it sinful")
fun looksLikeRuling(q: String): Boolean { val n = q.lowercase(); return rulingWords.any { n.contains(it) } }
fun rulingReply(): String = tr(
    "هذا سؤال شرعي، ولا أصدر فتاوى ولا أستنتج أحكامًا من عندي. اسأل عالمًا موثوقًا أو راجع دار الإفتاء المصرية (dar-alifta.org) أو موقع إسلام ويب (islamweb.net)، وأخبرني إن أحببت أن أضع لك سؤالك في مهام لتسأل عنه.",
    "That is a religious ruling question. I do not issue fatwas or invent rulings. Please ask a trusted scholar or check Egypt's Dar al-Ifta (dar-alifta.org) or IslamWeb (islamweb.net). I can add a task to remind you to ask."
)

object Rafiq {
    suspend fun weekly(c: android.content.Context): String {
        val dao = AppDb.get(c).dao(); val td = today(); val from = td - 6
        val tasks = dao.tasksNow(); val done = dao.taskDone(from, td).first(); val adh = dao.adhkarRange(from, td).first(); val pages = dao.pagesRead(from, td).first(); val sess = dao.sessions(from, td).first(); val hab = dao.habitLogs(from, td).first()
        val occ = (from..td).sumOf { d -> tasks.on(d).size }; val dn = (from..td).sumOf { d -> tasks.on(d).count { t -> done.any { it.taskId == t.id && it.day == d } } }
        val study = sess.filter { it.kind != "learn" }.sumOf { it.minutes }; val learn = sess.filter { it.kind == "learn" }.sumOf { it.minutes }
        return tr("ملخص أسبوعك:\n• مهام مكتملة: ${num(dn)} من ${num(occ)}\n• أذكار مكتملة: ${num(adh.count { it.done })}\n• صفحات قرآن: ${num(pages.size)}\n• مذاكرة: ${numS(fmtDur(study))}\n• تعلم برمجة: ${numS(fmtDur(learn))}\n• عادات: ${num(hab.size)}\nبارك الله في جهدك. اختر شيئًا صغيرًا واحدًا لتحسّنه الأسبوع القادم.",
            "Your week:\n• Tasks done: $dn of $occ\n• Adhkar completed: ${adh.count { it.done }}\n• Quran pages: ${pages.size}\n• Study: ${fmtDur(study)}\n• Coding: ${fmtDur(learn)}\n• Habits: ${hab.size}\nWell done. Pick one small thing to improve next week.")
    }
    suspend fun habitsTips(c: android.content.Context): String {
        val dao = AppDb.get(c).dao(); val td = today(); val hs = dao.habitsNow().filter { it.active }; val logs = dao.habitLogs(td - 13, td).first()
        if (hs.isEmpty()) return tr("ما عندك عادات بعد. ابدأ بعادة واحدة صغيرة (دقيقتان) واربطها بشيء تفعله يوميًا كالصلاة.", "You have no habits yet. Start with one tiny habit (2 minutes) tied to something you already do daily, like a prayer.")
        val sb = StringBuilder()
        for (h in hs) { val n = logs.count { it.habitId == h.id }
            sb.append("• ${h.name}: ${numS("$n/14")} ").append(if (n >= 10) tr("ممتاز، حافظ عليها.\n", "great, keep it.\n") else if (n >= 5) tr("جيدة، جرّب تذكيرًا ثابتًا.\n", "good, try a fixed reminder.\n") else tr("اجعلها أصغر وأسهل، وربطها بصلاة.\n", "make it smaller and tie it to a prayer.\n")) }
        return sb.toString()
    }
    suspend fun todayPlan(c: android.content.Context): String {
        val dao = AppDb.get(c).dao(); val td = today(); val ts = dao.tasksNow().on(td); val done = dao.taskDoneOn(td).map { it.taskId }.toSet()
        val left = ts.filter { it.id !in done }
        val next = PrayerSettings.next()
        return if (ts.isEmpty()) tr("لا توجد مهام اليوم. تقدر تبني جدولًا من \"مساعد الوقت\". الصلاة القادمة: ${prayerName(next.first)} ${numS(fmtTimeMs(next.second))}.", "No tasks today. Build a schedule from the Planner. Next prayer: ${prayerName(next.first)} ${fmtTimeMs(next.second)}.")
        else tr("لديك ${num(left.size)} مهمة متبقية من ${num(ts.size)}:\n" + left.take(6).joinToString("\n") { "• " + (if (it.startMin >= 0) numS(fmtMin(it.startMin)) + " " else "") + it.title } + "\nالصلاة القادمة: ${prayerName(next.first)} ${numS(fmtTimeMs(next.second))}.",
            "${left.size} of ${ts.size} tasks left:\n" + left.take(6).joinToString("\n") { "• " + (if (it.startMin >= 0) fmtMin(it.startMin) + " " else "") + it.title } + "\nNext prayer: ${prayerName(next.first)} ${fmtTimeMs(next.second)}.")
    }

    fun key(c: android.content.Context): String? = runCatching {
        val mk = androidx.security.crypto.MasterKey.Builder(c).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
        androidx.security.crypto.EncryptedSharedPreferences.create(c, "sakina_secure", mk, androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM).getString("ai_key", null)
    }.getOrNull()
    fun saveKey(c: android.content.Context, k: String) { runCatching {
        val mk = androidx.security.crypto.MasterKey.Builder(c).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
        androidx.security.crypto.EncryptedSharedPreferences.create(c, "sakina_secure", mk, androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM).edit().putString("ai_key", k).apply() } }

    /** Optional online answer. Only the typed question is sent. */
    suspend fun online(c: android.content.Context, q: String): String = withContext(Dispatchers.IO) {
        val k = key(c) ?: return@withContext tr("أضف مفتاح API في الإعدادات.", "Add an API key in Settings.")
        val url = Settings.sp.getString("ai_url", "https://api.openai.com/v1/chat/completions")!!
        if (!url.startsWith("https://")) return@withContext tr("العنوان يجب أن يبدأ بـ https", "The endpoint must start with https")
        val sys = tr("أنت رفيق، مساعد تنظيم الوقت والدراسة والعادات. أجب باختصار وبالعربية. لا تصدر فتاوى ولا تنسب أحاديث أو أحكامًا شرعية؛ إن سُئلت في الأحكام فوجّه المستخدم لعالم موثوق أو دار الإفتاء.", "You are Rafiq, an assistant for time management, study and habits. Be concise. Never issue religious rulings or attribute hadith; for such questions direct the user to a trusted scholar.")
        try {
            val body = JSONObject().put("model", Settings.sp.getString("ai_model", "gpt-4o-mini")).put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", sys)).put(JSONObject().put("role", "user").put("content", q)))
            val con = URL(url).openConnection() as HttpURLConnection
            con.requestMethod = "POST"; con.connectTimeout = 15000; con.readTimeout = 40000; con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json"); con.setRequestProperty("Authorization", "Bearer $k")
            con.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = con.responseCode
            val txt = (if (code in 200..299) con.inputStream else con.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) return@withContext tr("تعذّر الاتصال بالخدمة (رمز $code). تحقق من المفتاح والإنترنت.", "The service returned an error ($code). Check your key and connection.")
            JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) { tr("لا يوجد اتصال بالإنترنت أو حدث خطأ.", "No internet connection or an error occurred.") }
    }
}

@Composable fun AssistantScreen(back: () -> Unit) {
    val c = LocalContext.current; val scope = rememberCoroutineScope(); val dao = rememberDao()
    val msgs = remember { mutableStateListOf(Msg(false, tr("أنا رفيق. أساعدك في تنظيم يومك وعاداتك ودراستك. اختر من الأزرار أو اكتب سؤالك.", "I'm Rafiq. I help organise your day, habits and study. Tap a button or type.\nI work offline for the basics.")) ) }
    var input by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf("") }; var weeks by remember { mutableStateOf("4") }; var per by remember { mutableStateOf("3") }
    val state = rememberLazyListState()
    LaunchedEffect(msgs.size) { state.animateScrollToItem((msgs.size - 1).coerceAtLeast(0)) }
    fun ask(label: String, f: suspend () -> String) { msgs.add(Msg(true, label)); busy = true; scope.launch { val r = withContext(Dispatchers.IO) { f() }; msgs.add(Msg(false, r)); busy = false } }
    fun send() {
        val q = input.trim(); if (q.isEmpty() || busy) return; input = ""
        if (looksLikeRuling(q)) { msgs.add(Msg(true, q)); msgs.add(Msg(false, rulingReply())); return }
        if (Settings.sp.getBoolean("ai_on", false) && Rafiq.key(c) != null) ask(q) { Rafiq.online(c, q) }
        else { msgs.add(Msg(true, q)); msgs.add(Msg(false, tr("بدون الذكاء الاصطناعي الاختياري أجيب فقط عن الأزرار أعلاه. لتفعيل الأسئلة العامة، أضف مفتاحك في الإعدادات > المساعد.", "Without the optional online AI I only handle the buttons above. Enable it in Settings > Assistant for general questions."))) }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(back) { Text(tr("رجوع", "Back")) }; Title(tr("رفيق", "Rafiq")) }
        ChipRow(listOf("t" to tr("خطة اليوم", "Today"), "w" to tr("ملخص الأسبوع", "Weekly summary"), "h" to tr("تحسين العادات", "Habit tips"), "s" to tr("قسّم هدفًا", "Split a goal")), "") { k ->
            when (k) { "t" -> ask(tr("خطة اليوم", "Today")) { Rafiq.todayPlan(c) }; "w" -> ask(tr("ملخص الأسبوع", "Weekly summary")) { Rafiq.weekly(c) }; "h" -> ask(tr("تحسين العادات", "Habit tips")) { Rafiq.habitsTips(c) }; else -> split = true }
        }
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                    Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(if (m.mine) Emerald.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) { Text(m.text, color = if (m.mine) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface) }
                }
            }
            if (busy) item { Sub(tr("رفيق يكتب…", "Rafiq is typing…")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(tr("اكتب سؤالك", "Type a question")) }, shape = RoundedCornerShape(16.dp), maxLines = 3)
            Btn(tr("إرسال", "Send"), { send() }, enabled = !busy)
        }
    }
    if (split) FormDialog(tr("تقسيم هدف كبير", "Split a big goal"), { split = false }, tr("أنشئ المهام", "Create tasks"), onSave = {
        val w = weeks.toIntOrNull()?.coerceIn(1, 12) ?: 4; val p = per.toIntOrNull()?.coerceIn(1, 7) ?: 3; val g = goal.trim()
        if (g.isNotEmpty()) { split = false
            io(scope) {
                val start = today() + 1
                for (wk in 0 until w) for (i in 0 until p) dao.insertTask(TaskE(title = tr("$g: الأسبوع ${num(wk + 1)}، خطوة ${num(i + 1)}", "$g: week ${wk + 1}, step ${i + 1}"), category = "personal", day = start + wk * 7 + (i * 7 / p), priority = 1))
                Notify.refreshAsync(c)
            }
            msgs.add(Msg(false, tr("أنشأت ${num(w * p)} مهمة صغيرة موزعة على $w أسابيع، بداية من الغد. تجدها في المهام.", "Created ${w * p} small tasks across $w weeks starting tomorrow. Find them in Tasks.")))
        }
    }) {
        Field(goal, { goal = it }, tr("الهدف (مثال: إنهاء كورس بايثون)", "Goal (e.g. finish a Python course)"))
        Field(weeks, { weeks = it.filter { ch -> ch.isDigit() }.take(2) }, tr("عدد الأسابيع", "Weeks"), number = true)
        Field(per, { per = it.filter { ch -> ch.isDigit() }.take(1) }, tr("خطوات في الأسبوع", "Steps per week"), number = true)
        Sub(tr("يوزّع الهدف إلى خطوات متساوية. تقدر تعدّل أسماءها لاحقًا.", "Splits the goal into even steps. You can rename them later."))
    }
}
