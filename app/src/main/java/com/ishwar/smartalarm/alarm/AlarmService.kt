package com.ishwar.smartalarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ishwar.smartalarm.R
import com.ishwar.smartalarm.data.KIND_ROUTINE
import com.ishwar.smartalarm.data.Occurrence
import com.ishwar.smartalarm.data.Prefs
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Alarm bajata hai: sound + vibration + (recorded voice ya bolkar naam).
 * Done / Snooze 5 / Snooze 10 yahin handle hote hain.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.ishwar.smartalarm.RING"
        const val ACTION_DONE = "com.ishwar.smartalarm.DONE"
        const val ACTION_SNOOZE = "com.ishwar.smartalarm.SNOOZE"
        const val ACTION_STOP = "com.ishwar.smartalarm.STOP"
        const val ACTION_CLOSE_UI = "com.ishwar.smartalarm.CLOSE_UI"
        const val EXTRA_OCC = "occ"
        const val EXTRA_MIN = "minutes"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DESC = "desc"
        const val EXTRA_START = "start"
        const val NOTIF_ID = 4242
        private const val AUTO_STOP_MS = 3 * 60 * 1000L
        private const val TONE_GAP_MS = 8_000L
        private const val MAX_AUTO_REPEAT = 3
    }

    private class RingData(val occ: Occurrence, val sound: Uri?, val voice: File?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var tone: MediaPlayer? = null
    private var voice: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var stopped = true
    private var current: RingData? = null

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val autoStop = Runnable { onNoResponse() }
    private val announceAgain = Runnable {
        if (!stopped) {
            try { if (tone?.isPlaying == true) tone?.pause() } catch (e: Exception) { }
            announce()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Repo.init(this)
        Notifier.createChannels(this)
        val occId = intent?.getLongExtra(EXTRA_OCC, -1L) ?: -1L
        when (intent?.action) {
            ACTION_DONE -> {
                stopSound()
                scope.launch {
                    withContext(Dispatchers.IO) { if (occId > 0) Repo.complete(occId) }
                    finish()
                }
            }
            ACTION_SNOOZE -> {
                val min = intent.getIntExtra(EXTRA_MIN, 5)
                stopSound()
                scope.launch {
                    withContext(Dispatchers.IO) { snoozeOcc(occId, min) }
                    finish()
                }
            }
            ACTION_STOP -> {
                stopSound()
                finish()
            }
            else -> {
                // turant foreground me aana zaroori hai
                startFg(buildNotification(null))
                scope.launch {
                    val data = withContext(Dispatchers.IO) { load(occId) }
                    if (data == null) {
                        stopSound()
                        finish()
                        return@launch
                    }
                    stopSound()
                    current = data
                    stopped = false
                    startFg(buildNotification(data.occ))
                    begin(data)
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun snoozeOcc(occId: Long, min: Int) {
        if (occId <= 0) return
        val o = Repo.dao.occ(occId) ?: return
        Repo.dao.updateOcc(o.copy(snoozeCount = o.snoozeCount + 1))
        AlarmEngine.snooze(this, occId, min)
    }

    private suspend fun load(occId: Long): RingData? {
        val o = Repo.dao.occ(occId) ?: return null
        var soundStr: String? = null
        var voiceId: Long? = null
        if (o.kind == KIND_ROUTINE) {
            Repo.dao.task(o.sourceId)?.let { soundStr = it.soundUri; voiceId = it.voiceId }
        } else {
            Repo.dao.reminder(o.sourceId)?.let { soundStr = it.soundUri; voiceId = it.voiceId }
        }
        val s = soundStr ?: Prefs.defaultSound
        val sound = s?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val vf = voiceId?.let { Repo.dao.voice(it) }?.let { Repo.voiceFile(it) }?.takeIf { it.exists() }
        return RingData(o, sound, vf)
    }

    private fun startFg(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun buildNotification(o: Occurrence?): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val b = NotificationCompat.Builder(this, Notifier.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
        if (o == null) {
            return b.setContentTitle(getString(R.string.app_name)).setContentText("…").build()
        }
        val full = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_OCC, o.id)
            putExtra(EXTRA_TITLE, o.title)
            putExtra(EXTRA_DESC, o.description)
            putExtra(EXTRA_START, o.startMin)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val fullPi = PendingIntent.getActivity(this, 20, full, flags)
        fun svc(action: String, code: Int, min: Int = 0) = PendingIntent.getService(
            this, code,
            Intent(this, AlarmService::class.java).setAction(action).putExtra(EXTRA_OCC, o.id).putExtra(EXTRA_MIN, min),
            flags
        )
        val text = if (o.description.isNotBlank()) o.description
        else tr("It's time for ${o.title}!", "${o.title} का समय हो गया है!")
        return b.setContentTitle("${TimeUtil.formatMin(o.startMin)} • ${o.title}")
            .setContentText(text)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .addAction(0, tr("Done", "Done ✓"), svc(ACTION_DONE, 21))
            .addAction(0, tr("Snooze 5", "Snooze 5"), svc(ACTION_SNOOZE, 22, 5))
            .addAction(0, tr("Snooze 10", "Snooze 10"), svc(ACTION_SNOOZE, 23, 10))
            .build()
    }

    // ---------------- Sound ----------------

    private fun makePlayer(uri: Uri?, loop: Boolean): MediaPlayer? {
        val candidates = listOfNotNull(
            uri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (u in candidates) {
            try {
                return MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(this@AlarmService, u)
                    isLooping = loop
                    prepare()
                    setVolume(Prefs.volume, Prefs.volume)
                }
            } catch (e: Exception) {
                // agla try
            }
        }
        return null
    }

    private fun begin(d: RingData) {
        tone = makePlayer(d.sound, true)
        if (d.voice != null) {
            voice = try {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(d.voice.absolutePath)
                    prepare()
                    setVolume(Prefs.volume, Prefs.volume)
                    setOnCompletionListener { afterAnnounce() }
                }
            } catch (e: Exception) {
                null
            }
        }
        if (Prefs.vibration) startVibration()
        handler.postDelayed(autoStop, AUTO_STOP_MS)
        when {
            voice != null -> announce()
            else -> initTts()
        }
    }

    /** Recorded voice ya TTS bolo, phir tone */
    private fun announce() {
        if (stopped) return
        val v = voice
        if (v != null) {
            try { v.seekTo(0); v.start() } catch (e: Exception) { afterAnnounce() }
            return
        }
        val t = tts
        val d = current
        if (t != null && ttsReady && d != null) {
            val hi = Prefs.hindi
            val msg = if (hi) "अब समय है: ${d.occ.title}" else "It's time for ${d.occ.title}"
            t.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "alarm")
        } else {
            try { tone?.start() } catch (e: Exception) { }
        }
    }

    private fun afterAnnounce() {
        if (stopped) return
        try { tone?.start() } catch (e: Exception) { }
        handler.removeCallbacks(announceAgain)
        handler.postDelayed(announceAgain, TONE_GAP_MS)
    }

    private fun initTts() {
        if (tts != null) { announce(); return }
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val loc = if (Prefs.hindi) Locale("hi", "IN") else Locale.ENGLISH
                val r = tts?.setLanguage(loc)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setAudioAttributes(attrs)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        handler.post { afterAnnounce() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handler.post { afterAnnounce() }
                    }
                })
            }
            handler.post { announce() }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0))
        } catch (e: Exception) { }
    }

    private fun onNoResponse() {
        val d = current
        stopSound()
        scope.launch {
            if (d != null && Prefs.autoRepeat && d.occ.snoozeCount < MAX_AUTO_REPEAT) {
                withContext(Dispatchers.IO) { snoozeOcc(d.occ.id, 5) }
            }
            finish()
        }
    }

    private fun stopSound() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        listOf(tone, voice).forEach { p ->
            try { p?.stop() } catch (e: Exception) { }
            try { p?.release() } catch (e: Exception) { }
        }
        tone = null
        voice = null
        try { tts?.stop() } catch (e: Exception) { }
        vibrator?.cancel()
    }

    private fun finish() {
        current = null
        sendBroadcast(Intent(ACTION_CLOSE_UI).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        try { tts?.shutdown() } catch (e: Exception) { }
        tts = null
        scope.cancel()
        super.onDestroy()
    }
}
