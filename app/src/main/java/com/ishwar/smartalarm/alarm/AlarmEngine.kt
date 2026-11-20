package com.ishwar.smartalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ishwar.smartalarm.data.KIND_ROUTINE
import com.ishwar.smartalarm.data.REPEAT_NONE
import com.ishwar.smartalarm.data.Reminder
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.RoutineTask
import com.ishwar.smartalarm.data.ScheduleSet
import com.ishwar.smartalarm.ui.MainActivity
import com.ishwar.smartalarm.util.TimeUtil

/** Android AlarmManager ke saath saari scheduling. */
object AlarmEngine {
    const val EXTRA_TYPE = "type"
    const val EXTRA_ID = "id"

    const val TYPE_ROUTINE = 0
    const val TYPE_REMINDER = 1
    const val TYPE_SNOOZE = 2
    const val TYPE_MISSED = 3

    private const val BLOCK = 10_000_000

    private fun code(type: Int, id: Long): Int = (type + 1) * BLOCK + (id % BLOCK).toInt()

    private fun pi(ctx: Context, type: Int, id: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction("com.ishwar.smartalarm.ALARM_$type")
            .putExtra(EXTRA_TYPE, type)
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, code(type, id), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** showInClock = status bar / lock screen par "next alarm" ke roop me dikhe */
    private fun setAt(ctx: Context, time: Long, p: PendingIntent, showInClock: Boolean = true) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (showInClock) {
                val show = PendingIntent.getActivity(
                    ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), p)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, p)
        }
    }

    // ---------------- Next time calculation ----------------

    fun nextRoutine(t: RoutineTask, s: ScheduleSet, from: Long = System.currentTimeMillis()): Long? {
        if (!s.active || !t.alarmEnabled || t.days == 0) return null
        var d = maxOf(TimeUtil.dayOf(from), s.startDay ?: Long.MIN_VALUE)
        repeat(9) {
            if (s.endDay != null && d > s.endDay) return null
            if (Repo.taskOnDay(t, d)) {
                val m = TimeUtil.millis(d, t.startMin)
                if (m > from) return m
            }
            d++
        }
        return null
    }

    fun nextReminder(r: Reminder, from: Long = System.currentTimeMillis()): Long? {
        if (!r.alarmEnabled) return null
        if (r.repeat == REPEAT_NONE) {
            val m = TimeUtil.millis(r.day, r.timeMin)
            return if (m > from) m else null
        }
        var d = maxOf(TimeUtil.dayOf(from), r.day)
        repeat(800) {
            if (Repo.reminderOnDay(r, d)) {
                val m = TimeUtil.millis(d, r.timeMin)
                if (m > from) return m
            }
            d++
        }
        return null
    }

    // ---------------- Schedule / cancel ----------------

    suspend fun scheduleTask(ctx: Context, t: RoutineTask): Long? {
        cancelMain(ctx, KIND_ROUTINE, t.id)
        val s = Repo.dao.set(t.setId) ?: return null
        val next = nextRoutine(t, s) ?: return null
        setAt(ctx, next, pi(ctx, TYPE_ROUTINE, t.id))
        return next
    }

    fun scheduleReminder(ctx: Context, r: Reminder): Long? {
        cancelMain(ctx, 1, r.id)
        val next = nextReminder(r) ?: return null
        setAt(ctx, next, pi(ctx, TYPE_REMINDER, r.id))
        return next
    }

    fun cancelMain(ctx: Context, kind: Int, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx, if (kind == KIND_ROUTINE) TYPE_ROUTINE else TYPE_REMINDER, id))
    }

    fun snooze(ctx: Context, occId: Long, minutes: Int) {
        setAt(ctx, System.currentTimeMillis() + minutes * 60_000L, pi(ctx, TYPE_SNOOZE, occId))
    }

    fun scheduleMissedCheck(ctx: Context, occId: Long, at: Long) {
        setAt(ctx, at, pi(ctx, TYPE_MISSED, occId), showInClock = false)
    }

    fun cancelOccAlarms(ctx: Context, occId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx, TYPE_SNOOZE, occId))
        am.cancel(pi(ctx, TYPE_MISSED, occId))
    }

    /** Saare alarms dobara (boot, set badalne, restore ke baad). */
    suspend fun rescheduleAll(ctx: Context) {
        val dao = Repo.dao
        val sets = dao.sets().associateBy { it.id }
        for (t in dao.allTasks()) {
            cancelMain(ctx, KIND_ROUTINE, t.id)
            val s = sets[t.setId] ?: continue
            nextRoutine(t, s)?.let { setAt(ctx, it, pi(ctx, TYPE_ROUTINE, t.id)) }
        }
        for (r in dao.reminders()) scheduleReminder(ctx, r)
        // aaj ke khule tasks ke missed-check bhi
        val now = System.currentTimeMillis()
        for (o in dao.openAutoOcc(TimeUtil.today())) {
            val dl = Repo.deadlineMillis(o)
            if (dl > now) scheduleMissedCheck(ctx, o.id, dl)
        }
    }

    /** Sabse agla alarm (Home screen ke liye) */
    suspend fun nextAlarm(): Pair<String, Long>? {
        val dao = Repo.dao
        var best: Pair<String, Long>? = null
        val s = dao.activeSet()
        if (s != null) for (t in dao.tasks(s.id)) {
            val n = nextRoutine(t, s) ?: continue
            if (best == null || n < best.second) best = t.name to n
        }
        for (r in dao.reminders()) {
            val n = nextReminder(r) ?: continue
            if (best == null || n < best.second) best = r.title to n
        }
        return best
    }
}
