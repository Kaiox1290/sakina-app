package com.sakina.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class TaskE(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val notes: String = "", val category: String = "personal", val priority: Int = 1,
    val day: Long, val startMin: Int = -1, val endMin: Int = -1,
    val repeat: Int = 0, // 0 none, 1 daily, 2 weekly
    val remind: Boolean = false, val remindBefore: Int = 0,
    val auto: Boolean = false // created by the schedule assistant
) {
    fun occursOn(d: Long): Boolean = when (repeat) {
        1 -> d >= day
        2 -> d >= day && (d - day) % 7 == 0L
        else -> d == day
    }
}
@Entity(primaryKeys = ["taskId", "day"]) data class TaskDone(val taskId: Long, val day: Long)
@Entity(primaryKeys = ["key", "day"]) data class AdhkarProg(val key: String, val day: Long, val count: Int, val done: Boolean)
@Entity data class Fav(@PrimaryKey val key: String, val label: String = "")
@Entity(primaryKeys = ["day", "kind"]) data class TasbihLog(val day: Long, val kind: String, val count: Int)
@Entity data class CustomDhikr(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val target: Int = 33)
@Entity(primaryKeys = ["surah", "ayah"]) data class Bookmark(val surah: Int, val ayah: Int, val ts: Long)
@Entity(primaryKeys = ["day", "page"]) data class PageRead(val day: Long, val page: Int)
@Entity data class Habit(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val remindMin: Int = -1, val active: Boolean = true, val createdDay: Long = 0)
@Entity(primaryKeys = ["habitId", "day"]) data class HabitLog(val habitId: Long, val day: Long)
@Entity data class Activity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val day: Long, val name: String, val minutes: Int)
@Entity data class Subject(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val notes: String = "")
@Entity data class ClassSlot(@PrimaryKey(autoGenerate = true) val id: Long = 0, val subjectId: Long, val weekday: Int, val startMin: Int, val endMin: Int)
@Entity data class Assignment(@PrimaryKey(autoGenerate = true) val id: Long = 0, val subjectId: Long, val title: String, val dueDay: Long, val done: Boolean = false)
@Entity data class Exam(@PrimaryKey(autoGenerate = true) val id: Long = 0, val subjectId: Long, val title: String, val day: Long, val timeMin: Int = -1)
@Entity data class StudySession(@PrimaryKey(autoGenerate = true) val id: Long = 0, val day: Long, val minutes: Int, val kind: String, val tag: String = "")
@Entity data class CodeGoal(@PrimaryKey(autoGenerate = true) val id: Long = 0, val lang: String, val level: Int = 0, val lessonsDone: Int = 0, val lessonsTotal: Int = 20, val weeklyHours: Int = 5, val notes: String = "")
@Entity data class CodeProject(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val lang: String, val status: Int = 0, val notes: String = "")
@Entity data class Achievement(@PrimaryKey(autoGenerate = true) val id: Long = 0, val day: Long, val text: String, val area: String = "")

