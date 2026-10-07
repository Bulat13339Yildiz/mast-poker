package app.mast.poker.practice

import app.mast.poker.content.Position
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandDescriber
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.HandValue
import app.mast.poker.core.poker.Outs
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.PushFold
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.PreflopEquity
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.Suit
import app.mast.poker.practice.Exercises.Companion.plural
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Builds explanations from the actual cards. Every number and card named in a
 * sentence is computed here, never typed by hand — so explanations cannot lie.
 */
object Explainer {

    fun cardsText(cards: Collection<Card>): String = cards.joinToString(" ")

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    private fun cardsWord(n: Int) = plural(n, "карта", "карты", "карт")

    private val ordinals = listOf("первая", "вторая", "третья", "четвёртая", "пятая")

    // --- Facts ---------------------------------------------------------------------

    data class Facts(
        val value: HandValue,
        val rankCounts: Map<Rank, Int>,
        val suitCounts: Map<Suit, Int>,
        /** Ranks that would complete a straight (one card away), if the hand has no straight. */
        val straightNeeds: List<Rank>,
        /** A 5-rank run that "wraps" through the ace (Q-K-A-2-3 etc.), which is not a straight. */
        val wrapRun: List<Rank>?,
        val longestRun: List<Rank>,
    ) {
        val maxSuit: Pair<Suit, Int> get() = suitCounts.maxBy { it.value }.toPair()
        val maxRank: Pair<Rank, Int> get() = rankCounts.entries.sortedWith(compareByDescending<Map.Entry<Rank, Int>> { it.value }.thenByDescending { it.key }).first().toPair()
        val pairs: List<Rank> get() = rankCounts.filter { it.value == 2 }.keys.sortedDescending()
        val trips: List<Rank> get() = rankCounts.filter { it.value == 3 }.keys.sortedDescending()
    }

    private val wheelWindow = listOf(Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE)
    private val straightWindows: List<List<Rank>> =
        listOf(wheelWindow) + (6..14).map { high -> (high - 4..high).map { Rank.of(it) } }
    private val wrapWindows: List<List<Rank>> = listOf(
        listOf(Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE, Rank.TWO),
        listOf(Rank.QUEEN, Rank.KING, Rank.ACE, Rank.TWO, Rank.THREE),
        listOf(Rank.KING, Rank.ACE, Rank.TWO, Rank.THREE, Rank.FOUR),
    )

    fun facts(all: List<Card>): Facts {
        val v = HandEvaluator.evaluate(all)
        val ranks = all.map { it.rank }.toSet()
        val needs = if (v.category >= HandCategory.STRAIGHT) emptyList() else
            straightWindows.mapNotNull { w -> (w - ranks).singleOrNull() }.distinct().sortedDescending()
        val wrap = if (v.category >= HandCategory.STRAIGHT) null else wrapWindows.firstOrNull { ranks.containsAll(it) }
        return Facts(
            value = v,
            rankCounts = all.groupingBy { it.rank }.eachCount(),
            suitCounts = Suit.entries.associateWith { s -> all.count { it.suit == s } },
            straightNeeds = needs,
            wrapRun = wrap,
            longestRun = longestRun(ranks),
        )
    }

    private fun longestRun(ranks: Set<Rank>): List<Rank> {
        val values = ranks.map { it.value }.toMutableSet()
        if (14 in values) values += 1
        var best = emptyList<Int>()
        for (start in values) {
            if (start - 1 in values) continue
            var end = start
            while (end + 1 in values) end++
            if (end - start + 1 > best.size) best = (start..end).toList()
        }
        return best.map { if (it == 1) Rank.ACE else Rank.of(it) }
    }

    private fun runText(run: List<Rank>) = run.joinToString("-") { it.label }

    // --- Hand anatomy -------------------------------------------------------------------

