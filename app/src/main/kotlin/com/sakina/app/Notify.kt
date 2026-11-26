package com.sakina.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

data class Rem(val key: String, val whenMs: Long, val title: String, val text: String, val channel: String, val route: String = "home", val taskId: Long = -1, val day: Long = -1, val habitId: Long = -1)

object Notify {
    const val ACTION_FIRE = "com.sakina.app.FIRE"
    const val ACTION_SNOOZE = "com.sakina.app.SNOOZE"
    const val ACTION_DONE = "com.sakina.app.DONE"
    const val ACTION_FOCUS_END = "com.sakina.app.FOCUS_END"
    const val ACTION_FOCUS_STOP = "com.sakina.app.FOCUS_STOP"
    const val FOCUS_NOTIF = 9001
    val channelIds = listOf("prayer", "adhkar", "tasks", "health")

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        fun ch(id: String, ar: String, en: String, imp: Int) {
            val chn = NotificationChannel(id, tr(ar, en), imp); chn.description = tr(ar, en); nm.createNotificationChannel(chn)
        }
        ch("prayer", "الصلاة", "Prayer", NotificationManager.IMPORTANCE_HIGH)
        ch("adhkar", "الأذكار", "Adhkar", NotificationManager.IMPORTANCE_DEFAULT)
        ch("tasks", "المهام والدراسة", "Tasks & study", NotificationManager.IMPORTANCE_DEFAULT)
        ch("health", "الصحة والعادات", "Health & habits", NotificationManager.IMPORTANCE_LOW)
        ch("focus", "مؤقت التركيز", "Focus timer", NotificationManager.IMPORTANCE_LOW)
        val adhan = NotificationChannel("adhan", tr("الأذان", "Adhan"), NotificationManager.IMPORTANCE_HIGH)
        adhan.description = tr("صوت الأذان عند دخول وقت الصلاة", "Adhan sound at prayer time")
        adhan.setSound(android.net.Uri.parse("android.resource://" + c.packageName + "/" + R.raw.adhan),
            android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        nm.createNotificationChannel(adhan)
    }

