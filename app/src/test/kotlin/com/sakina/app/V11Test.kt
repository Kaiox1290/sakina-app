package com.sakina.app

import org.junit.Assert.*
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import java.io.File
import java.time.LocalDate

class V11Test {
    @Test fun khatmaOnTrack() { val p = khatmaPlan(100, 30, 100, 0); assertEquals(0, p.dayIndex); assertEquals(21, p.targetEnd); assertEquals(21, p.behindBy) }
    @Test fun khatmaLastDayReaches604() { val p = khatmaPlan(100, 30, 129, 580); assertEquals(604, p.targetEnd); assertEquals(24, p.remaining); assertEquals(1, p.daysLeft) }
    @Test fun khatmaAheadAndFinished() { val a = khatmaPlan(100, 30, 100, 100); assertEquals(79, a.aheadBy); assertTrue(khatmaPlan(100, 30, 110, 604).finished) }
    @Test fun khatmaOverdueIsGentle() { val p = khatmaPlan(100, 30, 200, 300); assertTrue(p.overdue); assertEquals(304, p.remaining) }
    @Test fun khatmaCatchUp() { val p = khatmaPlan(0, 30, 14, 100); assertEquals(16, p.daysLeft); assertEquals(Math.ceil(504 / 16.0).toInt(), p.catchUpPerDay) }
    @Test fun pointsAndLevels() {
        assertEquals(0, pointsOf(PointsIn())); assertEquals(5 + 3 + 2 + 1 + 1 + 1, pointsOf(PointsIn(1, 1, 1, 10, 1, 100)))
        assertEquals(1, levelOf(0).first); assertEquals(1, levelOf(99).first); assertEquals(2, levelOf(100).first); assertEquals(3, levelOf(300).first)
        val (l, lo, hi) = levelOf(250); assertEquals(2, l); assertEquals(100, lo); assertEquals(300, hi)
    }
    @Test fun pointsNeverDecreaseWithMoreActivity() { assertTrue(pointsOf(PointsIn(2, 2, 2, 20, 2, 200)) > pointsOf(PointsIn(1, 1, 1, 10, 1, 100))) }
    @Test fun badgesUnlock() { val b = badgesOf(PointsIn(adhkarDone = 30)); assertTrue(b.first { it.id == "a1" }.unlocked); assertTrue(b.first { it.id == "a2" }.unlocked); assertFalse(b.first { it.id == "a3" }.unlocked); assertEquals(16, b.size) }
    @Test fun ramadan2027() {
        val r = ramadanInfo(LocalDate.of(2027, 1, 1)); assertTrue("until ${r.daysUntil} on ${r.startsOn}", r.daysUntil in 36..40); assertEquals(0, r.ramadanDay)
        val m = ramadanInfo(LocalDate.of(2027, 2, 12)); assertEquals(0, m.daysUntil); assertTrue(m.ramadanDay in 3..6); assertTrue(m.ramadanLength in 29..30)
    }
    private val base = SugIn(600, "الظهر", "Dhuhr", 120, false, false, false, null, 0, 360, 1380, 0, 0, 2, 0)
    @Test fun suggestMorningAdhkar() { val o = suggest(base); assertTrue(o.doNow.any { it.route == "adhkar/morning" }); assertTrue(o.avoid.isNotEmpty()) }
    @Test fun suggestNearPrayer() { val o = suggest(base.copy(minsToPrayer = 10)); assertEquals("prayer", o.doNow.first().route); assertTrue(o.avoid.any { it.ar.contains("الصلاة") }) }
    @Test fun suggestLateNight() { val o = suggest(base.copy(minNow = 1340, minsToPrayer = 500)); assertTrue(o.avoid.any { it.ar.contains("النوم") }) }
    @Test fun suggestNeverEmptyAndCapped() { val o = suggest(base.copy(morningDone = true, pagesToday = 2)); assertTrue(o.doNow.isNotEmpty()); val big = suggest(base.copy(nextTask = "x", tasksLeft = 9, habitsLeft = 3, minsToPrayer = 5)); assertTrue(big.doNow.size <= 3 && big.avoid.size <= 2) }
    @Test fun nawawiAsset() {
        val h = Nawawi.parse(File("src/main/assets/nawawi.json").readText()); assertEquals(42, h.size); assertTrue(h.all { it.ar.isNotBlank() && it.en.isNotBlank() })
        assertTrue(h[0].ar.contains("إنَّمَا الْأَعْمَالُ"))
    }
}

