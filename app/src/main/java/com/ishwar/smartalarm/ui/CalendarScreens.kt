package com.ishwar.smartalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ishwar.smartalarm.alarm.AlarmEngine
import com.ishwar.smartalarm.data.KIND_REMINDER
import com.ishwar.smartalarm.data.REPEAT_DAILY
import com.ishwar.smartalarm.data.REPEAT_MONTHLY
import com.ishwar.smartalarm.data.REPEAT_NONE
import com.ishwar.smartalarm.data.REPEAT_WEEKLY
import com.ishwar.smartalarm.data.REPEAT_YEARLY
import com.ishwar.smartalarm.data.Reminder
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.ST_COMPLETED
import com.ishwar.smartalarm.data.ST_MISSED
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

fun repeatLabel(r: Int): String = when (r) {
    REPEAT_DAILY -> tr("Every day", "रोज़")
    REPEAT_WEEKLY -> tr("Every week", "हर हफ़्ते")
    REPEAT_MONTHLY -> tr("Every month", "हर महीने")
    REPEAT_YEARLY -> tr("Every year", "हर साल")
    else -> tr("Once", "एक बार")
}

@Composable
fun CalendarScreen(nav: NavState) {
    val scope = rememberCoroutineScope()
    val now = rememberNow(15_000L)
    val reminders by remember { Repo.dao.remindersFlow() }.collectAsState(initial = emptyList())
    val selected = nav.calendarDay
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(selected))) }
    var filter by remember { mutableIntStateOf(0) } // 0 date, 1 all, 2 completed, 3 missed
    val dayOccs by remember(selected) { Repo.dao.occFlow(selected) }.collectAsState(initial = emptyList())
    val completed by remember { Repo.dao.reminderOccByStatusFlow(ST_COMPLETED) }.collectAsState(initial = emptyList())
    val missed by remember { Repo.dao.reminderOccByStatusFlow(ST_MISSED) }.collectAsState(initial = emptyList())

    // Mahine ke jin dino par reminder hai
    val marked = remember(reminders, month) {
        val first = month.atDay(1).toEpochDay()
        val last = month.atEndOfMonth().toEpochDay()
        (first..last).filter { d -> reminders.any { Repo.reminderOnDay(it, d) } }.toSet()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
            item {
                Text(tr("Date Schedule", "Date Schedule"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, tr("Previous month", "पिछला महीना")) }
                            Text(
                                TimeUtil.formatMonth(month.year, month.monthValue),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, tr("Next month", "अगला महीना")) }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            for (i in 0..6) Text(
                                TimeUtil.dayName(i), Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MonthGrid(month, selected, marked) { d -> nav.calendarDay = d; filter = 0 }
                        TextButton(onClick = {
                            val t = TimeUtil.today(); nav.calendarDay = t; month = YearMonth.from(LocalDate.ofEpochDay(t)); filter = 0
                        }) { Text(tr("Go to today", "आज पर जाएँ")) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        tr("This date", "यह तारीख"), tr("All", "सभी"), tr("Done", "पूरे"), tr("Missed", "छूटे")
                    ).forEachIndexed { i, l ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l, maxLines = 1) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (filter) {
                0 -> {
                    val list = reminders.filter { Repo.reminderOnDay(it, selected) }.sortedBy { it.timeMin }
                    item {
                        SectionTitle(TimeUtil.dayLabel(selected) + if (selected == TimeUtil.today()) " • " + TimeUtil.formatDay(selected) else "")
                    }
                    if (list.isEmpty()) item {
                        EmptyState(Icons.Filled.EventNote, tr("No reminders on this date. Tap “Add Reminder”.", "इस तारीख पर कोई Reminder नहीं। “Reminder जोड़ें” दबाइए।"))
                    }
                    items(list, key = { it.id }) { r ->
                        val occ = dayOccs.firstOrNull { it.kind == KIND_REMINDER && it.sourceId == r.id }
                        val chip: (@Composable () -> Unit)? = if (occ != null) {
                            { StatusChip(Repo.status(occ, now)) }
                        } else null
                        ReminderCard(r, showDate = false, status = chip,
                            onClick = { nav.push(Screen.ReminderEdit(r.id, selected)) },
                            onToggle = { on -> scope.launch(Dispatchers.IO) { Repo.saveReminder(r.copy(alarmEnabled = on)) } },
                            onDelete = { scope.launch(Dispatchers.IO) { Repo.deleteReminder(r) } })
                        Spacer(Modifier.height(8.dp))
                    }
                }
                1 -> {
                    val list = reminders.sortedBy { AlarmEngine.nextReminder(it.copy(alarmEnabled = true)) ?: Long.MAX_VALUE }
                    if (list.isEmpty()) item { EmptyState(Icons.Filled.EventNote, tr("No reminders yet.", "अभी कोई Reminder नहीं।")) }
                    items(list, key = { it.id }) { r ->
                        ReminderCard(r, showDate = true, status = null,
                            onClick = { nav.push(Screen.ReminderEdit(r.id, r.day)) },
                            onToggle = { on -> scope.launch(Dispatchers.IO) { Repo.saveReminder(r.copy(alarmEnabled = on)) } },
                            onDelete = { scope.launch(Dispatchers.IO) { Repo.deleteReminder(r) } })
                        Spacer(Modifier.height(8.dp))
                    }
                }
                else -> {
                    val list = if (filter == 2) completed else missed
                    if (list.isEmpty()) item {
                        EmptyState(Icons.Filled.Inbox, if (filter == 2) tr("No completed reminders.", "कोई पूरा Reminder नहीं।") else tr("No missed reminders.", "कोई छूटा Reminder नहीं।"))
                    }
                    items(list, key = { it.id }) { o ->
                        OccurrenceRow(o, now, showDate = true, showActions = true)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.push(Screen.ReminderEdit(0, selected)) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(tr("Add Reminder", "Reminder जोड़ें")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun MonthGrid(month: YearMonth, selected: Long, marked: Set<Long>, onPick: (Long) -> Unit) {
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1 // Monday start
    val total = month.lengthOfMonth()
    val rows = (offset + total + 6) / 7
    val today = TimeUtil.today()
    Column {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val n = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..total) {
                            val d = month.atDay(n).toEpochDay()
                            val isSel = d == selected
                            val isToday = d == today
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .then(if (isToday && !isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .clickable { onPick(d) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "$n", fontSize = 15.sp,
                                    fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    Modifier.size(5.dp).clip(CircleShape).background(
                                        when {
                                            d !in marked -> Color.Transparent
                                            isSel -> MaterialTheme.colorScheme.onPrimary
                                            else -> Orange
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    r: Reminder, showDate: Boolean, status: (@Composable () -> Unit)?,
    onClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (showDate) TimeUtil.formatDay(r.day) + " • " else "") + TimeUtil.formatMin(r.timeMin),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
                )
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Repeat, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        " " + repeatLabel(r.repeat) + "  •  " + if (r.alarmEnabled) tr("Alarm On", "Alarm On") else tr("Alarm Off", "Alarm Off"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                status?.invoke()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = r.alarmEnabled, onCheckedChange = onToggle)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("Edit", "Edit करें")) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onClick() })
                            DropdownMenuItem(text = { Text(tr("Delete", "Delete करें")) }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; confirm = true })
                        }
                    }
                }
            }
        }
    }
    if (confirm) ConfirmDialog(tr("Delete reminder?", "Reminder हटाएँ?"), r.title, tr("Delete", "Delete"), { confirm = false }, onDelete)
}

