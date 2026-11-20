package com.ishwar.smartalarm.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.ishwar.smartalarm.data.Prefs
import java.io.File

object AudioUtil {

    /** Upload ki gayi audio file ko app ke andar copy karo (taaki baad me bhi chale). */
    fun importSound(ctx: Context, uri: Uri): String? = try {
        var name = "sound.mp3"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val safe = name.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
        val out = File(Prefs.soundsDir(ctx), "${System.currentTimeMillis()}_$safe")
        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
        if (out.exists() && out.length() > 0) Uri.fromFile(out).toString() else null
    } catch (e: Exception) {
        null
    }

    fun soundLabel(ctx: Context, value: String?, defaultLabel: String): String {
        if (value == null) return defaultLabel
        return try {
            val uri = Uri.parse(value)
            if (uri.scheme == "file") {
                (uri.lastPathSegment ?: "Audio").substringAfter('_')
            } else {
                RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx) ?: "Ringtone"
            }
        } catch (e: Exception) {
            "Ringtone"
        }
    }

    fun durationMs(file: File): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (e: Exception) {
        0L
    }

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000).toInt()
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }
}

/** App ke andar awaaz record karna. */
class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set
    private var startedAt = 0L

    fun start(): Boolean {
        val f = File(Prefs.voicesDir(ctx), "voice_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx)
        else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = f
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            r.release()
            f.delete()
            false
        }
    }

    /** @return duration ms */
    fun stop(): Long {
        val r = recorder ?: return 0
        try { r.stop() } catch (e: Exception) { file?.delete(); file = null }
        r.release()
        recorder = null
        return System.currentTimeMillis() - startedAt
    }

    fun isRecording() = recorder != null

    fun discard() {
        if (recorder != null) stop()
        file?.delete()
        file = null
    }
}

/** Chhota sa preview player */
object Preview {
    private var player: MediaPlayer? = null

    fun play(ctx: Context, uri: Uri, onEnd: () -> Unit = {}) {
        stop()
        player = try {
            MediaPlayer().apply {
                setDataSource(ctx, uri)
                setOnCompletionListener { stop(); onEnd() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            onEnd()
            null
        }
    }

    fun stop() {
        player?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        player = null
    }
}
