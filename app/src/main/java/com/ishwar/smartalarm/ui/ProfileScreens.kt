package com.ishwar.smartalarm.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ishwar.smartalarm.R
import com.ishwar.smartalarm.data.Prefs
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.VoiceRecording
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.AudioUtil
import com.ishwar.smartalarm.util.Backup
import com.ishwar.smartalarm.util.Preview
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ======================= Profile =======================

@Composable
fun ProfileScreen(nav: NavState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(Prefs.name) }
    var confirmRemove by remember { mutableStateOf(false) }
    val hasPhoto = remember(Prefs.photoVersion) { Prefs.photoFile(ctx).exists() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Backup.saveProfilePhoto(ctx, uri) }
            if (!ok) Toast.makeText(ctx, tr("Could not load photo", "Photo लोड नहीं हुई"), Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(tr("My Profile", "My Profile"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Box(Modifier.clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Avatar(112.dp) }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (hasPhoto) tr("Change photo", "Photo बदलें") else tr("Upload photo", "Photo Upload करें"))
                }
                if (hasPhoto) TextButton(onClick = { confirmRemove = true }) { Text(tr("Remove photo", "Photo हटाएँ"), color = StatusRed) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            name, { name = it }, label = { Text(tr("Your name", "आपका नाम")) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (name.trim() != Prefs.name) IconButton(onClick = {
                    Prefs.saveName(name.trim())
                    Toast.makeText(ctx, tr("Saved", "सेव हो गया"), Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Filled.Check, tr("Save", "सेव")) }
            }
        )
        if (name.trim() != Prefs.name) Button(
            onClick = { Prefs.saveName(name.trim()); Toast.makeText(ctx, tr("Saved", "सेव हो गया"), Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(tr("Save Changes", "Changes सेव करें")) }

        Spacer(Modifier.height(20.dp))
        MenuRow(Icons.Filled.Settings, tr("Settings", "Settings"), tr("Sound, volume, language, theme, backup", "Sound, Volume, भाषा, Theme, Backup")) { nav.push(Screen.Settings) }
        MenuRow(Icons.Filled.RecordVoiceOver, tr("My voice recordings", "मेरी Voice Recordings"), tr("Record, play, delete", "Record, सुनें, हटाएँ")) { nav.push(Screen.Voices) }
        MenuRow(Icons.Filled.History, tr("Task history", "Task History"), tr("Completed, pending, missed", "पूरे, बाकी, छूटे")) { nav.openHistory(null) }
    }

    if (confirmRemove) ConfirmDialog(
        tr("Remove photo?", "Photo हटाएँ?"), "", tr("Remove", "हटाएँ"),
        onDismiss = { confirmRemove = false },
        onConfirm = { Prefs.photoFile(ctx).delete(); Prefs.photoChanged() }
    )
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ======================= Settings =======================

@Composable
fun SettingsScreen(nav: NavState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val now = rememberNow(2000L)
    val health = remember(now / 2000L) { alarmHealth(ctx) }
    val mic = remember(now / 2000L) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { Backup.export(ctx, uri) }
            busy = false
            Toast.makeText(ctx, if (ok) tr("Backup saved", "Backup सेव हो गया") else tr("Backup failed", "Backup नहीं हो पाया"), Toast.LENGTH_LONG).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    Scaffold(topBar = { SimpleTopBar(tr("Settings", "Settings"), onBack = { nav.pop() }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            SectionTitle(tr("Alarm", "Alarm"))
            SoundPicker(Prefs.defaultSound, { Prefs.saveDefaultSound(it) }, allowDefault = false)
            Text(
                tr("Default sound for tasks that don't have their own sound.", "जिन Tasks का अपना Sound नहीं है, उनमें यही बजेगा।"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("Alarm volume", "Alarm Volume"), modifier = Modifier.weight(1f))
                Text("${(Prefs.volume * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
            Slider(value = Prefs.volume, onValueChange = { Prefs.saveVolume(it) }, valueRange = 0.1f..1f)
            Text(
                tr("Phone's alarm volume also applies.", "फ़ोन का Alarm Volume भी लागू होता है।"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow(Icons.Filled.Vibration, tr("Vibration", "Vibration"), null, Prefs.vibration) { Prefs.saveVibration(it) }
            SwitchRow(
                Icons.Filled.Replay, tr("Repeat alarm", "Alarm दोहराएँ"),
                tr("If not answered in 3 min, ring again after 5 min (up to 3 times)", "3 मिनट में जवाब न मिले तो 5 मिनट बाद फिर बजे (3 बार तक)"),
                Prefs.autoRepeat
            ) { Prefs.saveAutoRepeat(it) }

            SectionTitle(tr("Appearance", "भाषा और Theme"))
            Text(tr("Language", "भाषा"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = Prefs.hindi, onClick = { Prefs.saveHindi(true) }, label = { Text("हिंदी") })
                FilterChip(selected = !Prefs.hindi, onClick = { Prefs.saveHindi(false) }, label = { Text("English") })
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Theme", "Theme"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = Prefs.theme == Prefs.THEME_SYSTEM, onClick = { Prefs.saveTheme(Prefs.THEME_SYSTEM) }, label = { Text(tr("System", "System")) })
                FilterChip(selected = Prefs.theme == Prefs.THEME_LIGHT, onClick = { Prefs.saveTheme(Prefs.THEME_LIGHT) }, label = { Text(tr("Light", "Light")) },
                    leadingIcon = { Icon(Icons.Filled.LightMode, null, Modifier.size(18.dp)) })
                FilterChip(selected = Prefs.theme == Prefs.THEME_DARK, onClick = { Prefs.saveTheme(Prefs.THEME_DARK) }, label = { Text(tr("Dark", "Dark")) },
                    leadingIcon = { Icon(Icons.Filled.DarkMode, null, Modifier.size(18.dp)) })
            }

            SectionTitle(tr("Permissions", "Permissions"))
            PermRow(tr("Notifications", "Notifications"), health.notifications) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openAppSettings(ctx)
            }
            PermRow(tr("Exact alarms (Alarms & reminders)", "समय पर Alarm (Alarms & reminders)"), health.exact) { openExactAlarmSettings(ctx) }
            PermRow(tr("Full-screen alarm", "Full-screen Alarm"), health.fullScreen) { openFullScreenSettings(ctx) }
            PermRow(tr("Microphone (voice recording)", "Microphone (Voice Recording)"), mic) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            PermRow(tr("No battery restriction", "Battery Restriction नहीं"), health.battery) { openBatterySettings(ctx) }

            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BatteryAlert, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Battery optimization guide", "Battery Optimization Guide"), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr(
                            "Some phones (Xiaomi, Redmi, Realme, Oppo, Vivo, Samsung) stop apps in the background, which can block alarms.\n" +
                                "1. Open App Settings → Battery → choose \"No restrictions\" / \"Unrestricted\".\n" +
                                "2. Turn on \"Autostart\" if your phone has it.\n" +
                                "3. Don't force-stop or clear this app from recent apps with a lock.\n" +
                                "No Android phone can promise 100% delivery, but these steps help a lot.",
                            "कुछ फ़ोन (Xiaomi, Redmi, Realme, Oppo, Vivo, Samsung) Background में ऐप रोक देते हैं, जिससे Alarm रुक सकता है।\n" +
                                "1. App Settings → Battery → \"No restrictions\" / \"Unrestricted\" चुनें।\n" +
                                "2. फ़ोन में \"Autostart\" हो तो उसे चालू करें।\n" +
                                "3. इस ऐप को Force Stop न करें।\n" +
                                "कोई भी Android फ़ोन 100% गारंटी नहीं देता, पर इन steps से काफ़ी मदद मिलती है।"
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        TextButton(onClick = { openAppSettings(ctx) }) { Text(tr("App settings", "App Settings")) }
                        TextButton(onClick = { openBatterySettings(ctx) }) { Text(tr("Battery settings", "Battery Settings")) }
                    }
                }
            }

            SectionTitle(tr("Backup & Restore", "Backup और Restore"))
            Text(
                tr("Saves schedules, reminders, history, settings, voice recordings and photo in one .zip file.",
                    "Schedules, Reminders, History, Settings, Voice Recordings और Photo — सब एक .zip file में।"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    val stamp = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    backupLauncher.launch("SmartAlarm-backup-$stamp.zip")
                }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Backup, null); Spacer(Modifier.width(6.dp)); Text(tr("Backup", "Backup"))
                }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(6.dp)); Text(tr("Restore", "Restore"))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))

            SectionTitle(tr("About App", "ऐप के बारे में"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_logo), null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource0(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(tr("Version 1.0", "Version 1.0"), style = MaterialTheme.typography.bodySmall)
                    Text(
                        tr("Personal schedule & smart alarm. Works fully offline. Your data stays on your phone.",
                            "Personal Schedule और Smart Alarm. पूरी तरह Offline. आपका Data सिर्फ़ आपके फ़ोन में।"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    confirmRestore?.let { uri ->
        ConfirmDialog(
            tr("Restore backup?", "Backup Restore करें?"),
            tr("Current data will be replaced by the backup.", "अभी का Data Backup वाले Data से बदल जाएगा।"),
            tr("Restore", "Restore"),
            onDismiss = { confirmRestore = null },
            onConfirm = {
                scope.launch {
                    busy = true
                    val ok = withContext(Dispatchers.IO) { Backup.import(ctx, uri) }
                    busy = false
                    Toast.makeText(ctx, if (ok) tr("Restored", "Restore हो गया") else tr("Restore failed — is this a Smart Alarm backup?", "Restore नहीं हुआ — क्या यह Smart Alarm का Backup है?"), Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun stringResource0(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun PermRow(title: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
            tint = if (ok) StatusGreen else StatusRed
        )
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f))
        if (!ok) TextButton(onClick = onFix) { Text(tr("Allow", "चालू करें")) }
        else Text(tr("On", "चालू"), color = StatusGreen, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
    }
}

// ======================= Voice recordings =======================

@Composable
fun VoicesScreen(nav: NavState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val voices by remember { Repo.dao.voicesFlow() }.collectAsState(initial = emptyList())
    var recording by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<VoiceRecording?>(null) }
    var deleting by remember { mutableStateOf<VoiceRecording?>(null) }
    var playing by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) { onDispose { Preview.stop() } }

    Scaffold(
        topBar = { SimpleTopBar(tr("Voice Recordings", "Voice Recordings"), onBack = { nav.pop() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { recording = true }, icon = { Icon(Icons.Filled.Mic, null) }, text = { Text(tr("Record", "Record करें")) })
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item {
                Text(
                    tr("Record your own voice and assign it to any task or reminder. It plays when the alarm rings.",
                        "अपनी आवाज़ Record करें और किसी भी Task या Reminder में लगाएँ। Alarm बजने पर यही आवाज़ सुनाई देगी।"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
            if (voices.isEmpty()) item { EmptyState(Icons.Filled.MicNone, tr("No recordings yet.", "अभी कोई Recording नहीं।")) }
            items(voices, key = { it.id }) { v ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = {
                            if (playing == v.id) { Preview.stop(); playing = null }
                            else { playing = v.id; Preview.play(ctx, Uri.fromFile(Repo.voiceFile(v))) { playing = null } }
                        }) { Icon(if (playing == v.id) Icons.Filled.Stop else Icons.Filled.PlayArrow, null) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                AudioUtil.formatDuration(v.durationMs) + " • " + TimeUtil.formatDayShort(TimeUtil.dayOf(v.createdAt)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { renaming = v }) { Icon(Icons.Filled.Edit, tr("Rename", "नाम बदलें")) }
                        IconButton(onClick = { deleting = v }) { Icon(Icons.Filled.Delete, tr("Delete", "हटाएँ")) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (recording) RecorderDialog(onDismiss = { recording = false }, onSaved = { recording = false })
    renaming?.let { v ->
        TextInputDialog(tr("Rename", "नाम बदलें"), v.name, tr("Name", "नाम"), onDismiss = { renaming = null }) { n ->
            scope.launch(Dispatchers.IO) { Repo.dao.updateVoice(v.copy(name = n)) }
        }
    }
    deleting?.let { v ->
        ConfirmDialog(
            tr("Delete recording?", "Recording हटाएँ?"),
            tr("Tasks using it will speak the task name instead.", "जिन Tasks में यह लगी है, वहाँ ऐप Task का नाम बोलेगा।"),
            tr("Delete", "Delete"), onDismiss = { deleting = null },
            onConfirm = { Preview.stop(); scope.launch(Dispatchers.IO) { Repo.deleteVoice(v) } }
        )
    }
}
