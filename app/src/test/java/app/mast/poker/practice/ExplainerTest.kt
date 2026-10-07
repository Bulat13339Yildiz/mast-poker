package app.mast.poker.practice

import app.mast.poker.content.Position
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.EquityCalculator
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandCategory.FLUSH
import app.mast.poker.core.poker.HandCategory.FULL_HOUSE
import app.mast.poker.core.poker.HandCategory.PAIR
import app.mast.poker.core.poker.HandCategory.STRAIGHT
import app.mast.poker.core.poker.HandCategory.THREE_OF_A_KIND
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.Outs
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.cards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExplainerTest {

    private fun has(text: String, part: String) = assertTrue("«$part» not in:\n$text", text.contains(part))

    @Test
    fun `anatomy names the traps`() {
        has(Explainer.anatomy(emptyList(), cards("Jc Qd Ks Ah 2c")), "не стрит: ряд не продолжается через туза")
        has(Explainer.anatomy(cards("4h 5c"), cards("Ac 2d 3s Kh 9c")), "Туз здесь играет как единица")
        val threePairs = Explainer.anatomy(cards("Kh 4c"), cards("Kd 4s 9c 9d 2h"))
        has(threePairs, "Пар три")
        has(threePairs, "Пара четвёрок не играет")
        has(Explainer.anatomy(cards("2c 3d"), cards("Ah Kh Qd Js Tc")), "играет доска")
        has(Explainer.anatomy(cards("Ah Kh"), cards("7h 2h 9c 4s 5d")), "Карт червей — четыре: до флеша не хватает одной")
        has(Explainer.anatomy(cards("9s 8h"), cards("7s 6s 5s Ks 2d")), "Стрит здесь тоже есть, но флеш сильнее")
    }

    @Test
    fun `why not explains the chosen mistake`() {
        has(Explainer.whyNot(FLUSH, emptyList(), cards("Ah Kh 7h 2c 5h")), "больше всего червей, и их только четыре")
        has(Explainer.whyNot(STRAIGHT, emptyList(), cards("Qc Kd Ah 2s 3d")), "через туза")
        has(Explainer.whyNot(STRAIGHT, emptyList(), cards("5c 6d 7h 9s Kd")), "Самый длинный ряд здесь — 5-6-7, всего три")
        has(Explainer.whyNot(FULL_HOUSE, emptyList(), cards("Qc Qd Qh 5s 9d")), "Тройка дам есть, но второй пары к ней нет")
        has(Explainer.whyNot(PAIR, cards("9c 9d"), cards("4h 4s Kc Jd 2h")), "Пар здесь две: девятки и четвёрки")
        has(Explainer.whyNot(THREE_OF_A_KIND, emptyList(), cards("Td Ts 8c 5h 2d")), "максимум две — десятки")
        has(Explainer.whyNot(STRAIGHT, emptyList(), cards("Ac 9c 7c 4c 2c")), "недооценил")
    }

    @Test
    fun `why not never stays silent`() {
        val rnd = Random(11)
        repeat(1500) {
            val deal = Deck.shuffled(rnd).take(if (it % 2 == 0) 5 else 7)
            val actual = HandEvaluator.evaluate(deal).category
            HandCategory.entries.filter { c -> c != actual }.forEach { chosen ->
                val text = Explainer.whyNot(chosen, emptyList(), deal)
                assertTrue("$chosen vs $actual on $deal", text.isNotBlank())
            }
        }
    }

    @Test
    fun `showdown reasons name the deciding ranks`() {
        has(Explainer.winner(cards("Ac 9d 7s 4h 2c"), listOf(cards("Ad Jc"), cards("Ah Ts"))), "решает кикер: валет против десятки")
        has(Explainer.winner(cards("Kc Kd 6h 3s 2c"), listOf(cards("6c 5d"), cards("3c Ah"))), "решает вторая: шестёрки против троек")
        has(Explainer.winner(cards("Ah Kd Qc Js Th"), listOf(cards("Ac 2d"), cards("9s 8s"))), "играет стол")
        has(Explainer.winner(cards("8c 8d 4h 4s Kc"), listOf(cards("8h 2c"), cards("4d Ad"))), "тройка восьмёрок старше тройки четвёрок")
        has(Explainer.winner(cards("Kd 9c 5h 2s 3d"), listOf(cards("2h 2c"), cards("Kh 9h"))), "Тройка выше на лестнице, чем две пары")
        has(Explainer.winner(cards("Ac Ad Kh Qs Jc"), listOf(cards("Ah 2c"), cards("As 3d"))), "в пятёрку не попали")
    }

    @Test
    fun `outs breakdown adds up`() {
        val rnd = Random(5)
        repeat(300) {
            val deck = Deck.shuffled(rnd)
            val hole = deck.take(2)
            val board = deck.drop(2).take(3 + it % 2)
            val target = listOf(STRAIGHT, FLUSH, FULL_HOUSE).random(rnd)
            val groups = Explainer.outsGroups(hole, board, target)
            assertEquals(Outs.toCategory(hole, board, target).size, groups.sumOf { g -> g.cards.size })
            groups.forEach { g -> assertTrue(g.category >= target) }
        }
        val combo = Explainer.outsGroups(cards("9h 8h"), cards("7h 6c 2h"), STRAIGHT)
        assertEquals(mapOf(FLUSH to 9, STRAIGHT to 6), combo.associate { it.category to it.cards.size })
        has(Explainer.outs(cards("9h 8h"), cards("7h 6c 2h"), STRAIGHT), "Итого 15 аутов из 47")
    }

    @Test
    fun `pot odds text matches the decision`() {
        has(Explainer.potOdds(pot = 150, call = 50, outs = 9, cardsToCome = 1), "Нужно: 50 ÷ (150 + 50) = 25%")
        has(Explainer.potOdds(pot = 150, call = 50, outs = 9, cardsToCome = 1), "фолд")
        has(Explainer.potOdds(pot = 200, call = 100, outs = 15, cardsToCome = 2), "точно 54%")
        has(Explainer.potOdds(pot = 200, call = 100, outs = 15, cardsToCome = 2), "колл окупается")
    }

    @Test
    fun `range texts are generated from the chart`() {
        assertEquals("пары AA–77; одномастные AKs–ATs, KQs; разномастные AKo–AQo", PreflopChart.describe(PreflopChart.addedAt(SeatGroup.EARLY)))
        // Every hand is described in exactly one seat group (or none).
        val listed = SeatGroup.entries.flatMap { PreflopChart.addedAt(it) }
        assertEquals(listed.size, listed.toSet().size)
        assertEquals(PreflopChart.allHands.count { PreflopChart.shouldOpen(it, SeatGroup.LATE) }, listed.size)
        assertEquals(169, PreflopChart.allHands.size)
        fun h(s: String) = cards(s).let { StartingHand.of(it[0], it[1]) }
        has(Explainer.preflop(h("8s 7s"), Position.UTG, SeatGroup.EARLY, false), "только с поздней позиции")
        has(Explainer.preflop(h("7c 2d"), Position.BTN, SeatGroup.LATE, false), "лучше всегда сбрасывать")
        has(Explainer.preflop(h("Ah Kd"), Position.UTG, SeatGroup.EARLY, true), "повышай")
    }

    @Test
    fun `preflop matchup claims hold`() {
        has(Explainer.favourite(listOf(cards("As Kd"), cards("Ah Qc")), emptyList(), 0.74), "доминирование")
        has(Explainer.favourite(listOf(cards("As Kd"), cards("Ah Qc")), emptyList(), 0.74), "король против дамы")

        // Each class of matchup makes a claim about the stronger side's share; check it on random deals.
        val claims = mapOf(
            Explainer.Matchup.PAIR_VS_PAIR to 0.77..0.86,
            Explainer.Matchup.PAIR_VS_OVERS to 0.42..0.59,
            Explainer.Matchup.PAIR_VS_ONE_OVER to 0.60..0.75,
            Explainer.Matchup.PAIR_VS_SAME to 0.84..0.98,
            Explainer.Matchup.PAIR_VS_UNDERS to 0.72..0.90,
            Explainer.Matchup.DOMINATION to 0.62..0.80,
            Explainer.Matchup.OVERS_VS_UNDERS to 0.57..0.72,
        )
        val seen = mutableMapOf<Explainer.Matchup, MutableList<Double>>()
        val rnd = Random(9)
        while (seen.values.sumOf { it.size } < 160 || claims.keys.any { (seen[it]?.size ?: 0) < 8 }) {
            val deck = Deck.shuffled(rnd)
            val a = deck.take(2)
            val b = deck.drop(2).take(2)
            val m = Explainer.classify(a, b) ?: continue
            if ((seen[m]?.size ?: 0) >= 30) continue
            val e = EquityCalculator.headsUp(a, b, samples = 4000, random = rnd)
            val shareA = e.win + e.tie / 2
            val share = if (strongerIsFirst(m, a, b)) shareA else 1 - shareA
            seen.getOrPut(m) { mutableListOf() } += share
            assertTrue("$m: $a vs $b = $share", share in claims.getValue(m))
        }
        seen.forEach { (m, s) -> println("$m: ${"%.3f".format(s.min())} .. ${"%.3f".format(s.max())} (${s.size})") }
    }

    /** Which side the claim is about: the pair, the bigger pair, the better kicker or the higher cards. */
    private fun strongerIsFirst(m: Explainer.Matchup, a: List<app.mast.poker.core.poker.Card>, b: List<app.mast.poker.core.poker.Card>): Boolean {
        val ha = StartingHand.of(a[0], a[1])
        val hb = StartingHand.of(b[0], b[1])
        return when (m) {
            Explainer.Matchup.PAIR_VS_PAIR -> ha.high > hb.high
            Explainer.Matchup.PAIR_VS_OVERS, Explainer.Matchup.PAIR_VS_ONE_OVER,
            Explainer.Matchup.PAIR_VS_SAME, Explainer.Matchup.PAIR_VS_UNDERS -> ha.isPair
            Explainer.Matchup.DOMINATION -> {
                val shared = setOf(ha.high, ha.low).intersect(setOf(hb.high, hb.low)).first()
                val ka = if (ha.high == shared) ha.low else ha.high
                val kb = if (hb.high == shared) hb.low else hb.high
                ka > kb
            }
            Explainer.Matchup.OVERS_VS_UNDERS -> ha.low > hb.high
        }
    }

    @Test
    fun `postflop favourite names how many cards help`() {
        val text = Explainer.favourite(listOf(cards("Qc Qd"), cards("Ah Kh")), cards("Qh 7h 2s 3c"), 0.84)
        has(text, "у первой — тройка дам")
        has(text, "на ривере выведут вперёд 7 карт из 44")
        assertFalse(text.contains("null"))
    }
}
