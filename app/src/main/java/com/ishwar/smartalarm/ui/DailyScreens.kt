package com.ishwar.smartalarm.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ishwar.smartalarm.data.ALL_DAYS
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.RoutineTask
import com.ishwar.smartalarm.data.ScheduleSet
import com.ishwar.smartalarm.data.WEEKDAYS
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================= Sets list =======================

@Composable
fun DailyScreen(nav: NavState) {
    val scope = rememberCoroutineScope()
    val sets by remember { Repo.dao.setsFlow() }.collectAsState(initial = emptyList())
    val counts by remember { Repo.dao.setCountsFlow() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<ScheduleSet?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(nav.createSetRequest) {
        if (nav.createSetRequest) { creating = true; nav.createSetRequest = false }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
            item {
                Text(tr("Daily Schedule", "Daily Schedule"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    tr("Make as many routine sets as you like. Only one set is active at a time.",
                        "जितने चाहें उतने Routine Sets बनाइए। एक समय पर एक ही Set Active रहेगा।"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
            if (sets.isEmpty()) item {
                EmptyState(Icons.Filled.ViewTimeline, tr("No schedule yet. Tap “New Set”.", "अभी कोई Schedule नहीं है। “New Set” दबाइए।"))
            }
            items(sets, key = { it.id }) { s ->
                SetCard(
                    s, counts.firstOrNull { it.setId == s.id }?.c ?: 0,
                    onOpen = { nav.push(Screen.SetDetail(s.id)) },
                    onEdit = { editing = s },
                    onToggleActive = { on -> scope.launch(Dispatchers.IO) { if (on) Repo.activateSet(s.id) else Repo.deactivateSet(s.id) } },
                    onDuplicate = { scope.launch(Dispatchers.IO) { Repo.duplicateSet(s.id, tr("copy", "कॉपी")) } },
                    onDelete = { scope.launch(Dispatchers.IO) { Repo.deleteSet(s.id) } }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(tr("New Set", "New Set")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }

    if (creating) SetEditDialog(null, onDismiss = { creating = false }) { saved ->
        scope.launch {
            val id = withContext(Dispatchers.IO) { Repo.saveSet(saved) }
            nav.push(Screen.SetDetail(id))
        }
    }
    editing?.let { s ->
        SetEditDialog(s, onDismiss = { editing = null }) { saved -> scope.launch(Dispatchers.IO) { Repo.saveSet(saved) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetCard(
    s: ScheduleSet, taskCount: Int,
    onOpen: () -> Unit, onEdit: () -> Unit, onToggleActive: (Boolean) -> Unit,
    onDuplicate: () -> Unit, onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (s.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (s.active) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusGreen,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(StatusGreen.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    tr("$taskCount tasks", "$taskCount Tasks") + rangeText(s),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = s.active, onCheckedChange = onToggleActive)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Open / add tasks", "खोलें / Tasks जोड़ें")) }, leadingIcon = { Icon(Icons.Filled.List, null) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text(tr("Rename / Edit", "नाम बदलें / Edit")) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(tr("Duplicate", "Duplicate करें")) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(
                        text = { Text(if (s.active) tr("Deactivate", "Deactivate करें") else tr("Activate", "Activate करें")) },
                        leadingIcon = { Icon(if (s.active) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle, null) },
                        onClick = { menu = false; onToggleActive(!s.active) }
                    )
                    DropdownMenuItem(text = { Text(tr("Delete", "Delete करें")) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; confirm = true })
                }
            }
        }
    }
    if (confirm) ConfirmDialog(
        tr("Delete this set?", "यह Set हटाएँ?"),
        tr("\"${s.name}\" and all its tasks will be deleted.", "\"${s.name}\" और इसके सभी Tasks हट जाएँगे।"),
        tr("Delete", "Delete"), onDismiss = { confirm = false }, onConfirm = onDelete
    )
}

private fun rangeText(s: ScheduleSet): String {
    if (s.startDay == null && s.endDay == null) return ""
    val a = s.startDay?.let { TimeUtil.formatDayShort(it) } ?: "…"
    val b = s.endDay?.let { TimeUtil.formatDayShort(it) } ?: "…"
    return "  •  $a – $b"
}

@Composable
fun SetEditDialog(initial: ScheduleSet?, onDismiss: () -> Unit, onSave: (ScheduleSet) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var start by remember { mutableStateOf(initial?.startDay) }
    var end by remember { mutableStateOf(initial?.endDay) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) tr("New Schedule Set", "नया Schedule Set") else tr("Edit Schedule Set", "Schedule Set Edit करें")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Schedule name (e.g. Morning Shift)", "Schedule का नाम (जैसे Morning Shift)")) }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text(tr("Description (optional)", "विवरण (Optional)")) })
                PickerRow(Icons.Filled.Event, tr("Start date (optional)", "Start Date (Optional)"),
                    start?.let { TimeUtil.formatDay(it) } ?: tr("Not set", "सेट नहीं"), onClick = { pickStart = true }) {
                    if (start != null) IconButton(onClick = { start = null }) { Icon(Icons.Filled.Close, null) }
                }
                PickerRow(Icons.Filled.EventBusy, tr("End date (optional)", "End Date (Optional)"),
                    end?.let { TimeUtil.formatDay(it) } ?: tr("Not set", "सेट नहीं"), onClick = { pickEnd = true }) {
                    if (end != null) IconButton(onClick = { end = null }) { Icon(Icons.Filled.Close, null) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Active", "Active"), fontWeight = FontWeight.Medium)
                        Text(tr("Other sets' alarms will turn off", "दूसरे Sets के Alarms बंद हो जाएँगे"), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(active, { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val s = (initial ?: ScheduleSet(name = "")).copy(
                    name = name.trim(), description = desc.trim(), startDay = start, endDay = end, active = active
                )
                onSave(s)
                onDismiss()
            }) { Text(tr("Save", "सेव करें")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } }
    )
    if (pickStart) DatePickDialog(start ?: TimeUtil.today(), { pickStart = false }) { start = it }
    if (pickEnd) DatePickDialog(end ?: (start ?: TimeUtil.today()), { pickEnd = false }) { end = it }
}

// ======================= Set detail =======================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetDetailScreen(nav: NavState, setId: Long) {
    val scope = rememberCoroutineScope()
    val set by remember(setId) { Repo.dao.setFlow(setId) }.collectAsState(initial = null)
    val tasks by remember(setId) { Repo.dao.tasksFlow(setId) }.collectAsState(initial = emptyList())
    var editSet by remember { mutableStateOf(false) }
    var deleteTask by remember { mutableStateOf<RoutineTask?>(null) }

    Scaffold(
        topBar = {
            SimpleTopBar(set?.name ?: "", onBack = { nav.pop() }) {
                IconButton(onClick = { editSet = true }) { Icon(Icons.Filled.Edit, tr("Edit", "Edit")) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.push(Screen.TaskEdit(setId, 0)) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(tr("Add Task", "Task जोड़ें")) }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            set?.let { s ->
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = if (s.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (s.active) tr("Active Schedule", "Active Schedule") else tr("Inactive", "Inactive"),
                                    fontWeight = FontWeight.Bold, color = if (s.active) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodyMedium)
                                val r = rangeText(s).removePrefix("  •  ")
                                if (r.isNotBlank()) Text(r, style = MaterialTheme.typography.labelMedium)
                            }
                            Switch(checked = s.active, onCheckedChange = { on ->
                                scope.launch(Dispatchers.IO) { if (on) Repo.activateSet(s.id) else Repo.deactivateSet(s.id) }
                            })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (tasks.isEmpty()) item {
                EmptyState(Icons.Filled.AddAlarm, tr("No tasks yet. Tap “Add Task”.", "अभी कोई Task नहीं। “Task जोड़ें” दबाइए।"))
            }
            items(tasks, key = { it.id }) { t ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { nav.push(Screen.TaskEdit(setId, t.id)) },
                            onLongClick = { deleteTask = t }
                        )
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(72.dp)) {
                            Text(TimeUtil.formatMin(t.startMin).substringBefore(' '), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(TimeUtil.formatMin(t.startMin).substringAfter(' '), style = MaterialTheme.typography.labelSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(TimeUtil.formatRange(t.startMin, t.endMin), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(TimeUtil.daysText(t.days), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (t.voiceId != null) Icon(Icons.Filled.RecordVoiceOver, null, Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                if (t.soundUri != null) Icon(Icons.Filled.MusicNote, null, Modifier.padding(start = 4.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(checked = t.alarmEnabled, onCheckedChange = { on -> scope.launch(Dispatchers.IO) { Repo.setTaskAlarm(t, on) } })
                            Icon(
                                if (t.alarmEnabled) Icons.Filled.Alarm else Icons.Filled.AlarmOff, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (tasks.isNotEmpty()) item {
                Text(
                    tr("Tip: long-press a task to delete it.", "Tip: Task हटाने के लिए उसे देर तक दबाएँ।"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (editSet) set?.let { s ->
        SetEditDialog(s, onDismiss = { editSet = false }) { saved -> scope.launch(Dispatchers.IO) { Repo.saveSet(saved) } }
    }
    deleteTask?.let { t ->
        ConfirmDialog(
            tr("Delete task?", "Task हटाएँ?"), "\"${t.name}\" • ${TimeUtil.formatMin(t.startMin)}", tr("Delete", "Delete"),
            onDismiss = { deleteTask = null }, onConfirm = { scope.launch(Dispatchers.IO) { Repo.deleteTask(t) } }
        )
    }
}

// ======================= Task editor =======================

@Composable
fun TaskEditScreen(nav: NavState, setId: Long, taskId: Long) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(taskId == 0L) }
    var original by remember { mutableStateOf<RoutineTask?>(null) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(7 * 60) }
    var end by remember { mutableStateOf<Int?>(null) }
    var days by remember { mutableIntStateOf(ALL_DAYS) }
    var alarm by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf<String?>(null) }
    var voice by remember { mutableStateOf<Long?>(null) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(taskId) {
        if (taskId != 0L) {
            val t = withContext(Dispatchers.IO) { Repo.dao.task(taskId) }
            if (t != null) {
                original = t; name = t.name; desc = t.description; start = t.startMin; end = t.endMin
                days = t.days; alarm = t.alarmEnabled; sound = t.soundUri; voice = t.voiceId
            }
            loaded = true
        }
    }

    fun save() {
        if (name.isBlank()) { nameError = true; return }
        val base = original ?: RoutineTask(setId = setId, name = "", startMin = 0)
        val t = base.copy(
            name = name.trim(), description = desc.trim(), startMin = start, endMin = end,
            days = days, alarmEnabled = alarm, soundUri = sound, voiceId = voice
        )
        scope.launch {
            withContext(Dispatchers.IO) { Repo.saveTask(t) }
            nav.pop()
        }
    }

    Scaffold(
        topBar = {
            SimpleTopBar(if (taskId == 0L) tr("New Task", "नया Task") else tr("Edit Task", "Task Edit करें"), onBack = { nav.pop() }) {
                TextButton(onClick = { save() }) { Text(tr("Save", "सेव"), fontWeight = FontWeight.Bold) }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                name, { name = it; nameError = false },
                label = { Text(tr("Task name", "Task का नाम")) },
                placeholder = { Text(tr("e.g. Exercise", "जैसे Exercise")) },
                isError = nameError, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    PickerRow(Icons.Filled.Schedule, tr("Start time", "शुरू होने का समय"), TimeUtil.formatMin(start), onClick = { pickStart = true })
                }
                Box(Modifier.weight(1f)) {
                    PickerRow(Icons.Filled.TimerOff, tr("End time", "खत्म होने का समय"), end?.let { TimeUtil.formatMin(it) } ?: tr("Optional", "Optional"), onClick = { pickEnd = true })
                }
            }
            if (end != null) TextButton(onClick = { end = null }) { Text(tr("Remove end time", "End time हटाएँ")) }
            OutlinedTextField(
                desc, { desc = it }, label = { Text(tr("Description", "विवरण")) },
                placeholder = { Text(tr("e.g. One hour of exercise", "जैसे एक घंटे तक Exercise करना")) },
                minLines = 2, modifier = Modifier.fillMaxWidth()
            )
            Text(tr("Which days?", "किन दिनों में?"), fontWeight = FontWeight.Bold)
            DayChips(days) { days = it }
            Row {
                TextButton(onClick = { days = ALL_DAYS }) { Text(tr("Every day", "रोज़")) }
                TextButton(onClick = { days = WEEKDAYS }) { Text(tr("Mon–Fri", "सोम–शुक्र")) }
                TextButton(onClick = { days = 0b1100000 }) { Text(tr("Weekend", "शनि–रवि")) }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Alarm", "Alarm"), fontWeight = FontWeight.Medium)
                    Text(tr("Ring at start time", "शुरू होने के समय बजे"), style = MaterialTheme.typography.bodySmall)
                }
                Switch(alarm, { alarm = it })
            }
            if (alarm) {
                SoundPicker(sound, { sound = it })
                VoicePicker(voice, { voice = it })
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(tr("Save Task", "Task सेव करें"), fontSize = 16.sp)
            }
            if (original != null) OutlinedButton(
                onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
            ) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text(tr("Delete Task", "Task Delete करें")) }
        }
    }

    if (pickStart) TimePickDialog(start, { pickStart = false }) { start = it }
    if (pickEnd) TimePickDialog(end ?: (start + 60) % 1440, { pickEnd = false }) { end = it }
    if (confirmDelete) original?.let { t ->
        ConfirmDialog(
            tr("Delete task?", "Task हटाएँ?"), t.name, tr("Delete", "Delete"),
            onDismiss = { confirmDelete = false },
            onConfirm = { scope.launch { withContext(Dispatchers.IO) { Repo.deleteTask(t) }; nav.pop() } }
        )
    }
}
