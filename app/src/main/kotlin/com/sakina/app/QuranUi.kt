package com.sakina.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

@Composable fun QuranHome(go: Go) {
    val c = LocalContext.current; val dao = rememberDao(); val td = today()
    var surahs by remember { mutableStateOf<List<SurahData>?>(null) }
    LaunchedEffect(Unit) { surahs = withContext(Dispatchers.IO) { Quran.load(c) } }
    val marks by dao.bookmarks().collectAsState(emptyList())
    val pages by dao.pagesRead(td, td).collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    var ayahHits by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    LaunchedEffect(q) { ayahHits = if (q.length >= 3) withContext(Dispatchers.IO) { Quran.searchAyahs(c, q) } else emptyList() }
    val goal = Settings.int("wird_pages", 2)
    val ls = Settings.int("last_s", 0); val la = Settings.int("last_a", 1)
    val all = surahs
    LazyScreen(tr("القرآن الكريم", "The Quran"), actions = { IconButton({ go("search") }) { Icon(Icons.Default.Search, tr("بحث", "Search")) } }) {
        item {
            Glass(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ring(pages.size.toFloat() / goal.coerceAtLeast(1), 60, 6)
                    Column(Modifier.weight(1f)) {
                        Body(tr("الورد اليومي: ${num(pages.size)} من ${num(goal)} صفحة", "Daily wird: ${pages.size} of $goal pages"))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Sub(tr("الهدف:", "Goal:")); listOf(1, 2, 5, 10, 20).forEach { g -> Chip(numS("$g"), goal == g) { Settings.put("wird_pages", g) } }
                        }
                    }
                }
                if (ls > 0 && all != null) { Spacer(Modifier.height(8.dp)); Btn(tr("أكمل من حيث توقفت: ${Quran.surahName(all[ls - 1])} آية ${num(la)}", "Continue: ${Quran.surahName(all[ls - 1])} ayah $la"), { go("quran/$ls?a=$la") }, Modifier.fillMaxWidth()) }
            }
        }
        item { Field(q, { q = it }, tr("ابحث عن سورة (اسم/رقم) أو عن آية (٣ أحرف فأكثر)", "Search a surah (name/number) or an ayah (3+ letters)")) }
        if (ayahHits.isNotEmpty()) {
            item { SectionLabel(tr("آيات", "Ayahs")) }
            items(ayahHits) { (s, a, t) -> Glass(Modifier.fillMaxWidth(), onClick = { go("quran/$s?a=$a") }, pad = 12) {
                Text(t, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Amiri, fontSize = 20.sp, lineHeight = 32.sp), maxLines = 3)
                Sub(tr("سورة ${all?.get(s - 1)?.ar} · آية ${num(a)}", "${all?.get(s - 1)?.en} · ayah $a")) } }
        }
        if (marks.isNotEmpty() && q.isEmpty() && all != null) {
            item { SectionLabel(tr("العلامات المرجعية", "Bookmarks")) }
            items(marks) { m -> Glass(Modifier.fillMaxWidth(), onClick = { go("quran/${m.surah}?a=${m.ayah}") }, pad = 12) { Text(tr("${all[m.surah - 1].ar} · آية ${num(m.ayah)}", "${all[m.surah - 1].en} · ayah ${m.ayah}")) } }
        }
        item { SectionLabel(tr("السور", "Surahs")) }
        if (all == null) item { Sub(tr("جارٍ التحميل…", "Loading…")) }
        else {
            val list = if (q.isBlank()) all else Quran.searchSurahs(c, q)
            if (list.isEmpty()) item { Empty(tr("لا توجد سورة بهذا الاسم.", "No surah matches.")) }
            items(list) { s ->
                Glass(Modifier.fillMaxWidth(), onClick = { go("quran/${s.n}") }, pad = 12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(numS(s.n.toString()), Modifier.width(40.dp), color = GoldT)
                        Column(Modifier.weight(1f)) { Text(Quran.surahName(s), style = MaterialTheme.typography.titleMedium); Sub(tr(if (s.meccan) "مكية" else "مدنية", if (s.meccan) "Meccan" else "Medinan") + " · " + tr("${num(s.ayahs.size)} آية", "${s.ayahs.size} ayahs")) }
                        Sub(tr("ص ${num(s.pages[0])}", "p. ${s.pages[0]}"))
                    }
                }
            }
        }
        item { Sub(tr("نص المصحف: النص العثماني (مشروع تنزيل tanzil.net) عبر alquran.cloud. التفسير: التفسير الميسر (مجمع الملك فهد لطباعة المصحف الشريف). التلاوة: مشاري العفاسي من everyayah.com (تحتاج إنترنت).", "Text: Uthmani script (Tanzil project) via alquran.cloud. Tafsir: Al-Tafsir al-Muyassar (King Fahd Complex). Recitation: Mishary Alafasy from everyayah.com (needs internet).")) }
    }
}

class AyahPlayer(private val c: Context) {
    private var mp: MediaPlayer? = null
    var onChange: (Int) -> Unit = {}
    var onError: () -> Unit = {}
    private var surah = 0; private var count = 0
    fun play(s: Int, from: Int, total: Int) { surah = s; count = total; start(from) }
    private fun start(a: Int) {
        stop(false)
        if (a > count) { onChange(0); return }
        onChange(a)
        try {
            val p = MediaPlayer(); mp = p
            p.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build())
            p.setDataSource(String.format(java.util.Locale.US, "https://everyayah.com/data/Alafasy_128kbps/%03d%03d.mp3", surah, a))
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { start(a + 1) }
            p.setOnErrorListener { _, _, _ -> stop(true); onError(); true }
            p.prepareAsync()
        } catch (e: Exception) { stop(true); onError() }
    }
    fun stop(notify: Boolean = true) { runCatching { mp?.release() }; mp = null; if (notify) onChange(0) }
}

