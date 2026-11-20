package com.ishwar.smartalarm.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.ishwar.smartalarm.data.Prefs
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.TaskStatus
import com.ishwar.smartalarm.data.VoiceRecording
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.AudioUtil
import com.ishwar.smartalarm.util.Preview
import com.ishwar.smartalarm.util.TimeUtil
import com.ishwar.smartalarm.util.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** Har kuch second me "abhi ka time" — status live update ke liye */
@Composable
fun rememberNow(intervalMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
    return now
}

fun statusLabel(s: TaskStatus): String = when (s) {
    TaskStatus.UPCOMING -> tr("Upcoming", "आने वाला")
    TaskStatus.PENDING -> tr("Pending", "बाकी")
    TaskStatus.COMPLETED -> tr("Completed", "पूरा")
    TaskStatus.MISSED -> tr("Missed", "छूटा")
}

@Composable
fun StatusChip(s: TaskStatus) {
    val c = statusColor(s)
    Text(
        statusLabel(s),
        color = c,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, tr("Back", "वापस")) } },
        actions = actions
    )
}

@Composable
fun Avatar(size: Dp) {
    val ctx = LocalContext.current
    val version = Prefs.photoVersion
    val bmp = remember(version) {
        val f = Prefs.photoFile(ctx)
        if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull() else null
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val letter = Prefs.name.trim().firstOrNull()?.uppercase()
            if (letter != null) Text(letter, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            else Icon(Icons.Filled.Person, null, Modifier.size(size * 0.6f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } }
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()); onDismiss() }) { Text(tr("Save", "सेव करें")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initialMin: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = DateFormat.is24HourFormat(ctx)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initialDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } }
    ) { DatePicker(state = state) }
}

/** Ek row: label + value, tap karne par kuch khulta hai */
@Composable
fun PickerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Alarm sound chunna: Default / System ringtone / Audio file upload */
@Composable
fun SoundPicker(value: String?, onChange: (String?) -> Unit, allowDefault: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val defaultLabel = if (allowDefault) tr("App default sound", "ऐप का Default Sound") else tr("System alarm sound", "सिस्टम Alarm Sound")
    val label = remember(value, Prefs.hindi) { AudioUtil.soundLabel(ctx, value, defaultLabel) }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            if (uri != null) onChange(uri.toString())
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { AudioUtil.importSound(ctx, uri) }
            if (saved != null) onChange(saved)
        }
    }

    PickerRow(Icons.Filled.MusicNote, tr("Alarm sound", "Alarm Sound"), label, onClick = { open = true }) {
        IconButton(onClick = {
            val u = value?.let { Uri.parse(it) } ?: Prefs.defaultSound?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (u != null) Preview.play(ctx, u)
        }) { Icon(Icons.Filled.PlayArrow, tr("Play", "चलाएँ")) }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(tr("Choose alarm sound", "Alarm Sound चुनें")) },
            text = {
                Column {
                    if (allowDefault) OptionLine(Icons.Filled.Alarm, tr("App default sound", "ऐप का Default Sound")) { onChange(null); open = false }
                    OptionLine(Icons.Filled.LibraryMusic, tr("Phone ringtones / alarm tones", "फ़ोन की Ringtone / Alarm Tone")) {
                        open = false
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, tr("Alarm sound", "Alarm Sound"))
                        value?.let { i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        runCatching { ringtoneLauncher.launch(i) }
                    }
                    OptionLine(Icons.Filled.UploadFile, tr("Upload audio file (MP3 etc.)", "Audio File Upload करें (MP3 आदि)")) {
                        open = false
                        runCatching { fileLauncher.launch(arrayOf("audio/*")) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(tr("Close", "बंद करें")) } }
        )
    }
    DisposableEffect(Unit) { onDispose { Preview.stop() } }
}

@Composable
private fun OptionLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

