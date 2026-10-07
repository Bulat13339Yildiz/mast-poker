package app.mast.poker.habit

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import app.mast.poker.MainActivity
import app.mast.poker.MastApplication
import app.mast.poker.R
import app.mast.poker.practice.Challenges
import app.mast.poker.progress.ProgressReducer
import app.mast.poker.progress.UserProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Home-screen widget: the day's puzzle, its status and the streak. */
class PuzzleWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraws every placed widget, e.g. after the puzzle is solved. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, PuzzleWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { runCatching { render(app, manager, ids) } }
        }

        fun state(p: UserProgress, today: Long): WidgetState {
            val type = Challenges.puzzleType(today, p.lessons.keys)
            val streak = ProgressReducer.visibleStreak(p, today)
            val footer = "Серия $streak ${days(streak)} · ${p.xpOn(today)}/${p.dailyGoalXp} XP"
            if (type == null) {
                return WidgetState("Пройди первые уроки", "Задача дня откроется после урока «Ауты» или «Что за комбинация»", WidgetState.Kind.LOCKED, footer)
            }
            val done = p.puzzle.lastDay == today
            return when {
                done && p.puzzle.lastCorrect -> WidgetState(type.title, "Решена ✓ · серия задач ${ProgressReducer.visiblePuzzleStreak(p, today)}", WidgetState.Kind.DONE, footer)
                done -> WidgetState(type.title, "Попытка использована — завтра новая", WidgetState.Kind.MISSED, footer)
                else -> WidgetState(type.title, "Ждёт тебя · +${ProgressReducer.Xp.PUZZLE_SOLVED} XP", WidgetState.Kind.TODO, footer)
            }
        }

        private fun days(n: Int): String {
            val m10 = n % 10
            val m100 = n % 100
            return when {
                m10 == 1 && m100 != 11 -> "день"
                m10 in 2..4 && m100 !in 12..14 -> "дня"
                else -> "дней"
            }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = (context.applicationContext as MastApplication).progress
            val p = repo.progress.first()
            val state = state(p, repo.today())
            val density = context.resources.displayMetrics.density
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_PUZZLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 250
                val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 110
                val scale = minOf(density, 1100f / wDp, 700f / hDp)
                val bmp = WidgetArt.render(context, (wDp * scale).roundToInt(), (hDp * scale).roundToInt(), scale, state)
                val views = RemoteViews(context.packageName, R.layout.widget_puzzle).apply {
                    setImageViewBitmap(R.id.widget_image, bmp)
                    setContentDescription(R.id.widget_image, "Задача дня: ${state.title}. ${state.status}")
                    setOnClickPendingIntent(R.id.widget_image, open)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
