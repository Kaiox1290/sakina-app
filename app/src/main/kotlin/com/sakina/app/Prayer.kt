package com.sakina.app

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

data class CalcMethod(val id: String, val ar: String, val en: String, val fajr: Double, val isha: Double, val ishaMin: Int = 0)

val calcMethods = listOf(
    CalcMethod("egypt", "الهيئة المصرية العامة للمساحة", "Egyptian General Authority of Survey", 19.5, 17.5),
    CalcMethod("mwl", "رابطة العالم الإسلامي", "Muslim World League", 18.0, 17.0),
    CalcMethod("umm", "أم القرى (مكة المكرمة)", "Umm al-Qura (Makkah)", 18.5, 0.0, 90),
    CalcMethod("isna", "أمريكا الشمالية (ISNA)", "ISNA (North America)", 15.0, 15.0),
    CalcMethod("karachi", "جامعة العلوم الإسلامية بكراتشي", "Univ. of Islamic Sciences, Karachi", 18.0, 18.0),
    CalcMethod("kuwait", "الكويت", "Kuwait", 18.0, 17.5),
    CalcMethod("qatar", "قطر", "Qatar", 18.0, 0.0, 90),
    CalcMethod("dubai", "دبي", "Dubai", 18.2, 18.2),
    CalcMethod("turkey", "ديانت (تركيا)", "Diyanet (Turkey)", 18.0, 17.0),
    CalcMethod("singapore", "سنغافورة", "Singapore", 20.0, 18.0)
)
fun methodById(id: String) = calcMethods.firstOrNull { it.id == id } ?: calcMethods[0]

val prayerKeys = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
fun prayerName(k: String) = when (k) {
    "fajr" -> tr("الفجر", "Fajr"); "sunrise" -> tr("الشروق", "Sunrise"); "dhuhr" -> tr("الظهر", "Dhuhr")
    "asr" -> tr("العصر", "Asr"); "maghrib" -> tr("المغرب", "Maghrib"); else -> tr("العشاء", "Isha")
}

/** Minutes after local midnight (fractional) for each prayer. */
data class DayTimes(val fajr: Double, val sunrise: Double, val dhuhr: Double, val asr: Double, val maghrib: Double, val isha: Double) {
    fun get(k: String) = when (k) { "fajr" -> fajr; "sunrise" -> sunrise; "dhuhr" -> dhuhr; "asr" -> asr; "maghrib" -> maghrib; else -> isha }
}

object PrayerCalc {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fixAngle(a: Double) = (a % 360 + 360) % 360
    private fun fixHour(a: Double) = (a % 24 + 24) % 24

    private fun julian(y: Int, m: Int, d: Int): Double {
        var yy = y; var mm = m
        if (mm <= 2) { yy -= 1; mm += 12 }
        val a = floor(yy / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (yy + 4716)) + floor(30.6001 * (mm + 1)) + d + b - 1524.5
    }

    private fun sunPos(jd: Double): Pair<Double, Double> { // declination, equation of time (hours)
        val dd = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * dd)
        val q = fixAngle(280.459 + 0.98564736 * dd)
        val l = fixAngle(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)))
        val e = 23.439 - 0.00000036 * dd
        var ra = deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0
        ra = fixHour(ra)
        val eqt = q / 15.0 - ra
        val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
        return decl to (((eqt + 12) % 24 + 24) % 24 - 12)
    }

    /**
     * @param asrFactor 1 = Shafi'i/Maliki/Hanbali, 2 = Hanafi
     */
    fun compute(date: LocalDate, lat: Double, lng: Double, tzHours: Double, m: CalcMethod, asrFactor: Int): DayTimes {
        val jd0 = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24)
        fun decl(t: Double) = sunPos(jd0 + t).first
        fun eqt(t: Double) = sunPos(jd0 + t).second
        fun mid(t: Double) = fixHour(12 - eqt(t))
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val d = decl(t); val noon = mid(t)
            val x = (-sin(rad(angle)) - sin(rad(d)) * sin(rad(lat))) / (cos(rad(d)) * cos(rad(lat)))
            if (x > 1 || x < -1) return Double.NaN
            val tt = deg(acos(x)) / 15.0
            return noon + if (ccw) -tt else tt
        }
        fun asrTime(t: Double): Double {
            val d = decl(t)
            val ang = -deg(atan(1.0 / (asrFactor + tan(rad(abs(lat - d))))))
            return angleTime(ang, t, false)
        }
        var fajr = 5.0; var sunrise = 6.0; var dhuhr = 12.0; var asr = 13.0; var sunset = 18.0; var isha = 18.0
        repeat(1) { // praytimes.org computes once with dayPortion; repeat for convergence
            fajr = angleTime(m.fajr, fajr / 24, true)
            sunrise = angleTime(0.833, sunrise / 24, true)
            dhuhr = mid(dhuhr / 24)
            asr = asrTime(asr / 24)
            sunset = angleTime(0.833, sunset / 24, false)
            isha = if (m.ishaMin > 0) Double.NaN else angleTime(m.isha, isha / 24, false)
        }
        val shift = tzHours - lng / 15.0
        fajr += shift; sunrise += shift; dhuhr += shift; asr += shift; sunset += shift; if (!isha.isNaN()) isha += shift
        var maghrib = sunset
        if (m.ishaMin > 0) isha = maghrib + m.ishaMin / 60.0
        // High latitude fallback (angle-based night portion)
        val night = fixHour(sunrise + 24 - sunset)
        if (fajr.isNaN()) fajr = sunrise - night * (m.fajr / 60.0)
        else { val diff = sunrise - fajr; val lim = night * (m.fajr / 60.0); if (diff.isNaN() || diff > lim) fajr = sunrise - lim }
        if (isha.isNaN()) isha = sunset + night * (m.isha / 60.0)
        else if (m.ishaMin == 0) { val diff = isha - sunset; val lim = night * (m.isha / 60.0); if (diff > lim) isha = sunset + lim }
        return DayTimes(fajr * 60, sunrise * 60, dhuhr * 60, asr * 60, maghrib * 60, isha * 60)
    }

    fun tzHours(date: LocalDate, zone: ZoneId): Double = zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant()).totalSeconds / 3600.0

    fun qiblaBearing(lat: Double, lng: Double): Double {
        val kLat = rad(21.422487); val kLng = rad(39.826206)
        val p = rad(lat); val dl = kLng - rad(lng)
        return fixAngle(deg(atan2(sin(dl), cos(p) * tan(kLat) - sin(p) * cos(dl))))
    }
}