    /** What the best hand is and the traps around it. [hole] may be empty. */
    fun anatomy(hole: List<Card>, board: List<Card>): String {
        val all = hole + board
        val f = facts(all)
        val v = f.value
        val lines = mutableListOf("${cap(HandDescriber.describe(v))}: ${cardsText(v.cards)}.")

        val (suit, n) = f.maxSuit
        when {
            v.category < HandCategory.FLUSH && n == 4 ->
                lines += "Карт ${suit.ruGenitive} — четыре: до флеша не хватает одной."
            v.category == HandCategory.FLUSH && n > 5 ->
                lines += "Карт ${suit.ruGenitive} больше пяти — во флеш берут пять старших."
            v.category > HandCategory.FLUSH && n >= 5 && v.category != HandCategory.STRAIGHT_FLUSH && v.category != HandCategory.ROYAL_FLUSH ->
                lines += "Флеш здесь тоже есть, но ${v.category.short} сильнее."
        }
        if (v.category == HandCategory.FLUSH && f.straightNeeds.isEmpty() && hasStraight(all)) {
            lines += "Стрит здесь тоже есть, но флеш сильнее."
        }
        if (v.category < HandCategory.STRAIGHT) {
            f.wrapRun?.let { lines += "${runText(it)} — не стрит: ряд не продолжается через туза." }
            if (f.straightNeeds.isNotEmpty() && f.wrapRun == null) {
                lines += "До стрита не хватает одной карты: ранга ${f.straightNeeds.joinToString(" или ") { it.label }}."
            }
        }
        if ((v.category == HandCategory.STRAIGHT || v.category == HandCategory.STRAIGHT_FLUSH) && v.tiebreak.first() == 5) {
            lines += "Туз здесь играет как единица — это самый младший стрит."
        }
        if (v.category == HandCategory.TWO_PAIR && f.pairs.size >= 3) {
            lines += "Пар три, но в пятёрку берут две старшие. Пара ${f.pairs[2].ruGenPlural} не играет — вместо неё идёт старший кикер."
        }
        if (v.category == HandCategory.FULL_HOUSE && f.trips.size >= 2) {
            lines += "Две тройки: старшая играет тройкой, из младшей берут пару."
        }
        if (v.category == HandCategory.FULL_HOUSE && f.trips.size == 1 && f.pairs.size >= 2) {
            lines += "Пар две — к тройке берут старшую."
        }
        if (hole.isNotEmpty() && board.size == 5 && v.sameStrength(HandEvaluator.evaluate5(board))) {
            lines += "Все пять лучших карт лежат на столе — играет доска, твои карты не нужны."
        } else if (hole.isNotEmpty() && v.category == HandCategory.PAIR) {
            val pairRank = Rank.of(v.tiebreak[0])
            if (board.count { it.rank == pairRank } == 2) lines += "Эта пара лежит на столе — она общая для всех игроков."
        }
        return lines.joinToString("\n")
    }

    private fun hasStraight(cards: List<Card>): Boolean {
        val ranks = cards.map { it.rank }.toSet()
        return straightWindows.any { ranks.containsAll(it) }
    }

