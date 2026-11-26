package com.sakina.app

import android.content.Context
import org.json.JSONArray

class SurahData(val n: Int, val ar: String, val en: String, val meccan: Boolean, val ayahs: List<String>, val pages: IntArray, val juz: IntArray)

object Quran {
    @Volatile private var data: List<SurahData>? = null
    @Volatile private var tafsirData: List<List<String>>? = null
    @Volatile private var normCache: List<Triple<Int, Int, String>>? = null

    fun parse(json: String): List<SurahData> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val a = o.getJSONArray("a"); val p = o.getJSONArray("p"); val j = o.getJSONArray("j")
            SurahData(o.getInt("n"), o.getString("ar"), o.getString("en"), o.getString("t") == "M",
                (0 until a.length()).map { a.getString(it) }, IntArray(p.length()) { p.getInt(it) }, IntArray(j.length()) { j.getInt(it) })
        }
    }
    fun load(c: Context): List<SurahData> = data ?: synchronized(this) {
        data ?: parse(c.assets.open("quran.json").bufferedReader().use { it.readText() }).also { data = it }
    }
    fun tafsir(c: Context): List<List<String>> = tafsirData ?: synchronized(this) {
        tafsirData ?: run {
            val arr = JSONArray(c.assets.open("tafsir.json").bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i -> val s = arr.getJSONArray(i); (0 until s.length()).map { s.getString(it) } }
        }.also { tafsirData = it }
    }
    fun ayahText(c: Context, s: Int, a: Int): String = load(c)[s - 1].ayahs[a - 1]
    fun surahName(s: SurahData) = if (Settings.isAr) s.ar else s.en
    fun searchAyahs(c: Context, q: String, limit: Int = 40): List<Triple<Int, Int, String>> {
        val nq = normAr(q.trim()); if (nq.length < 2) return emptyList()
        val cache = normCache ?: synchronized(this) {
            normCache ?: load(c).flatMap { s -> s.ayahs.mapIndexed { i, t -> Triple(s.n, i + 1, normAr(t)) } }.also { normCache = it }
        }
        val out = ArrayList<Triple<Int, Int, String>>()
        val sur = load(c)
        for ((s, a, t) in cache) if (t.contains(nq)) { out.add(Triple(s, a, sur[s - 1].ayahs[a - 1])); if (out.size >= limit) break }
        return out
    }
    fun searchSurahs(c: Context, q: String): List<SurahData> {
        val nq = normAr(q.trim()); if (nq.isEmpty()) return load(c)
        val n = nq.toIntOrNull()
        return load(c).filter { (n != null && it.n == n) || normAr(it.ar).contains(nq) || it.en.lowercase().contains(nq) }
    }
}

class Dhikr(val text: String, val count: Int, val catId: Int, val catName: String, val ref: String)
class AdhkarGroup(val key: String, val ar: String, val en: String, val catIds: List<Int>, val quranRefs: List<Triple<Int, Int, Int>> = emptyList())

object Adhkar {
    @Volatile private var cats: Map<Int, Pair<String, List<Pair<String, Int>>>>? = null
    const val SOURCE = "حصن المسلم - سعيد بن علي بن وهف القحطاني"
    const val SOURCE_EN = "Hisn al-Muslim (Sa'id al-Qahtani)"

