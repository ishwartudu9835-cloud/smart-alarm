package com.ishwar.smartalarm.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ishwar.smartalarm.alarm.AlarmEngine
import com.ishwar.smartalarm.data.Repo
import com.ishwar.smartalarm.data.TaskStatus
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_MISSED = 1
    }

    private val nav = NavState()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.init(this)
        handleOpen(intent)
        setContent {
            SmartTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(nav)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(i: Intent?) {
        if (i?.getIntExtra(EXTRA_OPEN, 0) == OPEN_MISSED) nav.openHistory(TaskStatus.MISSED, allDays = true)
    }

    override fun onResume() {
        super.onResume()
        // Force-stop ke baad bhi app kholte hi alarms wapas
        lifecycleScope.launch(Dispatchers.IO) {
            Repo.refresh()
            AlarmEngine.rescheduleAll(this@MainActivity)
        }
    }
}

/** Bina library ke simple navigation */
sealed class Screen {
    data class SetDetail(val setId: Long) : Screen()
    data class TaskEdit(val setId: Long, val taskId: Long) : Screen()
    data class ReminderEdit(val id: Long, val day: Long) : Screen()
    data object Voices : Screen()
    data object Settings : Screen()
}

class NavState {
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Screen>()
    var historyFilter by mutableStateOf<TaskStatus?>(null)
    var historyAllDays by mutableStateOf(false)
    var historyDay by mutableLongStateOf(TimeUtil.today())
    var calendarDay by mutableLongStateOf(TimeUtil.today())
    var createSetRequest by mutableStateOf(false)

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    fun openHistory(filter: TaskStatus?, allDays: Boolean = false) {
        stack.clear()
        historyFilter = filter
        historyAllDays = allDays
        historyDay = TimeUtil.today()
        tab = 3
    }
}

@Composable
fun AppRoot(nav: NavState) {
    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != 0) {
        if (nav.stack.isNotEmpty()) nav.pop() else nav.tab = 0
    }
    val top = nav.stack.lastOrNull()
    AnimatedContent(targetState = top, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { screen ->
        when (screen) {
            null -> TabsScaffold(nav)
            is Screen.SetDetail -> SetDetailScreen(nav, screen.setId)
            is Screen.TaskEdit -> TaskEditScreen(nav, screen.setId, screen.taskId)
            is Screen.ReminderEdit -> ReminderEditScreen(nav, screen.id, screen.day)
            Screen.Voices -> VoicesScreen(nav)
            Screen.Settings -> SettingsScreen(nav)
        }
    }
}

@Composable
private fun TabsScaffold(nav: NavState) {
    val tabs = remember { listOf(0, 1, 2, 3, 4) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { i ->
                    val (icon, label) = when (i) {
                        0 -> Icons.Filled.Home to tr("Home", "होम")
                        1 -> Icons.Filled.ViewTimeline to tr("Daily", "डेली")
                        2 -> Icons.Filled.CalendarMonth to tr("Calendar", "कैलेंडर")
                        3 -> Icons.Filled.History to tr("History", "हिस्ट्री")
                        else -> Icons.Filled.Person to tr("Profile", "प्रोफ़ाइल")
                    }
                    NavigationBarItem(
                        selected = nav.tab == i,
                        onClick = {
                            if (i == 3 && nav.tab != 3) { nav.historyFilter = null; nav.historyAllDays = false; nav.historyDay = TimeUtil.today() }
                            nav.tab = i
                        },
                        icon = { Icon(icon, null) },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (nav.tab) {
                0 -> HomeScreen(nav)
                1 -> DailyScreen(nav)
                2 -> CalendarScreen(nav)
                3 -> HistoryScreen(nav)
                else -> ProfileScreen(nav)
            }
        }
    }
}