    /** Why the [chosen] category is wrong for these cards. */
    fun whyNot(chosen: HandCategory, hole: List<Card>, board: List<Card>): String {
        val all = hole + board
        val f = facts(all)
        val actual = f.value.category
        if (chosen == actual) return ""
        val real = HandDescriber.describe(f.value)
        if (chosen < actual) {
            return when {
                chosen == HandCategory.STRAIGHT_FLUSH && actual == HandCategory.ROYAL_FLUSH ->
                    "Стрит-флеш до туза называют отдельно — роял-флеш."
                chosen == HandCategory.STRAIGHT && actual == HandCategory.FLUSH && hasStraight(all) ->
                    "Стрит здесь есть, но пять карт одной масти — флеш, а он сильнее."
                chosen == HandCategory.FLUSH && actual > HandCategory.FLUSH && f.maxSuit.second >= 5 &&
                    actual != HandCategory.STRAIGHT_FLUSH && actual != HandCategory.ROYAL_FLUSH ->
                    "Флеш здесь есть, но выше по лестнице — $real."
                (chosen == HandCategory.STRAIGHT || chosen == HandCategory.FLUSH) && actual >= HandCategory.STRAIGHT_FLUSH ->
                    "Здесь сразу и стрит, и флеш из одних и тех же карт — это ${actual.short}."
                chosen == HandCategory.THREE_OF_A_KIND && actual == HandCategory.FULL_HOUSE ->
                    "Тройка есть, но к ней ещё и пара — это фулл-хаус: $real."
                chosen == HandCategory.PAIR && actual == HandCategory.TWO_PAIR ->
                    "Пар здесь две: ${f.pairs.take(2).joinToString(" и ") { it.ruPlural }}."
                else -> "Ты недооценил руку: здесь $real."
            }
        }
        return when (chosen) {
            HandCategory.ROYAL_FLUSH -> if (actual == HandCategory.STRAIGHT_FLUSH)
                "Роял — только стрит-флеш до туза. Здесь $real."
            else "Роял-флеш — это 10, J, Q, K, A одной масти. Здесь такого нет: $real."
            HandCategory.STRAIGHT_FLUSH -> when {
                actual == HandCategory.FLUSH -> "Пять карт одной масти есть, но они не идут подряд — это просто флеш."
                actual == HandCategory.STRAIGHT -> "Пять подряд есть, но разных мастей — это просто стрит."
                else -> "Стрит-флеш — пять карт подряд одной масти. Здесь такого нет: $real."
            }
            HandCategory.FOUR_OF_A_KIND -> {
                val (r, c) = f.maxRank
                if (c == 1) "Каре — четыре карты одного ранга. А здесь одинаковых карт нет вовсе."
                else "Каре — четыре карты одного ранга. Здесь одного ранга максимум ${countWord(c)} — ${r.ruPlural}."
            }
            HandCategory.FULL_HOUSE -> when {
                f.trips.isNotEmpty() -> "Тройка ${f.trips.first().ruGenPlural} есть, но второй пары к ней нет."
                f.pairs.size >= 2 -> "Фулл-хаусу нужна тройка, а здесь только пары. Это две пары."
                else -> "Фулл-хаус — это тройка плюс пара. Здесь $real."
            }
            HandCategory.FLUSH -> {
                val (s, c) = f.maxSuit
                "Флеш — пять карт одной масти. Здесь больше всего ${s.ruGenitive}, и их только ${countWord(c)}."
            }
            HandCategory.STRAIGHT -> {
                val wrap = f.wrapRun
                when {
                    wrap != null -> "${runText(wrap)} — не стрит: ряд не может продолжаться через туза."
                    f.longestRun.size >= 2 -> "Стрит — пять рангов подряд. Самый длинный ряд здесь — ${runText(f.longestRun)}, всего ${countWord(f.longestRun.size)}."
                    else -> "Стрит — пять рангов подряд. Здесь даже двух подряд нет."
                }
            }
            HandCategory.THREE_OF_A_KIND -> {
                val (r, c) = f.maxRank
                if (c >= 2) "Тройка — три карты одного ранга. Здесь одного ранга максимум ${countWord(c)} — ${r.ruPlural}."
                else "Тройка — три карты одного ранга. Здесь одинаковых карт нет."
            }
            HandCategory.TWO_PAIR -> if (f.pairs.size == 1) "Пара здесь только одна — ${f.pairs.first().ruPlural}." else "Пар здесь нет."
            HandCategory.PAIR -> "Карт одного ранга здесь нет — это старшая карта."
            HandCategory.HIGH_CARD -> "Здесь есть комбинация: $real."
        }
    }

    private fun countWord(n: Int) = when (n) {
        1 -> "одна"; 2 -> "две"; 3 -> "три"; 4 -> "четыре"; 5 -> "пять"; 6 -> "шесть"; 7 -> "семь"; else -> "$n"
    }

    // --- Showdown -------------------------------------------------------------------

