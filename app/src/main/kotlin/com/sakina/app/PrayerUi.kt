package com.sakina.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import kotlin.math.*

fun fetchLocation(c: Context, cb: (Location?) -> Unit) {
    if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { cb(null); return }
    val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = lm.getProviders(true)
    val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (last != null) { cb(last); return }
    val p = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: run { cb(null); return }
    try {
        if (Build.VERSION.SDK_INT >= 30) lm.getCurrentLocation(p, null, c.mainExecutor) { cb(it) }
        else @Suppress("DEPRECATION") lm.requestSingleUpdate(p, { cb(it) }, android.os.Looper.getMainLooper())
    } catch (e: Exception) { cb(null) }
}

@Composable fun PrayerScreen(go: Go) {
    val c = LocalContext.current
    var offset by remember { mutableIntStateOf(0) }
    val date = LocalDate.now().plusDays(offset.toLong())
    Settings.int("pr_tick", 0); Settings.str("method"); Settings.int("asr", 1); Settings.dbl("lat", 0.0)
    val tm = remember(date, Settings.tick.intValue) { PrayerSettings.timesFor(date) }
    val nextKey = if (offset == 0) PrayerSettings.next().first else ""
    var showSettings by remember { mutableStateOf(false) }
    val cityName = Settings.str("city", tr("المنصورة", "Mansoura"))
    Screen(tr("مواقيت الصلاة", "Prayer times"), actions = { TextButton({ showSettings = !showSettings }) { Text(if (showSettings) tr("إخفاء الإعدادات", "Hide settings") else tr("الإعدادات", "Settings")) } }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ offset-- }) { Text("‹") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Body(dayLabel(date.toEpochDay()) + " · " + gregText(date)); Sub(hijriText(date)); Sub(cityName) }
            TextButton({ offset++ }) { Text("›") }
        }
        prayerKeys.forEach { k ->
            Glass(Modifier.fillMaxWidth(), pad = 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(prayerName(k), style = MaterialTheme.typography.titleMedium, color = if (k == nextKey) GoldT else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (k == nextKey) Text(tr("القادمة", "Next"), color = EmeraldT, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp))
                    Text(numS(fmtClock(tm.get(k))), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Sub(tr("الحساب: ${methodById(Settings.str("method", "egypt")).ar} · ${if (Settings.int("asr", 1) == 2) "العصر حنفي" else "العصر جمهور"}", "Method: ${methodById(Settings.str("method", "egypt")).en} · Asr: ${if (Settings.int("asr", 1) == 2) "Hanafi" else "Shafi'i/standard"}"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn(tr("اتجاه القبلة", "Qibla"), { go("qibla") }, Modifier.weight(1f))
            Btn(tr("التقويم الهجري", "Hijri calendar"), { go("hijri") }, Modifier.weight(1f), outlined = true)
        }
        Btn(tr("إشعارات الصلاة", "Prayer notifications"), { go("notifications") }, Modifier.fillMaxWidth(), outlined = true)
        if (showSettings) PrayerSettingsPanel()
        Sub(tr("المواقيت محسوبة على الجهاز من إحداثيات موقعك وهي تقديرية، وقد تختلف دقيقة أو دقيقتين عن التقويم المحلي. عدّل الدقائق من الإعدادات لتطابق مسجد حيّك.", "Times are calculated on your device from your coordinates. They may differ by a minute or two from your local timetable; adjust minutes in settings to match your mosque."))
    }
}

@Composable fun PrayerSettingsPanel() {
    val c = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) fetchLocation(c) { l -> if (l != null) { Settings.put("lat", l.latitude); Settings.put("lng", l.longitude); Settings.put("city", tr("موقعي الحالي", "My location")); Notify.refreshAsync(c); msg = tr("تم تحديد موقعك.", "Location set.") } else msg = tr("تعذّر تحديد الموقع. اختر مدينة يدويًا.", "Could not get a location. Pick a city instead.") }
        else msg = tr("لم يتم السماح بالموقع. اختر مدينة يدويًا.", "Location not allowed. Pick a city instead.")
    }
    Glass(Modifier.fillMaxWidth()) {
        Text(tr("طريقة الحساب", "Calculation method"), style = MaterialTheme.typography.titleSmall)
        ChipRow(calcMethods.map { it.id to (if (Settings.isAr) it.ar else it.en) }, Settings.str("method", "egypt")) { Settings.put("method", it); Notify.refreshAsync(c) }
        Spacer(Modifier.height(10.dp))
        Text(tr("المذهب (وقت العصر)", "Asr calculation"), style = MaterialTheme.typography.titleSmall)
        ChipRow(listOf("1" to tr("الجمهور (ظل المثل)", "Standard (Shafi'i, Maliki, Hanbali)"), "2" to tr("الحنفي (ظل المثلين)", "Hanafi")), Settings.int("asr", 1).toString()) { Settings.put("asr", it.toInt()); Notify.refreshAsync(c) }
        Spacer(Modifier.height(10.dp))
        Text(tr("الموقع", "Location"), style = MaterialTheme.typography.titleSmall)
        ChipRow(presetCities.map { it.name to placeName(it) }, "") { n -> val p = presetCities.first { it.name == n }; Settings.put("lat", p.lat); Settings.put("lng", p.lng); Settings.put("city", placeName(p)); Notify.refreshAsync(c) }
        Spacer(Modifier.height(8.dp))
        Btn(tr("استخدم موقعي (يُحفظ على جهازك فقط)", "Use my location (stored on this device only)"), {
            if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, Modifier.fillMaxWidth(), outlined = true)
        var lat by remember { mutableStateOf(String.format(java.util.Locale.US, "%.4f", PrayerSettings.lat())) }
        var lng by remember { mutableStateOf(String.format(java.util.Locale.US, "%.4f", PrayerSettings.lng())) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(lat, { lat = it }, tr("خط العرض", "Latitude"), Modifier.weight(1f))
            Field(lng, { lng = it }, tr("خط الطول", "Longitude"), Modifier.weight(1f))
        }
        Btn(tr("حفظ الإحداثيات", "Save coordinates"), {
            val la = lat.toDoubleOrNull(); val lo = lng.toDoubleOrNull()
            if (la != null && lo != null && la in -66.0..66.0 && lo in -180.0..180.0) { Settings.put("lat", la); Settings.put("lng", lo); Settings.put("city", tr("إحداثيات يدوية", "Manual coordinates")); Notify.refreshAsync(c); msg = tr("تم الحفظ.", "Saved.") } else msg = tr("إحداثيات غير صحيحة.", "Invalid coordinates.")
        }, Modifier.fillMaxWidth())
        if (msg.isNotEmpty()) Sub(msg)
        Spacer(Modifier.height(10.dp))
        Text(tr("تعديل يدوي بالدقائق", "Manual adjustment (minutes)"), style = MaterialTheme.typography.titleSmall)
        prayerKeys.forEach { k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prayerName(k), Modifier.weight(1f))
                OutlinedButton({ Settings.put("adj_$k", (Settings.int("adj_$k", 0) - 1).coerceAtLeast(-30)); Notify.refreshAsync(c) }) { Text("−") }
                Text(numS(Settings.int("adj_$k", 0).toString()), Modifier.width(48.dp), textAlign = TextAlign.Center)
                OutlinedButton({ Settings.put("adj_$k", (Settings.int("adj_$k", 0) + 1).coerceAtMost(30)); Notify.refreshAsync(c) }) { Text("+") }
            }
        }
    }
}

