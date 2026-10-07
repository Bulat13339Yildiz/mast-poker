package app.mast.poker.practice.sim

import app.mast.poker.content.Position
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SixMaxSimTest {

    private fun h(s: String) = cards(s).let { StartingHand.of(it[0], it[1]) }
    private fun read(hole: String, board: String) = HandReader.read(cards(hole), cards(board))

    @Test
    fun `every style and scenario plays out with straight chips`() {
        val rnd = Random(2024)
        val seen = mutableSetOf<Pair<VillainStyle, Scenario>>()
        repeat(3000) {
            val style = VillainStyle.entries[it % VillainStyle.entries.size]
            val sim = HandSimulator(Random(rnd.nextLong()), style)
            seen += style to sim.scenario
            assertTrue(sim.position != sim.villainSeat)
            when (sim.scenario) {
                Scenario.FOLDED_TO_HERO -> assertEquals(Position.BB, sim.villainSeat)
                Scenario.VILLAIN_OPENED -> {
                    assertTrue(HandSimulator.PreflopOrder.indexOf(sim.villainSeat) < HandSimulator.PreflopOrder.indexOf(sim.position))
                    assertTrue(StartingHand.of(sim.villain[0], sim.villain[1]) in VillainStyle.top(style.open.coerceAtLeast(0.05)))
                }
                Scenario.VILLAIN_LIMPED -> {
                    assertEquals(VillainStyle.FISH, style)
                    assertTrue(StartingHand.of(sim.villain[0], sim.villain[1]) in VillainStyle.top(style.limp))
                }
            }
            var steps = 0
            while (sim.result == null) {
                val options = sim.options()
                assertTrue(options.isNotEmpty())
                sim.act(options.random(rnd).act)
                steps++
                assertTrue("too many decisions", steps <= 16)
                assertEquals(sim.pot, sim.heroInvested + sim.villainInvested + sim.deadMoney)
            }
            val r = sim.result!!
            assertTrue(r.heroNet >= -sim.heroInvested && r.heroNet <= sim.pot)
            // Every graded decision appears once in the history, on a hero line.
            val tagged = sim.history.filterIsInstance<HistoryEntry.Line>().mapNotNull { l -> l.verdict?.also { assertEquals(Who.HERO, l.who) } }
            assertEquals(sim.verdicts, tagged)
        }
        VillainStyle.entries.forEach { s ->
            assertTrue("$s never folded to hero", s to Scenario.FOLDED_TO_HERO in seen)
            assertTrue("$s never opened", s to Scenario.VILLAIN_OPENED in seen)
        }
        assertTrue(VillainStyle.FISH to Scenario.VILLAIN_LIMPED in seen)
    }

    @Test
    fun `hero cards and board never collide with the opponent`() {
        repeat(500) {
            val sim = HandSimulator(Random(it.toLong()))
            val all = sim.hero + sim.villain
            assertEquals(4, all.toSet().size)
        }
    }

    @Test
    fun `facing an open follows the plan`() {
        fun best(hand: String, hero: Position, opener: Position, style: VillainStyle = VillainStyle.TAG) =
            Coach.preflopVsOpen(h(hand), hero, opener, style, Act.FOLD).best
        assertEquals(Act.RAISE, best("Ks Kd", Position.CO, Position.UTG))
        assertEquals(Act.RAISE, best("Ah Kd", Position.BTN, Position.MP))
        assertEquals(Act.CALL, best("Jh Jd", Position.BTN, Position.UTG))
        assertEquals(Act.CALL, best("Ah Qd", Position.BTN, Position.UTG))
        assertEquals(Act.FOLD, best("Kh Jd", Position.BTN, Position.UTG))
        // Big blind defends wider against a late open, tighter against an early one.
        assertEquals(Act.CALL, best("8h 7h", Position.BB, Position.BTN))
        assertEquals(Act.FOLD, best("8h 7h", Position.BB, Position.UTG))
        assertEquals(Act.FOLD, best("7h 2d", Position.BB, Position.BTN))
        // Small blind: 3-bet or fold.
        assertEquals(Act.RAISE, best("Jh Jd", Position.SB, Position.BTN))
        assertEquals(Quality.OK, Coach.preflopVsOpen(h("Jh Jd"), Position.SB, Position.BTN, VillainStyle.TAG, Act.CALL).quality)
        // Against wide openers 3-bet more; against a nit fold AQ.
        assertEquals(Act.RAISE, best("Th Td", Position.BTN, Position.CO, VillainStyle.MANIAC))
        assertEquals(Act.CALL, best("8h 8d", Position.BTN, Position.CO, VillainStyle.LAG))
        assertEquals(Act.FOLD, best("Ah Qd", Position.BTN, Position.UTG, VillainStyle.NIT))
        assertEquals(Quality.OK, Coach.preflopVsOpen(h("Ah Qd"), Position.BTN, Position.UTG, VillainStyle.NIT, Act.CALL).quality)
    }

    @Test
    fun `limpers get isolated or punished`() {
        assertEquals(Act.RAISE, Coach.preflopVsLimp(h("Ah Jd"), Position.CO, Act.FOLD).best)
        assertEquals(Act.FOLD, Coach.preflopVsLimp(h("9h 4d"), Position.CO, Act.FOLD).best)
        assertEquals(Act.RAISE, Coach.preflopVsLimp(h("Ah Jh"), Position.BB, Act.CHECK).best)
        assertEquals(Act.CHECK, Coach.preflopVsLimp(h("9h 4d"), Position.BB, Act.CHECK).best)
    }

    @Test
    fun `stealing from a nit`() {
        // K9s is a late-position open; from the small blind it opens only against a nit.
        assertEquals(Act.FOLD, Coach.preflopOpen(h("Ks 9s"), Position.SB, Act.RAISE).best)
        assertEquals(Act.RAISE, Coach.preflopOpen(h("Ks 9s"), Position.SB, Act.RAISE, VillainStyle.NIT).best)
    }

    private fun spot(read: HandRead, facing: Facing, style: VillainStyle, street: String = "Флоп", pot: Int = 20, toCall: Int = 0, aggressor: Boolean = true, wet: Boolean = false) =
        Coach.Spot(street, facing, read, pot, toCall, aggressor, wet, street == "Ривер", 47, style)

    @Test
    fun `postflop exploits follow the opponents chapter`() {
        val air = read("Ac Qd", "8h 5s 2c")
        val middle = read("8h 7h", "Kc 7s 2d")
        val topPair = read("Ah Kd", "Kc 7s 2h 9d")
        val fd = read("Ah 5h", "Kh 9h 2c")
        val set = read("7c 7d", "7h Ks 2c")
        // Calling station: no bluffs, no semi-bluffs, thin value.
        assertEquals(Act.CHECK, Coach.postflop(spot(air, Facing.CHECKED_TO, VillainStyle.FISH), Act.BET).best)
        assertEquals(Act.CHECK, Coach.postflop(spot(fd, Facing.CHECKED_TO, VillainStyle.FISH), Act.BET).best)
        assertEquals(Act.BET, Coach.postflop(spot(middle, Facing.CHECKED_TO, VillainStyle.FISH, street = "Ривер"), Act.CHECK).best)
        // Nit: c-bet any flop, believe big bets later.
        assertEquals(Act.BET, Coach.postflop(spot(air, Facing.CHECKED_TO, VillainStyle.NIT, wet = true), Act.CHECK).best)
        assertEquals(Act.FOLD, Coach.postflop(spot(topPair, Facing.BET, VillainStyle.NIT, street = "Тёрн", pot = 40, toCall = 20), Act.CALL).best)
        assertEquals(Act.FOLD, Coach.postflop(spot(middle, Facing.BET, VillainStyle.NIT, pot = 24, toCall = 4), Act.CALL).best)
        // Maniac: call down wider, let him bet.
        assertEquals(Act.CALL, Coach.postflop(spot(middle, Facing.BET, VillainStyle.MANIAC, pot = 40, toCall = 20), Act.FOLD).best)
        assertEquals(Act.CHECK, Coach.postflop(spot(set, Facing.FIRST, VillainStyle.MANIAC), Act.BET).best)
        assertEquals(Act.CHECK, Coach.postflop(spot(air, Facing.CHECKED_TO, VillainStyle.MANIAC), Act.BET).best)
        // The regular gets the plain plan.
        assertEquals(Act.BET, Coach.postflop(spot(air, Facing.CHECKED_TO, VillainStyle.TAG), Act.BET).best)
        assertEquals(Act.FOLD, Coach.postflop(spot(middle, Facing.BET, VillainStyle.TAG, pot = 40, toCall = 20), Act.FOLD).best)
    }
}