    fun winner(board: List<Card>, hands: List<List<Card>>, labels: List<String> = listOf("Первый", "Второй")): String {
        val r = Showdown.resolve(board, hands)
        val lines = hands.indices.map { i -> "${labels[i]}: ${HandDescriber.describe(r.values[i])} — ${cardsText(r.values[i].cards)}." }.toMutableList()
        if (r.isSplit) {
            val boardValue = HandEvaluator.evaluate5(board)
            val boardPlays = r.values.all { it.sameStrength(boardValue) }
            lines += if (boardPlays) "Лучшие пять карт у обоих — общие, играет стол. Банк делится."
            else "Пятёрки совпадают по рангам, а масти не считаются. Банк делится."
            val unused = hands.flatten().filter { c -> r.values.none { c in it.cards } }
            if (unused.isNotEmpty()) lines += "Карты ${cardsText(unused)} в пятёрку не попали и ничего не решают."
            return lines.joinToString("\n")
        }
        val w = r.winners.first()
        val l = hands.indices.first { it != w }
        lines += reason(r.values[w], r.values[l]) + " Выигрывает ${labels[w].lowercase()}."
        return lines.joinToString("\n")
    }

    /** Why [win] beats [lose], naming exactly the ranks that decided it. */
    fun reason(win: HandValue, lose: HandValue): String {
        if (win.category != lose.category) {
            return "${win.category.ruName} выше на лестнице, чем ${lose.category.short}."
        }
        val i = win.tiebreak.indices.first { win.tiebreak[it] != lose.tiebreak[it] }
        val a = Rank.of(win.tiebreak[i])
        val b = Rank.of(lose.tiebreak[i])
        val first = Rank.of(win.tiebreak[0])
        return when (win.category) {
            HandCategory.PAIR -> if (i == 0) "Пара ${a.ruGenPlural} старше пары ${b.ruGenPlural}."
            else "Пары равны (${first.ruPlural}), решает кикер: ${a.ruName} против ${b.ruGenitive}."
            HandCategory.TWO_PAIR -> when (i) {
                0 -> "Старшая пара решает: ${a.ruPlural} против ${b.ruGenPlural}."
                1 -> "Старшие пары равны (${first.ruPlural}), решает вторая: ${a.ruPlural} против ${b.ruGenPlural}."
                else -> "Обе пары совпадают, решает кикер: ${a.ruName} против ${b.ruGenitive}."
            }
            HandCategory.THREE_OF_A_KIND -> if (i == 0) "Тройка ${a.ruGenPlural} старше тройки ${b.ruGenPlural}."
            else "Тройки равны, решает кикер: ${a.ruName} против ${b.ruGenitive}."
            HandCategory.STRAIGHT -> "Стрит до ${a.ruGenitive} старше стрита до ${b.ruGenitive}."
            HandCategory.STRAIGHT_FLUSH -> "Стрит-флеш до ${a.ruGenitive} старше стрит-флеша до ${b.ruGenitive}."
            HandCategory.FLUSH -> if (i == 0) "Флеш до ${a.ruGenitive} старше флеша до ${b.ruGenitive}."
            else "Старшие карты флешей равны, решает ${ordinals[i]}: ${a.ruName} против ${b.ruGenitive}."
            HandCategory.FULL_HOUSE -> if (i == 0) "У фулл-хауса сначала сравнивают тройку: тройка ${a.ruGenPlural} старше тройки ${b.ruGenPlural}."
            else "Тройки равны, решает пара: ${a.ruPlural} против ${b.ruGenPlural}."
            HandCategory.FOUR_OF_A_KIND -> if (i == 0) "Каре ${a.ruGenPlural} старше каре ${b.ruGenPlural}."
            else "Каре одинаковые, решает кикер: ${a.ruName} против ${b.ruGenitive}."
            HandCategory.HIGH_CARD -> if (i == 0) "Старшая карта решает: ${a.ruName} против ${b.ruGenitive}."
            else "Старшие карты равны, решает ${ordinals[i]}: ${a.ruName} против ${b.ruGenitive}."
            HandCategory.ROYAL_FLUSH -> "Роял-флеш не бывает двух разных."
        }
    }

    // --- Best five ------------------------------------------------------------------