@Dao interface AppDao {
    @Query("SELECT COUNT(*) FROM AdhkarProg WHERE done = 1") suspend fun cntAdhkar(): Int
    @Query("SELECT COUNT(*) FROM TaskDone") suspend fun cntTasks(): Int
    @Query("SELECT COUNT(*) FROM HabitLog") suspend fun cntHabits(): Int
    @Query("SELECT COALESCE(SUM(minutes), 0) FROM StudySession") suspend fun sumStudy(): Int
    @Query("SELECT COUNT(*) FROM PageRead") suspend fun cntPages(): Int
    @Query("SELECT COALESCE(SUM(count), 0) FROM TasbihLog") suspend fun sumTasbih(): Int
    // tasks
    @Query("SELECT * FROM TaskE ORDER BY day, startMin") fun tasks(): Flow<List<TaskE>>
    @Query("SELECT * FROM TaskE") suspend fun tasksNow(): List<TaskE>
    @Query("SELECT * FROM TaskE WHERE id=:id") suspend fun task(id: Long): TaskE?
    @Insert suspend fun insertTask(t: TaskE): Long
    @Update suspend fun updateTask(t: TaskE)
    @Query("DELETE FROM TaskE WHERE id=:id") suspend fun deleteTask(id: Long)
    @Query("DELETE FROM TaskDone WHERE taskId=:id") suspend fun deleteTaskDone(id: Long)
    @Query("DELETE FROM TaskE WHERE auto=1") suspend fun deleteAutoTasks()
    @Query("SELECT * FROM TaskDone WHERE day BETWEEN :a AND :b") fun taskDone(a: Long, b: Long): Flow<List<TaskDone>>
    @Query("SELECT * FROM TaskDone WHERE day=:d") suspend fun taskDoneOn(d: Long): List<TaskDone>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setTaskDone(x: TaskDone)
    @Delete suspend fun unsetTaskDone(x: TaskDone)
    // adhkar
    @Query("SELECT * FROM AdhkarProg WHERE day=:d") fun adhkarProg(d: Long): Flow<List<AdhkarProg>>
    @Query("SELECT * FROM AdhkarProg WHERE day BETWEEN :a AND :b") fun adhkarRange(a: Long, b: Long): Flow<List<AdhkarProg>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAdhkar(x: AdhkarProg)
    @Query("DELETE FROM AdhkarProg WHERE day=:d AND key LIKE :prefix") suspend fun clearAdhkar(d: Long, prefix: String)
    @Query("SELECT * FROM Fav") fun favs(): Flow<List<Fav>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFav(f: Fav)
    @Query("DELETE FROM Fav WHERE key=:k") suspend fun delFav(k: String)
    // tasbih
    @Query("SELECT * FROM TasbihLog WHERE day BETWEEN :a AND :b") fun tasbihRange(a: Long, b: Long): Flow<List<TasbihLog>>
    @Query("SELECT * FROM TasbihLog ORDER BY day DESC LIMIT 400") fun tasbihAll(): Flow<List<TasbihLog>>
    @Query("SELECT count FROM TasbihLog WHERE day=:d AND kind=:k") suspend fun tasbihCount(d: Long, k: String): Int?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasbih(x: TasbihLog)
    @Query("SELECT * FROM CustomDhikr") fun customDhikr(): Flow<List<CustomDhikr>>
    @Insert suspend fun addCustom(x: CustomDhikr)
    @Query("DELETE FROM CustomDhikr WHERE id=:id") suspend fun delCustom(id: Long)
    // quran
    @Query("SELECT * FROM Bookmark ORDER BY ts DESC") fun bookmarks(): Flow<List<Bookmark>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addBookmark(b: Bookmark)
    @Delete suspend fun delBookmark(b: Bookmark)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addPageRead(p: PageRead)
    @Query("SELECT * FROM PageRead WHERE day BETWEEN :a AND :b") fun pagesRead(a: Long, b: Long): Flow<List<PageRead>>
    // habits
    @Query("SELECT * FROM Habit") fun habits(): Flow<List<Habit>>
    @Query("SELECT * FROM Habit") suspend fun habitsNow(): List<Habit>
    @Insert suspend fun addHabit(h: Habit): Long
    @Update suspend fun updateHabit(h: Habit)
    @Query("DELETE FROM Habit WHERE id=:id") suspend fun delHabit(id: Long)
    @Query("DELETE FROM HabitLog WHERE habitId=:id") suspend fun delHabitLogs(id: Long)
    @Query("SELECT * FROM HabitLog WHERE day BETWEEN :a AND :b") fun habitLogs(a: Long, b: Long): Flow<List<HabitLog>>
    @Query("SELECT * FROM HabitLog WHERE day=:d") suspend fun habitLogsOn(d: Long): List<HabitLog>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabitLog(x: HabitLog)
    @Delete suspend fun delHabitLog(x: HabitLog)
    @Query("SELECT * FROM Activity ORDER BY day DESC, id DESC LIMIT 200") fun activities(): Flow<List<Activity>>
    @Insert suspend fun addActivity(a: Activity)
    @Query("DELETE FROM Activity WHERE id=:id") suspend fun delActivity(id: Long)
    // study
    @Query("SELECT * FROM Subject") fun subjects(): Flow<List<Subject>>
    @Insert suspend fun addSubject(s: Subject): Long
    @Update suspend fun updateSubject(s: Subject)
    @Query("DELETE FROM Subject WHERE id=:id") suspend fun delSubject(id: Long)
    @Query("DELETE FROM ClassSlot WHERE subjectId=:id") suspend fun delSlotsOf(id: Long)
    @Query("DELETE FROM Assignment WHERE subjectId=:id") suspend fun delAssignOf(id: Long)
    @Query("DELETE FROM Exam WHERE subjectId=:id") suspend fun delExamsOf(id: Long)
    @Query("SELECT * FROM ClassSlot ORDER BY weekday, startMin") fun slots(): Flow<List<ClassSlot>>
    @Insert suspend fun addSlot(s: ClassSlot)
    @Query("DELETE FROM ClassSlot WHERE id=:id") suspend fun delSlot(id: Long)
    @Query("SELECT * FROM Assignment ORDER BY done, dueDay") fun assignments(): Flow<List<Assignment>>
    @Insert suspend fun addAssignment(a: Assignment)
    @Update suspend fun updateAssignment(a: Assignment)
    @Delete suspend fun delAssignment(a: Assignment)
    @Query("SELECT * FROM Exam ORDER BY day") fun exams(): Flow<List<Exam>>
    @Insert suspend fun addExam(e: Exam)
    @Delete suspend fun delExam(e: Exam)
    @Query("SELECT * FROM StudySession WHERE day BETWEEN :a AND :b") fun sessions(a: Long, b: Long): Flow<List<StudySession>>
    @Insert suspend fun addSession(s: StudySession)
    // coding
    @Query("SELECT * FROM CodeGoal") fun codeGoals(): Flow<List<CodeGoal>>
    @Insert suspend fun addCodeGoal(g: CodeGoal)
    @Update suspend fun updateCodeGoal(g: CodeGoal)
    @Delete suspend fun delCodeGoal(g: CodeGoal)
    @Query("SELECT * FROM CodeProject") fun codeProjects(): Flow<List<CodeProject>>
    @Insert suspend fun addCodeProject(p: CodeProject)
    @Update suspend fun updateCodeProject(p: CodeProject)
    @Delete suspend fun delCodeProject(p: CodeProject)
    @Query("SELECT * FROM Achievement ORDER BY day DESC, id DESC LIMIT 100") fun achievements(): Flow<List<Achievement>>
    @Insert suspend fun addAchievement(a: Achievement)
    @Delete suspend fun delAchievement(a: Achievement)
}

@Database(
    entities = [TaskE::class, TaskDone::class, AdhkarProg::class, Fav::class, TasbihLog::class, CustomDhikr::class, Bookmark::class, PageRead::class,
        Habit::class, HabitLog::class, Activity::class, Subject::class, ClassSlot::class, Assignment::class, Exam::class, StudySession::class,
        CodeGoal::class, CodeProject::class, Achievement::class],
    version = 1, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "sakina.db").build().also { inst = it }
        }
        fun reset() { synchronized(this) { inst?.close(); inst = null } }
    }
}

fun TaskDone.key() = "$taskId|$day"
