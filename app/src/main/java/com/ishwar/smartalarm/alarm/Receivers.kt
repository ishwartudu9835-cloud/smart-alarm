package com.ishwar.smartalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ishwar.smartalarm.data.Occurrence
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.ST_AUTO
import com.ishwar.smartalarm.data.ST_COMPLETED
import com.ishwar.smartalarm.data.ST_MISSED
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarm ka time aate hi Android ise chalata hai. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getIntExtra(AlarmEngine.EXTRA_TYPE, -1)
        val id = intent.getLongExtra(AlarmEngine.EXTRA_ID, -1L)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, type, id)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(ctx: Context, type: Int, id: Long) {
        Repo.init(ctx)
        val dao = Repo.dao
        val today = TimeUtil.today()
        when (type) {
            AlarmEngine.TYPE_ROUTINE -> {
                val t = dao.task(id) ?: return
                AlarmEngine.scheduleTask(ctx, t) // agla din
                val s = dao.set(t.setId)
                if (s == null || !s.active || !t.alarmEnabled) return
                ring(ctx, Repo.ensureRoutineOcc(t, today))
            }
            AlarmEngine.TYPE_REMINDER -> {
                val r = dao.reminder(id) ?: return
                AlarmEngine.scheduleReminder(ctx, r) // repeat ho to agla
                if (!r.alarmEnabled) return
                ring(ctx, Repo.ensureReminderOcc(r, today))
            }
            AlarmEngine.TYPE_SNOOZE -> {
                val o = dao.occ(id) ?: return
                if (o.status == ST_COMPLETED) return
                startRinging(ctx, o)
            }
            AlarmEngine.TYPE_MISSED -> {
                val o = dao.occ(id) ?: return
                if (o.status == ST_AUTO) {
                    dao.updateOcc(o.copy(status = ST_MISSED))
                    Notifier.missed(ctx, o)
                }
            }
        }
    }

    private fun ring(ctx: Context, o: Occurrence) {
        if (o.status == ST_COMPLETED) return
        val dl = Repo.deadlineMillis(o)
        if (dl > System.currentTimeMillis()) AlarmEngine.scheduleMissedCheck(ctx, o.id, dl)
        startRinging(ctx, o)
    }

    private fun startRinging(ctx: Context, o: Occurrence) {
        val i = Intent(ctx, AlarmService::class.java)
            .setAction(AlarmService.ACTION_RING)
            .putExtra(AlarmService.EXTRA_OCC, o.id)
            .putExtra(AlarmService.EXTRA_TITLE, o.title)
            .putExtra(AlarmService.EXTRA_DESC, o.description)
            .putExtra(AlarmService.EXTRA_START, o.startMin)
        ContextCompat.startForegroundService(ctx, i)
    }
}

/** Phone restart, app update ya time badalne par saare alarms wapas. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Repo.init(app)
                Repo.refresh()
                AlarmEngine.rescheduleAll(app)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
