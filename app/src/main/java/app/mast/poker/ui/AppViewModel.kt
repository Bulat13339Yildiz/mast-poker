package app.mast.poker.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mast.poker.MastApplication
import app.mast.poker.achievements.Achievement
import app.mast.poker.content.Concept
import app.mast.poker.content.DrillType
import app.mast.poker.content.Question
import app.mast.poker.practice.Grader
import app.mast.poker.progress.DrillMode
import app.mast.poker.progress.ProgressRepository
import app.mast.poker.progress.Reward
import app.mast.poker.progress.Settings
import app.mast.poker.progress.UserProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Celebration {
    data class NewAchievement(val achievement: Achievement) : Celebration
    data class LevelUp(val level: Int) : Celebration
}

/** Session-wide state: progress, and the queue of celebrations shown over any screen. */
class AppViewModel(private val repo: ProgressRepository) : ViewModel() {

    val progress: StateFlow<UserProgress?> = repo.progress.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val celebrations = mutableStateListOf<Celebration>()

    /** Achievements earned mid-session (e.g. answer streaks) — shown when the session ends. */
    private val deferred = mutableListOf<Achievement>()

    fun today(): Long = repo.today()

    fun completeOnboarding(goalXp: Int) {
        viewModelScope.launch { repo.completeOnboarding(goalXp) }
    }

    suspend fun answer(q: Question, correct: Boolean) {
        val r = repo.answer(q.concept, correct, Grader.extras(q, correct))
        deferred += r.achievements
    }

    suspend fun completeLesson(lessonId: String, correct: Int, total: Int): Reward =
        celebrate(repo.completeLesson(lessonId, correct, total))

    suspend fun finishDrill(type: DrillType, mode: DrillMode, correct: Int): Reward =
        celebrate(repo.finishDrill(type, mode, correct))

    suspend fun finishReview(results: Map<Concept, Boolean>, correct: Int): Reward =
        celebrate(repo.finishReview(results, correct))

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    fun setDailyGoal(xp: Int) {
        viewModelScope.launch { repo.completeOnboarding(xp) }
    }

    fun reset() {
        viewModelScope.launch { repo.reset() }
    }

    fun dismissCelebration() {
        if (celebrations.isNotEmpty()) celebrations.removeAt(0)
    }

    private fun celebrate(r: Reward): Reward {
        val all = (deferred + r.achievements).distinctBy { it.id }
        deferred.clear()
        if (r.leveledUp) celebrations += Celebration.LevelUp(r.levelAfter)
        celebrations += all.map { Celebration.NewAchievement(it) }
        return r.copy(achievements = all)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MastApplication
                AppViewModel(app.progress)
            }
        }
    }
}