@Composable fun QiblaScreen(back: () -> Unit) {
    val c = LocalContext.current
    val bearing = remember { PrayerCalc.qiblaBearing(PrayerSettings.lat(), PrayerSettings.lng()) }
    var heading by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rv == null) { hasSensor = false; onDispose { } } else {
            val l = object : SensorEventListener {
                val rot = FloatArray(9); val ori = FloatArray(3); var smooth = Float.NaN
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rot, e.values); SensorManager.getOrientation(rot, ori)
                    val az = ((Math.toDegrees(ori[0].toDouble()) + 360) % 360).toFloat()
                    if (smooth.isNaN()) smooth = az else { var d = az - smooth; if (d > 180) d -= 360; if (d < -180) d += 360; smooth = (smooth + d * 0.15f + 360) % 360 }
                    heading = smooth
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(l, rv, SensorManager.SENSOR_DELAY_UI)
            onDispose { sm.unregisterListener(l) }
        }
    }
    val dark = isDark(); val onS = MaterialTheme.colorScheme.onSurface
    Screen(tr("اتجاه القبلة", "Qibla direction"), back) {
        Glass(Modifier.fillMaxWidth(), pad = 20) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2 * 0.92f; val ctr = center
                    rotate(-heading, ctr) {
                        drawCircle(Emerald.copy(alpha = 0.18f), r, ctr); drawCircle(onS.copy(alpha = 0.35f), r, ctr, style = Stroke(3f))
                        for (i in 0 until 72) { val a = Math.toRadians(i * 5.0); val len = if (i % 9 == 0) 22f else 10f
                            drawLine(onS.copy(alpha = 0.5f), Offset(ctr.x + sin(a).toFloat() * (r - len), ctr.y - cos(a).toFloat() * (r - len)), Offset(ctr.x + sin(a).toFloat() * r, ctr.y - cos(a).toFloat() * r), 3f) }
                        // North marker
                        drawCircle(Danger, 10f, Offset(ctr.x, ctr.y - r + 38f))
                        // Qibla needle
                        rotate(bearing.toFloat(), ctr) {
                            val p = Path().apply { moveTo(ctr.x, ctr.y - r * 0.78f); lineTo(ctr.x - 22f, ctr.y); lineTo(ctr.x + 22f, ctr.y); close() }
                            drawPath(p, Gold)
                            drawCircle(Gold, 14f, Offset(ctr.x, ctr.y - r * 0.82f))
                        }
                    }
                    drawCircle(onS, 8f, ctr)
                }
            }
        }
        val diff = ((bearing - heading + 540) % 360 - 180)
        Body(tr("اتجاه القبلة من موقعك: ${numS(String.format(java.util.Locale.US, "%.0f", bearing))}° من الشمال", "Qibla bearing from your location: ${String.format(java.util.Locale.US, "%.0f", bearing)}° from north"))
        if (hasSensor) Sub(if (abs(diff) < 5) tr("أنت تواجه القبلة تقريبًا ✓", "You are facing the Qibla ✓") else tr("أدر الجهاز حتى يشير السهم الذهبي لأعلى", "Turn until the gold arrow points up"))
        else Sub(tr("لا يوجد حساس بوصلة في هذا الجهاز، استخدم الزاوية أعلاه مع بوصلة خارجية.", "No compass sensor on this device. Use the angle above with another compass."))
        Sub(tr("ابتعد عن المعادن والمغناطيس وحرّك الجهاز على شكل ٨ لمعايرة البوصلة. الموقع المستخدم: ${Settings.str("city", "المنصورة")}", "Keep away from metal/magnets and move the phone in a figure 8 to calibrate. Location used: ${Settings.str("city", "Mansoura")}"))
    }
}

