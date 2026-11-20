package com.ishwar.smartalarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SetCount(val setId: Long, val c: Int)

@Dao
interface AppDao {
    // ---------- Schedule sets ----------
    @Query("SELECT * FROM schedule_sets ORDER BY createdAt")
    fun setsFlow(): Flow<List<ScheduleSet>>

    @Query("SELECT * FROM schedule_sets ORDER BY createdAt")
    suspend fun sets(): List<ScheduleSet>

    @Query("SELECT * FROM schedule_sets WHERE id = :id")
    suspend fun set(id: Long): ScheduleSet?

    @Query("SELECT * FROM schedule_sets WHERE id = :id")
    fun setFlow(id: Long): Flow<ScheduleSet?>

    @Query("SELECT * FROM schedule_sets WHERE active = 1 LIMIT 1")
    suspend fun activeSet(): ScheduleSet?

    @Query("SELECT * FROM schedule_sets WHERE active = 1 LIMIT 1")
    fun activeSetFlow(): Flow<ScheduleSet?>

    @Insert
    suspend fun insertSet(s: ScheduleSet): Long

    @Update
    suspend fun updateSet(s: ScheduleSet)

    @Query("DELETE FROM schedule_sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE schedule_sets SET active = 0")
    suspend fun deactivateAllSets()

    @Query("SELECT setId, COUNT(*) AS c FROM routine_tasks GROUP BY setId")
    fun setCountsFlow(): Flow<List<SetCount>>

    // ---------- Routine tasks ----------
    @Query("SELECT * FROM routine_tasks WHERE setId = :setId ORDER BY startMin")
    fun tasksFlow(setId: Long): Flow<List<RoutineTask>>

    @Query("SELECT * FROM routine_tasks WHERE setId = :setId ORDER BY startMin")
    suspend fun tasks(setId: Long): List<RoutineTask>

    @Query("SELECT * FROM routine_tasks")
    suspend fun allTasks(): List<RoutineTask>

    @Query("SELECT * FROM routine_tasks WHERE id = :id")
    suspend fun task(id: Long): RoutineTask?

    @Insert
    suspend fun insertTask(t: RoutineTask): Long

    @Update
    suspend fun updateTask(t: RoutineTask)

    @Query("DELETE FROM routine_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM routine_tasks WHERE setId = :setId")
    suspend fun deleteTasksOfSet(setId: Long)

    // ---------- Reminders ----------
    @Query("SELECT * FROM reminders ORDER BY day, timeMin")
    fun remindersFlow(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY day, timeMin")
    suspend fun reminders(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun reminder(id: Long): Reminder?

    @Insert
    suspend fun insertReminder(r: Reminder): Long

    @Update
    suspend fun updateReminder(r: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    // ---------- Voices ----------
    @Query("SELECT * FROM voices ORDER BY createdAt DESC")
    fun voicesFlow(): Flow<List<VoiceRecording>>

    @Query("SELECT * FROM voices ORDER BY createdAt DESC")
    suspend fun voices(): List<VoiceRecording>

    @Query("SELECT * FROM voices WHERE id = :id")
    suspend fun voice(id: Long): VoiceRecording?

    @Insert
    suspend fun insertVoice(v: VoiceRecording): Long

    @Update
    suspend fun updateVoice(v: VoiceRecording)

    @Query("DELETE FROM voices WHERE id = :id")
    suspend fun deleteVoice(id: Long)

    @Query("UPDATE routine_tasks SET voiceId = NULL WHERE voiceId = :id")
    suspend fun clearVoiceFromTasks(id: Long)

    @Query("UPDATE reminders SET voiceId = NULL WHERE voiceId = :id")
    suspend fun clearVoiceFromReminders(id: Long)

    // ---------- Occurrences / history ----------
    @Query("SELECT * FROM occurrences WHERE day = :day ORDER BY startMin")
    fun occFlow(day: Long): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE day = :day ORDER BY startMin")
    suspend fun occurrences(day: Long): List<Occurrence>

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun occ(id: Long): Occurrence?

    @Query("SELECT * FROM occurrences WHERE kind = :kind AND sourceId = :sourceId AND day = :day LIMIT 1")
    suspend fun findOcc(kind: Int, sourceId: Long, day: Long): Occurrence?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOcc(o: Occurrence): Long

    @Update
    suspend fun updateOcc(o: Occurrence)

    @Query("DELETE FROM occurrences WHERE id = :id")
    suspend fun deleteOcc(id: Long)

    @Query("DELETE FROM occurrences WHERE kind = :kind AND sourceId = :sourceId AND day >= :fromDay AND status = 0")
    suspend fun deleteFutureAutoOcc(kind: Int, sourceId: Long, fromDay: Long)

    @Query("SELECT * FROM occurrences WHERE status = :status ORDER BY day DESC, startMin DESC")
    fun occByStatusFlow(status: Int): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE kind = 1 AND status = :status ORDER BY day DESC, startMin DESC")
    fun reminderOccByStatusFlow(status: Int): Flow<List<Occurrence>>

    @Query("SELECT * FROM occurrences WHERE status = 0 AND day <= :today")
    suspend fun openAutoOcc(today: Long): List<Occurrence>

    // ---------- Backup ----------
    @Query("SELECT * FROM occurrences")
    suspend fun allOcc(): List<Occurrence>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSets(l: List<ScheduleSet>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTasks(l: List<RoutineTask>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putReminders(l: List<Reminder>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putVoices(l: List<VoiceRecording>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOcc(l: List<Occurrence>)

    @Query("DELETE FROM schedule_sets") suspend fun clearSets()
    @Query("DELETE FROM routine_tasks") suspend fun clearTasks()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM voices") suspend fun clearVoices()
    @Query("DELETE FROM occurrences") suspend fun clearOcc()
}
