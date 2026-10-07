package app.mast.poker.practice.sim

import app.mast.poker.content.Position
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HandSimTest {

    private fun read(hole: String, board: String) = HandReader.read(cards(hole), cards(board))

    @Test
    fun `hand reader sorts hands like a coach would`() {
        assertEquals(Strength.STRONG, read("7c 7d", "7h Ks 2c").strength)
        assertTrue(read("7c 7d", "7h Ks 2c").monster)
        assertEquals(Strength.STRONG, read("Ah Kd", "Kc 7s 2h").strength) // top pair, ace kicker
        assertEquals(Strength.MEDIUM, read("Kh 4d", "Kc 7s 2h").strength) // top pair, weak kicker
        assertEquals(Strength.MEDIUM, read("8h 7h", "Kc 7s 2d").strength) // middle pair
        assertEquals(Strength.STRONG, read("Qh Qd", "Jc 7s 2d").strength) // overpair
        assertEquals(Strength.MEDIUM, read("5h 5d", "Jc 7s 2d").strength) // underpair
        assertEquals(Strength.DRAW, read("Ah 5h", "Kh 9h 2c").strength) // flush draw
        assertEquals(9, read("Ah 5h", "Kh 9h 2c").outs)
        assertEquals(Strength.DRAW, read("9c 8d", "7h 6s 2c").strength) // open-ended
        assertEquals(Strength.WEAK, read("Ac Qd", "8h 5s 2c").strength)
        assertEquals(Strength.WEAK, read("9c 8d", "Ks Kd 4h").strength) // pair is on the board
        assertEquals(Strength.STRONG, read("Kh 7d", "Kc 7s 2h").strength) // two pair with both cards
        assertTrue(read("Kh 7d", "Kc 7s 2h").monster)
        assertEquals(Strength.MEDIUM, read("7h 4d", "Kc Ks 7c").strength) // board pair + own small pair
    }

    @Test
    fun `board texture`() {
        assertFalse(HandReader.isWet(cards("Ks 7d 2c")))
        assertTrue(HandReader.isWet(cards("Jh Th 8c")))
        assertFalse(HandReader.isWet(cards("9c 9d 4s")))
        assertFalse(HandReader.isWet(cards("Qs 8h 3d")))
        assertTrue(HandReader.isWet(cards("9h 8h 7c")))
        assertTrue(HandReader.isWet(cards("Ah 7h 2h")))
    }

    @Test
    fun `coach preflop`() {
        fun h(s: String) = cards(s).let { StartingHand.of(it[0], it[1]) }
        assertEquals(Quality.BEST, Coach.preflopOpen(h("Ah Kd"), Position.UTG, Act.RAISE).quality)
        assertEquals(Quality.MISTAKE, Coach.preflopOpen(h("Ah Kd"), Position.UTG, Act.CALL).quality)
        assertEquals(Quality.MISTAKE, Coach.preflopOpen(h("8s 7s"), Position.UTG, Act.RAISE).quality)
        assertEquals(Quality.BEST, Coach.preflopOpen(h("8s 7s"), Position.BTN, Act.RAISE).quality)
        assertTrue(Coach.preflopOpen(h("8s 7s"), Position.UTG, Act.CALL).text.contains("лимп"))
        assertEquals(Quality.BEST, Coach.preflopVs3bet(h("Qh Qd"), Act.CALL).quality)
        assertEquals(Quality.BEST, Coach.preflopVs3bet(h("9h 9d"), Act.FOLD).quality)
        assertEquals(Quality.BEST, Coach.preflopVs3bet(h("Ah Ad"), Act.RAISE).quality)
        assertEquals(Quality.OK, Coach.preflopVs3bet(h("Ah Ad"), Act.CALL).quality)
        assertEquals(Act.FOLD, Coach.preflopVs3bet(h("Ah Qd"), Act.CALL).best)
        assertEquals(Act.CALL, Coach.preflopVs3bet(h("Ah Qd"), Act.CALL, VillainStyle.MANIAC).best)
    }

    private fun spot(read: HandRead, facing: Facing, pot: Int = 20, toCall: Int = 0, aggressor: Boolean = true, wet: Boolean = false, river: Boolean = false) =
        Coach.Spot("Флоп", facing, read, pot, toCall, aggressor, wet, river, 47)

    @Test
    fun `coach postflop`() {
        val set = read("7c 7d", "7h Ks 2c")
        assertEquals(Act.BET, Coach.postflop(spot(set, Facing.CHECKED_TO), Act.CHECK).best)
        assertEquals(Act.RAISE, Coach.postflop(spot(set, Facing.BET, pot = 30, toCall = 10), Act.CALL).best)
        assertEquals(Quality.OK, Coach.postflop(spot(set, Facing.BET, pot = 30, toCall = 10), Act.CALL).quality)

        val air = read("Ac Qd", "8h 5s 2c")
        assertEquals(Act.BET, Coach.postflop(spot(air, Facing.CHECKED_TO), Act.BET).best) // c-bet on a dry flop
        assertEquals(Act.CHECK, Coach.postflop(spot(air, Facing.CHECKED_TO, aggressor = false), Act.BET).best)
        assertEquals(Act.FOLD, Coach.postflop(spot(air, Facing.BET, pot = 30, toCall = 10), Act.CALL).best)

        // Flush draw: pot 30 with the bet, call 10 → need 25%, chance 9/47 ≈ 19% → fold.
        val fd = read("Ah 5h", "Kh 9h 2c")
        val tooPricey = Coach.postflop(spot(fd, Facing.BET, pot = 30, toCall = 10), Act.CALL)
        assertEquals(Act.FOLD, tooPricey.best)
        assertTrue(tooPricey.text.contains("25%"))
        // Small bet: pot 44, call 4 → need ~8% → call.
        assertEquals(Act.CALL, Coach.postflop(spot(fd, Facing.BET, pot = 44, toCall = 4), Act.CALL).best)
    }

    @Test
    fun `thousands of random hands keep the chips straight`() {
        val rnd = Random(77)
        var showdowns = 0
        repeat(4000) {
            val sim = HandSimulator(Random(rnd.nextLong()))
            var steps = 0
            while (sim.result == null) {
                assertTrue("stuck", sim.awaitingHero)
                val options = sim.options()
                assertTrue(options.isNotEmpty())
                val v = sim.act(options.random(rnd).act)
                assertTrue(v.text.isNotBlank())
                steps++
                assertTrue("too many decisions", steps <= 14)
                assertEquals(sim.pot, sim.heroInvested + sim.villainInvested + sim.deadMoney)
            }
            val r = assertNotNull(sim.result).let { sim.result!! }
            assertTrue(r.text.isNotBlank())
            assertTrue(r.heroNet >= -sim.heroInvested && r.heroNet <= sim.pot)
            if (r.showdown) showdowns++
        }
        assertTrue("some hands should reach showdown", showdowns > 100)
    }
}
