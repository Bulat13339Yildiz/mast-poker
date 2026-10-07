package app.mast.poker.practice.sim

import app.mast.poker.content.Position
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.practice.Explainer
import app.mast.poker.practice.seatGroup
import kotlin.math.roundToInt

enum class Act(val ruName: String) { FOLD("Фолд"), CHECK("Чек"), CALL("Колл"), BET("Бет"), RAISE("Рейз") }

enum class Quality(val ruName: String) { BEST("Отлично"), OK("Допустимо"), MISTAKE("Ошибка") }

data class Verdict(val street: String, val chosen: Act, val quality: Quality, val best: Act, val text: String)

/** What the hero is reacting to. */
enum class Facing {
    /** Everyone folded to the hero. */
    OPEN,
    /** An opponent opened with a raise before the hero. */
    VS_OPEN,
    /** An opponent limped; the hero sits behind him (not in the big blind). */
    VS_LIMP,
    /** An opponent limped and the hero is in the big blind: check or raise. */
    LIMPED_TO_BB,
    /** The hero raised and got re-raised. */
    THREE_BET,
    /** The hero 3-bet and got re-raised again. */
    FOUR_BET,
    FIRST,
    CHECKED_TO,
    BET,
    RAISE,
}

/**
 * The gameplan from the lessons: one simple, sound rule per situation, adjusted to
 * the opponent's style the way the «Соперники» chapter teaches. Grades each hero
 * decision as best / acceptable / mistake and explains it in plain words.
 */
object Coach {

    private fun verdict(street: String, chosen: Act, best: Act, ok: Set<Act>, text: String) =
        Verdict(street, chosen, if (chosen == best) Quality.BEST else if (chosen in ok) Quality.OK else Quality.MISTAKE, best, text)

    private fun range(spec: String) = HandRange.parse(spec).hands

    private fun chart(group: SeatGroup) = HandClasses.all.filter { PreflopChart.shouldOpen(it, group) }.toSet()

    private val latePositions = setOf(Position.CO, Position.BTN, Position.SB)

    // --- Preflop ------------------------------------------------------------------------

    fun preflopOpen(hand: StartingHand, position: Position, chosen: Act, style: VillainStyle? = null): Verdict {
        // A nit in the big blind folds too often: steal from the small blind as from the button.
        val steal = style == VillainStyle.NIT && position in latePositions
        val group = if (steal) SeatGroup.LATE else position.seatGroup
        val open = PreflopChart.shouldOpen(hand, group)
        val widened = steal && open && !PreflopChart.shouldOpen(hand, position.seatGroup)
        val base = if (widened) {
            "${hand.notation} — ${hand.ruDescription}. С малого блайнда обычно играем строже, но на большом блайнде нит: " +
                "он защищается только сильными руками. Воруй шире, как с баттона, — повышай."
        } else {
            Explainer.preflop(hand, position, position.seatGroup, open) +
                if (steal && open) "\nНа большом блайнде нит — он часто сбрасывает, кража сработает." else ""
        }
        val limp = if (chosen == Act.CALL) "\nПросто уравнять (лимп) — слабый ход: ты пускаешь всех дёшево и отдаёшь инициативу. Входи рейзом или пасуй." else ""
        return verdict("Префлоп", chosen, if (open) Act.RAISE else Act.FOLD, emptySet(), base + limp)
    }

