package com.ishwar.smartalarm.data

import android.content.Context
import com.ishwar.smartalarm.alarm.AlarmEngine
import com.ishwar.smartalarm.util.TimeUtil
import java.io.File

/**
 * Saara business logic yahan: status nikalna, occurrences banana, sets activate karna,
 * aur har badlaav ke baad alarms dobara set karna.
 */
object Repo {
    lateinit var dao: AppDao
        private set
    lateinit var app: Context
        private set

    fun init(ctx: Context) {
        if (::dao.isInitialized) return
        app = ctx.applicationContext
        Prefs.init(app)
        dao = AppDatabase.get(app).dao()
    }

    // ---------------- Rules ----------------

    fun setCoversDay(s: ScheduleSet, day: Long): Boolean =
        (s.startDay == null || day >= s.startDay) && (s.endDay == null || day <= s.endDay)

    fun taskOnDay(t: RoutineTask, day: Long): Boolean =
        (t.days and (1 shl TimeUtil.weekdayIndex(day))) != 0

    fun reminderOnDay(r: Reminder, day: Long): Boolean {
        if (day < r.day) return false
        val a = TimeUtil.date(day)
        val b = TimeUtil.date(r.day)
        return when (r.repeat) {
            REPEAT_NONE -> day == r.day
            REPEAT_DAILY -> true
            REPEAT_WEEKLY -> (day - r.day) % 7 == 0L
            REPEAT_MONTHLY -> a.dayOfMonth == b.dayOfMonth
            REPEAT_YEARLY -> a.dayOfMonth == b.dayOfMonth && a.monthValue == b.monthValue
            else -> false
        }
    }

    fun startMillis(o: Occurrence): Long = TimeUtil.millis(o.day, o.startMin)

    /** Iske baad task "Missed" maana jaayega: end time, ya end time na ho to 1 ghanta. */
    fun deadlineMillis(o: Occurrence): Long {
        val e = o.endMin
        val end = when {
            e == null || e == o.startMin -> o.startMin + 60
            e > o.startMin -> e
            else -> e + 1440 // raat ki shift: agle din khatam
        }
        return TimeUtil.millis(o.day, end)
    }

    fun status(o: Occurrence, now: Long = System.currentTimeMillis()): TaskStatus = when (o.status) {
        ST_COMPLETED -> TaskStatus.COMPLETED
        ST_MISSED -> TaskStatus.MISSED
        ST_KEPT_PENDING -> TaskStatus.PENDING
        else -> when {
            now < startMillis(o) -> TaskStatus.UPCOMING
            now < deadlineMillis(o) -> TaskStatus.PENDING
            else -> TaskStatus.MISSED
        }
    }

    // ---------------- Occurrences ----------------

    suspend fun ensureRoutineOcc(t: RoutineTask, day: Long): Occurrence {
        val ex = dao.findOcc(KIND_ROUTINE, t.id, day)
        if (ex != null) {
            if (ex.status == ST_AUTO && (ex.title != t.name || ex.description != t.description ||
                    ex.startMin != t.startMin || ex.endMin != t.endMin)
            ) {
                val u = ex.copy(title = t.name, description = t.description, startMin = t.startMin, endMin = t.endMin)
                dao.updateOcc(u)
                return u
            }
            return ex
        }
        val o = Occurrence(
            kind = KIND_ROUTINE, sourceId = t.id, setId = t.setId, title = t.name,
            description = t.description, day = day, startMin = t.startMin, endMin = t.endMin
        )
        val id = dao.insertOcc(o)
        return if (id > 0) o.copy(id = id) else dao.findOcc(KIND_ROUTINE, t.id, day) ?: o
    }

    suspend fun ensureReminderOcc(r: Reminder, day: Long): Occurrence {
        val ex = dao.findOcc(KIND_REMINDER, r.id, day)
        if (ex != null) {
            if (ex.status == ST_AUTO && (ex.title != r.title || ex.description != r.description || ex.startMin != r.timeMin)) {
                val u = ex.copy(title = r.title, description = r.description, startMin = r.timeMin)
                dao.updateOcc(u)
                return u
            }
            return ex
        }
        val o = Occurrence(
            kind = KIND_REMINDER, sourceId = r.id, title = r.title,
            description = r.description, day = day, startMin = r.timeMin
        )
        val id = dao.insertOcc(o)
        return if (id > 0) o.copy(id = id) else dao.findOcc(KIND_REMINDER, r.id, day) ?: o
    }

    /** Us din ke saare tasks + reminders ki list taiyaar karo. */
    suspend fun generateFor(day: Long) {
        val valid = HashSet<String>()
        val set = dao.activeSet()
        if (set != null && setCoversDay(set, day)) {
            val since = maxOf(set.activatedAt, 0L)
            for (t in dao.tasks(set.id)) {
                val startsAt = TimeUtil.millis(day, t.startMin)
                // naya task / naya active set: pichhle time wale aaj missed na dikhen
                if (taskOnDay(t, day) && startsAt >= maxOf(t.createdAt, since)) {
                    ensureRoutineOcc(t, day)
                    valid.add("0_${t.id}")
                }
            }
        }
        for (r in dao.reminders()) {
            if (reminderOnDay(r, day)) {
                ensureReminderOcc(r, day)
                valid.add("1_${r.id}")
            }
        }
        for (o in dao.occurrences(day)) {
            if (o.status == ST_AUTO && "${o.kind}_${o.sourceId}" !in valid) dao.deleteOcc(o.id)
        }
    }

