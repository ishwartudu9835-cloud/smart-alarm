package com.ishwar.smartalarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ek Daily Schedule Set (jaise Morning Shift). startDay/endDay = epochDay, optional. */
@Entity(tableName = "schedule_sets")
data class ScheduleSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val startDay: Long? = null,
    val endDay: Long? = null,
    val active: Boolean = false,
    val activatedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Schedule set ke andar ek activity. startMin/endMin = din ke minute (0..1439). days = bitmask Som=bit0..Ravi=bit6 */
@Entity(tableName = "routine_tasks", indices = [Index("setId")])
data class RoutineTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: Long,
    val name: String,
    val description: String = "",
    val startMin: Int,
    val endMin: Int? = null,
    val days: Int = ALL_DAYS,
    val alarmEnabled: Boolean = true,
    val soundUri: String? = null,
    val voiceId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Calendar ki kisi tareekh ka reminder. day = epochDay. */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val day: Long,
    val timeMin: Int,
    val repeat: Int = REPEAT_NONE,
    val alarmEnabled: Boolean = true,
    val soundUri: String? = null,
    val voiceId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** App ke andar record ki gayi awaaz. fileName = filesDir/voices/ ke andar. */
@Entity(tableName = "voices")
data class VoiceRecording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fileName: String,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Kisi task/reminder ka ek din ka record — status aur history yahin se. */
@Entity(
    tableName = "occurrences",
    indices = [Index(value = ["kind", "sourceId", "day"], unique = true), Index("day")]
)
data class Occurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int,
    val sourceId: Long,
    val setId: Long? = null,
    val title: String,
    val description: String = "",
    val day: Long,
    val startMin: Int,
    val endMin: Int? = null,
    val status: Int = ST_AUTO,
    val completedAt: Long? = null,
    val snoozeCount: Int = 0
)

const val ALL_DAYS = 0b1111111
const val WEEKDAYS = 0b0011111

const val KIND_ROUTINE = 0
const val KIND_REMINDER = 1

/** Stored status */
const val ST_AUTO = 0
const val ST_COMPLETED = 1
const val ST_MISSED = 2
const val ST_KEPT_PENDING = 3

const val REPEAT_NONE = 0
const val REPEAT_DAILY = 1
const val REPEAT_WEEKLY = 2
const val REPEAT_MONTHLY = 3
const val REPEAT_YEARLY = 4

/** Dikhane wala status */
enum class TaskStatus { UPCOMING, PENDING, COMPLETED, MISSED }
