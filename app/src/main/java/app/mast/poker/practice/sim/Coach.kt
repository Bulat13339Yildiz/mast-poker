package app.mast.poker.practice.sim

import app.mast.poker.content.Position
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.practice.Explainer
import app.mast.poker.practice.seatGroup
import kotlin.math.roundToInt

enum class Act(val ruName: String) { FOLD("Фолд"), CHECK("Чек"), CALL("Колл"), BET("Бет"), RAISE("Рейз") }

enum class Quality(val ruName: String) { BEST("Отлично"), OK("Допустимо"), MISTAKE("Ошибка") }

data class Verdict(val street: String, val chosen: Act, val quality: Quality, val best: Act, val text: String)

/** What the hero is reacting to. */
enum class Facing { OPEN, THREE_BET, FIRST, CHECKED_TO, BET, RAISE }

/**
 * The beginner gameplan: one simple, sound rule per situation. Grades each hero
 * decision as best / acceptable / mistake and explains it in plain words.
 */
object Coach {

    private fun verdict(street: String, chosen: Act, best: Act, ok: Set<Act>, text: String) =
        Verdict(street, chosen, if (chosen == best) Quality.BEST else if (chosen in ok) Quality.OK else Quality.MISTAKE, best, text)

    fun preflopOpen(hand: StartingHand, position: Position, chosen: Act): Verdict {
        val open = PreflopChart.shouldOpen(hand, position.seatGroup)
        val base = Explainer.preflop(hand, position, position.seatGroup, open)
        val limp = if (chosen == Act.CALL) "\nПросто уравнять (лимп) — слабый ход: ты пускаешь всех дёшево и отдаёшь инициативу. Входи рейзом или пасуй." else ""
        return verdict("Префлоп", chosen, if (open) Act.RAISE else Act.FOLD, emptySet(), base + limp)
    }

    private fun continuesVs3bet(h: StartingHand): Boolean =
        (h.isPair && h.high >= Rank.JACK) || (h.high == Rank.ACE && h.low == Rank.KING) || (h.high == Rank.ACE && h.low == Rank.QUEEN && h.suited)

    fun preflopVs3bet(hand: StartingHand, chosen: Act): Verdict {
        val cont = continuesVs3bet(hand)
        val premium = hand.isPair && hand.high >= Rank.KING
        val text = "Соперник повысил ещё раз (3-бет) — это сильный ход. Продолжаем только с JJ+, AK и AQs. " +
            if (cont) "${hand.notation} в этом списке — уравнивай" + (if (premium) ", а с KK и AA можно повысить снова." else ".")
            else "${hand.notation} сюда не входит — пас: против такой силы рука чаще проигрывает."
        val best = if (cont) Act.CALL else Act.FOLD
        return verdict("Префлоп", chosen, best, if (premium) setOf(Act.RAISE) else emptySet(), text)
    }

    data class Spot(
        val street: String,
        val facing: Facing,
        val read: HandRead,
        val pot: Int,
        val toCall: Int,
        val heroAggressor: Boolean,
        val wet: Boolean,
        val river: Boolean,
        val unseen: Int,
    )

    fun postflop(s: Spot, chosen: Act): Verdict {
        val intro = "У тебя ${s.read.label} — ${s.read.strength.ruName}."
        return when (s.facing) {
            Facing.FIRST, Facing.CHECKED_TO -> whenNoBet(s, chosen, intro)
            Facing.BET -> whenFacingBet(s, chosen, intro)
            Facing.RAISE -> whenFacingRaise(s, chosen, intro)
            else -> error("Not a postflop spot: ${s.facing}")
        }
    }

    private fun whenNoBet(s: Spot, chosen: Act, intro: String): Verdict {
        val flop = s.street == "Флоп"
        return when (s.read.strength) {
            Strength.STRONG -> verdict(s.street, chosen, Act.BET, emptySet(), "$intro С сильной рукой ставь на ценность: соперник с рукой похуже заплатит, а ты не дашь ему бесплатно добрать карту.")
            Strength.DRAW -> verdict(s.street, chosen, Act.BET, setOf(Act.CHECK), "$intro Ставка с дро — полублеф: соперник может сбросить сразу, а если заплатит, у тебя ${s.read.outs} аутов. Чек тоже допустим.")
            Strength.MEDIUM -> if (flop && s.heroAggressor) {
                verdict(s.street, chosen, Act.CHECK, setOf(Act.BET), "$intro Средней рукой обычно лучше чек — контролируешь размер банка. Но ты повышал до флопа, поэтому продолженная ставка тоже допустима.")
            } else {
                verdict(s.street, chosen, Act.CHECK, emptySet(), "$intro Средней рукой лучше чек: на ставку заплатят в основном руки сильнее твоей, а слабые сбросят.")
            }
            Strength.WEAK -> if (flop && s.heroAggressor && !s.wet) {
                verdict(s.street, chosen, Act.BET, setOf(Act.CHECK), "$intro Но ты повышал до флопа, а борд сухой — небольшая продолженная ставка часто забирает банк сразу: соперник тоже, скорее всего, не попал.")
            } else {
                verdict(s.street, chosen, Act.CHECK, emptySet(), "$intro Без пары и без дро лучше чек: блеф против соперника, который заплатит, стоит дорого." + if (s.wet) " Борд мокрый — у соперника много попаданий и дро." else "")
            }
        }
    }