    /** App khulne par / alarm ke baad: aaj ki list update + purane missed mark karo. */
    suspend fun refresh() {
        val today = TimeUtil.today()
        val last = Prefs.lastGeneratedDay
        if (last in 0 until today) {
            var d = maxOf(last + 1, today - 7)
            while (d < today) { generateFor(d); d++ }
        }
        generateFor(today)
        Prefs.lastGeneratedDay = today
        val now = System.currentTimeMillis()
        for (o in dao.openAutoOcc(today)) {
            if (now >= deadlineMillis(o)) dao.updateOcc(o.copy(status = ST_MISSED))
        }
    }

    suspend fun complete(id: Long) {
        val o = dao.occ(id) ?: return
        dao.updateOcc(o.copy(status = ST_COMPLETED, completedAt = System.currentTimeMillis()))
        AlarmEngine.cancelOccAlarms(app, id)
    }

    suspend fun keepPending(id: Long) {
        val o = dao.occ(id) ?: return
        dao.updateOcc(o.copy(status = ST_KEPT_PENDING))
    }

    suspend fun markMissed(id: Long) {
        val o = dao.occ(id) ?: return
        dao.updateOcc(o.copy(status = ST_MISSED))
    }

    suspend fun deleteOcc(id: Long) {
        AlarmEngine.cancelOccAlarms(app, id)
        dao.deleteOcc(id)
    }

    // ---------------- Schedule sets ----------------

    suspend fun saveSet(s: ScheduleSet): Long {
        val id = if (s.id == 0L) dao.insertSet(s.copy(active = false)) else {
            val old = dao.set(s.id)
            dao.updateSet(s.copy(active = old?.active ?: false, activatedAt = old?.activatedAt ?: 0))
            s.id
        }
        val old = dao.set(id)
        if (s.active && old?.active != true) activateSet(id)
        else if (!s.active && old?.active == true) deactivateSet(id)
        else afterChange()
        return id
    }

    suspend fun activateSet(id: Long) {
        val s = dao.set(id) ?: return
        dao.deactivateAllSets()
        dao.updateSet(s.copy(active = true, activatedAt = System.currentTimeMillis()))
        afterChange()
    }

    suspend fun deactivateSet(id: Long) {
        val s = dao.set(id) ?: return
        dao.updateSet(s.copy(active = false))
        afterChange()
    }

    suspend fun renameSet(id: Long, name: String) {
        val s = dao.set(id) ?: return
        dao.updateSet(s.copy(name = name))
    }

    suspend fun duplicateSet(id: Long, copyLabel: String): Long {
        val s = dao.set(id) ?: return -1
        val newId = dao.insertSet(
            s.copy(id = 0, name = "${s.name} ($copyLabel)", active = false, activatedAt = 0, createdAt = System.currentTimeMillis())
        )
        for (t in dao.tasks(id)) dao.insertTask(t.copy(id = 0, setId = newId, createdAt = System.currentTimeMillis()))
        return newId
    }

    suspend fun deleteSet(id: Long) {
        for (t in dao.tasks(id)) {
            AlarmEngine.cancelMain(app, KIND_ROUTINE, t.id)
            dao.deleteFutureAutoOcc(KIND_ROUTINE, t.id, TimeUtil.today())
        }
        dao.deleteTasksOfSet(id)
        dao.deleteSet(id)
        afterChange()
    }

    // ---------------- Tasks ----------------

    suspend fun saveTask(t: RoutineTask): Long {
        val id = if (t.id == 0L) dao.insertTask(t) else { dao.updateTask(t); t.id }
        val saved = dao.task(id) ?: return id
        AlarmEngine.scheduleTask(app, saved)
        generateFor(TimeUtil.today())
        return id
    }

    suspend fun setTaskAlarm(t: RoutineTask, on: Boolean) {
        saveTask(t.copy(alarmEnabled = on))
    }

    suspend fun deleteTask(t: RoutineTask) {
        AlarmEngine.cancelMain(app, KIND_ROUTINE, t.id)
        dao.deleteTask(t.id)
        dao.deleteFutureAutoOcc(KIND_ROUTINE, t.id, TimeUtil.today())
        generateFor(TimeUtil.today())
    }

    // ---------------- Reminders ----------------

    suspend fun saveReminder(r: Reminder): Long {
        val id = if (r.id == 0L) dao.insertReminder(r) else { dao.updateReminder(r); r.id }
        val saved = dao.reminder(id) ?: return id
        AlarmEngine.scheduleReminder(app, saved)
        generateFor(TimeUtil.today())
        return id
    }

    suspend fun deleteReminder(r: Reminder) {
        AlarmEngine.cancelMain(app, KIND_REMINDER, r.id)
        dao.deleteReminder(r.id)
        dao.deleteFutureAutoOcc(KIND_REMINDER, r.id, TimeUtil.today())
        generateFor(TimeUtil.today())
    }

    // ---------------- Voices ----------------

    fun voiceFile(v: VoiceRecording): File = File(Prefs.voicesDir(app), v.fileName)

    suspend fun deleteVoice(v: VoiceRecording) {
        voiceFile(v).delete()
        dao.clearVoiceFromTasks(v.id)
        dao.clearVoiceFromReminders(v.id)
        dao.deleteVoice(v.id)
    }

    // ---------------- Common ----------------

    suspend fun afterChange() {
        AlarmEngine.rescheduleAll(app)
        generateFor(TimeUtil.today())
    }
}
