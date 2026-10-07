package app.mast.poker.progress

import app.mast.poker.habit.Reminders
import app.mast.poker.ui.theme.SoundSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HabitTest {

    @Test
    fun `reminder speaks only on days without practice`() {
        val active = UserProgress(onboarded = true, dayStreak = 5, lastActiveDay = 100)
        assertNull(Reminders.message(active, 100))
        val (title, _) = Reminders.message(active, 101)!!
        assertEquals("Серия 5 дней под угрозой", title)
        assertEquals("Серия 1 день под угрозой", Reminders.message(active.copy(dayStreak = 1), 101)!!.first)
        assertEquals("Серия 3 дня под угрозой", Reminders.message(active.copy(dayStreak = 3), 101)!!.first)
        // The streak is already lost two days later — invite to start a new one.
        assertEquals("Пара минут покера?", Reminders.message(active, 102)!!.first)
        assertNull(Reminders.message(UserProgress(), 100))
    }

    @Test
    fun `synthesised sounds are short valid wav files`() {
        listOf(SoundSynth.card(), SoundSynth.chip()).forEach { samples ->
            assertTrue(samples.size in 2_000..5_000)
            assertTrue(samples.any { it > 10_000 })
            val wav = SoundSynth.wav(samples)
            val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(wav, 0, 4))
            assertEquals(wav.size - 8, b.getInt(4))
            assertEquals("WAVE", String(wav, 8, 4))
            assertEquals(SoundSynth.RATE, b.getInt(24))
            assertEquals(samples.size * 2, b.getInt(40))
        }
    }
}
