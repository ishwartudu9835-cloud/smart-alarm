package com.ishwar.smartalarm.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ishwar.smartalarm.R
import com.ishwar.smartalarm.alarm.AlarmEngine
import com.ishwar.smartalarm.data.KIND_ROUTINE
import com.ishwar.smartalarm.data.Occurrence
import com.ishwar.smartalarm.data.Prefs
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.TaskStatus
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(nav: NavState) {
    val ctx = LocalContext.current
    val now = rememberNow(1000L)
    val today = TimeUtil.dayOf(now)
    val occs by remember(today) { Repo.dao.occFlow(today) }.collectAsState(initial = emptyList())
    val activeSet by remember { Repo.dao.activeSetFlow() }.collectAsState(initial = null)
    var next by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val minuteTick = now / 60_000L

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) { Repo.refresh() }
            delay(60_000L)
        }
    }
    LaunchedEffect(occs, activeSet, minuteTick) {
        next = withContext(Dispatchers.IO) { AlarmEngine.nextAlarm() }
    }

    val statuses = occs.map { Repo.status(it, now) }
    val done = statuses.count { it == TaskStatus.COMPLETED }
    val pending = statuses.count { it == TaskStatus.PENDING }
    val missed = statuses.count { it == TaskStatus.MISSED }
    val upcoming = statuses.count { it == TaskStatus.UPCOMING }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { nav.tab = 4 }) { Avatar(56.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (Prefs.name.isBlank()) tr("Hello!", "नमस्ते!") else tr("Hello, ${Prefs.name}", "नमस्ते, ${Prefs.name}"),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${TimeUtil.formatWeekday(today)}, ${TimeUtil.formatDay(today)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(TimeUtil.formatClock(now), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        item { SetupBanner(now) }

        // Today's overview
        item {
            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Navy, Color(0xFF15408F))))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Today's Overview", "आज का Overview"), color = Cyan, fontWeight = FontWeight.Bold)
                            Text(TimeUtil.formatDay(today), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                activeSet?.let { tr("Active: ${it.name}", "Active: ${it.name}") } ?: tr("No active schedule", "कोई Schedule Active नहीं"),
                                color = Color(0xFFB8C8F0), fontSize = 13.sp
                            )
                        }
                        Image(painterResource(R.drawable.app_logo), null, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountBox(done, tr("Completed", "पूरे"), StatusGreen, Modifier.weight(1f)) { nav.openHistory(TaskStatus.COMPLETED) }
                        CountBox(pending, tr("Pending", "बाकी"), StatusOrange, Modifier.weight(1f)) { nav.openHistory(TaskStatus.PENDING) }
                        CountBox(missed, tr("Missed", "छूटे"), StatusRed, Modifier.weight(1f)) { nav.openHistory(TaskStatus.MISSED) }
                    }
                    if (occs.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { done.toFloat() / occs.size },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = StatusGreen, trackColor = Color(0x33FFFFFF)
                        )
                        Text(
                            tr("$done of ${occs.size} done • $upcoming upcoming", "${occs.size} में से $done पूरे • $upcoming आने वाले"),
                            color = Color(0xFFB8C8F0), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // Next alarm
        item {
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Next Alarm", "अगला Alarm"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val n = next
                        if (n == null) {
                            Text(tr("No alarm set", "कोई Alarm सेट नहीं"), style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text(n.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${TimeUtil.dayLabel(TimeUtil.dayOf(n.second))} • ${TimeUtil.formatClock(n.second)} • ${TimeUtil.relative(n.second)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Quick actions
        item {
            SectionTitle(tr("Quick Actions", "Quick Actions"))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(Icons.Filled.ViewTimeline, tr("Create Daily Schedule", "Daily Schedule बनाएँ"), Modifier.weight(1f)) {
                        nav.createSetRequest = true; nav.tab = 1
                    }
                    QuickAction(Icons.Filled.EditCalendar, tr("Create Date Reminder", "Date Reminder बनाएँ"), Modifier.weight(1f)) {
                        nav.push(Screen.ReminderEdit(0, today))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(Icons.Filled.Checklist, tr("View All Tasks", "सभी Tasks देखें"), Modifier.weight(1f)) {
                        nav.openHistory(null)
                    }
                    QuickAction(Icons.Filled.ErrorOutline, tr("View Missed Tasks", "Missed Tasks देखें"), Modifier.weight(1f)) {
                        nav.openHistory(TaskStatus.MISSED, allDays = true)
                    }
                }
            }
        }

        item { SectionTitle(tr("Today's Tasks", "आज के Tasks")) }
        if (occs.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.EventAvailable,
                    tr("Nothing scheduled today. Create a daily schedule or a date reminder.",
                        "आज के लिए कुछ नहीं है। Daily Schedule या Date Reminder बनाइए।")
                )
            }
        } else {
            items(occs, key = { it.id }) { o ->
                OccurrenceRow(o, now, showDate = false)
                Spacer(Modifier.height(8.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CountBox(n: Int, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x1FFFFFFF)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$n", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color(0xFFDCE6FF), fontSize = 13.sp)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
    }
}

/** Ek task/reminder ki line, status ke saath. History me actions bhi. */
@Composable
fun OccurrenceRow(o: Occurrence, now: Long, showDate: Boolean, showActions: Boolean = false) {
    val scope = rememberCoroutineScope()
    val st = Repo.status(o, now)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(50)).background(statusColor(st)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (o.kind == KIND_ROUTINE) Icons.Filled.Repeat else Icons.Filled.Event, null,
                        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        (if (showDate) TimeUtil.formatDayShort(o.day) + " • " else "") + TimeUtil.formatRange(o.startMin, o.endMin),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(o.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (o.description.isNotBlank()) Text(o.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(st)
                if (st != TaskStatus.COMPLETED) {
                    Row {
                        IconButton(onClick = { scope.launch(Dispatchers.IO) { Repo.complete(o.id) } }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.CheckCircle, tr("Mark done", "पूरा करें"), tint = StatusGreen)
                        }
                        if (showActions) Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.MoreVert, null) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(tr("Mark complete", "Complete करें")) }, onClick = {
                                    menu = false; scope.launch(Dispatchers.IO) { Repo.complete(o.id) }
                                })
                                DropdownMenuItem(text = { Text(tr("Keep pending", "Pending रखें")) }, onClick = {
                                    menu = false; scope.launch(Dispatchers.IO) { Repo.keepPending(o.id) }
                                })
                                DropdownMenuItem(text = { Text(tr("Delete", "Delete करें")) }, onClick = {
                                    menu = false; confirmDelete = true
                                })
                            }
                        }
                    }
                } else if (showActions) {
                    TextButton(onClick = { confirmDelete = true }) { Text(tr("Delete", "Delete"), fontSize = 12.sp) }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            tr("Delete this entry?", "यह Entry हटाएँ?"), o.title, tr("Delete", "Delete"),
            onDismiss = { confirmDelete = false },
            onConfirm = { scope.launch(Dispatchers.IO) { Repo.deleteOcc(o.id) } }
        )
    }
}

// ---------------- Permission / reliability helpers ----------------

data class AlarmHealth(
    val notifications: Boolean,
    val exact: Boolean,
    val fullScreen: Boolean,
    val battery: Boolean
) {
    val allOk get() = notifications && exact && fullScreen
}

fun alarmHealth(ctx: Context): AlarmHealth {
    val notif = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val fs = if (Build.VERSION.SDK_INT >= 34) ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() else true
    val pm = ctx.getSystemService(PowerManager::class.java)
    return AlarmHealth(notif, AlarmEngine.canExact(ctx), fs, pm.isIgnoringBatteryOptimizations(ctx.packageName))
}

fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 31) runCatching {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
    }
}