// ======================= Reminder editor =======================

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ReminderEditScreen(nav: NavState, id: Long, day: Long) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Reminder?>(null) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(day) }
    var time by remember {
        val m = TimeUtil.minuteOfDay(System.currentTimeMillis())
        mutableIntStateOf(((m / 30) + 1) * 30 % 1440) // agla aadha ghanta
    }
    var repeat by remember { mutableIntStateOf(REPEAT_NONE) }
    var alarm by remember { mutableStateOf(true) }
    var sound by remember { mutableStateOf<String?>(null) }
    var voice by remember { mutableStateOf<Long?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val r = withContext(Dispatchers.IO) { Repo.dao.reminder(id) }
            if (r != null) {
                original = r; title = r.title; desc = r.description; date = r.day; time = r.timeMin
                repeat = r.repeat; alarm = r.alarmEnabled; sound = r.soundUri; voice = r.voiceId
            }
            loaded = true
        }
    }

    fun save() {
        if (title.isBlank()) { titleError = true; return }
        val base = original ?: Reminder(title = "", day = date, timeMin = time)
        val r = base.copy(
            title = title.trim(), description = desc.trim(), day = date, timeMin = time,
            repeat = repeat, alarmEnabled = alarm, soundUri = sound, voiceId = voice
        )
        scope.launch {
            withContext(Dispatchers.IO) { Repo.saveReminder(r) }
            nav.calendarDay = date
            nav.pop()
        }
    }

    Scaffold(
        topBar = {
            SimpleTopBar(if (id == 0L) tr("New Reminder", "नया Reminder") else tr("Edit Reminder", "Reminder Edit करें"), onBack = { nav.pop() }) {
                TextButton(onClick = { save() }) { Text(tr("Save", "सेव"), fontWeight = FontWeight.Bold) }
            }
        }
    ) { pad ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                title, { title = it; titleError = false },
                label = { Text(tr("Reminder title", "Reminder का नाम")) },
                placeholder = { Text(tr("e.g. Doctor Appointment", "जैसे Doctor Appointment")) },
                isError = titleError, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { PickerRow(Icons.Filled.Event, tr("Date", "तारीख"), TimeUtil.formatDay(date), onClick = { pickDate = true }) }
                Box(Modifier.weight(1f)) { PickerRow(Icons.Filled.Schedule, tr("Time", "समय"), TimeUtil.formatMin(time), onClick = { pickTime = true }) }
            }
            OutlinedTextField(desc, { desc = it }, label = { Text(tr("Description", "विवरण")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text(tr("Repeat", "Repeat"), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(REPEAT_NONE, REPEAT_DAILY, REPEAT_WEEKLY, REPEAT_MONTHLY, REPEAT_YEARLY).forEach { r ->
                    FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(repeatLabel(r)) })
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(tr("Alarm", "Alarm"), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(alarm, { alarm = it })
            }
            if (alarm) {
                SoundPicker(sound, { sound = it })
                VoicePicker(voice, { voice = it })
            }
            val nextAt = AlarmEngine.nextReminder(Reminder(title = "", day = date, timeMin = time, repeat = repeat, alarmEnabled = alarm))
            if (alarm) Text(
                if (nextAt != null) tr("Will ring: ", "बजेगा: ") + TimeUtil.dayLabel(TimeUtil.dayOf(nextAt)) + " • " + TimeUtil.formatClock(nextAt) + " (" + TimeUtil.relative(nextAt) + ")"
                else tr("This time has already passed.", "यह समय निकल चुका है।"),
                style = MaterialTheme.typography.bodySmall,
                color = if (nextAt != null) MaterialTheme.colorScheme.primary else StatusRed
            )
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(tr("Save Reminder", "Reminder सेव करें"), fontSize = 16.sp)
            }
            if (original != null) OutlinedButton(
                onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
            ) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text(tr("Delete Reminder", "Reminder Delete करें")) }
        }
    }

    if (pickDate) DatePickDialog(date, { pickDate = false }) { date = it }
    if (pickTime) TimePickDialog(time, { pickTime = false }) { time = it }
    if (confirmDelete) original?.let { r ->
        ConfirmDialog(
            tr("Delete reminder?", "Reminder हटाएँ?"), r.title, tr("Delete", "Delete"),
            onDismiss = { confirmDelete = false },
            onConfirm = { scope.launch { withContext(Dispatchers.IO) { Repo.deleteReminder(r) }; nav.pop() } }
        )
    }
}
