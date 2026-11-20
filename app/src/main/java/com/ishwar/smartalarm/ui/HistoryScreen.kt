package com.ishwar.smartalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.ST_COMPLETED
import com.ishwar.smartalarm.data.ST_KEPT_PENDING
import com.ishwar.smartalarm.data.ST_MISSED
import com.ishwar.smartalarm.data.TaskStatus
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HistoryScreen(nav: NavState) {
    val now = rememberNow(15_000L)
    val today = TimeUtil.dayOf(now)
    val day = nav.historyDay
    val allDays = nav.historyAllDays
    val filter = nav.historyFilter
    var pickDate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Repo.refresh() } }

    val dayOccs by remember(day) { Repo.dao.occFlow(day) }.collectAsState(initial = emptyList())
    val allMissed by remember { Repo.dao.occByStatusFlow(ST_MISSED) }.collectAsState(initial = emptyList())
    val allDone by remember { Repo.dao.occByStatusFlow(ST_COMPLETED) }.collectAsState(initial = emptyList())
    val allKept by remember { Repo.dao.occByStatusFlow(ST_KEPT_PENDING) }.collectAsState(initial = emptyList())

    val withStatus = dayOccs.map { it to Repo.status(it, now) }
    val list = if (allDays) {
        when (filter) {
            TaskStatus.COMPLETED -> allDone
            TaskStatus.PENDING -> allKept
            else -> allMissed
        }
    } else {
        withStatus.filter { filter == null || it.second == filter }.map { it.first }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(tr("Task History", "Task History"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !allDays, onClick = { nav.historyAllDays = false },
                    label = { Text(tr("By day", "दिन के हिसाब से")) }, leadingIcon = { Icon(Icons.Filled.Today, null, Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = allDays,
                    onClick = {
                        nav.historyAllDays = true
                        if (nav.historyFilter != TaskStatus.COMPLETED && nav.historyFilter != TaskStatus.PENDING) nav.historyFilter = TaskStatus.MISSED
                    },
                    label = { Text(tr("All days", "सभी दिन")) }, leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)) }
                )
            }
        }

        if (!allDays) {
            item {
                Spacer(Modifier.height(10.dp))
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { nav.historyDay = day - 1 }) { Icon(Icons.Filled.ChevronLeft, null) }
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { pickDate = true }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(TimeUtil.dayLabel(day), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("${TimeUtil.formatWeekday(day)}, ${TimeUtil.formatDay(day)}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { nav.historyDay = day + 1 }, enabled = day < today) { Icon(Icons.Filled.ChevronRight, null) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(TaskStatus.COMPLETED, TaskStatus.PENDING, TaskStatus.MISSED, TaskStatus.UPCOMING).forEach { s ->
                                val n = withStatus.count { it.second == s }
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(statusColor(s).copy(alpha = 0.12f))
                                        .clickable { nav.historyFilter = if (filter == s) null else s }.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("$n", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = statusColor(s))
                                    Text(statusLabel(s), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val opts: List<TaskStatus?> = if (allDays) listOf(TaskStatus.MISSED, TaskStatus.COMPLETED, TaskStatus.PENDING)
                else listOf(null, TaskStatus.UPCOMING, TaskStatus.PENDING, TaskStatus.COMPLETED, TaskStatus.MISSED)
                opts.forEach { s ->
                    FilterChip(
                        selected = filter == s, onClick = { nav.historyFilter = s },
                        label = { Text(s?.let { statusLabel(it) } ?: tr("All", "सभी")) }
                    )
                }
            }
            if (allDays && filter == TaskStatus.PENDING) Text(
                tr("Tasks you chose to keep pending", "जिन Tasks को आपने Pending रखा है"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        if (list.isEmpty()) item {
            EmptyState(Icons.Filled.Inbox, tr("Nothing here.", "यहाँ कुछ नहीं है।"))
        }
        items(list, key = { it.id }) { o ->
            OccurrenceRow(o, now, showDate = allDays, showActions = true)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Text(
                tr("Tip: tap ⋮ on a task to complete it, keep it pending or delete it.",
                    "Tip: किसी Task पर ⋮ दबाकर उसे Complete, Pending या Delete कर सकते हैं।"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }

    if (pickDate) DatePickDialog(day, { pickDate = false }) { nav.historyDay = minOf(it, today) }
}
