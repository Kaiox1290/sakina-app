package com.sakina.app

import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun vibrate(c: Context, ms: Long = 18) {
    if (!Settings.sp.getBoolean("vib", true)) return
    val v = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    runCatching { v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
}

@Composable fun AdhkarHome(go: Go, back: () -> Unit) {
    val c = LocalContext.current; val td = today(); val dao = rememberDao()
    val prog by dao.adhkarProg(td).collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    val cats = remember { Adhkar.load(c) }
    val totals = remember { Adhkar.groups.associate { it.key to Adhkar.items(c, it).size } }
    LazyScreen(tr("الأذكار والأدعية", "Adhkar & duas"), back, actions = { IconButton({ go("favorites") }) { Icon(Icons.Default.Star, tr("المفضلة", "Favorites")) } }) {
        item { Sub(tr("المصدر: ${Adhkar.SOURCE}. النصوص كما وردت في الكتاب، بلا إضافة منّا.", "Source: ${Adhkar.SOURCE_EN}. Texts as printed in the book, nothing added by us.")) }
        items(Adhkar.groups.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { g ->
                    val d = prog.count { it.done && it.key.startsWith(g.key + "|") }
                    Glass(Modifier.weight(1f), onClick = { go("adhkar/${g.key}") }, pad = 14) {
                        Text(Adhkar.gName(g), style = MaterialTheme.typography.titleSmall, minLines = 2)
                        Sub(numS("$d/${totals[g.key]}"))
                        LinearProgressIndicator(progress = { if ((totals[g.key] ?: 0) == 0) 0f else d.toFloat() / totals[g.key]!! }, Modifier.fillMaxWidth().padding(top = 6.dp), color = EmeraldT)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Glass(Modifier.fillMaxWidth(), onClick = { go("nawawi") }, pad = 14) { Text(tr("الأربعون النووية (أحاديث مع المصدر)", "Forty Hadith of an-Nawawi (with sources)"), style = MaterialTheme.typography.titleSmall) } }
        item { SectionLabel(tr("كل الأبواب (${num(cats.size)})", "All chapters (${cats.size})")); Field(q, { q = it }, tr("ابحث في الأبواب", "Search chapters")) }
        val nq = normAr(q)
        items(cats.entries.filter { nq.isEmpty() || normAr(it.value.first).contains(nq) }.toList()) { (id, v) ->
            Glass(Modifier.fillMaxWidth(), onClick = { go("adhkarcat/$id") }, pad = 12) { Row { Text(v.first, Modifier.weight(1f)); Sub(num(v.second.size)) } }
        }
    }
}

private fun shareText(c: Context, text: String) {
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable fun AdhkarGroupScreen(key: String, back: () -> Unit) {
    val c = LocalContext.current
    val g = remember(key) { Adhkar.group(key) }
    val items = remember(key) { Adhkar.items(c, g) }
    AdhkarList(key, Adhkar.gName(g), items, back)
}
@Composable fun AdhkarCatScreen(id: Int, back: () -> Unit) {
    val c = LocalContext.current
    val items = remember(id) { Adhkar.itemsOfCat(c, id) }
    AdhkarList("cat$id", Adhkar.load(c)[id]?.first ?: "", items, back)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun AdhkarList(prefix: String, title: String, items: List<Dhikr>, back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope(); val td = today()
    val prog by dao.adhkarProg(td).collectAsState(emptyList())
    val favs by dao.favs().collectAsState(emptyList())
    val favSet = favs.map { it.key }.toSet()
    val byKey = prog.associateBy { it.key }
    var reading by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val font = Settings.int("adhkar_font", 22)
    val doneCount = items.indices.count { byKey["$prefix|$it"]?.done == true }
    fun tap(i: Int) {
        val k = "$prefix|$i"; val cur = byKey[k]; val need = items[i].count
        val n = ((cur?.count ?: 0) + 1).coerceAtMost(need)
        vibrate(c, if (n >= need) 40 else 12)
        io(scope) { dao.putAdhkar(AdhkarProg(k, td, n, n >= need)) }
    }
    fun reset(i: Int) = io(scope) { dao.putAdhkar(AdhkarProg("$prefix|$i", td, 0, false)) }
    fun fav(i: Int) = io(scope) { val k = "$prefix|$i"; if (k in favSet) dao.delFav(k) else dao.addFav(Fav(k, items[i].text.take(40))) }
    fun markDone(i: Int) = io(scope) { val k = "$prefix|$i"; val cur = byKey[k]; if (cur?.done == true) dao.putAdhkar(AdhkarProg(k, td, 0, false)) else dao.putAdhkar(AdhkarProg(k, td, items[i].count, true)) }

    @Composable fun Card(i: Int, big: Boolean) {
        val it = items[i]; val p = byKey["$prefix|$i"]; val cnt = p?.count ?: 0; val done = p?.done == true
        Glass(Modifier.fillMaxWidth()) {
            Text(it.text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Amiri, fontSize = (if (big) font + 6 else font).sp, lineHeight = ((if (big) font + 6 else font) * 1.7).sp), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            Spacer(Modifier.height(8.dp))
            Sub(it.ref)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button({ tap(i) }, Modifier.weight(1f).heightIn(min = 52.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (done) Emerald.copy(alpha = 0.35f) else Emerald)) {
                    Text(if (done) tr("✓ تم", "✓ Done") else numS("$cnt / ${it.count}"), style = MaterialTheme.typography.titleMedium)
                }
                IconButton({ fav(i) }) { Icon(if ("$prefix|$i" in favSet) Icons.Default.Star else Icons.Default.Star, tr("مفضلة", "Favorite"), tint = if ("$prefix|$i" in favSet) Gold else MaterialTheme.colorScheme.outline) }
                IconButton({ reset(i) }) { Icon(Icons.Default.Refresh, tr("إعادة العداد", "Reset counter")) }
                IconButton({ markDone(i) }) { Icon(Icons.Default.Check, tr("تحديد كمكتمل", "Mark done"), tint = if (done) Emerald else MaterialTheme.colorScheme.outline) }
                IconButton({ shareText(c, it.text + "\n" + it.ref) }) { Icon(Icons.Default.Share, tr("مشاركة", "Share")) }
            }
        }
    }

    if (reading) {
        val pager = rememberPagerState { items.size }
        Screen(title, { reading = false }, actions = {
            IconButton({ Settings.put("adhkar_font", (font - 2).coerceAtLeast(16)) }) { Text("A−") }
            IconButton({ Settings.put("adhkar_font", (font + 2).coerceAtMost(40)) }) { Text("A+") }
        }) {
            Text(numS("${pager.currentPage + 1} / ${items.size}"), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = GoldT)
            Box(Modifier.fillMaxWidth().height(560.dp)) { HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 12.dp) { i -> Column(Modifier.verticalScroll(rememberScrollState())) { Card(i, true) } } }
            Sub(tr("اسحب يمينًا ويسارًا للانتقال بين الأذكار.", "Swipe sideways to move between adhkar."))
        }
        return
    }
    LazyScreen(title, back, actions = {
        TextButton({ reading = true }) { Text(tr("وضع القراءة", "Reading mode")) }
        IconButton({ confirmReset = true }) { Icon(Icons.Default.Refresh, tr("إعادة الكل", "Reset all")) }
    }) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ring(if (items.isEmpty()) 0f else doneCount.toFloat() / items.size, 64)
                Column { Body(numS(tr("أكملت $doneCount من ${items.size}", "$doneCount of ${items.size} done"))); Sub(tr("العدّاد يتجدد كل يوم", "Counters renew every day")) }
            }
        }
        if (items.isEmpty()) item { Empty(tr("لا توجد أذكار في هذا الباب.", "Nothing in this chapter.")) }
        items(items.indices.toList()) { i ->
            if (i == 0 || items[i].catName != items[i - 1].catName) if (items.map { it.catName }.distinct().size > 1) Text(items[i].catName, style = MaterialTheme.typography.titleMedium, color = GoldT, modifier = Modifier.padding(top = 6.dp))
            Card(i, false)
        }
        item { Sub(tr("الدرجة: النصوص منقولة من حصن المسلم دون تخريج داخل التطبيق؛ راجع الكتاب للتخريج. لم نضف فضائل أو أعدادًا من عندنا.", "Grading: texts are from Hisn al-Muslim with no hadith grading shown in the app; refer to the book. We added no virtues or counts of our own.")) }
    }
    if (confirmReset) Confirm(tr("إعادة العدّادات", "Reset counters"), tr("تصفير عدّادات هذا الباب لليوم؟", "Reset today's counters for this chapter?"), {
        confirmReset = false; io(scope) { dao.clearAdhkar(td, "$prefix|%") }
    }, { confirmReset = false })
}

