package com.sakina.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Backup {
    private val skip = setOf("android_metadata", "room_master_table", "sqlite_sequence")
    private val neverExported = setOf("alarm_keys", "ab_uri", "ab_last", "focus_end", "focus_start", "focus_kind")
    private val locationKeys = setOf("lat", "lng", "city")
    /** Which settings go into a backup: location only if the user opted in; folder and timer state never. */
    fun includePref(k: String, withLocation: Boolean) = k !in neverExported && (withLocation || k !in locationKeys)
    fun export(c: Context): String {
        val db = AppDb.get(c).openHelper.readableDatabase
        val root = JSONObject().put("app", "sakina").put("version", 1).put("exported", System.currentTimeMillis())
        val tables = JSONObject()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { t ->
            while (t.moveToNext()) {
                val name = t.getString(0); if (name in skip) continue
                val rows = JSONArray()
                db.query("SELECT * FROM `$name`").use { cur ->
                    while (cur.moveToNext()) {
                        val o = JSONObject()
                        for (i in 0 until cur.columnCount) when (cur.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> o.put(cur.getColumnName(i), cur.getLong(i))
                            android.database.Cursor.FIELD_TYPE_FLOAT -> o.put(cur.getColumnName(i), cur.getDouble(i))
                            android.database.Cursor.FIELD_TYPE_STRING -> o.put(cur.getColumnName(i), cur.getString(i))
                            else -> {}
                        }
                        rows.put(o)
                    }
                }
                tables.put(name, rows)
            }
        }
        root.put("tables", tables)
        val prefs = JSONObject()
        val withLoc = Settings.sp.getBoolean("backup_location", false)
        for ((k, v) in Settings.sp.all) if (includePref(k, withLoc)) prefs.put(k, v)
        root.put("prefs", prefs)
        return root.toString()
    }
    /** Returns null on success or an error message. */
    fun restore(c: Context, json: String): String? {
        val root = try { JSONObject(json) } catch (e: Exception) { return tr("الملف غير صالح", "Invalid file") }
        if (root.optString("app") != "sakina" || !root.has("tables")) return tr("هذا ليس ملف نسخة احتياطية لسكينة", "Not a Sakina backup file")
        val db = AppDb.get(c).openHelper.writableDatabase
        val tables = root.getJSONObject("tables")
        val existing = ArrayList<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { while (it.moveToNext()) { val n = it.getString(0); if (n !in skip) existing.add(n) } }
        db.beginTransaction()
        try {
            for (n in existing) db.execSQL("DELETE FROM `$n`")
            for (n in tables.keys()) {
                if (n !in existing) continue
                val rows = tables.getJSONArray(n)
                for (i in 0 until rows.length()) {
                    val o = rows.getJSONObject(i); val cv = ContentValues()
                    for (k in o.keys()) when (val v = o.get(k)) { is Int -> cv.put(k, v.toLong()); is Long -> cv.put(k, v); is Double -> cv.put(k, v); is String -> cv.put(k, v); is Boolean -> cv.put(k, if (v) 1 else 0) }
                    db.insert(n, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) { return tr("فشلت الاستعادة، لم يتغير شيء", "Restore failed, nothing changed") } finally { db.endTransaction() }
        val prefs = root.optJSONObject("prefs")
        if (prefs != null) { val e = Settings.sp.edit(); for (k in prefs.keys()) when (val v = prefs.get(k)) { is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v); is String -> e.putString(k, v) }; e.apply() }
        return null
    }
    fun wipe(c: Context) {
        AppDb.get(c).clearAllTables()
        Settings.sp.edit().clear().apply(); File(c.filesDir, "profile.jpg").delete()
    }
}

@Composable fun MoreScreen(go: Go) {
    val items = listOf(
        "adhkar" to tr("الأذكار والأدعية", "Adhkar & duas"), "nawawi" to tr("الأربعون النووية", "Forty Hadith"), "khatma" to tr("خطة الختمة", "Khatma plan"), "ramadan" to tr("رمضان", "Ramadan"), "levels" to tr("الإنجازات", "Achievements"), "tasbih" to tr("المسبحة", "Tasbih"), "calendar" to tr("التقويم", "Calendar"), "planner" to tr("مساعد الوقت", "Schedule assistant"),
        "study" to tr("الدراسة", "Study"), "coding" to tr("تعلم البرمجة", "Learn coding"), "habits" to tr("العادات والرياضة", "Habits & sport"), "stats" to tr("الإحصائيات", "Statistics"),
        "assistant" to tr("رفيق", "Rafiq"), "qibla" to tr("القبلة", "Qibla"), "hijri" to tr("التقويم الهجري", "Hijri calendar"), "notifications" to tr("الإشعارات", "Notifications"),
        "search" to tr("بحث شامل", "Search"), "privacy" to tr("سياسة الخصوصية", "Privacy policy"), "settings" to tr("الإعدادات", "Settings"), "about" to tr("عن التطبيق", "About")
    )
    Screen(tr("المزيد", "More")) {
        items.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (k, v) -> Glass(Modifier.weight(1f), onClick = { go(k) }, pad = 18) { Text(v, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        } }
        Sub("Developed by Yousef Esam", Modifier.fillMaxWidth())
    }
}

@Composable fun SettingsScreen(go: Go, back: () -> Unit) {
    val c = LocalContext.current; val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Settings.str("name")) }
    var msg by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(false) }
    var aiKey by remember { mutableStateOf("") }
    var avatarVer by remember { mutableIntStateOf(0) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? -> if (u != null) io(scope) { runCatching {
        val bmp = c.contentResolver.openInputStream(u)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
        File(c.filesDir, "profile.jpg").outputStream().use { bmp!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }; avatarVer++ } } }
    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u: Uri? -> if (u != null) io(scope) {
        msg = try { c.contentResolver.openOutputStream(u)!!.use { it.write(Backup.export(c).toByteArray()) }; tr("تم حفظ النسخة الاحتياطية", "Backup saved") } catch (e: Exception) { tr("فشل الحفظ", "Save failed") } } }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> if (u != null) io(scope) {
        val txt = try { c.contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() } } catch (e: Exception) { null }
        val err = if (txt == null) tr("تعذّرت قراءة الملف", "Could not read the file") else Backup.restore(c, txt)
        if (err == null) { Notify.refreshAsync(c); (c as? Activity)?.runOnUiThread { c.recreate() } } else msg = err } }
    val avatar = remember(avatarVer) { File(c.filesDir, "profile.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    Screen(tr("الإعدادات", "Settings"), back) {
        SectionLabel(tr("الملف الشخصي", "Profile"))
        Glass(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Emerald.copy(alpha = 0.2f)).clickable { photo.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (avatar != null) Image(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text("☾", color = GoldT, style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f)) { Field(name, { name = it; Settings.put("name", it.trim()) }, tr("الاسم", "Name")) }
            }
            Sub(tr("اضغط على الدائرة لاختيار صورة (تُحفظ على جهازك فقط).", "Tap the circle to choose a photo (kept on this device only)."))
        }
        SectionLabel(tr("المظهر واللغة", "Appearance & language"))
        Glass(Modifier.fillMaxWidth()) {
            Text(tr("اللغة", "Language"), style = MaterialTheme.typography.titleSmall)
            ChipRow(listOf("ar" to "العربية", "en" to "English"), Settings.lang) { Settings.put("lang", it); Notify.createChannels(c); Notify.refreshAsync(c) }
            Spacer(Modifier.height(8.dp)); Text(tr("الوضع", "Theme"), style = MaterialTheme.typography.titleSmall)
            ChipRow(listOf("0" to tr("حسب النظام", "System"), "1" to tr("فاتح", "Light"), "2" to tr("داكن", "Dark")), Settings.int("theme", 0).toString()) { Settings.put("theme", it.toInt()) }
            RowSwitch(tr("صوت التلاوة في القرآن", "Quran recitation audio"), Settings.bool("audio_on", true), { Settings.put("audio_on", it) }, tr("يعمل بالإنترنت. أوقفه لإخفاء أزرار التشغيل.", "Needs internet. Turn off to hide the play buttons."))
            Sub(tr("حجم الخط", "Text size"))
            ChipRow(listOf("0" to tr("عادي", "Normal"), "1" to tr("كبير", "Large"), "2" to tr("كبير جدًا", "Extra large")), Settings.int("font_size", 0).toString()) { Settings.put("font_size", it.toInt()) }
        }
        SectionLabel(tr("تخصيص الصفحة الرئيسية", "Customize home"))
        Glass(Modifier.fillMaxWidth()) {
            listOf("prayer" to tr("الصلاة القادمة", "Next prayer"), "quote" to tr("العبارة اليومية", "Daily phrase"), "wird" to tr("الورد", "Wird"), "tasks" to tr("المهام", "Tasks"), "adhkar" to tr("الأذكار", "Adhkar"),
                "study" to tr("الدراسة", "Study"), "coding" to tr("البرمجة", "Coding"), "habits" to tr("العادات", "Habits"), "sleep" to tr("النوم", "Sleep"), "ach" to tr("الإنجازات", "Wins")).forEach { (k, v) ->
                RowSwitch(v, Settings.bool("home_$k", true), { Settings.put("home_$k", it) })
            }
        }
        SectionLabel(tr("الصلاة والإشعارات", "Prayer & notifications"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Btn(tr("إعدادات الصلاة", "Prayer settings"), { go("prayer") }, Modifier.weight(1f), outlined = true); Btn(tr("الإشعارات", "Notifications"), { go("notifications") }, Modifier.weight(1f), outlined = true) }
        SectionLabel(tr("المساعد رفيق (اختياري)", "Rafiq assistant (optional)"))
        Glass(Modifier.fillMaxWidth()) {
            RowSwitch(tr("تفعيل الذكاء الاصطناعي عبر الإنترنت", "Enable online AI"), Settings.bool("ai_on", false), { Settings.put("ai_on", it) },
                tr("يرسل نص سؤالك فقط إلى الخدمة التي تحددها. لا تُرسل بياناتك الشخصية. التطبيق يعمل بدونه.", "Sends only the text you type to the service you choose. No personal data is sent. The app works without it."))
            Field(Settings.str("ai_url", "https://api.openai.com/v1/chat/completions"), { Settings.put("ai_url", it.trim()) }, tr("عنوان الخدمة (متوافق مع OpenAI)", "Endpoint (OpenAI-compatible)"))
            Field(Settings.str("ai_model", "gpt-4o-mini"), { Settings.put("ai_model", it.trim()) }, tr("النموذج", "Model"))
            Field(aiKey, { aiKey = it }, tr("مفتاح API (يُخزَّن مشفّرًا على جهازك)", "API key (stored encrypted on this device)"))
            Btn(tr("حفظ المفتاح", "Save key"), { if (aiKey.isNotBlank()) { Rafiq.saveKey(c, aiKey.trim()); aiKey = ""; msg = tr("تم حفظ المفتاح", "Key saved") } }, outlined = true)
        }
        SectionLabel(tr("النسخ الاحتياطي والبيانات", "Backup & data"))
        Glass(Modifier.fillMaxWidth()) {
            Sub(tr("كل بياناتك محفوظة على جهازك فقط. النسخ الاحتياطي ملف تحفظه أنت حيث تريد. لا توجد مزامنة سحابية.", "All your data stays on this device. A backup is a file you save wherever you like. There is no cloud sync."))
            Btn(tr("تصدير / نسخة احتياطية", "Export / backup"), { exportL.launch("sakina-backup-${today()}.json") }, Modifier.fillMaxWidth())
            Btn(tr("استعادة من ملف", "Restore from file"), { importL.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.fillMaxWidth(), outlined = true)
            TextButton({ confirmWipe = true }) { Text(tr("حذف كل بياناتي", "Delete all my data"), color = DangerT) }
        }
        AutoBackupCard()
        if (msg.isNotEmpty()) Body(msg)
    }
    if (confirmWipe) Confirm(tr("حذف كل البيانات", "Delete all data"), tr("سيُحذف كل شيء نهائيًا من هذا الجهاز (مهام، عادات، تقدم، إعدادات). لا يمكن التراجع. هل صدّرت نسخة؟", "Everything on this device is erased permanently (tasks, habits, progress, settings). This cannot be undone. Did you export a backup?"), {
        confirmWipe = false; io(scope) { Backup.wipe(c); Notify.refreshAsync(c); (c as? Activity)?.runOnUiThread { c.recreate() } }
    }, { confirmWipe = false })
}

@Composable fun NotificationsScreen(back: () -> Unit) {
    val c = LocalContext.current
    var granted by remember { mutableStateOf(Notify.canNotify(c)) }
    var exact by remember { mutableStateOf(Notify.canExact(c)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it || Notify.canNotify(c); Notify.refreshAsync(c) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) { val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { granted = Notify.canNotify(c); exact = Notify.canExact(c) } }; lifecycle.addObserver(ob); onDispose { lifecycle.removeObserver(ob) } }
    fun sw(k: String, d: Boolean) = Settings.bool(k, d)
    fun set(k: String, v: Any) { Settings.put(k, v); Notify.refreshAsync(c) }
    Screen(tr("الإشعارات", "Notifications"), back) {
        Glass(Modifier.fillMaxWidth()) {
            Text(tr("الأذونات", "Permissions"), style = MaterialTheme.typography.titleSmall)
            Sub(tr("نحتاج إذن الإشعارات لنذكّرك، وإذن المنبهات الدقيقة لتصل التذكيرات في وقتها. لا نستخدم أي إذن آخر للتنبيهات.", "We need notification permission to remind you, and exact-alarm permission so reminders arrive on time."))
            Row(verticalAlignment = Alignment.CenterVertically) { Text((if (granted) "✓ " else "✗ ") + tr("الإشعارات", "Notifications"), Modifier.weight(1f)); if (!granted) Btn(tr("اسمح", "Allow"), { if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS) else c.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, c.packageName)) }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text((if (exact) "✓ " else "✗ ") + tr("المنبهات الدقيقة", "Exact alarms"), Modifier.weight(1f)); if (!exact && Build.VERSION.SDK_INT >= 31) Btn(tr("افتح الإعدادات", "Open settings"), { c.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${c.packageName}"))) }) }
            Sub(tr("إن لم تصلك التذكيرات على بعض الهواتف (هواوي، شاومي…) أوقف تحسين البطارية لسكينة من إعدادات النظام.", "On some phones (Huawei, Xiaomi…) reminders may be delayed unless you disable battery optimisation for Sakina in system settings."))
        }
        SectionLabel(tr("الصلاة", "Prayer"))
        Glass(Modifier.fillMaxWidth()) {
            RowSwitch(tr("صوت الأذان", "Adhan sound"), sw("adhan_on", false), { set("adhan_on", it) }, tr("يُشغَّل عند دخول وقت الفجر والظهر والعصر والمغرب والعشاء. التسجيل مرخّص CC0. غيّر الإعدادات من قناة «الأذان» في إعدادات النظام لتغيير الصوت أو مستواه.", "Plays at Fajr, Dhuhr, Asr, Maghrib and Isha. CC0-licensed recording. Use the Adhan channel in system settings to change the sound or volume."))
            Text(tr("عند دخول الوقت", "At prayer time"), style = MaterialTheme.typography.titleSmall)
            prayerKeys.forEach { k -> RowSwitch(prayerName(k), sw("n_prayer_$k", k != "sunrise"), { set("n_prayer_$k", it) }) }
            Spacer(Modifier.height(8.dp)); Text(tr("قبل الصلاة", "Before prayer"), style = MaterialTheme.typography.titleSmall)
            ChipRow(listOf(5, 10, 15, 20, 30).map { it.toString() to numS("$it ${tr("د", "m")}") }, Settings.int("pre_min", 10).toString()) { set("pre_min", it.toInt()) }
            prayerKeys.filter { it != "sunrise" }.forEach { k -> RowSwitch(prayerName(k), sw("nb_prayer_$k", false), { set("nb_prayer_$k", it) }) }
        }
        SectionLabel(tr("الأذكار", "Adhkar"))
        Glass(Modifier.fillMaxWidth()) {
            RowSwitch(tr("أذكار الصباح", "Morning adhkar"), sw("n_morning", true), { set("n_morning", it) }); TimeField(tr("الوقت", "Time"), Settings.int("t_morning", 360), { set("t_morning", it) })
            RowSwitch(tr("أذكار المساء", "Evening adhkar"), sw("n_evening", true), { set("n_evening", it) }); TimeField(tr("الوقت", "Time"), Settings.int("t_evening", 1050), { set("t_evening", it) })
            RowSwitch(tr("أذكار النوم", "Before-sleep adhkar"), sw("n_sleepdhikr", true), { set("n_sleepdhikr", it) }); TimeField(tr("الوقت", "Time"), Settings.int("t_sleepdhikr", 1350), { set("t_sleepdhikr", it) })
        }
        SectionLabel(tr("الحياة اليومية", "Daily life"))
        Glass(Modifier.fillMaxWidth()) {
            RowSwitch(tr("تذكير المهام", "Task reminders"), sw("n_tasks", true), { set("n_tasks", it) }, tr("يُضبط من كل مهمة", "Set per task"))
            RowSwitch(tr("تذكير العادات", "Habit reminders"), sw("n_habits", true), { set("n_habits", it) })
            RowSwitch(tr("وقت المذاكرة", "Study time"), sw("n_study", false), { set("n_study", it) }); if (sw("n_study", false)) TimeField(tr("الوقت", "Time"), Settings.int("t_study", 1020), { set("t_study", it) })
            RowSwitch(tr("الرياضة", "Exercise"), sw("n_sport", false), { set("n_sport", it) }); if (sw("n_sport", false)) TimeField(tr("الوقت", "Time"), Settings.int("t_sport", 1080), { set("t_sport", it) })
            RowSwitch(tr("شرب الماء", "Drink water"), sw("n_water", false), { set("n_water", it) }, tr("بين الاستيقاظ والنوم فقط", "Only between wake and sleep")); if (sw("n_water", false)) ChipRow(listOf(2, 3, 4).map { it.toString() to numS(tr("كل $it ساعات", "every $it h")) }, Settings.int("water_h", 2).toString()) { set("water_h", it.toInt()) }
            RowSwitch(tr("الاستيقاظ", "Wake-up"), sw("n_wake", false), { set("n_wake", it) }); TimeField(tr("وقت الاستيقاظ", "Wake time"), Settings.int("wake_t", 360), { set("wake_t", it) })
            RowSwitch(tr("الاستعداد للنوم", "Bedtime"), sw("n_bed", false), { set("n_bed", it) }); TimeField(tr("وقت النوم", "Bedtime"), Settings.int("bed_t", 1380), { set("bed_t", it) })
        }
        SectionLabel(tr("الصوت", "Sound"))
        Glass(Modifier.fillMaxWidth()) {
            Sub(tr("اختر نغمة كل نوع من إعدادات النظام.", "Choose each type's sound in system settings."))
            Notify.channelIds.forEach { id -> OutlinedButton({ c.startActivity(Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, c.packageName).putExtra(AndroidSettings.EXTRA_CHANNEL_ID, id)) }, Modifier.fillMaxWidth()) { Text(tr("صوت: ", "Sound: ") + when (id) { "prayer" -> tr("الصلاة", "Prayer"); "adhkar" -> tr("الأذكار", "Adhkar"); "tasks" -> tr("المهام", "Tasks"); else -> tr("الصحة", "Health") }) } }
        }
        Btn(tr("أرسل إشعارًا تجريبيًا", "Send a test notification"), { Notify.show(c, "test", tr("سَكِينَة", "Sakina"), tr("الإشعارات تعمل ✓ يمكنك تأجيل هذا التنبيه.", "Notifications work ✓ You can snooze this one."), "adhkar", "home", -1, -1, 0) }, Modifier.fillMaxWidth())
        val upcoming = remember(Settings.tick.intValue) { runCatching { Notify.build(c).take(5) }.getOrDefault(emptyList()) }
        if (upcoming.isNotEmpty()) { SectionLabel(tr("التذكيرات القادمة", "Upcoming reminders")); upcoming.forEach { Sub(numS(fmtTimeMs(it.whenMs)) + " · " + it.title) } }
    }
}

@Composable fun SearchScreen(go: Go, back: () -> Unit) {
    val c = LocalContext.current; val dao = rememberDao()
    var q by remember { mutableStateOf("") }
    val tasks by dao.tasks().collectAsState(emptyList())
    val subjects by dao.subjects().collectAsState(emptyList())
    var ayahs by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    var surahs by remember { mutableStateOf<List<SurahData>>(emptyList()) }
    var adhk by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    LaunchedEffect(q) {
        val r = withContext(Dispatchers.IO) {
            if (q.trim().length < 2) Triple(emptyList<Triple<Int, Int, String>>(), emptyList<SurahData>(), emptyList<Pair<Int, String>>()) else {
                val nq = normAr(q.trim())
                Triple(Quran.searchAyahs(c, q, 15), Quran.searchSurahs(c, q).take(8), Adhkar.load(c).filter { normAr(it.value.first).contains(nq) || it.value.second.any { d -> normAr(d.first).contains(nq) } }.map { it.key to it.value.first }.take(10))
            }
        }
        ayahs = r.first; surahs = r.second; adhk = r.third
    }
    val nq = normAr(q.trim())
    val tHits = if (nq.length < 2) emptyList() else tasks.filter { normAr(it.title + it.notes).contains(nq) }.take(10)
    val sHits = if (nq.length < 2) emptyList() else subjects.filter { normAr(it.name).contains(nq) }
    val ar = Settings.isAr
    LazyScreen(tr("بحث شامل", "Search"), back) {
        item { Field(q, { q = it }, tr("ابحث في القرآن والأذكار والمهام والمواد", "Search Quran, adhkar, tasks, subjects")) }
        if (nq.length < 2) item { Empty(tr("اكتب حرفين على الأقل.", "Type at least 2 characters.")) }
        else if (ayahs.isEmpty() && surahs.isEmpty() && adhk.isEmpty() && tHits.isEmpty() && sHits.isEmpty()) item { Empty(tr("لا نتائج.", "No results.")) }
        if (surahs.isNotEmpty()) { item { SectionLabel(tr("السور", "Surahs")) }; items(surahs) { s -> Glass(Modifier.fillMaxWidth(), onClick = { go("quran/${s.n}") }, pad = 12) { Text(numS("${s.n}. ") + Quran.surahName(s)) } } }
        if (ayahs.isNotEmpty()) { item { SectionLabel(tr("آيات", "Ayahs")) }; items(ayahs) { (s, a, t) -> Glass(Modifier.fillMaxWidth(), onClick = { go("quran/$s?a=$a") }, pad = 12) { Text(t, maxLines = 2); Sub(numS("$s:$a")) } } }
        if (adhk.isNotEmpty()) { item { SectionLabel(tr("الأذكار", "Adhkar")) }; items(adhk) { (id, n) -> Glass(Modifier.fillMaxWidth(), onClick = { go("adhkarcat/$id") }, pad = 12) { Text(n) } } }
        if (tHits.isNotEmpty()) { item { SectionLabel(tr("المهام", "Tasks")) }; items(tHits) { t -> Glass(Modifier.fillMaxWidth(), onClick = { go("tasks") }, pad = 12) { Text(t.title); Sub(catName(t.category)) } } }
        if (sHits.isNotEmpty()) { item { SectionLabel(tr("المواد", "Subjects")) }; items(sHits) { s -> Glass(Modifier.fillMaxWidth(), onClick = { go("study") }, pad = 12) { Text(s.name) } } }
    }
}

@Composable fun AboutScreen(back: () -> Unit) {
    Screen(tr("عن سَكِينَة", "About Sakina"), back) {
        Glass(Modifier.fillMaxWidth()) {
            Text("SAKINA | سَكِينَة", style = MaterialTheme.typography.headlineSmall, color = GoldT)
            Body(tr("نظّم حياتك، اقترب من ربك، واصنع مستقبلك.", "Organize your life, draw closer to your Lord, and build your future."))
            Sub(tr("الإصدار 1.3", "Version 1.3"))
            Sub("Developed by Yousef Esam")
            Sub(tr("صُنع بحب ليوسف", "Made with love for Yousef"))
        }
        SectionLabel(tr("المصادر", "Sources"))
        Glass(Modifier.fillMaxWidth()) {
            Body(tr("• نص القرآن: النص العثماني من مشروع تنزيل (tanzil.net) عبر alquran.cloud.\n• التفسير: التفسير الميسر، مجمع الملك فهد لطباعة المصحف الشريف (عبر alquran.cloud).\n• الأذكار: حصن المسلم، سعيد بن علي بن وهف القحطاني.\n• التلاوة: مشاري العفاسي، everyayah.com (تحتاج إنترنت).\n• الأربعون النووية: للإمام النووي، عبر hadith-api.\n• الأذان: تسجيل Beautiful adhan.ogg من ويكيميديا كومنز (CC0).\n• الخطوط: Amiri وTajawal (رخصة OFL).", "• Quran text: Uthmani script from the Tanzil project via alquran.cloud.\n• Tafsir: Al-Tafsir al-Muyassar, King Fahd Complex (via alquran.cloud).\n• Adhkar: Hisn al-Muslim by Sa'id al-Qahtani.\n• Recitation: Mishary Alafasy via everyayah.com (internet needed).\n• Forty Hadith of an-Nawawi via hadith-api.\n• Adhan: Beautiful adhan.ogg from Wikimedia Commons (CC0).\n• Fonts: Amiri and Tajawal (OFL)."))
        }
        SectionLabel(tr("دعم المطوّر", "Developer support"))
        Glass(Modifier.fillMaxWidth()) {
            Body(tr("طوّر التطبيق: يوسف عصام (Yousef Esam). التطبيق مجاني بلا إعلانات. دعمك الأفضل أن تنشره وتدعو من تحب، وأن تدعو للمطوّر بظهر الغيب.", "Developed by Yousef Esam. The app is free with no ads. The best support is to share it with people you care about, and to make dua for the developer."))
            LinkRow(tr("موقع التطبيق", "App website"), "https://skina.web.app")
            LinkRow(tr("صفحة التحميل والإصدارات", "Downloads and releases"), "https://github.com/Kaiox1290/sakina-app/releases")
        }
        SectionLabel(tr("ادعُ أصدقاءك", "Invite friends"))
        Glass(Modifier.fillMaxWidth()) {
            val ctx = LocalContext.current
            var used by remember { mutableStateOf(Settings.sp.getInt("invites_used", 0)) }
            Body(tr("تُفتح قائمة المشاركة في أندرويد، فتختار واتساب أو ماسنجر أو غيرهما. التطبيق لا يقرأ جهات اتصالك ولا يعرف من استلم الدعوة. الحد: 5 دعوات.", "This opens Android's share sheet so you can pick WhatsApp, Messenger or anything else. The app cannot read your contacts and does not know who received it. Limit: 5 invites."))
            Sub(tr("الدعوات المفتوحة: $used من 5", "Invites started: $used of 5"))
            Button(onClick = {
                if (used < 5) {
                    used += 1; Settings.sp.edit().putInt("invites_used", used).apply()
                    val t = tr("جرّب تطبيق سَكِينَة: مواقيت الصلاة والأذكار والقرآن وتنظيم يومك، مجاني وبلا إعلانات.\nhttps://skina.web.app", "Try SAKINA: prayer times, adhkar, Quran and a day planner. Free, no ads.\nhttps://skina.web.app")
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), tr("دعوة", "Invite")))
                }
            }, enabled = used < 5, modifier = Modifier.fillMaxWidth()) { Text(tr("ادعُ صديقًا", "Invite a friend")) }
        }
        SectionLabel(tr("الترخيص والشكر", "Licence and credits"))
        Glass(Modifier.fillMaxWidth()) {
            Body(tr("كود التطبيق © 2026 يوسف عصام، مفتوح المصدر برخصة GPL-3.0 (الرخصة وملف المصادر على صفحة المشروع في GitHub). الرخصة تخص الكود فقط. المحتوى الديني والصوتي والخطوط لأصحابها وبشروطهم الخاصة أدناه، ولا يجوز تعديل نص القرآن. التطبيق يُقدَّم كما هو بلا ضمان.", "App code (c) 2026 Yousef Esam, open source under GPL-3.0 (licence and notices are in the project repository on GitHub). The licence covers the code only. Religious content, audio and fonts belong to their owners under their own terms, listed below, and the Quran text must not be changed. The app is provided as is, without warranty."))
            Sub(tr("نص القرآن: Tanzil Project، رخصة CC BY 3.0، من دون أي تعديل.", "Quran text: Tanzil Project, CC BY 3.0, unmodified."))
            LinkRow("tanzil.net", "https://tanzil.net")
            LinkRow(tr("الكود والتراخيص (GitHub)", "Source code and licences (GitHub)"), "https://github.com/Kaiox1290/sakina-app")
            Sub(tr("التلاوة (مشاري العفاسي) تُبث من everyayah.com.", "Recitation (Mishary Alafasy) is streamed from everyayah.com."))
            LinkRow("everyayah.com", "https://everyayah.com")
            Sub(tr("التفسير الميسر (مجمع الملك فهد) عبر alquran.cloud.", "Al-Muyassar tafsir (King Fahd Complex) via alquran.cloud."))
            LinkRow("alquran.cloud", "https://alquran.cloud")
            Sub(tr("الأذكار: حصن المسلم عبر rn0x/Adhkar-json. الأربعون النووية (الإنجليزية): fawazahmed0/hadith-api (Unlicense). الأذان: Wikimedia Commons (CC0).", "Adhkar: Hisn al-Muslim via rn0x/Adhkar-json. Forty Hadith English: fawazahmed0/hadith-api (Unlicense). Adhan: Wikimedia Commons (CC0)."))
            LinkRow("github.com/rn0x/Adhkar-json", "https://github.com/rn0x/Adhkar-json")
            LinkRow("github.com/fawazahmed0/hadith-api", "https://github.com/fawazahmed0/hadith-api")
        }
        SectionLabel(tr("الخصوصية", "Privacy"))
        Glass(Modifier.fillMaxWidth()) {
            Body(tr("بياناتك تبقى على جهازك. الموقع يُستخدم لحساب المواقيت فقط ويُحفظ محليًا ولا يُرسل لأي جهة. الإنترنت يُستخدم فقط لتشغيل التلاوة، وللمساعد إن فعّلته بنفسك.", "Your data stays on your device. Location is used only to calculate prayer times, stored locally and never sent anywhere. The internet is used only for recitation audio and, if you enable it, the assistant."))
        }
        Sub(tr("التطبيق أداة مساعدة ولا يصدر فتاوى. المواقيت محسوبة وقد تحتاج لتعديل بسيط ليوافق مسجدك.", "This app is an aid and issues no fatwas. Prayer times are calculated and may need a small adjustment to match your mosque."))
    }
}