class BackupPrivacyTest {
    @Test fun locationExcludedByDefault() { assertFalse(Backup.includePref("lat", false)); assertFalse(Backup.includePref("lng", false)); assertFalse(Backup.includePref("city", false)); assertTrue(Backup.includePref("lat", true)); assertTrue(Backup.includePref("theme", false)) }
    @Test fun folderAndTimerNeverExported() { for (k in listOf("ab_uri", "alarm_keys", "focus_end")) assertFalse(Backup.includePref(k, true)) }
}

class V12Test {
    private fun lum(c: androidx.compose.ui.graphics.Color) = c.luminance()
    private fun ratio(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double { val x = lum(a).toDouble(); val y = lum(b).toDouble(); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }
    @Test fun textContrastInBothThemes() {
        for ((n, s) in listOf("dark" to DarkScheme, "light" to LightScheme)) {
            assertTrue("$n onBackground", ratio(s.onBackground, s.background) >= 7.0)
            assertTrue("$n onSurface", ratio(s.onSurface, s.surface) >= 7.0)
            assertTrue("$n onSurfaceVariant/surface", ratio(s.onSurfaceVariant, s.surface) >= 4.5)
            assertTrue("$n onSurfaceVariant/background", ratio(s.onSurfaceVariant, s.background) >= 4.5)
            assertTrue("$n onPrimary", ratio(s.onPrimary, s.primary) >= 4.5)
        }
        assertTrue("gold dark", ratio(Gold, DarkScheme.background) >= 4.5); assertTrue("gold light", ratio(GoldInk, LightScheme.background) >= 4.5)
        assertTrue("emerald dark", ratio(Emerald, DarkScheme.surface) >= 4.5); assertTrue("emerald light", ratio(EmeraldInk, LightScheme.surface) >= 4.5)
        assertTrue("danger dark", ratio(Danger, DarkScheme.surface) >= 4.5); assertTrue("danger light", ratio(DangerInk, LightScheme.surface) >= 4.5)
        assertTrue("white on emerald button", ratio(androidx.compose.ui.graphics.Color.White, EmeraldDeep) >= 4.5)
    }
    @Test fun occasions2027() {
        val o = occasions(LocalDate.of(2027, 2, 12)).associateBy { it.key }
        assertTrue(o["fitr"]!!.date in LocalDate.of(2027, 3, 9)..LocalDate.of(2027, 3, 11))
        assertTrue(o["adha"]!!.date in LocalDate.of(2027, 5, 15)..LocalDate.of(2027, 5, 18))
        assertEquals(o["adha"]!!.date.minusDays(1), o["arafah"]!!.date)
        assertEquals(5, o.size); assertEquals(0, occasions(LocalDate.of(2027, 2, 8)).first { it.key == "ramadan" }.daysUntil)
    }
    @Test fun prayerLog() {
        var s = ""; s = togglePrayerLog(s, "asr"); s = togglePrayerLog(s, "fajr"); assertEquals(setOf("fajr", "asr"), parsePrayerLog(s)); assertEquals("fajr,asr", s)
        s = togglePrayerLog(s, "asr"); assertEquals("fajr", s); assertEquals(emptySet<String>(), parsePrayerLog("junk,,x"))
        assertEquals(10, pointsOf(PointsIn(prayers = 5)))
    }
}
