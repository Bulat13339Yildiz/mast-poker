package app.mast.poker.practice.sim

import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TableSimTest {

    private fun table(random: Random): List<VillainStyle?> = listOf(null) + List(5) { VillainStyle.entries.random(random) }

    @Test
    fun `thousands of table hands keep every chip`() {
        val rnd = Random(91)
        var multiway = 0
        var showdowns = 0
        var heroAllIn = 0
        repeat(3000) { n ->
            val styles = table(rnd)
            val stack = listOf(30, 80, 200, 400).random(rnd)
            val hand = TableHand(Random(rnd.nextLong()), stack, n % 6, styles)
            var steps = 0
            var sawMultiway = false
            while (hand.result == null) {
                assertTrue("hero must be asked", hand.awaitingHero)
                val options = hand.options()
                assertTrue(options.isNotEmpty())
                val v = hand.act(options.random(rnd).act)
                assertTrue(v.text.isNotBlank())
                assertTrue(v.facing != null)
                if (hand.street != SimStreet.PREFLOP && hand.players.count { it.inHand } > 2) sawMultiway = true
                steps++
                assertTrue("too many decisions", steps <= 30)
                // Every chip in the pot came from somebody's stack.
                assertEquals(hand.pot, hand.players.sumOf { it.total })
                assertTrue(hand.players.all { it.stack >= 0 })
            }
            val r = hand.result!!
            assertTrue(r.heroNet >= -stack && r.heroNet <= hand.pot)
            assertEquals(stack + r.heroNet, hand.heroStackAfter)
            if (sawMultiway) multiway++
            if (r.showdown) showdowns++
            if (hand.hero.stack == 0 && r.showdown) heroAllIn++
        }
        assertTrue("multiway pots should happen: $multiway", multiway > 100)
        assertTrue("showdowns should happen: $showdowns", showdowns > 200)
        assertTrue("short stacks get all in: $heroAllIn", heroAllIn > 10)
    }

    @Test
    fun `positions follow the button around the table`() {
        val styles = table(Random(1))
        val seen = (0 until 6).map { TableHand(Random(it.toLong()), 200, it, styles).hero.position }.toSet()
        assertEquals(6, seen.size)
    }

    @Test
    fun `coach handles multiway pots and cold 3-bets`() {
        fun read(hole: String, board: String) = HandReader.read(cards(hole), cards(board))
        fun spot(r: HandRead, facing: Facing, opponents: Int, pot: Int = 30, toCall: Int = 0) =
            Coach.Spot("Флоп", facing, r, pot, toCall, heroAggressor = true, wet = false, river = false, unseen = 47, opponents = opponents)
        val air = read("Ac Qd", "8h 5s 2c")
        val middle = read("8h 7h", "Kc 7s 2d")
        assertEquals(Act.BET, Coach.postflop(spot(air, Facing.CHECKED_TO, 1), Act.BET).best)
        assertEquals(Act.CHECK, Coach.postflop(spot(air, Facing.CHECKED_TO, 2), Act.BET).best)
        assertEquals(Act.FOLD, Coach.postflop(spot(middle, Facing.BET, 2, pot = 40, toCall = 20), Act.CALL).best)
        assertEquals(Act.CALL, Coach.postflop(spot(middle, Facing.BET, 2, pot = 40, toCall = 8), Act.CALL).best)
        fun h(s: String) = cards(s).let { StartingHand.of(it[0], it[1]) }
        assertEquals(Act.RAISE, Coach.preflopVsColdThreeBet(h("Ah Ad"), Act.RAISE).best)
        assertEquals(Act.FOLD, Coach.preflopVsColdThreeBet(h("Qh Qd"), Act.CALL).best)
        assertEquals(Quality.OK, Coach.preflopVsColdThreeBet(h("Qh Qd"), Act.CALL).quality)
        assertEquals(Act.FOLD, Coach.preflopVsOpen(h("Ah Qd"), app.mast.poker.content.Position.BTN, app.mast.poker.content.Position.UTG, VillainStyle.TAG, Act.CALL, callers = 1).best)
    }
}