@Composable fun Onboarding(done: () -> Unit) {
    val c = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Notify.refreshAsync(c); step++ }
    val loc = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) fetchLocation(c) { l -> if (l != null) { Settings.put("lat", l.latitude); Settings.put("lng", l.longitude); Settings.put("city", tr("موقعي الحالي", "My location")) } }; step++ }
    Screen("SAKINA | سَكِينَة") {
        when (step) {
            0 -> {
                Text("☾", style = MaterialTheme.typography.displayLarge, color = GoldT, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Title(tr("أهلًا بك في سَكِينَة", "Welcome to Sakina"))
                Body(tr("نظّم حياتك، اقترب من ربك، واصنع مستقبلك.", "Organize your life, draw closer to your Lord, and build your future."))
                ChipRow(listOf("ar" to "العربية", "en" to "English"), Settings.lang) { Settings.put("lang", it) }
                Field(name, { name = it }, tr("ما اسمك؟", "What's your name?"))
                Btn(tr("التالي", "Next"), { Settings.put("name", name.trim()); step = 1 }, Modifier.fillMaxWidth())
            }
            1 -> {
                Title(tr("موقعك لمواقيت الصلاة", "Your location for prayer times"))
                Body(tr("نستخدم موقعك التقريبي لحساب المواقيت واتجاه القبلة فقط. يُحفظ على جهازك ولا يُرسل لأحد. تقدر تختار مدينة بدلًا من ذلك.", "We use your approximate location only to calculate prayer times and Qibla. It stays on your device and is never sent anywhere. You can pick a city instead."))
                Btn(tr("استخدم موقعي", "Use my location"), { loc.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, Modifier.fillMaxWidth())
                Text(tr("أو اختر مدينة:", "Or choose a city:"), style = MaterialTheme.typography.titleSmall)
                ChipRow(presetCities.map { it.name to placeName(it) }, "") { n -> val p = presetCities.first { it.name == n }; Settings.put("lat", p.lat); Settings.put("lng", p.lng); Settings.put("city", placeName(p)); step = 2 }
                TextButton({ step = 2 }) { Text(tr("تخطَّ (المنصورة افتراضيًا)", "Skip (defaults to Mansoura)")) }
            }
            2 -> {
                Title(tr("التذكيرات", "Reminders"))
                Body(tr("لنذكّرك بالصلاة والأذكار والمهام نحتاج إذن الإشعارات. لن نرسل إلا ما تفعّله أنت، وتقدر تغيّر كل شيء من الإعدادات.", "To remind you about prayers, adhkar and tasks we need notification permission. We only send what you enable, and you can change everything in settings."))
                Btn(tr("السماح بالإشعارات", "Allow notifications"), { if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS) else step = 3 }, Modifier.fillMaxWidth())
                TextButton({ step = 3 }) { Text(tr("لاحقًا", "Later")) }
            }
            else -> {
                Title(tr("جاهز!", "All set!"))
                Body(tr("ابدأ بالرئيسية. كل قسم على بعد ضغطة أو ضغطتين. مواقيت الصلاة تقديرية فتأكد منها مع مسجدك وعدّلها من قسم الصلاة.", "Start on Home. Every section is one or two taps away. Prayer times are calculated estimates; check them against your mosque and adjust in Prayer."))
                Btn(tr("ابدأ", "Start"), { Settings.put("onboarded", true); Notify.refreshAsync(c); done() }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable fun LinkRow(label: String, url: String) {
    val c = LocalContext.current
    TextButton({ runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }) { Text(label, color = GoldT) }
}