@Composable fun SurahScreen(s: Int, startAyah: Int, back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope(); val td = today()
    var sd by remember { mutableStateOf<SurahData?>(null) }
    var tafsir by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(s) { sd = withContext(Dispatchers.IO) { Quran.load(c)[s - 1] } }
    val marks by dao.bookmarks().collectAsState(emptyList())
    val markSet = marks.filter { it.surah == s }.map { it.ayah }.toSet()
    var selected by remember { mutableIntStateOf(0) }
    var playing by remember { mutableIntStateOf(0) }
    var audioErr by remember { mutableStateOf(false) }
    val player = remember { AyahPlayer(c).also { it.onChange = { a -> playing = a }; it.onError = { audioErr = true } } }
    DisposableEffect(Unit) { onDispose { player.stop(false) } }
    val font = Settings.int("quran_font", 28); val night = Settings.bool("quran_night", false)
    val state = rememberLazyListState()
    val data = sd
    LaunchedEffect(data) { if (data != null && startAyah > 1) state.scrollToItem(startAyah.coerceAtMost(data.ayahs.size)) }
    LaunchedEffect(data) {
        if (data != null) snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { idx ->
            val a = (idx).coerceIn(1, data.ayahs.size)
            Settings.put("last_s", s); Settings.put("last_a", a)
            withContext(Dispatchers.IO) { dao.addPageRead(PageRead(td, data.pages[a - 1])) }
        }
    }
    val bg = if (night) Color(0xFF050505) else Color.Transparent
    val fg = if (night) Color(0xFFE9D9B0) else MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxSize().background(bg)) {
        LazyScreen(if (data != null) tr("سورة ${data.ar}", data.en) else "", back, state = state, actions = {
            IconButton({ Settings.put("quran_font", (font - 2).coerceAtLeast(20)) }) { Text("A−") }
            IconButton({ Settings.put("quran_font", (font + 2).coerceAtMost(48)) }) { Text("A+") }
            IconButton({ Settings.put("quran_night", !night) }) { Icon(Icons.Default.Info, tr("الوضع الليلي", "Night mode"), tint = if (night) Gold else MaterialTheme.colorScheme.onSurface) }
            if (data != null && Settings.bool("audio_on", true)) IconButton({ if (playing > 0) player.stop() else { audioErr = false; player.play(s, if (selected > 0) selected else 1, data.ayahs.size) } }) { Icon(if (playing > 0) Icons.Default.Close else Icons.Default.PlayArrow, tr("تلاوة", "Recitation")) }
        }) {
            if (data == null) { item { Sub(tr("جارٍ التحميل…", "Loading…")) } } else {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(data.ar, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Amiri), color = GoldT)
                        if (s != 1 && s != 9) Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Amiri, fontSize = (font - 2).sp), color = fg)
                        if (audioErr) Text(tr("تعذّر تشغيل التلاوة. تحقق من الإنترنت.", "Could not play audio. Check your internet."), color = DangerT)
                    }
                }
                itemsIndexed(data.ayahs) { i, t ->
                    val a = i + 1
                    Column(Modifier.fillMaxWidth().clickable { selected = if (selected == a) 0 else a; if (selected == a && tafsir == null) tafsir = Quran.tafsir(c)[s - 1] }.then(if (playing == a) Modifier.background(Emerald.copy(alpha = 0.18f)) else Modifier)) {
                        Text("$t ﴿${numS(a.toString())}﴾", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Amiri, fontSize = font.sp, lineHeight = (font * 1.9).sp), color = fg, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Justify)
                        if (a in markSet) Text("🔖", Modifier.padding(start = 4.dp))
                        if (selected == a) Glass(Modifier.fillMaxWidth().padding(vertical = 6.dp), pad = 12) {
                            Text(tr("التفسير الميسر", "Tafsir al-Muyassar"), color = GoldT, style = MaterialTheme.typography.titleSmall)
                            val tx = tafsir?.getOrNull(i)
                            Text(tx ?: tr("جارٍ التحميل…", "Loading…"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp))
                            Sub(tr("المصدر: التفسير الميسر، مجمع الملك فهد لطباعة المصحف الشريف", "Source: Al-Tafsir al-Muyassar, King Fahd Complex"))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton({ io(scope) { val b = Bookmark(s, a, System.currentTimeMillis()); if (a in markSet) dao.delBookmark(b) else dao.addBookmark(b) } }) { Text(if (a in markSet) tr("إزالة العلامة", "Remove bookmark") else tr("علامة مرجعية", "Bookmark")) }
                                if (Settings.bool("audio_on", true)) TextButton({ audioErr = false; player.play(s, a, data.ayahs.size) }) { Text(tr("تشغيل من هنا", "Play from here")) }
                                TextButton({ (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ayah", "$t ﴿$a﴾ [${data.ar}]")) }) { Text(tr("نسخ", "Copy")) }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            }
        }
    }
}