/** Task ke liye apni recorded awaaz chunna */
@Composable
fun VoicePicker(voiceId: Long?, onChange: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val voices by remember { Repo.dao.voicesFlow() }.collectAsState(initial = emptyList())
    var open by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val current = voices.firstOrNull { it.id == voiceId }

    PickerRow(
        Icons.Filled.RecordVoiceOver, tr("Custom voice", "अपनी आवाज़ (Custom Voice)"),
        current?.name ?: tr("None — app speaks the task name", "कोई नहीं — ऐप Task का नाम बोलेगा"),
        onClick = { open = true }
    ) {
        if (current != null) IconButton(onClick = { Preview.play(ctx, Uri.fromFile(Repo.voiceFile(current))) }) {
            Icon(Icons.Filled.PlayArrow, null)
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(tr("Choose voice", "आवाज़ चुनें")) },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        OptionLine(Icons.Filled.Mic, tr("Record new voice", "नई आवाज़ Record करें")) { open = false; recording = true }
                        OptionLine(Icons.Filled.VoiceOverOff, tr("None", "कोई नहीं")) { onChange(null); open = false }
                        if (voices.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                    items(voices, key = { it.id }) { v ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onChange(v.id); open = false }.padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = v.id == voiceId, onClick = { onChange(v.id); open = false })
                            Column(Modifier.weight(1f)) {
                                Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(AudioUtil.formatDuration(v.durationMs), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { Preview.play(ctx, Uri.fromFile(Repo.voiceFile(v))) }) { Icon(Icons.Filled.PlayArrow, null) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(tr("Close", "बंद करें")) } }
        )
    }
    if (recording) {
        RecorderDialog(onDismiss = { recording = false }, onSaved = { onChange(it); recording = false })
    }
}

/** Awaaz record karne ka dialog: Start → Stop → Play → Save / Delete */
@Composable
fun RecorderDialog(onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(ctx) }
    var phase by remember { mutableIntStateOf(0) } // 0 idle, 1 recording, 2 recorded
    var startedAt by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var name by remember { mutableStateOf(tr("My voice", "मेरी आवाज़") + " " + TimeUtil.formatClock(System.currentTimeMillis())) }
    var error by remember { mutableStateOf<String?>(null) }
    val now = rememberNow(250L)

    fun begin() {
        if (recorder.start()) { phase = 1; startedAt = System.currentTimeMillis(); error = null }
        else error = tr("Could not start the microphone.", "माइक शुरू नहीं हो सका।")
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else error = tr("Microphone permission is needed.", "Microphone की permission चाहिए।")
    }

    DisposableEffect(Unit) {
        onDispose {
            Preview.stop()
            if (recorder.isRecording()) recorder.discard()
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Record your voice", "अपनी आवाज़ Record करें")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    tr("Example: \"Hello Ishwar! It's exercise time.\"", "उदाहरण: \"हेलो ईश्वर! चलिए जल्दी कीजिए, Exercise का समय हो गया है।\""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                val shown = when (phase) { 1 -> now - startedAt; 2 -> duration; else -> 0L }
                Text(AudioUtil.formatDuration(shown), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                when (phase) {
                    0 -> FilledIconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = StatusRed)) {
                        Icon(Icons.Filled.Mic, tr("Start", "शुरू"), Modifier.size(36.dp), tint = Color.White)
                    }
                    1 -> FilledIconButton(onClick = {
                        duration = recorder.stop()
                        phase = if (recorder.file != null) 2 else 0
                    }, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = StatusRed)) {
                        Icon(Icons.Filled.Stop, tr("Stop", "रोकें"), Modifier.size(36.dp), tint = Color.White)
                    }
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { recorder.file?.let { Preview.play(ctx, Uri.fromFile(it)) } }) {
                                Icon(Icons.Filled.PlayArrow, null); Text(tr("Play", "सुनें"))
                            }
                            OutlinedButton(onClick = { Preview.stop(); recorder.discard(); phase = 0 }) {
                                Icon(Icons.Filled.Refresh, null); Text(tr("Redo", "दोबारा"))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(name, { name = it }, label = { Text(tr("Name", "नाम")) }, singleLine = true)
                    }
                }
                Text(
                    when (phase) { 0 -> tr("Tap the mic to start", "शुरू करने के लिए Mic दबाएँ"); 1 -> tr("Recording… tap to stop", "Recording हो रही है… रोकने के लिए दबाएँ"); else -> "" },
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = phase == 2 && name.isNotBlank(), onClick = {
                val f: File = recorder.file ?: return@TextButton
                Preview.stop()
                scope.launch {
                    val id = withContext(Dispatchers.IO) {
                        val d = AudioUtil.durationMs(f).takeIf { it > 0 } ?: duration
                        Repo.dao.insertVoice(VoiceRecording(name = name.trim(), fileName = f.name, durationMs = d))
                    }
                    onSaved(id)
                }
            }) { Text(tr("Save", "सेव करें")) }
        },
        dismissButton = {
            TextButton(onClick = { Preview.stop(); recorder.discard(); onDismiss() }) { Text(tr("Delete / Cancel", "हटाएँ / रद्द")) }
        }
    )
}

/** Din chunne wale chips (Som..Ravi) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayChips(mask: Int, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..6) {
            val on = (mask and (1 shl i)) != 0
            FilterChip(selected = on, onClick = { onChange(mask xor (1 shl i)) }, label = { Text(TimeUtil.dayName(i)) })
        }
    }
}