fun openFullScreenSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 34) runCatching {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
    }
}

fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }
}

fun openBatterySettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        .onFailure { openAppSettings(ctx) }
}

@Composable
private fun SetupBanner(now: Long) {
    val ctx = LocalContext.current
    val tick = now / 3000L
    val health = remember(tick) { alarmHealth(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (!Prefs.askedPermissions && Build.VERSION.SDK_INT >= 33 && !health.notifications) {
            Prefs.askedPermissions = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (health.allOk) return
    Spacer(Modifier.height(12.dp))
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StatusOrange.copy(alpha = 0.15f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = StatusOrange)
                Spacer(Modifier.width(8.dp))
                Text(tr("Allow these so alarms ring on time", "Alarm समय पर बजे, इसके लिए ये चालू करें"), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            if (!health.notifications) TextButton(onClick = { openAppSettings(ctx) }) { Text("• " + tr("Allow notifications", "Notifications चालू करें")) }
            if (!health.exact) TextButton(onClick = { openExactAlarmSettings(ctx) }) {
                Text("• " + tr("Allow alarms & reminders", "Alarms & reminders चालू करें"))
            }
            if (!health.fullScreen) TextButton(onClick = { openFullScreenSettings(ctx) }) {
                Text("• " + tr("Allow full-screen alarm", "Full-screen alarm चालू करें"))
            }
        }
    }
}
