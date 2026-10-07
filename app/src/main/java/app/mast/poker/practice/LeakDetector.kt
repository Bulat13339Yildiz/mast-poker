package app.mast.poker.practice

import app.mast.poker.content.Concept
import app.mast.poker.content.Question
import app.mast.poker.practice.sim.Act
import app.mast.poker.practice.sim.Facing
import app.mast.poker.practice.sim.Quality
import app.mast.poker.practice.sim.Verdict
import app.mast.poker.progress.Leak
import app.mast.poker.progress.LeakEvent

/**
 * Turns graded decisions into leak statistics. Each decision counts as a spot for the
 * leak its situation could reveal; it is a mistake only when the coach called it one
 * and the choice went that way.
 */
object LeakDetector {

    fun fromVerdict(v: Verdict): List<LeakEvent> {
        val facing = v.facing ?: return emptyList()
        val wrong = v.quality == Quality.MISTAKE
        val chosen = v.chosen
        fun e(leak: Leak, deviation: Boolean) = LeakEvent(leak, wrong && deviation)
        return when (facing) {
            Facing.OPEN -> if (v.best == Act.RAISE) {
                listOf(e(Leak.TIGHT_PREFLOP, chosen == Act.FOLD), e(Leak.LIMPING, chosen == Act.CALL))
            } else {
                listOf(e(Leak.LOOSE_PREFLOP, chosen != Act.FOLD))
            }
            Facing.VS_OPEN -> when (v.best) {
                Act.RAISE -> listOf(e(Leak.MISSED_3BET, chosen != Act.RAISE))
                Act.CALL -> listOf(e(Leak.TIGHT_PREFLOP, chosen == Act.FOLD))
                else -> listOf(e(Leak.LOOSE_VS_RAISE, chosen != Act.FOLD))
            }
            Facing.VS_LIMP, Facing.LIMPED_TO_BB -> if (v.best == Act.RAISE) {
                listOf(e(Leak.TIGHT_PREFLOP, chosen != Act.RAISE))
            } else {
                listOf(e(Leak.LOOSE_PREFLOP, chosen == Act.RAISE))
            }
            Facing.THREE_BET, Facing.FOUR_BET -> if (v.best == Act.FOLD) {
                listOf(e(Leak.LOOSE_VS_RAISE, chosen != Act.FOLD))
            } else {
                listOf(e(Leak.TIGHT_PREFLOP, chosen == Act.FOLD))
            }
            Facing.FIRST, Facing.CHECKED_TO -> if (v.best == Act.BET) {
                listOf(e(Leak.PASSIVE, chosen == Act.CHECK))
            } else {
                listOf(e(Leak.OVERBET, chosen == Act.BET))
            }
            Facing.BET, Facing.RAISE -> when (v.best) {
                Act.FOLD -> listOf(e(if (v.street == "Ривер") Leak.RIVER_OVERCALL else Leak.OVERCALL, chosen != Act.FOLD))
                Act.RAISE -> listOf(e(Leak.OVERFOLD, chosen == Act.FOLD), e(Leak.PASSIVE, chosen == Act.CALL))
                else -> listOf(e(Leak.OVERFOLD, chosen == Act.FOLD))
            }
        }
    }

    fun fromAnswer(q: Question, a: Answer?, correct: Boolean): List<LeakEvent> {
        val yes = (a as? Answer.Decision)?.yes
        return when (q) {
            is Question.OpenOrFold -> if (yes == null) emptyList()
            else if (q.shouldOpen) listOf(LeakEvent(Leak.TIGHT_PREFLOP, !yes)) else listOf(LeakEvent(Leak.LOOSE_PREFLOP, yes))
            is Question.CallOrFold -> if (yes == null) emptyList()
            else if (q.shouldCall) listOf(LeakEvent(Leak.OVERFOLD, !yes)) else listOf(LeakEvent(Leak.CHASING, yes))
            is Question.PushOrFold -> if (yes == null) emptyList()
            else if (q.shouldPush) listOf(LeakEvent(Leak.PUSH_TIGHT, !yes)) else listOf(LeakEvent(Leak.PUSH_LOOSE, yes))
            is Question.Choice -> sizing(q, a, correct)
            else -> emptyList()
        }
    }

    /** Bet-sizing drill questions list check first and then three sizes. */
    private fun sizing(q: Question.Choice, a: Answer?, correct: Boolean): List<LeakEvent> {
        if (q.concept != Concept.BET_SIZING || q.options.size != 4 || q.options.first() != "Чек") return emptyList()
        val picked = (a as? Answer.Pick)?.index ?: return emptyList()
        return when {
            q.correct == 0 -> listOf(LeakEvent(Leak.OVERBET, picked != 0))
            picked == 0 -> listOf(LeakEvent(Leak.PASSIVE, true))
            else -> listOf(LeakEvent(Leak.SIZE_WRONG, !correct))
        }
    }
}
