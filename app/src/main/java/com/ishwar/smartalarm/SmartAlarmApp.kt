package com.ishwar.smartalarm

import android.app.Application
import com.ishwar.smartalarm.alarm.Notifier
import com.ishwar.smartalarm.data.Repo

class SmartAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.createChannels(this)
    }
}