    /** Someone opened before the hero: 3-bet, call or fold. */
    fun preflopVsOpen(hand: StartingHand, heroSeat: Position, opener: Position, style: VillainStyle, chosen: Act): Verdict {
        val premium = range("QQ+, AK")
        val strong = range("JJ, TT, AQ")
        val wideCalls = range("99-77, AJs, ATs, KQs, KJs, QJs")
        val threeBet = premium.toMutableSet()
        val call = mutableSetOf<StartingHand>()
        val lines = mutableListOf("${opener.short} открылся рейзом. План против рейза: 3-бет — QQ+ и AK, колл — JJ, TT и AQ, остальное — пас.")
        when {
            style.wild -> {
                threeBet += strong
                call += wideCalls
                lines += "${style.ruName} открывается очень широко: против него 3-бет и с JJ, TT, AQ, а уравнивать можно и пары 77–99, AJs, ATs, KQs, KJs, QJs."
            }
            style == VillainStyle.NIT -> {
                call += range("JJ, TT")
                lines += "Нит открывается только с сильными руками: AQ против него часто доминирована, JJ и TT лучше просто уравнять."
            }
            else -> call += strong
        }
        when (heroSeat) {
            Position.BB -> {
                val late = opener in latePositions
                val defend = chart(if (late) SeatGroup.LATE else SeatGroup.MIDDLE) - threeBet
                call += defend
                lines += "На большом блайнде скидка: ты уже вложил блайнд, поэтому защищайся шире — " +
                    (if (late) "против поздней позиции уравнивай руки из диапазона поздней позиции." else "против ранней позиции — руки из диапазона средней позиции.")
            }
            Position.SB -> {
                threeBet += call.intersect(strong)
                call.clear()
                lines += "С малого блайнда — 3-бет или пас: после флопа ты будешь ходить первым, колл здесь играть труднее всего."
            }
            else -> Unit
        }
        val best = when (hand) {
            in threeBet -> Act.RAISE
            in call -> Act.CALL
            else -> Act.FOLD
        }
        val ok = when {
            best == Act.RAISE && hand !in premium -> setOf(Act.CALL)
            best == Act.CALL && hand in strong -> setOf(Act.RAISE)
            style == VillainStyle.NIT && hand in range("AQ") -> setOf(Act.CALL)
            else -> emptySet()
        }
        lines += when (best) {
            Act.RAISE -> "${hand.notation} — 3-бет."
            Act.CALL -> "${hand.notation} — колл."
            else -> "${hand.notation} сюда не входит — пас."
        }
        return verdict("Префлоп", chosen, best, ok, lines.joinToString("\n"))
    }

    /** Someone limped before the hero. */
    fun preflopVsLimp(hand: StartingHand, heroSeat: Position, chosen: Act): Verdict {
        if (heroSeat == Position.BB) {
            val raise = PreflopChart.shouldOpen(hand, SeatGroup.MIDDLE)
            val text = "Соперник просто уравнял (лимп), и на большом блайнде ты можешь посмотреть флоп бесплатно. " +
                if (raise) "${hand.notation} — сильная рука: повышай примерно до 5 BB, иначе лимпер увидит флоп дёшево. Чек тоже допустим."
                else "${hand.notation} недостаточно сильна, чтобы раздувать банк без позиции, — бесплатный чек."
            return verdict("Префлоп", chosen, if (raise) Act.RAISE else Act.CHECK, if (raise) setOf(Act.CHECK) else emptySet(), text)
        }
        val iso = PreflopChart.shouldOpen(hand, heroSeat.seatGroup)
        val text = "Перед тобой лимп — так часто играют слабые игроки. Против лимпера повышай примерно до 4 BB (изоляция) с руками, " +
            "с которыми открылся бы с этой позиции, остальное — пас.\n" +
            if (iso) "${hand.notation} подходит — повышай и играй со слабым игроком один на один." else "${hand.notation} не входит в диапазон — пас."
        return verdict("Префлоп", chosen, if (iso) Act.RAISE else Act.FOLD, emptySet(), text)
    }

    /** The hero raised and got 3-bet. */
    fun preflopVs3bet(hand: StartingHand, chosen: Act, style: VillainStyle? = null): Verdict {
        val fourBet = range("KK+")
        val call = range("QQ, JJ, AK").toMutableSet()
        val wild = style?.wild == true
        if (wild) call += range("TT, AQ")
        val best = when (hand) {
            in fourBet -> Act.RAISE
            in call -> Act.CALL
            else -> Act.FOLD
        }
        val ok = when (best) {
            Act.RAISE -> setOf(Act.CALL)
            Act.CALL -> if (wild && hand in range("QQ, AK")) setOf(Act.RAISE) else emptySet()
            else -> emptySet()
        }
        val text = "Соперник повысил ещё раз (3-бет) — это сильный ход. План: 4-бет с AA и KK, колл — QQ, JJ и AK, остальное — пас." +
            (if (wild) " ${style!!.ruName} делает 3-бет широко, поэтому TT и AQ тоже можно уравнять." else "") + "\n" +
            when (best) {
                Act.RAISE -> "${hand.notation} — повышай снова."
                Act.CALL -> "${hand.notation} — уравнивай."
                else -> "${hand.notation} сюда не входит — пас: против такой силы рука чаще проигрывает."
            }
        return verdict("Префлоп", chosen, best, ok, text)
    }