    fun parse(json: String): Map<Int, Pair<String, List<Pair<String, Int>>>> {
        val arr = JSONArray(json); val m = LinkedHashMap<Int, Pair<String, List<Pair<String, Int>>>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val items = o.getJSONArray("i")
            m[o.getInt("id")] = o.getString("c") to (0 until items.length()).map { items.getJSONObject(it).let { x -> x.getString("t") to x.getInt("n") } }
        }
        return m
    }
    fun load(c: Context) = cats ?: synchronized(this) { cats ?: parse(c.assets.open("adhkar.json").bufferedReader().use { it.readText() }).also { cats = it } }

    // Quranic supplications: references only, the text is read from the bundled Quran text.
    val quranDuas = listOf(
        Triple(2, 201, 201), Triple(2, 286, 286), Triple(3, 8, 8), Triple(3, 147, 147), Triple(3, 193, 193), Triple(7, 23, 23),
        Triple(14, 40, 41), Triple(17, 24, 24), Triple(18, 10, 10), Triple(20, 25, 28), Triple(20, 114, 114), Triple(21, 83, 83),
        Triple(21, 87, 87), Triple(23, 118, 118), Triple(25, 74, 74), Triple(28, 24, 24), Triple(46, 15, 15), Triple(59, 10, 10),
        Triple(71, 28, 28), Triple(9, 129, 129)
    )
    val groups = listOf(
        AdhkarGroup("morning", "أذكار الصباح", "Morning adhkar", listOf(1)),
        AdhkarGroup("evening", "أذكار المساء", "Evening adhkar", listOf(1)),
        AdhkarGroup("sleep", "أذكار النوم", "Before sleep", listOf(2)),
        AdhkarGroup("wake", "أذكار الاستيقاظ", "Upon waking", listOf(3)),
        AdhkarGroup("afterprayer", "أذكار بعد الصلاة", "After prayer", listOf(27)),
        AdhkarGroup("enterexit", "الدخول والخروج", "Entering & leaving", listOf(8, 9, 4, 5, 10, 11, 12, 6, 7)),
        AdhkarGroup("food", "أذكار الطعام", "Food & drink", listOf(69, 70, 71, 68, 73, 74)),
        AdhkarGroup("travel", "أذكار السفر", "Travel", listOf(95, 96, 97, 98, 100, 101, 102, 103, 104, 105)),
        AdhkarGroup("istighfar", "الاستغفار", "Seeking forgiveness", listOf(129)),
        AdhkarGroup("salawat", "الصلاة على النبي ﷺ", "Salawat on the Prophet ﷺ", listOf(107, 25)),
        AdhkarGroup("tasbih", "التسبيح والتحميد", "Tasbih & tahmid", listOf(130, 131)),
        AdhkarGroup("duas", "أدعية القرآن والسنة", "Duas from Quran & Sunnah", listOf(26, 28, 34, 35, 41, 43, 44, 45, 46, 53, 106), quranDuas)
    )
    fun group(key: String) = groups.first { it.key == key }
    fun gName(g: AdhkarGroup) = if (Settings.isAr) g.ar else g.en

    fun items(c: Context, g: AdhkarGroup): List<Dhikr> {
        val all = load(c); val out = ArrayList<Dhikr>()
        for (id in g.catIds) {
            val cat = all[id] ?: continue
            for ((t, n) in cat.second) out.add(Dhikr(t, n.coerceAtLeast(1), id, cat.first, SOURCE))
        }
        val q = Quran.load(c)
        for ((s, a, b) in g.quranRefs) {
            val txt = (a..b).joinToString(" ") { Quran.ayahText(c, s, it) } + " ﴿" + (if (a == b) "$a" else "$a-$b") + "﴾"
            out.add(Dhikr(txt, 1, 0, tr("من القرآن الكريم", "From the Quran"), tr("القرآن الكريم، سورة ${q[s - 1].ar}، الآية ${if (a == b) a else "$a-$b"}", "Quran, surah ${q[s - 1].en}, ayah ${if (a == b) a else "$a-$b"}")))
        }
        return out
    }
    fun itemsOfCat(c: Context, id: Int): List<Dhikr> {
        val cat = load(c)[id] ?: return emptyList()
        return cat.second.map { Dhikr(it.first, it.second.coerceAtLeast(1), id, cat.first, SOURCE) }
    }
}

object Quotes {
    private val ayahRefs = listOf(Triple(94, 5, 6), Triple(13, 28, 28), Triple(65, 3, 3), Triple(2, 153, 153), Triple(3, 139, 139), Triple(39, 10, 10), Triple(2, 286, 286), Triple(94, 7, 8))
    private val lines = listOf(
        "خطوة صغيرة كل يوم تصنع فرقًا كبيرًا." to "A small step every day makes a big difference.",
        "الاستمرار أهم من الكمال." to "Consistency matters more than perfection.",
        "ابدأ بما تستطيع، ثم زِد بهدوء." to "Start with what you can, then grow gently.",
        "نظّم وقتك قبل أن يُنظّمك الزحام." to "Organize your time before the rush organizes you.",
        "إن فاتك اليوم شيء، فغدًا فرصة جديدة." to "If you missed something today, tomorrow is a new chance.",
        "الراحة جزء من الإنجاز." to "Rest is part of getting things done.",
        "مهمة واحدة مكتملة أفضل من خمس معلّقة." to "One finished task beats five half-started ones."
    )
    fun forDay(c: Context, day: Long): Pair<String, String> {
        val i = (day % (lines.size + ayahRefs.size)).toInt()
        return if (i < lines.size) lines[i].let { tr(it.first, it.second) to "" }
        else { val (s, a, b) = ayahRefs[i - lines.size]; val sd = Quran.load(c)[s - 1]
            (a..b).joinToString(" ") { sd.ayahs[it - 1] } to tr("القرآن الكريم، سورة ${sd.ar}", "Quran, ${sd.en}") }
    }
}
