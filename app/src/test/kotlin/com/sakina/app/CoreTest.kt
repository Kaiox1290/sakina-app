package com.sakina.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PrayerTimesTest {
    private val d = LocalDate.of(2026, 11, 26)
    private fun hm(s: String) = s.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
    private fun check(lat: Double, lng: Double, tz: Double, m: String, asr: Int, exp: Map<String, String>) {
        val t = PrayerCalc.compute(d, lat, lng, tz, methodById(m), asr)
        for ((k, v) in exp) { val diff = Math.abs(t.get(k) - hm(v)); assertTrue("$k expected $v got ${t.get(k)} (diff $diff)", diff <= 2.5) }
    }
    @Test fun cairoEgyptian() = check(30.0444, 31.2357, 2.0, "egypt", 1, mapOf("fajr" to "04:58", "sunrise" to "06:29", "dhuhr" to "11:42", "asr" to "14:36", "maghrib" to "16:55", "isha" to "18:17"))
    @Test fun mansouraEgyptian() = check(31.0409, 31.3785, 2.0, "egypt", 1, mapOf("fajr" to "04:59", "sunrise" to "06:31", "dhuhr" to "11:42", "asr" to "14:34", "maghrib" to "16:53", "isha" to "18:15"))
    @Test fun makkahUmmAlQura() = check(21.3891, 39.8579, 3.0, "umm", 1, mapOf("fajr" to "05:18", "sunrise" to "06:38", "dhuhr" to "12:08", "asr" to "15:16", "maghrib" to "17:37", "isha" to "19:07"))
    @Test fun londonMwl() = check(51.5072, -0.1276, 0.0, "mwl", 1, mapOf("fajr" to "05:36", "sunrise" to "07:36", "dhuhr" to "11:48", "asr" to "13:41", "maghrib" to "15:59", "isha" to "17:52"))
    @Test fun nycIsna() = check(40.7128, -74.0060, -5.0, "isna", 1, mapOf("fajr" to "05:35", "sunrise" to "06:55", "dhuhr" to "11:43", "asr" to "14:12", "maghrib" to "16:31", "isha" to "17:51"))
    @Test fun cairoHanafiAsr() = check(30.0444, 31.2357, 2.0, "egypt", 2, mapOf("asr" to "15:19"))
    @Test fun orderIsIncreasing() { val t = PrayerCalc.compute(d, 30.0, 31.0, 2.0, methodById("egypt"), 1); val l = prayerKeys.map { t.get(it) }; assertEquals(l.sorted(), l) }
    @Test fun qiblaFromCairo() { val b = PrayerCalc.qiblaBearing(30.0444, 31.2357); assertTrue("bearing $b", b in 134.0..138.0) }
    @Test fun qiblaFromNyc() { val b = PrayerCalc.qiblaBearing(40.7128, -74.0060); assertTrue("bearing $b", b in 56.0..60.0) }
    @Test fun highLatitudeDoesNotProduceNaN() { val t = PrayerCalc.compute(LocalDate.of(2026, 6, 21), 64.1, -21.9, 0.0, methodById("mwl"), 1); prayerKeys.forEach { assertFalse(it, t.get(it).isNaN()) } }
}