    fun canNotify(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun at(day: LocalDate, minutes: Int): Long =
        LocalDateTime.of(day, LocalTime.MIN).plusMinutes(minutes.toLong()).atZone(zone()).toInstant().toEpochMilli()
    fun nextDaily(minutes: Int, now: Long): Long {
        val d = java.time.Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val a = at(d, minutes); return if (a > now) a else at(d.plusDays(1), minutes)
    }
    private fun on(k: String, d: Boolean) = Settings.sp.getBoolean(k, d)
    private fun iv(k: String, d: Int) = Settings.sp.getInt(k, d)

    /** Pure scheduling logic: what to remind next. Reads settings and the database. */
    fun build(c: Context, now: Long = System.currentTimeMillis()): List<Rem> {
        val out = ArrayList<Rem>()
        // prayers
        val d0 = java.time.Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val pre = iv("pre_min", 10)
        for (k in listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")) {
            val atOn = on("n_prayer_$k", k != "sunrise"); val preOn = on("nb_prayer_$k", false)
            if (!atOn && !preOn) continue
            var atDone = !atOn; var preDone = !preOn
            for (off in 0..2) {
                if (atDone && preDone) break
                val d = d0.plusDays(off.toLong()); val ms = PrayerSettings.millis(d, PrayerSettings.timesFor(d), k)
                if (!atDone && ms > now) { out.add(Rem("p_$k", ms, tr("حان وقت ${prayerName(k)}", "${prayerName(k)} time"), if (k == "sunrise") tr("طلعت الشمس", "The sun has risen") else tr("حان الآن موعد صلاة ${prayerName(k)}", "It is time for ${prayerName(k)}"), if (on("adhan_on", false) && k != "sunrise") "adhan" else "prayer", "prayer")); atDone = true }
                if (!preDone && ms - pre * 60_000L > now) { out.add(Rem("pb_$k", ms - pre * 60_000L, tr("اقترب وقت ${prayerName(k)}", "${prayerName(k)} soon"), tr("بقي $pre دقيقة على ${prayerName(k)}", "$pre minutes until ${prayerName(k)}"), "prayer", "prayer")); preDone = true }
            }
        }
        // adhkar
        if (on("n_morning", true)) out.add(Rem("a_morning", nextDaily(iv("t_morning", 6 * 60), now), tr("أذكار الصباح", "Morning adhkar"), tr("ابدأ يومك بذكر الله", "Start your day with remembrance"), "adhkar", "adhkar/morning"))
        if (on("n_evening", true)) out.add(Rem("a_evening", nextDaily(iv("t_evening", 17 * 60 + 30), now), tr("أذكار المساء", "Evening adhkar"), tr("حان وقت أذكار المساء", "Time for the evening adhkar"), "adhkar", "adhkar/evening"))
        if (on("n_sleepdhikr", true)) out.add(Rem("a_sleep", nextDaily(iv("t_sleepdhikr", 22 * 60 + 30), now), tr("أذكار النوم", "Before-sleep adhkar"), tr("حان وقت أذكار النوم", "Time for the before-sleep adhkar"), "adhkar", "adhkar/sleep"))
        // lifestyle
        if (on("n_wake", false)) out.add(Rem("l_wake", nextDaily(iv("wake_t", 6 * 60), now), tr("وقت الاستيقاظ", "Wake-up time"), tr("صباح الخير! يوم جديد", "Good morning! A new day"), "health"))
        if (on("n_bed", false)) out.add(Rem("l_bed", nextDaily(iv("bed_t", 23 * 60) - 30, now), tr("استعد للنوم", "Get ready for bed"), tr("بقي نصف ساعة على موعد نومك", "Half an hour until your bedtime"), "health"))
        if (on("n_study", false)) out.add(Rem("l_study", nextDaily(iv("t_study", 17 * 60), now), tr("وقت المذاكرة", "Study time"), tr("جلسة تركيز قصيرة؟", "A short focus session?"), "tasks", "study"))
        if (on("n_sport", false)) out.add(Rem("l_sport", nextDaily(iv("t_sport", 18 * 60), now), tr("وقت الرياضة", "Exercise time"), tr("حركة خفيفة تكفي اليوم", "A light workout is enough today"), "health", "habits"))
        if (on("n_water", false)) {
            val step = iv("water_h", 2) * 60; val wake = iv("wake_t", 6 * 60); val bed = iv("bed_t", 23 * 60)
            var best = Long.MAX_VALUE
            for (off in 0..1) { val d = d0.plusDays(off.toLong()); var m = wake + step; while (m < bed) { val ms = at(d, m); if (ms > now && ms < best) best = ms; m += step } }
            if (best != Long.MAX_VALUE) out.add(Rem("l_water", best, tr("اشرب ماءً", "Drink water"), tr("كوب ماء الآن", "Time for a glass of water"), "health"))
        }
        // tasks and habits from the database
        runCatching {
            val dao = AppDb.get(c).dao()
            val tasks = runBlocking { dao.tasksNow() }
            val t0 = d0.toEpochDay()
            for (t in tasks) {
                if (!t.remind || t.startMin < 0) continue
                for (off in 0..8) {
                    val d = t0 + off
                    if (!t.occursOn(d)) continue
                    val ms = at(dayOf(d), t.startMin) - t.remindBefore * 60_000L
                    if (ms > now) { out.add(Rem("t_${t.id}", ms, t.title, tr("المهمة تبدأ ${fmtMin(t.startMin)}", "Starts at ${fmtMin(t.startMin)}"), "tasks", "tasks", t.id, d)); break }
                }
            }
            if (on("n_habits", true)) for (h in runBlocking { dao.habitsNow() }) if (h.active && h.remindMin >= 0)
                out.add(Rem("h_${h.id}", nextDaily(h.remindMin, now), h.name, tr("تذكير بعادتك اليومية", "Your daily habit"), "health", "habits", habitId = h.id))
        }
        return out.sortedBy { it.whenMs }
    }

    private fun pi(c: Context, key: String, action: String, extras: Intent.() -> Unit): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action).also(extras)
        return PendingIntent.getBroadcast(c, key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun remPi(c: Context, r: Rem) = pi(c, r.key, ACTION_FIRE) {
        putExtra("key", r.key); putExtra("title", r.title); putExtra("text", r.text); putExtra("ch", r.channel); putExtra("route", r.route)
        putExtra("taskId", r.taskId); putExtra("day", r.day); putExtra("habitId", r.habitId)
    }

    fun schedule(c: Context, r: Rem) {
        val am = c.getSystemService(AlarmManager::class.java); val p = remPi(c, r)
        try {
            if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.whenMs, p) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.whenMs, p)
        } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.whenMs, p) }
    }

    @Synchronized fun rescheduleAll(c: Context) {
        Settings.init(c); createChannels(c)
        val am = c.getSystemService(AlarmManager::class.java)
        val old = Settings.sp.getString("alarm_keys", "")!!.split(",").filter { it.isNotEmpty() }
        for (k in old) am.cancel(pi(c, k, ACTION_FIRE) {})
        val rems = build(c)
        rems.forEach { schedule(c, it) }
        Settings.sp.edit().putString("alarm_keys", rems.joinToString(",") { it.key }).apply()
        NextPrayerWidget.update(c)
    }
    fun refreshAsync(c: Context) { val app = c.applicationContext; Thread { runCatching { rescheduleAll(app) } }.start() }

    fun ensureWorker(c: Context) {
        val req = PeriodicWorkRequestBuilder<ResyncWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("sakina_resync", ExistingPeriodicWorkPolicy.KEEP, req)
        val ab = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("sakina_autobackup", ExistingPeriodicWorkPolicy.KEEP, ab)
    }

    /** Background focus timer: an alarm ends the session and saves it even if the app is closed. */
    fun startFocus(c: Context, minutes: Int, kind: String) {
        Settings.init(c); createChannels(c)
        val now = System.currentTimeMillis(); val end = now + minutes * 60_000L
        Settings.sp.edit().putLong("focus_end", end).putLong("focus_start", now).putString("focus_kind", kind).apply()
        val am = c.getSystemService(AlarmManager::class.java); val p = pi(c, "focus_end", ACTION_FOCUS_END) {}
        try { if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, p) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, p) }
        catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, p) }
        if (!canNotify(c)) return
        val open = PendingIntent.getActivity(c, 9002, Intent(c, MainActivity::class.java).putExtra("route", "study").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(c, "focus").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(tr(if (kind == "study") "جلسة مذاكرة" else "جلسة تركيز", if (kind == "study") "Study session" else "Focus session"))
            .setContentText(tr("الوقت المتبقي", "Time left")).setUsesChronometer(true).setChronometerCountDown(true).setWhen(end)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, tr("إيقاف وحفظ", "Stop & save"), pi(c, "focus_stop", ACTION_FOCUS_STOP) {})
        try { NotificationManagerCompat.from(c).notify(FOCUS_NOTIF, b.build()) } catch (e: SecurityException) { }
    }
    /** Ends the active focus session once (alarm, notification button or UI), saving minutes done. Returns minutes saved, or -1 if none was active. Call off the main thread. */
    @Synchronized fun finishFocus(c: Context): Int {
        Settings.init(c)
        val end = Settings.sp.getLong("focus_end", 0L); if (end <= 0L) return -1
        val start = Settings.sp.getLong("focus_start", end); val kind = Settings.sp.getString("focus_kind", "focus") ?: "focus"
        Settings.sp.edit().remove("focus_end").remove("focus_start").remove("focus_kind").apply()
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, "focus_end", ACTION_FOCUS_END) {})
        NotificationManagerCompat.from(c).cancel(FOCUS_NOTIF)
        val done = ((minOf(System.currentTimeMillis(), end) - start) / 60_000L).toInt()
        if (done >= 1) runBlocking { AppDb.get(c).dao().addSession(StudySession(day = java.time.Instant.ofEpochMilli(start).atZone(zone()).toLocalDate().toEpochDay(), minutes = done, kind = kind)) }
        return done
    }

    fun show(c: Context, key: String, title: String, text: String, ch: String, route: String, taskId: Long, day: Long, snoozes: Int) {
        if (!canNotify(c)) return
        createChannels(c)
        val open = PendingIntent.getActivity(c, key.hashCode(), Intent(c, MainActivity::class.java).putExtra("route", route).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(c, ch).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
            .setPriority(if (ch == "prayer" || ch == "adhan") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setOnlyAlertOnce(true).setGroup("sakina_$ch")
        if (snoozes < 2) b.addAction(0, tr("تأجيل ١٠ دقائق", "Snooze 10 min"), pi(c, "sz_$key", ACTION_SNOOZE) {
            putExtra("key", key); putExtra("title", title); putExtra("text", text); putExtra("ch", ch); putExtra("route", route); putExtra("snoozes", snoozes + 1)
        })
        if (taskId >= 0) b.addAction(0, tr("تم", "Done"), pi(c, "dn_$key", ACTION_DONE) { putExtra("taskId", taskId); putExtra("day", day); putExtra("key", key) })
        try { NotificationManagerCompat.from(c).notify(key.hashCode(), b.build()) } catch (e: SecurityException) { }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Settings.init(c)
        val pr = goAsync()
        Thread {
            try {
                val key = i.getStringExtra("key") ?: ""
                when (i.action) {
                    Notify.ACTION_FIRE -> {
                        var skip = false
                        val taskId = i.getLongExtra("taskId", -1); val day = i.getLongExtra("day", -1); val habitId = i.getLongExtra("habitId", -1)
                        val dao = AppDb.get(c).dao()
                        if (taskId >= 0) skip = runBlocking { dao.taskDoneOn(day).any { it.taskId == taskId } || dao.task(taskId) == null }
                        if (habitId >= 0) skip = runBlocking { dao.habitLogsOn(today()).any { it.habitId == habitId } }
                        if (!skip) Notify.show(c, key, i.getStringExtra("title") ?: "", i.getStringExtra("text") ?: "", i.getStringExtra("ch") ?: "tasks", i.getStringExtra("route") ?: "home", taskId, day, 0)
                        Notify.rescheduleAll(c)
                    }
                    Notify.ACTION_SNOOZE -> {
                        NotificationManagerCompat.from(c).cancel(key.hashCode())
                        val r = Rem("snz_" + key.removePrefix("snz_"), System.currentTimeMillis() + 10 * 60_000L, i.getStringExtra("title") ?: "", i.getStringExtra("text") ?: "", i.getStringExtra("ch") ?: "tasks", i.getStringExtra("route") ?: "home")
                        Notify.schedule(c, r)
                    }
                    Notify.ACTION_FOCUS_END, Notify.ACTION_FOCUS_STOP -> {
                        val full = i.action == Notify.ACTION_FOCUS_END
                        val d = Notify.finishFocus(c)
                        if (full && d > 0) Notify.show(c, "focus_done", tr("انتهت الجلسة", "Session finished"), tr("أحسنت! تم حفظ ${num(d)} دقيقة.", "Well done! Saved $d min."), "tasks", "study", -1, -1, 2)
                    }
                    Notify.ACTION_DONE -> {
                        runBlocking { AppDb.get(c).dao().setTaskDone(TaskDone(i.getLongExtra("taskId", -1), i.getLongExtra("day", today()))) }
                        NotificationManagerCompat.from(c).cancel(key.hashCode())
                    }
                }
            } catch (e: Exception) { } finally { pr.finish() }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Settings.init(c); Notify.refreshAsync(c); Notify.ensureWorker(c) }
}

class ResyncWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result { Settings.init(applicationContext); Notify.rescheduleAll(applicationContext); return Result.success() }
}