    fun bestFive(hole: List<Card>, board: List<Card>, selected: Set<Card>? = null): String {
        val all = hole + board
        val best = HandEvaluator.evaluate(all)
        val lines = mutableListOf("Лучшая рука — ${HandDescriber.describe(best)}: ${cardsText(best.cards)}.")
        val alternatives = HandEvaluator.allBestFives(all)
        if (alternatives.size > 1) lines += "Подходит и другой набор с теми же рангами — масти тут не важны."
        if (selected != null && selected.size == 5 && !HandEvaluator.evaluate5(selected.toList()).sameStrength(best)) {
            val closest = alternatives.maxBy { alt -> alt.count { it in selected } }.toSet()
            lines += "Твой выбор даёт только ${HandDescriber.describe(HandEvaluator.evaluate5(selected.toList()))}."
            val extra = selected - closest
            val missing = closest - selected
            if (extra.isNotEmpty()) lines += "Лишние: ${cardsText(extra)}. Не хватает: ${cardsText(missing)}."
        }
        return lines.joinToString("\n")
    }

    // --- Outs -------------------------------------------------------------------------

    data class OutsGroup(val category: HandCategory, val cards: List<Card>)

    fun outsGroups(hole: List<Card>, board: List<Card>, target: HandCategory): List<OutsGroup> =
        Outs.toCategory(hole, board, target)
            .groupBy { HandEvaluator.evaluate(hole + board + it).category }
            .map { (c, cs) -> OutsGroup(c, cs.sortedWith(compareByDescending<Card> { it.rank }.thenBy { it.suit })) }
            .sortedByDescending { it.category }

    fun outs(hole: List<Card>, board: List<Card>, target: HandCategory): String {
        val groups = outsGroups(hole, board, target)
        val total = groups.sumOf { it.cards.size }
        val unseen = 52 - hole.size - board.size
        val lines = groups.map { g -> "${g.category.ruName}: ${g.cards.size} ${cardsWord(g.cards.size)} — ${cardsText(g.cards)}." }.toMutableList()
        val exact = (PotOdds.exactHitChance(total, unseen, 1) * 100).roundToInt()
        lines += "Итого $total ${plural(total, "аут", "аута", "аутов")} из $unseen неизвестных карт. " +
            "На следующей карте ≈ ${total * 2}% по правилу, точно — $exact%."
        return lines.joinToString("\n")
    }

    // --- Pot odds --------------------------------------------------------------------

    fun potOdds(pot: Int, call: Int, outs: Int, cardsToCome: Int): String {
        val need = PotOdds.requiredEquity(pot, call)
        val rule = PotOdds.ruleOfTwoAndFour(outs, cardsToCome)
        val exact = PotOdds.exactHitChance(outs, if (cardsToCome == 2) 47 else 46, cardsToCome)
        val verdict = if (exact >= need) "Шанс больше нужного — колл окупается на дистанции."
        else "Шанс меньше нужного — колл в среднем теряет фишки, фолд."
        return "Нужно: $call ÷ ($pot + $call) = ${pct(need)}.\n" +
            "Шанс: $outs × ${if (cardsToCome == 2) 4 else 2} ≈ $rule% (точно ${pct(exact)}).\n$verdict"
    }

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    // --- Preflop -----------------------------------------------------------------------

    /** The opening range for [seat], generated from the chart so it can never disagree with it. */
    fun seatRange(seat: SeatGroup): String {
        val added = PreflopChart.describe(PreflopChart.addedAt(seat))
        return when (seat) {
            SeatGroup.EARLY -> added
            SeatGroup.MIDDLE -> "всё из ранней позиции, плюс $added"
            SeatGroup.LATE -> "всё из средней позиции, плюс $added"
        }
    }

    fun preflop(hand: StartingHand, position: Position, seat: SeatGroup, shouldOpen: Boolean): String {
        val earliest = SeatGroup.entries.firstOrNull { PreflopChart.shouldOpen(hand, it) }
        val seatLine = when (position) {
            Position.SB, Position.BB -> "С блайнда после флопа ходишь первым, поэтому играем строго — как со средней позиции: ${seatRange(seat)}."
            else -> "${position.ruName} (${position.short}) — это ${seat.ruName.lowercase()}. Отсюда открываемся с такими руками: ${seatRange(seat)}."
        }
        val lines = mutableListOf("${hand.notation} — ${hand.ruDescription}.", seatLine)
        lines += when {
            shouldOpen -> "${hand.notation} входит в этот список — повышай."
            earliest != null -> "${hand.notation} сюда не входит — пас. Эту руку можно открывать только ${earliest.fromPhrase}."
            else -> "${hand.notation} слишком слаба для любой позиции — новичку её лучше всегда сбрасывать."
        }
        return lines.joinToString("\n")
    }

