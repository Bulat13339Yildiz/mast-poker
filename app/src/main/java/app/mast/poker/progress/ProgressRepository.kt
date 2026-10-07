package app.mast.poker.progress

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import app.mast.poker.content.Concept
import app.mast.poker.content.DrillType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneId

object ProgressSerializer : Serializer<UserProgress> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    override val defaultValue: UserProgress = UserProgress()

    override suspend fun readFrom(input: InputStream): UserProgress = try {
        json.decodeFromString(UserProgress.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot read progress", e)
    }

    override suspend fun writeTo(t: UserProgress, output: OutputStream) {
        output.write(json.encodeToString(UserProgress.serializer(), t).encodeToByteArray())
    }
}

fun interface Clock {
    fun now(): Moment

    companion object {
        val System = Clock {
            val zone = ZoneId.systemDefault()
            val dt = LocalDateTime.now(zone)
            Moment(dt.toLocalDate().toEpochDay(), dt.hour, java.lang.System.currentTimeMillis())
        }
    }
}

class ProgressRepository(
    private val store: DataStore<UserProgress>,
    private val clock: Clock = Clock.System,
) {
    val progress: Flow<UserProgress> = store.data

    fun today(): Long = clock.now().day

    suspend fun completeOnboarding(dailyGoalXp: Int) {
        store.updateData { ProgressReducer.completeOnboarding(it, dailyGoalXp) }
    }

    suspend fun answer(
        concept: Concept,
        correct: Boolean,
        extras: AnswerExtras = AnswerExtras(),
        difficulty: Int = Rating.difficulty(1),
    ): Reward = transform { p, now -> ProgressReducer.answer(p, concept, correct, extras, now, difficulty) }

    suspend fun finishExam(chapterId: String, correct: Int, total: Int): Reward =
        transform { p, now -> ProgressReducer.finishExam(p, chapterId, correct, total, now) }

    suspend fun solvePuzzle(correct: Boolean): Reward =
        transform { p, now -> ProgressReducer.solvePuzzle(p, correct, now) }

    suspend fun completeLesson(lessonId: String, correct: Int, total: Int): Reward =
        transform { p, now -> ProgressReducer.completeLesson(p, lessonId, correct, total, now) }

    suspend fun finishDrill(type: DrillType, mode: DrillMode, correct: Int): Reward =
        transform { p, now -> ProgressReducer.finishDrill(p, type, mode, correct, now) }

    suspend fun finishReview(results: Map<Concept, Boolean>, correct: Int): Reward =
        transform { p, now -> ProgressReducer.finishReview(p, results, correct, now) }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        store.updateData { ProgressReducer.updateSettings(it, transform(it.settings)) }
    }

    suspend fun reset() {
        store.updateData { UserProgress(onboarded = true, dailyGoalXp = it.dailyGoalXp, settings = it.settings) }
    }

    private suspend fun transform(block: (UserProgress, Moment) -> Pair<UserProgress, Reward>): Reward {
        var reward: Reward? = null
        store.updateData { current ->
            val (next, r) = block(current, clock.now())
            reward = r
            next
        }
        return reward!!
    }

    companion object {
        fun create(context: Context): ProgressRepository = ProgressRepository(
            DataStoreFactory.create(
                serializer = ProgressSerializer,
                produceFile = { File(context.filesDir, "datastore/progress.json") },
            ),
        )
    }
}