/** Resolved user settings -> times for a given day, with manual per-prayer adjustment (minutes). */
data class Place(val name: String, val en: String, val lat: Double, val lng: Double)
val presetCities = listOf(
    Place("المنصورة", "Mansoura", 31.0409, 31.3785), Place("القاهرة", "Cairo", 30.0444, 31.2357), Place("الإسكندرية", "Alexandria", 31.2001, 29.9187),
    Place("مكة المكرمة", "Makkah", 21.3891, 39.8579), Place("المدينة المنورة", "Madinah", 24.4686, 39.6142), Place("الرياض", "Riyadh", 24.7136, 46.6753),
    Place("دبي", "Dubai", 25.2048, 55.2708), Place("الكويت", "Kuwait City", 29.3759, 47.9774), Place("الدوحة", "Doha", 25.2854, 51.5310),
    Place("عمّان", "Amman", 31.9539, 35.9106), Place("بغداد", "Baghdad", 33.3152, 44.3661), Place("الدار البيضاء", "Casablanca", 33.5731, -7.5898),
    Place("تونس", "Tunis", 36.8065, 10.1815), Place("الخرطوم", "Khartoum", 15.5007, 32.5599), Place("إسطنبول", "Istanbul", 41.0082, 28.9784),
    Place("لندن", "London", 51.5072, -0.1276), Place("نيويورك", "New York", 40.7128, -74.0060)
)
fun placeName(p: Place) = if (Settings.isAr) p.name else p.en
object PrayerSettings {
    fun lat() = Settings.sp.getLong("lat", java.lang.Double.doubleToLongBits(31.0409)).let { java.lang.Double.longBitsToDouble(it) }
    fun lng() = Settings.sp.getLong("lng", java.lang.Double.doubleToLongBits(31.3785)).let { java.lang.Double.longBitsToDouble(it) }
    fun method() = methodById(Settings.sp.getString("method", "egypt") ?: "egypt")
    fun asrFactor() = Settings.sp.getInt("asr", 1)
    fun adj(k: String) = Settings.sp.getInt("adj_$k", 0)
    fun timesFor(d: LocalDate): DayTimes {
        val base = PrayerCalc.compute(d, lat(), lng(), PrayerCalc.tzHours(d, zone()), method(), asrFactor())
        return DayTimes(
            base.fajr + adj("fajr"), base.sunrise + adj("sunrise"), base.dhuhr + adj("dhuhr"),
            base.asr + adj("asr"), base.maghrib + adj("maghrib"), base.isha + adj("isha")
        )
    }
    /** Epoch millis of prayer [k] on date [d]. */
    fun millis(d: LocalDate, tm: DayTimes, k: String): Long =
        java.time.LocalDateTime.of(d, java.time.LocalTime.MIN).plusMinutes((tm.get(k) + 0.5).toLong()).atZone(zone()).toInstant().toEpochMilli()
    fun next(nowMs: Long = System.currentTimeMillis()): Pair<String, Long> {
        val d = java.time.Instant.ofEpochMilli(nowMs).atZone(zone()).toLocalDate()
        for (off in 0..1) {
            val dd = d.plusDays(off.toLong()); val tm = timesFor(dd)
            for (k in listOf("fajr", "dhuhr", "asr", "maghrib", "isha")) {
                val ms = millis(dd, tm, k)
                if (ms > nowMs) return k to ms
            }
        }
        return "fajr" to nowMs
    }
}

fun fmtClock(minutes: Double): String {
    val m = (minutes + 0.5).toInt()
    return fmtMin(m)
}