    /** The hero 3-bet and got 4-bet. */
    fun preflopVs4bet(hand: StartingHand, chosen: Act, style: VillainStyle): Verdict {
        val top = range("KK+")
        val next = range("QQ, AK")
        val best = when {
            hand in top -> Act.CALL
            hand in next && style.wild -> Act.CALL
            else -> Act.FOLD
        }
        val ok = if (hand in next) setOf(if (best == Act.CALL) Act.FOLD else Act.CALL) else emptySet()
        val text = "Соперник ответил 4-бетом — обычно это AA, KK, QQ или AK." +
            (if (style.wild) " Но ${style.ruName.lowercase()} делает 4-бет и с руками слабее." else "") + "\n" +
            when {
                hand in top -> "${hand.notation} — уравнивай: против такого диапазона ты фаворит."
                best == Act.CALL -> "${hand.notation} против его широкого 4-бета держится — колл."
                hand in next -> "${hand.notation} против AA и KK сильно проигрывает — пас разумен, колл допустим."
                else -> "${hand.notation} — пас."
            }
        return verdict("Префлоп", chosen, best, ok, text)
    }

    // --- Postflop -----------------------------------------------------------------------

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
        val style: VillainStyle? = null,
    ) {
        val flop: Boolean get() = street == "Флоп"
    }

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
        val style = s.style
        return when (s.read.strength) {
            Strength.STRONG -> if (style == VillainStyle.MANIAC && s.facing == Facing.FIRST) {
                verdict(s.street, chosen, Act.CHECK, setOf(Act.BET), "$intro Против маньяка с сильной рукой можно чекнуть: он поставит сам и вложит в банк больше, чем заплатил бы на твою ставку. Ставка тоже хороша.")
            } else {
                val extra = if (style == VillainStyle.FISH) " Колл-станция заплатит и с рукой заметно хуже — не стесняйся." else ""
                verdict(s.street, chosen, Act.BET, emptySet(), "$intro С сильной рукой ставь на ценность: соперник с рукой похуже заплатит, а ты не дашь ему бесплатно добрать карту.$extra")
            }
            Strength.DRAW -> if (style == VillainStyle.FISH) {
                verdict(s.street, chosen, Act.CHECK, setOf(Act.BET), "$intro Колл-станция почти не сбрасывает — у полублефа нет главного козыря. Возьми бесплатную карту: собрав руку, ты получишь с неё сполна.")
            } else {
                verdict(s.street, chosen, Act.BET, setOf(Act.CHECK), "$intro Ставка с дро — полублеф: соперник может сбросить сразу, а если заплатит, у тебя ${s.read.outs} аутов. Чек тоже допустим.")
            }
            Strength.MEDIUM -> when {
                style == VillainStyle.FISH ->
                    verdict(s.street, chosen, Act.BET, setOf(Act.CHECK), "$intro Против колл-станции ставь на вэлью и со средней рукой: она уравняет и с парой похуже.")
                s.flop && s.heroAggressor ->
                    verdict(s.street, chosen, Act.CHECK, setOf(Act.BET), "$intro Средней рукой обычно лучше чек — контролируешь размер банка. Но ты повышал до флопа, поэтому продолженная ставка тоже допустима.")
                else ->
                    verdict(s.street, chosen, Act.CHECK, emptySet(), "$intro Средней рукой лучше чек: на ставку заплатят в основном руки сильнее твоей, а слабые сбросят.")
            }
            Strength.WEAK -> when {
                style == VillainStyle.FISH || style == VillainStyle.MANIAC ->
                    verdict(s.street, chosen, Act.CHECK, emptySet(), "$intro Не блефуй ${if (style == VillainStyle.FISH) "колл-станцию" else "маньяка"}: он почти никогда не сбрасывает. Чек.")
                s.flop && s.heroAggressor && style == VillainStyle.NIT ->
                    verdict(s.street, chosen, Act.BET, setOf(Act.CHECK), "$intro Но ты повышал до флопа, а против тебя нит: он сбрасывает всё, во что не попал. Продолженная ставка работает отлично.")
                s.flop && s.heroAggressor && !s.wet ->
                    verdict(s.street, chosen, Act.BET, setOf(Act.CHECK), "$intro Но ты повышал до флопа, а борд сухой — небольшая продолженная ставка часто забирает банк сразу: соперник тоже, скорее всего, не попал.")
                else ->
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
        val style = s.style
        val nitBigBet = style == VillainStyle.NIT && !s.flop && betShare > 0.5
        return when (s.read.strength) {
            Strength.STRONG -> when {
                s.read.monster ->
                    verdict(s.street, chosen, Act.RAISE, setOf(Act.CALL), "$intro С такой рукой повышай: соперник заплатит больше, а дро придётся платить дорого. Колл тоже неплох.")
                nitBigBet ->
                    verdict(s.street, chosen, Act.FOLD, setOf(Act.CALL), "$intro Но крупная ставка нита на ${s.street.lowercase()}е — почти всегда рука сильнее одной пары. Верь его силе: пас. Колл допустим.")
                else ->
                    verdict(s.street, chosen, Act.CALL, setOf(Act.RAISE), "$intro Этого хватает для колла. Рейз рискованнее: на него заплатят в основном руки сильнее.")
            }
            Strength.MEDIUM -> when {
                style?.wild == true ->
                    verdict(s.street, chosen, Act.CALL, emptySet(), "$intro ${style.ruName} ставит и со слабыми руками — средняя пара против него часто впереди. Уравнивай.")
                style == VillainStyle.NIT ->
                    verdict(s.street, chosen, Act.FOLD, emptySet(), "$intro Нит почти не блефует: если он ставит, у него обычно рука сильнее средней пары. Пас.")
                betShare <= 0.67 ->
                    verdict(s.street, chosen, Act.CALL, emptySet(), "$intro Ставка небольшая — примерно ${pct(betShare)} банка. Средней рукой можно уравнять: соперник часто ставит и с худшими руками.")
                else ->
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
        Strength.STRONG -> when {
            s.style == VillainStyle.NIT && !s.read.monster ->
                verdict(s.street, chosen, Act.FOLD, setOf(Act.CALL), "$intro Нит повышает только с очень сильными руками. Одна пара против него обычно проигрывает — пас.")
            s.read.monster || !s.river ->
                verdict(s.street, chosen, Act.CALL, emptySet(), "$intro Соперник повысил — это сила, но твоя рука достаточно хороша, чтобы уравнять.")
            else ->
                verdict(s.street, chosen, Act.FOLD, setOf(Act.CALL), "$intro Рейз на ривере почти всегда означает очень сильную руку. Одна пара здесь часто проигрывает — пас разумен.")
        }
        Strength.DRAW -> {
            val (need, chance) = oddsLine(s)
            val text = "$intro Нужно ${pct(need)}, шанс собрать на следующей карте — ${pct(chance)}."
            if (chance >= need) verdict(s.street, chosen, Act.CALL, emptySet(), "$text Шанс больше цены — колл.")
            else verdict(s.street, chosen, Act.FOLD, emptySet(), "$text Цена слишком высока — пас.")
        }
        Strength.MEDIUM -> if (s.style == VillainStyle.MANIAC && !s.river) {
            verdict(s.street, chosen, Act.FOLD, setOf(Act.CALL), "$intro Маньяк повышает и со слабыми руками, поэтому колл допустим. Но рейз — всё-таки сила: пас осторожнее.")
        } else {
            verdict(s.street, chosen, Act.FOLD, emptySet(), "$intro Соперник повысил твою ставку — так поступают с сильными руками. Средняя рука здесь проигрывает: пас.")
        }
        Strength.WEAK -> verdict(s.street, chosen, Act.FOLD, emptySet(), "$intro Соперник повысил твою ставку — так поступают с сильными руками. Слабая рука здесь проигрывает: пас.")
    }
}