    private fun oddsLine(s: Spot): Pair<Double, Double> {
        val need = PotOdds.requiredEquity(s.pot, s.toCall)
        val chance = PotOdds.exactHitChance(s.read.outs, s.unseen, 1)
        return need to chance
    }

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    private fun whenFacingBet(s: Spot, chosen: Act, intro: String): Verdict {
        val betShare = s.toCall.toDouble() / (s.pot - s.toCall).coerceAtLeast(1)
        return when (s.read.strength) {
            Strength.STRONG -> if (s.read.monster) {
                verdict(s.street, chosen, Act.RAISE, setOf(Act.CALL), "$intro С такой рукой повышай: соперник заплатит больше, а дро придётся платить дорого. Колл тоже неплох.")
            } else {
                verdict(s.street, chosen, Act.CALL, setOf(Act.RAISE), "$intro Этого хватает для колла. Рейз рискованнее: на него заплатят в основном руки сильнее.")
            }
            Strength.MEDIUM -> if (betShare <= 0.67) {
                verdict(s.street, chosen, Act.CALL, emptySet(), "$intro Ставка небольшая — примерно ${pct(betShare)} банка. Средней рукой можно уравнять: соперник часто ставит и с худшими руками.")
            } else {
                verdict(s.street, chosen, Act.FOLD, if (!s.river) setOf(Act.CALL) else emptySet(), "$intro Ставка крупная — около ${pct(betShare)} банка. Так чаще ставят сильные руки, и средняя пара проигрывает. Пас.")
            }
            Strength.DRAW -> {
                val (need, chance) = oddsLine(s)
                val text = "$intro Нужно ${s.toCall} ÷ (${s.pot} + ${s.toCall}) = ${pct(need)}. Шанс собрать на следующей карте — ${s.read.outs} из ${s.unseen}, это ${pct(chance)}."
                if (chance >= need) verdict(s.street, chosen, Act.CALL, setOf(Act.RAISE), "$text Шанс больше цены — колл выгоден. Рейз-полублеф тоже возможен.")
                else verdict(s.street, chosen, Act.FOLD, if (need - chance < 0.04) setOf(Act.CALL) else emptySet(), "$text Цена выше шанса — пас. Колл окупится, только если соперник потом заплатит ещё много.")
            }
            Strength.WEAK -> verdict(s.street, chosen, Act.FOLD, emptySet(), "$intro Без пары и без сильного дро против ставки — пас. Бесплатно держаться в раздаче нельзя, а платить не за что.")
        }
    }

    private fun whenFacingRaise(s: Spot, chosen: Act, intro: String): Verdict = when (s.read.strength) {
        Strength.STRONG -> if (s.read.monster || !s.river) {
            verdict(s.street, chosen, Act.CALL, emptySet(), "$intro Соперник повысил — это сила, но твоя рука достаточно хороша, чтобы уравнять.")
        } else {
            verdict(s.street, chosen, Act.FOLD, setOf(Act.CALL), "$intro Рейз на ривере почти всегда означает очень сильную руку. Одна пара здесь часто проигрывает — пас разумен.")
        }
        Strength.DRAW -> {
            val (need, chance) = oddsLine(s)
            val text = "$intro Нужно ${pct(need)}, шанс собрать на следующей карте — ${pct(chance)}."
            if (chance >= need) verdict(s.street, chosen, Act.CALL, emptySet(), "$text Шанс больше цены — колл.")
            else verdict(s.street, chosen, Act.FOLD, emptySet(), "$text Цена слишком высока — пас.")
        }
        else -> verdict(s.street, chosen, Act.FOLD, emptySet(), "$intro Соперник повысил твою ставку — так поступают с сильными руками. Средняя или слабая рука здесь проигрывает: пас.")
    }
}
