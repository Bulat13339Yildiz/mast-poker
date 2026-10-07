package app.mast.poker.practice.sim

import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.StartingHand
import java.util.concurrent.ConcurrentHashMap

/**
 * How the simulated opponent plays. Preflop ranges are the top share of hands
 * (by strength against a random hand); postflop numbers are action frequencies.
 */
enum class VillainStyle(
    val ruName: String,
    val tip: String,
    /** Opens with a raise when first in. */
    val open: Double,
    /** Limps when first in (calling stations only). */
    val limp: Double,
    /** Continues against a raise (call or 3-bet). */
    val defend: Double,
    val threeBet: Double,
    val fourBet: Double,
    /** Calls a 3-bet without re-raising. */
    val callThreeBet: Double,
    val betStrong: Float,
    val betDraw: Float,
    val betMedium: Float,
    val bluff: Float,
    /** Continuation bet on the flop after raising preflop, with any hand. */
    val cbet: Float,
    val callMediumBig: Float,
    val callWeak: Float,
    val raiseMonster: Float,
    val raiseBluff: Float,
) {
    NIT(
        "Нит", "Играет мало рук и почти не блефует. Крупная ставка нита — почти всегда сильная рука.",
        open = 0.08, limp = 0.0, defend = 0.12, threeBet = 0.025, fourBet = 0.012, callThreeBet = 0.035,
        betStrong = 0.7f, betDraw = 0.3f, betMedium = 0.15f, bluff = 0.04f, cbet = 0.45f,
        callMediumBig = 0.1f, callWeak = 0.0f, raiseMonster = 0.3f, raiseBluff = 0.0f,
    ),
    TAG(
        "Регуляр", "Играет мало рук, но активно. Крепкий соперник — играй по плану.",
        open = 0.18, limp = 0.0, defend = 0.30, threeBet = 0.05, fourBet = 0.025, callThreeBet = 0.08,
        betStrong = 0.75f, betDraw = 0.5f, betMedium = 0.3f, bluff = 0.18f, cbet = 0.65f,
        callMediumBig = 0.35f, callWeak = 0.05f, raiseMonster = 0.35f, raiseBluff = 0.03f,
    ),
    LAG(
        "Агрессор", "Играет много рук и часто давит ставками — блефует чаще других.",
        open = 0.30, limp = 0.0, defend = 0.45, threeBet = 0.10, fourBet = 0.04, callThreeBet = 0.15,
        betStrong = 0.8f, betDraw = 0.65f, betMedium = 0.45f, bluff = 0.4f, cbet = 0.75f,
        callMediumBig = 0.5f, callWeak = 0.12f, raiseMonster = 0.4f, raiseBluff = 0.08f,
    ),
    FISH(
        "Колл-станция", "Играет много рук и уравнивает почти всё. Не блефуй, а с сильной рукой ставь.",
        open = 0.06, limp = 0.45, defend = 0.60, threeBet = 0.02, fourBet = 0.012, callThreeBet = 0.30,
        betStrong = 0.45f, betDraw = 0.2f, betMedium = 0.2f, bluff = 0.08f, cbet = 0.3f,
        callMediumBig = 0.9f, callWeak = 0.45f, raiseMonster = 0.15f, raiseBluff = 0.0f,
    ),
    MANIAC(
        "Маньяк", "Ставит и повышает почти всегда, даже с ничем. Не пугайся его ставок.",
        open = 0.50, limp = 0.0, defend = 0.65, threeBet = 0.18, fourBet = 0.08, callThreeBet = 0.30,
        betStrong = 0.85f, betDraw = 0.75f, betMedium = 0.6f, bluff = 0.55f, cbet = 0.85f,
        callMediumBig = 0.75f, callWeak = 0.3f, raiseMonster = 0.5f, raiseBluff = 0.15f,
    );

    /** Plays many hands and rarely folds or rarely stops betting — the coach adapts to both. */
    val loose: Boolean get() = this == LAG || this == MANIAC || this == FISH
    val wild: Boolean get() = this == LAG || this == MANIAC

    companion object {
        private val tops = ConcurrentHashMap<Double, Set<StartingHand>>()

        fun top(fraction: Double): Set<StartingHand> = tops.getOrPut(fraction) { HandRange.top(fraction).hands }

        fun inTop(h: StartingHand, fraction: Double): Boolean = fraction > 0 && h in top(fraction)
    }
}
