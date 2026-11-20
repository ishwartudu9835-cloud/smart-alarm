package com.ishwar.smartalarm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ishwar.smartalarm.alarm.AlarmEngine
import com.ishwar.smartalarm.data.Occurrence
import com.ishwar.smartalarm.data.Prefs
import com.ishwar.smartalarm.data.Reminder
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.RoutineTask
import com.ishwar.smartalarm.data.ScheduleSet
import com.ishwar.smartalarm.data.VoiceRecording
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Poora data ek .zip file me (schedule, reminders, history, settings, recordings, photo). */
object Backup {

    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k)) null else optLong(k)
    private fun JSONObject.optIntOrNull(k: String): Int? = if (isNull(k)) null else optInt(k)
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else optString(k)

    private fun setJ(s: ScheduleSet) = JSONObject().put("id", s.id).put("name", s.name).put("description", s.description)
        .put("startDay", s.startDay ?: JSONObject.NULL).put("endDay", s.endDay ?: JSONObject.NULL)
        .put("active", s.active).put("activatedAt", s.activatedAt).put("createdAt", s.createdAt)

    private fun setF(o: JSONObject) = ScheduleSet(
        o.getLong("id"), o.getString("name"), o.optString("description"), o.optLongOrNull("startDay"),
        o.optLongOrNull("endDay"), o.optBoolean("active"), o.optLong("activatedAt"), o.optLong("createdAt")
    )

    private fun taskJ(t: RoutineTask) = JSONObject().put("id", t.id).put("setId", t.setId).put("name", t.name)
        .put("description", t.description).put("startMin", t.startMin).put("endMin", t.endMin ?: JSONObject.NULL)
        .put("days", t.days).put("alarmEnabled", t.alarmEnabled).put("soundUri", t.soundUri ?: JSONObject.NULL)
        .put("voiceId", t.voiceId ?: JSONObject.NULL).put("createdAt", t.createdAt)

    private fun taskF(o: JSONObject) = RoutineTask(
        o.getLong("id"), o.getLong("setId"), o.getString("name"), o.optString("description"), o.getInt("startMin"),
        o.optIntOrNull("endMin"), o.optInt("days", 127), o.optBoolean("alarmEnabled", true),
        o.optStringOrNull("soundUri"), o.optLongOrNull("voiceId"), o.optLong("createdAt")
    )

    private fun remJ(r: Reminder) = JSONObject().put("id", r.id).put("title", r.title).put("description", r.description)
        .put("day", r.day).put("timeMin", r.timeMin).put("repeat", r.repeat).put("alarmEnabled", r.alarmEnabled)
        .put("soundUri", r.soundUri ?: JSONObject.NULL).put("voiceId", r.voiceId ?: JSONObject.NULL).put("createdAt", r.createdAt)

    private fun remF(o: JSONObject) = Reminder(
        o.getLong("id"), o.getString("title"), o.optString("description"), o.getLong("day"), o.getInt("timeMin"),
        o.optInt("repeat"), o.optBoolean("alarmEnabled", true), o.optStringOrNull("soundUri"), o.optLongOrNull("voiceId"),
        o.optLong("createdAt")
    )

    private fun voiceJ(v: VoiceRecording) = JSONObject().put("id", v.id).put("name", v.name).put("fileName", v.fileName)
        .put("durationMs", v.durationMs).put("createdAt", v.createdAt)

    private fun voiceF(o: JSONObject) = VoiceRecording(
        o.getLong("id"), o.getString("name"), o.getString("fileName"), o.optLong("durationMs"), o.optLong("createdAt")
    )

    private fun occJ(x: Occurrence) = JSONObject().put("id", x.id).put("kind", x.kind).put("sourceId", x.sourceId)
        .put("setId", x.setId ?: JSONObject.NULL).put("title", x.title).put("description", x.description).put("day", x.day)
        .put("startMin", x.startMin).put("endMin", x.endMin ?: JSONObject.NULL).put("status", x.status)
        .put("completedAt", x.completedAt ?: JSONObject.NULL).put("snoozeCount", x.snoozeCount)

    private fun occF(o: JSONObject) = Occurrence(
        o.getLong("id"), o.getInt("kind"), o.getLong("sourceId"), o.optLongOrNull("setId"), o.getString("title"),
        o.optString("description"), o.getLong("day"), o.getInt("startMin"), o.optIntOrNull("endMin"), o.optInt("status"),
        o.optLongOrNull("completedAt"), o.optInt("snoozeCount")
    )

    suspend fun export(ctx: Context, uri: Uri): Boolean = try {
        val dao = Repo.dao
        val root = JSONObject()
            .put("app", "SmartAlarm").put("version", 1).put("exportedAt", System.currentTimeMillis())
            .put("prefs", JSONObject(Prefs.export()))
            .put("sets", JSONArray(dao.sets().map { setJ(it) }))
            .put("tasks", JSONArray(dao.allTasks().map { taskJ(it) }))
            .put("reminders", JSONArray(dao.reminders().map { remJ(it) }))
            .put("voices", JSONArray(dao.voices().map { voiceJ(it) }))
            .put("occurrences", JSONArray(dao.allOcc().map { occJ(it) }))
        ctx.contentResolver.openOutputStream(uri)?.use { os ->
            ZipOutputStream(os).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(root.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                fun addDir(dir: File, prefix: String) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile) {
                            zip.putNextEntry(ZipEntry("$prefix/${f.name}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                addDir(Prefs.voicesDir(ctx), "voices")
                addDir(Prefs.soundsDir(ctx), "sounds")
                val photo = Prefs.photoFile(ctx)
                if (photo.exists()) {
                    zip.putNextEntry(ZipEntry("profile.jpg"))
                    photo.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("no stream")
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    suspend fun import(ctx: Context, uri: Uri): Boolean = try {
        var json: String? = null
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    val base = File(name).name // zip-slip se bachav
                    when {
                        e.isDirectory -> {}
                        name == "data.json" -> {
                            val bos = ByteArrayOutputStream()
                            zip.copyTo(bos)
                            json = bos.toString("UTF-8")
                        }
                        name.startsWith("voices/") -> File(Prefs.voicesDir(ctx), base).outputStream().use { zip.copyTo(it) }
                        name.startsWith("sounds/") -> File(Prefs.soundsDir(ctx), base).outputStream().use { zip.copyTo(it) }
                        name == "profile.jpg" -> {
                            Prefs.photoFile(ctx).outputStream().use { zip.copyTo(it) }
                            Prefs.photoChanged()
                        }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        }
        val root = JSONObject(json ?: throw IllegalStateException("data.json missing"))
        fun arr(k: String): List<JSONObject> {
            val a = root.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).map { a.getJSONObject(it) }
        }
        val dao = Repo.dao
        dao.clearOcc(); dao.clearTasks(); dao.clearReminders(); dao.clearSets(); dao.clearVoices()
        dao.putSets(arr("sets").map { setF(it) })
        dao.putTasks(arr("tasks").map { taskF(it) })
        dao.putReminders(arr("reminders").map { remF(it) })
        dao.putVoices(arr("voices").map { voiceF(it) })
        dao.putOcc(arr("occurrences").map { occF(it) })
        root.optJSONObject("prefs")?.let { p ->
            if (p.has("name")) Prefs.setName(p.optString("name"))
            if (p.has("hindi")) Prefs.setHindi(p.optBoolean("hindi", true))
            if (p.has("theme")) Prefs.setTheme(p.optInt("theme"))
            if (p.has("volume")) Prefs.setVolume(p.optDouble("volume", 1.0).toFloat())
            if (p.has("vibration")) Prefs.setVibration(p.optBoolean("vibration", true))
            if (p.has("autoRepeat")) Prefs.setAutoRepeat(p.optBoolean("autoRepeat", true))
            Prefs.setDefaultSound(if (p.has("defaultSound") && !p.isNull("defaultSound")) p.optString("defaultSound") else null)
        }
        AlarmEngine.rescheduleAll(ctx)
        Repo.refresh()
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /** Profile photo ko chhota karke save karo */
    fun saveProfilePhoto(ctx: Context, uri: Uri): Boolean = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= 512 && opts.outHeight / (sample * 2) >= 512) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("decode failed")
        Prefs.photoFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Prefs.photoChanged()
        true
    } catch (e: Exception) {
        false
    }
}