class LogicTest {
    @Test fun recurrence() {
        val daily = TaskE(title = "a", day = 100, repeat = 1); assertFalse(daily.occursOn(99)); assertTrue(daily.occursOn(100)); assertTrue(daily.occursOn(5000))
        val weekly = TaskE(title = "b", day = 100, repeat = 2); assertTrue(weekly.occursOn(107)); assertFalse(weekly.occursOn(108))
        val once = TaskE(title = "c", day = 100); assertTrue(once.occursOn(100)); assertFalse(once.occursOn(101))
    }
    @Test fun gapFinder() {
        assertEquals(60, findGap(listOf(Interval(0, 60)), 0, 600, 30))
        assertEquals(100, findGap(listOf(Interval(60, 100)), 70, 600, 30))
        assertNull(findGap(listOf(Interval(0, 600)), 0, 600, 30))
    }
    private val prayers = listOf("fajr" to 298, "dhuhr" to 702, "asr" to 876, "maghrib" to 1015, "isha" to 1097)
    @Test fun planHasNoOverlaps() {
        val r = buildPlan(PlanInput(wake = 360, bed = 1380, workStart = 480, workEnd = 840, studyMin = 120, learnMin = 60, sportMin = 30, restMin = 30, prayers = prayers))
        val b = r.blocks.filter { it.kind != "work" }.sortedBy { it.start }
        for (i in 1 until b.size) assertTrue("overlap ${b[i - 1]} ${b[i]}", b[i - 1].end <= b[i].start)
        assertTrue(r.unplaced.isEmpty())
        assertTrue(b.none { it.kind != "prayer" && b.any { p -> p.kind == "prayer" && it.start < p.end && it.end > p.start } })
        assertEquals(7 * 60, r.sleepMinutes)
        assertTrue(r.blocks.filter { it.kind == "study" }.sumOf { it.end - it.start } == 120)
    }
    @Test fun shortSleepWarns() { val r = buildPlan(PlanInput(wake = 300, bed = 60 + 0, prayers = prayers)); assertTrue("sleep" in r.warnings) }
    @Test fun overfullDayReportsUnplaced() { val r = buildPlan(PlanInput(wake = 360, bed = 1380, workStart = 360, workEnd = 1200, studyMin = 600, prayers = prayers)); assertTrue(r.unplaced.isNotEmpty()) }
    @Test fun reflowAvoidsBusy() {
        val late = listOf(TaskE(id = 1, title = "x", day = 1, startMin = 600, endMin = 660))
        val other = listOf(TaskE(id = 2, title = "y", day = 1, startMin = 700, endMin = 760))
        val m = reflowTasks(late, other, 690, 1380, listOf(Interval(702, 722)))
        val s = m[1]!!; assertTrue(s >= 695); assertTrue(s + 60 <= 700 || s >= 760)
    }
    @Test fun streakCount() { assertEquals(3, streak(setOf(8L, 9L, 10L), 10)); assertEquals(2, streak(setOf(8L, 9L), 10)); assertEquals(0, streak(setOf(5L), 10)) }
    @Test fun rulingGuard() { assertTrue(looksLikeRuling("هل يجوز كذا")); assertTrue(looksLikeRuling("is this haram?")); assertFalse(looksLikeRuling("رتب لي يومي")) }
    @Test fun arabicNormalization() { assertEquals(normAr("ٱلرَّحْمَٰنِ"), normAr("الرحمن")); assertEquals("الله", normAr("اللَّهُ")) }
}

class ContentTest {
    private fun asset(n: String) = File("src/main/assets/$n").readText()
    @Test fun quranComplete() {
        val q = Quran.parse(asset("quran.json"))
        assertEquals(114, q.size); assertEquals(6236, q.sumOf { it.ayahs.size })
        assertEquals(7, q[0].ayahs.size); assertEquals(286, q[1].ayahs.size); assertEquals(6, q[113].ayahs.size)
        assertEquals(604, q.maxOf { it.pages.max() }); assertEquals(30, q.maxOf { it.juz.max() })
        assertTrue(q[111].ayahs[0].contains("أَحَدٌ") || q[111].ayahs[0].contains("أَحَدٌۭ"))
        for (s in q) { assertFalse("basmala leaked in ${s.n}", s.n != 1 && s.n != 9 && s.ayahs[0].startsWith("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ")) }
    }
    @Test fun tafsirAligned() {
        val q = Quran.parse(asset("quran.json")); val t = org.json.JSONArray(asset("tafsir.json"))
        assertEquals(114, t.length()); for (i in 0 until 114) assertEquals("surah ${i + 1}", q[i].ayahs.size, t.getJSONArray(i).length())
    }
    @Test fun adhkarBundle() {
        val a = Adhkar.parse(asset("adhkar.json")); assertEquals(132, a.size); assertTrue(a.values.sumOf { it.second.size } > 250)
        assertTrue(a[1]!!.second.all { it.second >= 0 })
        for (g in Adhkar.groups) for (id in g.catIds) assertTrue("group ${g.key} cat $id", a.containsKey(id))
    }
    @Test fun quranDuaRefsValid() {
        val q = Quran.parse(asset("quran.json"))
        for ((s, a, b) in Adhkar.quranDuas) { assertTrue("$s:$a", s in 1..114 && a >= 1 && b <= q[s - 1].ayahs.size && a <= b) }
        println("DUA 3:8 = " + q[2].ayahs[7]); println("DUA 21:87 = " + q[20].ayahs[86]); println("DUA 20:25-28 = " + (25..28).joinToString(" ") { q[19].ayahs[it - 1] })
    }
}
