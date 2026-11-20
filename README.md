# Smart Alarm — Personal Schedule & Smart Alarm (Android)

Offline Android app: Daily routine sets, date-wise reminders, full-screen alarm with Done / Snooze 5 / Snooze 10, missed-task tracking, custom sounds and your own recorded voice.

## Features (Version 1)
**Home** — photo + naam, aaj ki date aur live time, Completed / Pending / Missed count, Next Alarm, Quick Actions, aaj ke saare Tasks.

**Daily Schedule** — unlimited Schedule Sets (Morning Shift, Night Shift…), create / rename / edit / duplicate / delete, ek time par sirf ek Set Active. Har Set mein unlimited Tasks: naam, start + end time, description, din, alarm on/off, alarm sound, custom voice. Set ki optional start/end date.

**Calendar** — mahina aage/peeche, kisi bhi date par unlimited reminders, repeat (ek baar / roz / hafta / mahina / saal), edit / delete, Completed aur Missed reminders. Daily Set badalne se calendar reminders par asar nahi.

**Smart Alarm** — lock screen par full-screen alarm, DONE / SNOOZE 5 / SNOOZE 10, ringtone / phone ki tone / upload ki hui MP3 / apni recorded awaaz, recording na ho to app task ka naam bolkar batata hai, volume + vibration, jawab na mile to 5 min baad dobara (3 baar tak).

**Missed system** — end time (ya 1 ghanta) tak Done na ho to Missed + "Missed Task Alert" notification. Snooze karne se Missed nahi hota. Baad mein Complete / Pending rakho / Delete.

**History** — Upcoming / Pending / Completed / Missed, kisi bhi din ki list, sabhi dinon ke Missed aur Completed.

**Profile & Settings** — naam, photo upload/change/remove, default sound, volume, vibration, हिंदी / English, Light / Dark / System theme, permissions ka status, battery guide, Backup / Restore (.zip), voice recordings.

**Technical** — Room database, AlarmManager exact alarms (setAlarmClock), foreground service se sound, phone restart / app update / time change ke baad alarms wapas, app kholte hi alarms restore (force-stop ke baad bhi), poora offline.

## APK
GitHub Actions har push par APK banata hai: **Actions → latest run → Artifacts → SmartAlarm-APK**.

## Pehli baar install ke baad
- Notifications, "Alarms & reminders" aur "Full-screen alarm" allow karein (Home par yellow card dikhega agar kuch baaki ho).
- Xiaomi / Redmi / Realme / Oppo / Vivo: Settings → Apps → Smart Alarm → Battery → **No restrictions**, aur **Autostart** on.
- Hindi awaaz ke liye: phone Settings → Text-to-speech → Google → Hindi voice install.