    // --- Equity ------------------------------------------------------------------------

    fun favourite(hands: List<List<Card>>, board: List<Card>, equity: Double): String {
        val p = (equity * 100).roundToInt()
        val lines = mutableListOf("Шансы: первая рука — $p%, вторая — ${100 - p}%.")
        if (board.isEmpty()) {
            preflopMatchup(hands[0], hands[1])?.let { lines += it }
        } else {
            val v1 = HandEvaluator.evaluate(hands[0] + board)
            val v2 = HandEvaluator.evaluate(hands[1] + board)
            lines += "Сейчас у первой — ${HandDescriber.describe(v1)}, у второй — ${HandDescriber.describe(v2)}."
            val trailing = if (equity > 0.5) 1 else 0
            val leading = 1 - trailing
            val unseen = Deck.without(hands.flatten() + board)
            val helps = unseen.count { c ->
                HandEvaluator.evaluate(hands[trailing] + board + c) > HandEvaluator.evaluate(hands[leading] + board + c)
            }
            val street = if (board.size == 3) "тёрне" else "ривере"
            lines += if (helps == 0) "Отстающей руке ни одна карта на $street не поможет выйти вперёд."
            else "Отстающую руку на $street выведут вперёд $helps ${cardsWord(helps)} из ${unseen.size}."
        }
        return lines.joinToString("\n")
    }

    /** Classes of preflop matchups whose typical odds are stated in explanations (verified by tests). */
    enum class Matchup(val text: String) {
        PAIR_VS_PAIR("Старшая пара против младшей — у неё примерно 4 шанса из 5."),
        PAIR_VS_OVERS("Пара против двух старших карт — почти спор: примерно 50 на 50."),
        PAIR_VS_ONE_OVER("Пара против одной старшей карты — у пары заметный перевес, примерно 2 к 1."),
        PAIR_VS_SAME("Пара против руки с картой того же ранга — у пары огромный перевес."),
        PAIR_VS_UNDERS("Пара против двух младших карт — у пары большой перевес."),
        DOMINATION("Это доминирование: у руки со старшим кикером обычно около 70%."),
        OVERS_VS_UNDERS("Две старшие карты против двух младших — у старших перевес, обычно примерно 60–70%."),
    }

    fun classify(a: List<Card>, b: List<Card>): Matchup? {
        val h1 = StartingHand.of(a[0], a[1])
        val h2 = StartingHand.of(b[0], b[1])
        fun pairVsCards(pair: StartingHand, other: StartingHand): Matchup {
            val over = listOf(other.high, other.low).count { it > pair.high }
            return when {
                over == 2 -> Matchup.PAIR_VS_OVERS
                over == 1 -> Matchup.PAIR_VS_ONE_OVER
                other.high == pair.high || other.low == pair.high -> Matchup.PAIR_VS_SAME
                else -> Matchup.PAIR_VS_UNDERS
            }
        }
        return when {
            h1.isPair && h2.isPair -> Matchup.PAIR_VS_PAIR
            h1.isPair -> pairVsCards(h1, h2)
            h2.isPair -> pairVsCards(h2, h1)
            setOf(h1.high, h1.low).intersect(setOf(h2.high, h2.low)).size == 1 -> {
                // Small kickers rarely play (the board counterfeits them), so only claim domination with real kickers.
                val kickers = (setOf(h1.high, h1.low) + setOf(h2.high, h2.low)) - setOf(h1.high, h1.low).intersect(setOf(h2.high, h2.low))
                if (kickers.all { it >= Rank.NINE }) Matchup.DOMINATION else null
            }
            minOf(h1.high, h1.low) > maxOf(h2.high, h2.low) || minOf(h2.high, h2.low) > maxOf(h1.high, h1.low) -> Matchup.OVERS_VS_UNDERS
            else -> null
        }
    }