@Composable fun FavoritesScreen(back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope()
    val favs by dao.favs().collectAsState(emptyList())
    val resolved = remember(favs) {
        favs.mapNotNull { f ->
            val (p, i) = f.key.split("|").let { it[0] to it[1].toInt() }
            val list = if (p.startsWith("cat")) Adhkar.itemsOfCat(c, p.removePrefix("cat").toInt()) else Adhkar.items(c, Adhkar.group(p))
            list.getOrNull(i)?.let { f to it }
        }
    }
    LazyScreen(tr("المفضلة", "Favorites"), back) {
        if (resolved.isEmpty()) item { Empty(tr("لم تضف شيئًا للمفضلة بعد. اضغط النجمة على أي ذكر.", "No favorites yet. Tap the star on any dhikr.")) }
        items(resolved) { (f, d) -> Glass(Modifier.fillMaxWidth()) {
            Text(d.text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Amiri, fontSize = 22.sp, lineHeight = 36.sp))
            Sub(d.ref)
            TextButton({ io(scope) { dao.delFav(f.key) } }) { Text(tr("إزالة", "Remove")) }
        } }
    }
}

private fun tasbihLabel(k: String, customs: List<CustomDhikr>): String = when (k) {
    "tasbih" -> "سُبْحَانَ اللَّهِ"; "istighfar" -> "أَسْتَغْفِرُ اللَّهَ"; "salawat" -> "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ"
    else -> customs.firstOrNull { "c${it.id}" == k }?.text ?: ""
}
private fun tasbihTitle(k: String) = when (k) { "tasbih" -> tr("تسبيح", "Tasbih"); "istighfar" -> tr("استغفار", "Istighfar"); "salawat" -> tr("صلاة على النبي", "Salawat"); else -> "" }

