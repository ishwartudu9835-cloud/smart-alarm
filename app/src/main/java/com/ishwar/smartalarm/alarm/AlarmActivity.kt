package com.ishwar.smartalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ishwar.smartalarm.R
import com.ishwar.smartalarm.data.Prefs
import com.ishwar.smartalarm.data.tr
import com.ishwar.smartalarm.ui.Blue
import com.ishwar.smartalarm.ui.Cyan
import com.ishwar.smartalarm.ui.Navy
import com.ishwar.smartalarm.ui.NavyDeep
import com.ishwar.smartalarm.ui.Orange
import com.ishwar.smartalarm.ui.SmartTheme
import com.ishwar.smartalarm.util.TimeUtil

/** Full screen alarm — lock screen par bhi. */
class AlarmActivity : ComponentActivity() {

    private var occId by mutableStateOf(-1L)
    private var title by mutableStateOf("")
    private var desc by mutableStateOf("")
    private var start by mutableStateOf(0)

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ContextCompat.registerReceiver(
            this, closeReceiver, IntentFilter(AlarmService.ACTION_CLOSE_UI), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        read(intent)
        setContent {
            SmartTheme {
                AlarmScreen(
                    title = title, desc = desc, start = start,
                    onDone = { send(AlarmService.ACTION_DONE, 0) },
                    onSnooze = { send(AlarmService.ACTION_SNOOZE, it) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(i: Intent) {
        occId = i.getLongExtra(AlarmService.EXTRA_OCC, -1L)
        title = i.getStringExtra(AlarmService.EXTRA_TITLE) ?: ""
        desc = i.getStringExtra(AlarmService.EXTRA_DESC) ?: ""
        start = i.getIntExtra(AlarmService.EXTRA_START, 0)
    }

    private fun send(action: String, min: Int) {
        startService(
            Intent(this, AlarmService::class.java).setAction(action)
                .putExtra(AlarmService.EXTRA_OCC, occId).putExtra(AlarmService.EXTRA_MIN, min)
        )
        finish()
    }

    override fun onDestroy() {
        try { unregisterReceiver(closeReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(title: String, desc: String, start: Int, onDone: () -> Unit, onSnooze: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy, NavyDeep)))
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painterResource(R.drawable.app_logo), null,
                Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                tr("SCHEDULE REMINDER", "SCHEDULE REMINDER"),
                color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp
            )
            Text(TimeUtil.formatMin(start), color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("$title Time!", "$title का समय!"),
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                tr("It's time for $title. Let's go!", "आपका $title करने का समय हो गया है। चलिए जल्दी कीजिए!"),
                color = Color(0xFFCFE0FF), fontSize = 17.sp, textAlign = TextAlign.Center
            )
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(16.dp)
                ) {
                    Text(tr("Description", "विवरण"), color = Cyan, fontSize = 13.sp)
                    Text(desc, color = Color.White, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(tr("DONE / COMPLETE", "DONE / पूरा हुआ"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(5, 10).forEach { m ->
                    OutlinedButton(
                        onClick = { onSnooze(m) },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange)
                    ) {
                        Icon(Icons.Filled.Snooze, null)
                        Spacer(Modifier.width(6.dp))
                        Text("SNOOZE $m MIN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