val occasions = mapOf((1 to 1) to ("رأس السنة الهجرية" to "Islamic New Year"), (1 to 10) to ("يوم عاشوراء" to "Day of Ashura"), (9 to 1) to ("بداية رمضان" to "Start of Ramadan"),
    (10 to 1) to ("عيد الفطر" to "Eid al-Fitr"), (12 to 9) to ("يوم عرفة" to "Day of Arafah"), (12 to 10) to ("عيد الأضحى" to "Eid al-Adha"))

@Composable fun HijriCalendarScreen(back: () -> Unit) {
    val adj = Settings.int("hijri_adj", 0)
    val nowH = remember(adj) { hijriParts(LocalDate.now()) ?: Triple(1447, 1, 1) }
    var yy by remember { mutableIntStateOf(nowH.first) }; var mm by remember { mutableIntStateOf(nowH.second) }
    val first = remember(yy, mm, adj) { runCatching { LocalDate.from(HijrahChronology.INSTANCE.date(yy, mm, 1)).minusDays(adj.toLong()) }.getOrNull() }
    val len = remember(yy, mm) { runCatching { HijrahChronology.INSTANCE.date(yy, mm, 1).lengthOfMonth() }.getOrDefault(30) }
    Screen(tr("التقويم الهجري", "Hijri calendar"), back) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ if (mm == 1) { mm = 12; yy-- } else mm-- }) { Text("‹") }
            Text(numS("${if (Settings.isAr) hijriMonthsAr[mm - 1] else hijriMonthsEn[mm - 1]} $yy"), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            TextButton({ if (mm == 12) { mm = 1; yy++ } else mm++ }) { Text("›") }
        }
        if (first != null) {
            val heads = if (Settings.isAr) listOf("س", "ح", "ن", "ث", "ر", "خ", "ج") else listOf("Sa", "Su", "Mo", "Tu", "We", "Th", "Fr")
            val lead = (first.dayOfWeek.value + 1) % 7 // Saturday = 0
            val cells = (0 until lead).map { 0 } + (1..len)
            Glass(Modifier.fillMaxWidth(), pad = 10) {
                Row(Modifier.fillMaxWidth()) { heads.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = GoldT, style = MaterialTheme.typography.labelMedium) } }
                cells.chunked(7).forEach { wk ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        for (i in 0 until 7) {
                            val d = wk.getOrNull(i) ?: 0
                            val isToday = d > 0 && first.plusDays((d - 1).toLong()) == LocalDate.now()
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (d > 0) {
                                    Text(numS(d.toString()), color = if (isToday) Emerald else MaterialTheme.colorScheme.onSurface, style = if (isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                                    Text(numS(first.plusDays((d - 1).toLong()).dayOfMonth.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (occasions.containsKey(mm to d)) Text("•", color = GoldT)
                                } else Spacer(Modifier.height(36.dp))
                            }
                        }
                    }
                }
            }
            (1..len).filter { occasions.containsKey(mm to it) }.forEach { d -> val o = occasions[mm to d]!!; Glass(Modifier.fillMaxWidth(), pad = 12) { Row { Text(numS("$d"), color = GoldT, modifier = Modifier.width(36.dp)); Text(tr(o.first, o.second)) } } }
        } else Empty(tr("تعذّر عرض هذا الشهر.", "This month cannot be displayed."))
        Text(tr("تصحيح التاريخ الهجري (±يوم) ليتوافق مع الرؤية المحلية", "Adjust Hijri date (±days) to match local moon sighting"), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ Settings.put("hijri_adj", (adj - 1).coerceAtLeast(-2)) }) { Text("−") }
            Text(numS(adj.toString())); OutlinedButton({ Settings.put("hijri_adj", (adj + 1).coerceAtMost(2)) }) { Text("+") }
        }
        Sub(tr("التقويم يعتمد حساب أم القرى المضمّن في أندرويد وقد يختلف يومًا عن الإعلان الرسمي لبلدك.", "The calendar uses the Umm al-Qura calculation built into Android and may differ by a day from your country's official announcement."))
    }
}