@Composable fun TasbihScreen(back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao(); val scope = rememberCoroutineScope(); val td = today()
    val logs by dao.tasbihRange(td - 6, td).collectAsState(emptyList())
    val customs by dao.customDhikr().collectAsState(emptyList())
    var kind by remember { mutableStateOf("tasbih") }
    var addDialog by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val kinds = listOf("tasbih", "istighfar", "salawat") + customs.map { "c${it.id}" }
    val goal = if (kind.startsWith("c")) customs.firstOrNull { "c${it.id}" == kind }?.target ?: 33 else Settings.int("goal_$kind", 100)
    val count = logs.firstOrNull { it.day == td && it.kind == kind }?.count ?: 0
    Screen(tr("المسبحة", "Tasbih counter"), back) {
        ChipRow(kinds.map { it to (if (it.startsWith("c")) customs.first { c2 -> "c${c2.id}" == it }.text.take(14) else tasbihTitle(it)) }, kind) { kind = it }
        Text(tasbihLabel(kind, customs), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Amiri), textAlign = TextAlign.Center, color = GoldT)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(240.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Emerald, EmeraldDeep))).clickable {
                vibrate(c); io(scope) { dao.putTasbih(TasbihLog(td, kind, (dao.tasbihCount(td, kind) ?: 0) + 1)) }
            }, contentAlignment = Alignment.Center) {
                Text(numS(count.toString()), style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp), color = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ring(count.toFloat() / goal.coerceAtLeast(1), 56, 6)
            Column(Modifier.weight(1f)) { Body(tr("الهدف اليومي: ${num(goal)}", "Daily goal: $goal")); Sub(if (count >= goal) tr("أتممت هدف اليوم، بارك الله فيك", "Daily goal reached") else tr("متبقي ${num(goal - count)}", "${goal - count} to go")) }
            OutlinedButton({ confirmReset = true }) { Text(tr("تصفير", "Reset")) }
        }
        if (!kind.startsWith("c")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sub(tr("تغيير الهدف:", "Change goal:"))
            listOf(33, 100, 300, 1000).forEach { g -> Chip(numS(g.toString()), goal == g) { Settings.put("goal_$kind", g) } }
        }
        RowSwitch(tr("اهتزاز عند العد", "Vibrate on count"), Settings.bool("vib", true), { Settings.put("vib", it) })
        SectionLabel(tr("آخر ٧ أيام", "Last 7 days"))
        val days = (td - 6..td).toList()
        BarChart(days.map { d -> (logs.firstOrNull { it.day == d && it.kind == kind }?.count ?: 0).toFloat() }, days.map { numS(dayOf(it).dayOfMonth.toString()) })
        Btn(tr("إضافة ذكر مخصص", "Add custom dhikr"), { addDialog = true }, Modifier.fillMaxWidth(), outlined = true)
        if (kind.startsWith("c")) TextButton({ val id = kind.removePrefix("c").toLong(); kind = "tasbih"; io(scope) { dao.delCustom(id) } }) { Text(tr("حذف هذا الذكر المخصص", "Delete this custom dhikr"), color = DangerT) }
        val total = logs.filter { it.kind == kind }.sumOf { it.count }
        Sub(tr("مجموع الأسبوع: ${num(total)}", "Week total: $total"))
    }
    if (confirmReset) Confirm(tr("تصفير عداد اليوم", "Reset today"), tr("سيُصفّر عدّاد هذا الذكر لليوم فقط.", "This resets only today's count for this dhikr."), { confirmReset = false; io(scope) { dao.putTasbih(TasbihLog(td, kind, 0)) } }, { confirmReset = false })
    if (addDialog) {
        var txt by remember { mutableStateOf("") }; var tg by remember { mutableStateOf("33") }
        AlertDialog({ addDialog = false }, title = { Text(tr("ذكر مخصص", "Custom dhikr")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Field(txt, { txt = it }, tr("نص الذكر", "Text")); Field(tg, { tg = it.filter { ch -> ch.isDigit() }.take(5) }, tr("الهدف اليومي", "Daily goal"), number = true) }
        }, confirmButton = { TextButton({ if (txt.isNotBlank()) { val t0 = txt.trim(); val g0 = tg.toIntOrNull()?.coerceAtLeast(1) ?: 33; addDialog = false; io(scope) { dao.addCustom(CustomDhikr(text = t0, target = g0)) } } }) { Text(tr("حفظ", "Save")) } },
            dismissButton = { TextButton({ addDialog = false }) { Text(tr("إلغاء", "Cancel")) } })
    }
}
private typealias Color = androidx.compose.ui.graphics.Color
