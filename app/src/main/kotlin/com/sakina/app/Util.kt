package com.sakina.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.util.Locale

/** App settings stored locally. Reading inside a composable subscribes to changes. */
object Settings {
    lateinit var sp: SharedPreferences
    val tick = mutableIntStateOf(0)
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("sakina", Context.MODE_PRIVATE) }
    fun str(k: String, d: String = ""): String { tick.intValue; return sp.getString(k, d) ?: d }
    fun int(k: String, d: Int = 0): Int { tick.intValue; return sp.getInt(k, d) }
    fun bool(k: String, d: Boolean = false): Boolean { tick.intValue; return sp.getBoolean(k, d) }
    fun dbl(k: String, d: Double): Double { tick.intValue; return java.lang.Double.longBitsToDouble(sp.getLong(k, java.lang.Double.doubleToLongBits(d))) }
    fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) {
            is String -> e.putString(k, v)
            is Int -> e.putInt(k, v)
            is Boolean -> e.putBoolean(k, v)
            is Double -> e.putLong(k, java.lang.Double.doubleToLongBits(v))
            else -> error("type")
        }
        e.apply(); tick.intValue++
    }
    val lang: String get() = str("lang", "ar")
    val isAr: Boolean get() = lang == "ar"
}

fun tr(ar: String, en: String): String = if (Settings.isAr) ar else en
@Composable fun t(ar: String, en: String): String = tr(ar, en)

fun zone(): ZoneId = ZoneId.systemDefault()
fun today(): Long = LocalDate.now(zone()).toEpochDay()
fun dayOf(d: Long): LocalDate = LocalDate.ofEpochDay(d)

fun fmtMin(m: Int): String { val x = ((m % 1440) + 1440) % 1440; return String.format(Locale.US, "%02d:%02d", x / 60, x % 60) }
fun fmtTimeMs(ms: Long): String {
    val z = java.time.Instant.ofEpochMilli(ms).atZone(zone())
    return String.format(Locale.US, "%02d:%02d", z.hour, z.minute)
}
fun fmtDur(min: Int): String = if (min >= 60) "${min / 60}${tr("س", "h")}" + (if (min % 60 > 0) " ${min % 60}${tr("د", "m")}" else "") else "$min${tr("د", "m")}"

fun toArabicDigits(s: String): String {
    val sb = StringBuilder()
    for (ch in s) sb.append(if (ch in '0'..'9') ('\u0660' + (ch - '0')) else ch)
    return sb.toString()
}
fun num(n: Int): String = if (Settings.isAr) toArabicDigits(n.toString()) else n.toString()
fun numS(s: String): String = if (Settings.isAr) toArabicDigits(s) else s

val hijriMonthsAr = listOf("مُحَرَّم", "صَفَر", "رَبِيع الأوَّل", "رَبِيع الآخِر", "جُمَادَى الأولى", "جُمَادَى الآخرة", "رَجَب", "شَعْبَان", "رَمَضَان", "شَوَّال", "ذو القَعْدة", "ذو الحِجَّة")
val hijriMonthsEn = listOf("Muharram", "Safar", "Rabi' al-awwal", "Rabi' al-thani", "Jumada al-awwal", "Jumada al-thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")
val weekdaysAr = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
val weekdaysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
val monthsAr = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

fun hijriOf(d: LocalDate, adj: Int = Settings.sp.getInt("hijri_adj", 0)): HijrahDate =
    HijrahDate.from(d.plusDays(adj.toLong())).let { it }

fun hijriText(d: LocalDate): String {
    val h = try { HijrahChronology.INSTANCE.date(d.plusDays(Settings.sp.getInt("hijri_adj", 0).toLong())) } catch (e: Exception) { return "" }
    val y = h.get(java.time.temporal.ChronoField.YEAR); val m = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR); val dd = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
    return if (Settings.isAr) numS("$dd ${hijriMonthsAr[m - 1]} $y هـ") else "$dd ${hijriMonthsEn[m - 1]} $y AH"
}
fun hijriParts(d: LocalDate): Triple<Int, Int, Int>? = try {
    val h = HijrahChronology.INSTANCE.date(d.plusDays(Settings.sp.getInt("hijri_adj", 0).toLong()))
    Triple(h.get(java.time.temporal.ChronoField.YEAR), h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR), h.get(java.time.temporal.ChronoField.DAY_OF_MONTH))
} catch (e: Exception) { null }

fun gregText(d: LocalDate): String {
    val wd = if (Settings.isAr) weekdaysAr[d.dayOfWeek.value - 1] else weekdaysEn[d.dayOfWeek.value - 1]
    val mo = if (Settings.isAr) monthsAr[d.monthValue - 1] else monthsEn[d.monthValue - 1]
    return numS("$wd ${d.dayOfMonth} $mo ${d.year}")
}
fun dayLabel(epochDay: Long): String {
    val t = today()
    return when (epochDay) { t -> tr("اليوم", "Today"); t + 1 -> tr("غدًا", "Tomorrow"); t - 1 -> tr("أمس", "Yesterday"); else -> gregText(dayOf(epochDay)) }
}

private val diacritics = Regex("[\u064B-\u065F\u0670\u06D6-\u06ED\u0640\u08D3-\u08FF]")
fun normAr(s: String): String = s.replace(diacritics, "")
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
    .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي').lowercase()

// task categories
val categories = listOf("worship", "study", "coding", "sport", "health", "family", "personal")
fun catName(k: String): String = when (k) {
    "worship" -> tr("العبادة", "Worship"); "study" -> tr("الدراسة", "Study"); "coding" -> tr("البرمجة", "Coding")
    "sport" -> tr("الرياضة", "Sport"); "health" -> tr("الصحة", "Health"); "family" -> tr("العائلة", "Family")
    else -> tr("أعمال شخصية", "Personal")
}
