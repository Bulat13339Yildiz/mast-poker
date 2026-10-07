package app.mast.poker.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mast.poker.habit.PuzzleWidget
import app.mast.poker.habit.WidgetArt
import app.mast.poker.habit.WidgetState
import app.mast.poker.progress.PuzzleState
import app.mast.poker.progress.UserProgress
import app.mast.poker.content.Curriculum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WidgetArtTest {

    private val sample = WidgetState("Математика блефа", "Ждёт тебя · +25 XP", WidgetState.Kind.TODO, "Серия 5 дней · 35/60 XP")

    @Test
    fun `widget paints a full card`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val density = 2.625f
        val bmp = WidgetArt.render(context, (250 * density).toInt(), (110 * density).toInt(), density, sample)
        // Corners are rounded off; the middle is the dark felt; the right side holds the ivory card.
        assertEquals(0, Color.alpha(bmp.getPixel(0, 0)))
        assertTrue(Color.alpha(bmp.getPixel(bmp.width / 3, bmp.height / 2)) == 255)
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "widget.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (System.getenv("MAST_GENERATE") == "1") {
            File("src/main/res/drawable-nodpi/widget_preview.webp").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it)
            }
        }
    }

    @Test
    fun `widget state follows the puzzle`() {
        val all = Curriculum.lessons.map { it.id }.associateWith { app.mast.poker.progress.LessonRecord(3, 3, true, 0) }
        val fresh = UserProgress(onboarded = true)
        assertEquals(WidgetState.Kind.LOCKED, PuzzleWidget.state(fresh, 100).statusKind)
        val ready = fresh.copy(lessons = all)
        assertEquals(WidgetState.Kind.TODO, PuzzleWidget.state(ready, 100).statusKind)
        val solved = ready.copy(puzzle = PuzzleState(lastDay = 100, lastCorrect = true, streak = 2, bestStreak = 2, solved = 2))
        assertEquals(WidgetState.Kind.DONE, PuzzleWidget.state(solved, 100).statusKind)
        assertEquals(WidgetState.Kind.TODO, PuzzleWidget.state(solved, 101).statusKind)
        val missed = ready.copy(puzzle = PuzzleState(lastDay = 100, lastCorrect = false))
        assertEquals(WidgetState.Kind.MISSED, PuzzleWidget.state(missed, 100).statusKind)
    }
}