    private fun preflopMatchup(a: List<Card>, b: List<Card>): String? {
        val m = classify(a, b) ?: return null
        if (m != Matchup.DOMINATION) return m.text
        val h1 = StartingHand.of(a[0], a[1])
        val h2 = StartingHand.of(b[0], b[1])
        val r = setOf(h1.high, h1.low).intersect(setOf(h2.high, h2.low)).first()
        val k1 = if (h1.high == r) h1.low else h1.high
        val k2 = if (h2.high == r) h2.low else h2.high
        return "Общая карта — ${r.ruName}. Решает вторая карта: ${k1.ruName} против ${k2.ruGenitive}. ${m.text}"
    }

    // --- Ranges ------------------------------------------------------------------------

    /** "12 рук — 64 комбинации, 4,8% всех раздач". */
    fun rangeSize(hands: Set<StartingHand>): String {
        val r = HandRange(hands)
        val n = hands.size
        return "$n ${plural(n, "рука", "руки", "рук")} — ${r.comboCount} ${plural(r.comboCount, "комбинация", "комбинации", "комбинаций")}, ${percent1(r.percent)} всех раздач"
    }

    fun range(title: String, target: Set<StartingHand>, note: String? = null): String {
        val lines = mutableListOf("$title: ${HandRange(target).describe()}.", cap(rangeSize(target)) + ".")
        note?.let { lines += it }
        return lines.joinToString("\n")
    }

    /** Which hands were missed or added by mistake; null when the grid matches exactly. */
    fun rangeDiff(target: Set<StartingHand>, selected: Set<StartingHand>): String? {
        val missed = target - selected
        val extra = selected - target
        if (missed.isEmpty() && extra.isEmpty()) return null
        fun list(hs: Set<StartingHand>): String {
            val sorted = hs.sortedByDescending { PreflopEquity.of(it) }
            val shown = sorted.take(12).joinToString(", ") { it.notation }
            return if (sorted.size > 12) "$shown и ещё ${sorted.size - 12}" else shown
        }
        val lines = mutableListOf<String>()
        if (missed.isNotEmpty()) lines += "Пропущены: ${list(missed)}."
        if (extra.isNotEmpty()) lines += "Лишние: ${list(extra)}."
        return lines.joinToString("\n")
    }

    // --- Push / fold --------------------------------------------------------------------

    fun pushFold(stackBb: Double, callPercent: Int, r: PushFold.Result): String {
        val s = stackBb
        val whenCalled = 2 * s * r.equityWhenCalled - s
        val combos = (r.callChance * 1225).roundToInt()
        val lines = mutableListOf(
            "Сбросит: ${percent1(1 - r.callChance)} — забираешь большой блайнд, +1 BB.",
            "Уравняет: ${percent1(r.callChance)} ($combos из 1225 комбинаций). Против топ-$callPercent% у тебя ${percent1(r.equityWhenCalled)} эквити: " +
                "банк ${bb(2 * s)} BB × ${percent1(r.equityWhenCalled)} − твои ${bb(s)} BB = ${signedBb(whenCalled)} BB.",
            "Олл-ин в среднем: ${percent1(1 - r.callChance)} × 1 + ${percent1(r.callChance)} × (${signedBb(whenCalled)}) = ${signedBb(r.evPush)} BB.",
            "Пас: −0,5 BB — малый блайнд уже в банке.",
        )
        lines += if (r.shouldPush) "Олл-ин выгоднее паса на ${bb(r.evPush - r.evFold)} BB — ставь всё."
        else "Пас выгоднее: олл-ин в среднем теряет на ${bb(r.evFold - r.evPush)} BB больше."
        return lines.joinToString("\n")
    }

    /** One decimal with a comma: 2,5. */
    fun bb(x: Double): String = String.format(Locale.ROOT, "%.1f", abs(x)).replace('.', ',').removeSuffix(",0")

    fun signedBb(x: Double): String {
        val t = bb(x)
        return when {
            t == "0" -> "0"
            x > 0 -> "+$t"
            else -> "−$t"
        }
    }

    private fun percent1(x: Double): String {
        val t = String.format(Locale.ROOT, "%.1f", x * 100).replace('.', ',').removeSuffix(",0")
        return "$t%"
    }
}
