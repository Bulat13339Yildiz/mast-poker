package app.mast.poker.practice

import app.mast.poker.core.poker.BluffMath
import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.Combos
import app.mast.poker.core.poker.Deck
import app.mast.poker.core.poker.HandClasses
import app.mast.poker.core.poker.HandRange
import app.mast.poker.core.poker.PreflopChart
import app.mast.poker.core.poker.PushFold
import app.mast.poker.core.poker.Rank
import app.mast.poker.core.poker.SeatGroup
import app.mast.poker.core.poker.StartingHand
import app.mast.poker.core.poker.Suit
import app.mast.poker.practice.Exercises.Companion.plural
import app.mast.poker.practice.sim.HandRead
import app.mast.poker.practice.sim.HandReader
import app.mast.poker.practice.sim.Strength
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

enum class BetStreet(val ruName: String, val boardCards: Int) { FLOP("флоп", 3), TURN("тёрн", 4), RIVER("ривер", 5) }

enum class Sizing(val ruName: String) {
    CHECK("Чек"),
    THIRD("Треть банка"),
    TWO_THIRDS("Две трети банка"),
    POT("Весь банк"),
}

/**
 * The bet-sizing plan taught in the «Размер ставок» chapter. Hero raised preflop, got
 * called, and the opponent checks to them. Board texture matters only on the flop.
 */
object SizingRules {
    fun best(street: BetStreet, read: HandRead, wet: Boolean): Sizing = when (read.strength) {
        Strength.MEDIUM -> Sizing.CHECK
        Strength.WEAK -> if (street == BetStreet.FLOP && !wet) Sizing.THIRD else Sizing.CHECK
        Strength.DRAW -> Sizing.TWO_THIRDS
        Strength.STRONG -> when {
            street == BetStreet.RIVER && read.monster -> Sizing.POT
            street == BetStreet.FLOP && !wet -> Sizing.THIRD
            else -> Sizing.TWO_THIRDS
        }
    }

    fun reason(street: BetStreet, read: HandRead, wet: Boolean): String = when (read.strength) {
        Strength.MEDIUM -> if (street == BetStreet.RIVER)
            "Средняя рука на ривере — чек. На ставку заплатят в основном руки сильнее, а слабые просто сбросят."
        else "Средняя рука — чек. Она часто лучше, чем у соперника, но на ставку заплатят в основном руки сильнее, а слабые сбросят. Чек держит банк маленьким."
        Strength.WEAK -> when (street) {
            BetStreet.FLOP -> if (wet)
                "Мокрый флоп: у соперника часто есть пара или дро, и на блеф он ответит коллом. Без пары и без дро — чек."
            else "Сухой флоп: в такой борд соперник попадает редко. Ты повышал до флопа — маленькая продолженная ставка (треть банка) часто заберёт банк сразу."
            BetStreet.TURN -> "Без пары и без дро на тёрне — чек. Соперник уже дошёл до тёрна, и блеф против него работает редко."
            BetStreet.RIVER -> "Без пары на ривере — чек. Блеф против соперника, который дошёл до ривера, работает редко."
        }
        Strength.DRAW -> "Дро — ставь две трети банка (полублеф). Соперник может сбросить сразу, а если уравняет, у тебя остаются ауты."
        Strength.STRONG -> when {
            street == BetStreet.RIVER && read.monster ->
                "Очень сильная рука на ривере — ставь весь банк. Это последняя ставка в раздаче: бери максимум."
            street == BetStreet.RIVER ->
                "Сильная рука на ривере — две трети банка: заплатят руки слабее, а ставка в весь банк их отпугнула бы."
            street == BetStreet.FLOP && !wet ->
                "Сильная рука на сухом флопе — хватит трети банка. Опасных дро почти нет, а маленькую ставку охотнее уравняют руки слабее."
            street == BetStreet.FLOP ->
                "Сильная рука на мокром флопе — две трети банка: дро должны платить дорого за попытку собраться."
            else -> "Сильная рука — две трети банка: руки слабее заплатят, а дро не получат дешёвую карту."
        }
    }
}

/** Tasks for the second-level drills. */
sealed interface AdvancedTask : DrillTask {
    data class RangeTask(val title: String, val target: Set<StartingHand>, val note: String) : AdvancedTask

    data class PushFoldTask(val hole: List<Card>, val stackBb: Double, val callPercent: Int, val result: PushFold.Result) : AdvancedTask

    data class CombosTask(
        val prompt: String,
        val hole: List<Card>,
        val board: List<Card>,
        val answer: Int,
        val options: List<Int>,
        val explanation: String,
        /** Cards that illustrate a question with no table, e.g. all four kings. */
        val example: List<Card> = emptyList(),
        val exampleCaption: String? = null,
    ) : AdvancedTask

    data class SizingTask(
        val hole: List<Card>,
        val board: List<Card>,
        val street: BetStreet,
        val read: HandRead,
        val wet: Boolean,
        val pot: Int,
    ) : AdvancedTask {
        val answer: Sizing get() = SizingRules.best(street, read, wet)
    }

    data class BluffMathTask(val prompt: String, val answer: String, val options: List<String>, val explanation: String) : AdvancedTask
}

class AdvancedDrills(private val random: Random) {

    // --- Ranges -----------------------------------------------------------------------

    fun range(level: Int): AdvancedTask.RangeTask {
        val chartNote = "Это диапазон из таблицы новичка — глава «Стартовые руки»."
        val topNote = "Топ — по силе против случайной руки."
        val options = when (level) {
            1 -> listOf(
                Triple("3-бет в ответ на рейз", HandRange.parse("QQ+, AK").hands, "С этими руками отвечай на рейз повторным рейзом."),
                Triple("Открытие с ранней позиции", chart(SeatGroup.EARLY), chartNote),
                Triple("Топ-5% рук", HandRange.top(0.05).hands, topNote),
            )
            2 -> listOf(
                Triple("Открытие со средней позиции", chart(SeatGroup.MIDDLE), chartNote),
                Triple("Топ-10% рук", HandRange.top(0.10).hands, topNote),
            )
            else -> listOf(
                Triple("Открытие с поздней позиции", chart(SeatGroup.LATE), chartNote),
                Triple("Топ-15% рук", HandRange.top(0.15).hands, topNote),
                Triple("Топ-20% рук", HandRange.top(0.20).hands, topNote),
            )
        }
        val (title, target, note) = options.random(random)
        return AdvancedTask.RangeTask(title, target, note)
    }

    private fun chart(seat: SeatGroup) = HandClasses.all.filter { PreflopChart.shouldOpen(it, seat) }.toSet()

    // --- Push / fold ------------------------------------------------------------------

    /**
     * Small blind against the big blind's calling range. Levels are margin bands: level 1
     * spots differ by a big blind or more, level 3 ones by a fraction of it. Every answer
     * is at least three standard errors away from the break-even point.
     */
    fun pushFold(level: Int): AdvancedTask.PushFoldTask {
        val band = when (level) { 1 -> 1.0..99.0; 2 -> 0.4..1.0; else -> 0.15..0.4 }
        val wantPush = random.nextBoolean()
        repeat(120) { attempt ->
            val stack = (5..15).random(random).toDouble()
            val callPercent = listOf(10, 15, 20, 30, 40, 50).random(random)
            val deck = Deck.shuffled(random)
            val hole = listOf(deck.removeLast(), deck.removeLast())
            val range = HandRange.top(callPercent / 100.0)
            var r = PushFold.evaluate(hole, stack, range, samples = 6000, random = Random(random.nextInt()))
            if (attempt < 80 && r.shouldPush != wantPush) return@repeat
            if (r.margin !in band) return@repeat
            if (r.margin < 3 * error(r, stack, 6000)) {
                r = PushFold.evaluate(hole, stack, range, samples = 24000, random = Random(random.nextInt()))
                if (r.margin !in band || r.margin < 3 * error(r, stack, 24000)) return@repeat
            }
            return AdvancedTask.PushFoldTask(hole, stack, callPercent, r)
        }
        val hole = app.mast.poker.core.poker.cards("Ah Kd")
        return AdvancedTask.PushFoldTask(hole, 10.0, 30, PushFold.evaluate(hole, 10.0, HandRange.top(0.30)))
    }

    /** Standard error of the push EV that comes from the simulated equity. */
    private fun error(r: PushFold.Result, stack: Double, samples: Int): Double {
        val eq = r.equityWhenCalled
        return r.callChance * 2 * stack * sqrt(eq * (1 - eq) / samples)
    }

    // --- Combinatorics ----------------------------------------------------------------

    fun combos(level: Int): AdvancedTask.CombosTask = when (level) {
        1 -> plainCombos()
        2 -> blockedCombos()
        else -> setCombos()
    }

    private fun plainCombos(): AdvancedTask.CombosTask {
        val (a, b) = Rank.entries.shuffled(random).take(2).sortedDescending()
        val (prompt, hand, why) = when (random.nextInt(4)) {
            0 -> Triple(
                "Сколько комбинаций у карманной пары ${a.code}${a.code}?",
                "${a.code}${a.code}",
                "У ранга 4 масти. Пару из четырёх карт можно собрать 6 способами: 4 × 3 ÷ 2 = 6.",
            )
            1 -> Triple(
                "Сколько комбинаций у ${a.code}${b.code}s — одной масти?",
                "${a.code}${b.code}s",
                "Одна масть на обе карты — по варианту на каждую из 4 мастей: 4 комбинации.",
            )
            2 -> Triple(
                "Сколько комбинаций у ${a.code}${b.code}o — разных мастей?",
                "${a.code}${b.code}o",
                "Всего 4 × 4 = 16 сочетаний мастей, из них 4 одномастных. Остаётся 12 разномастных.",
            )
            else -> Triple(
                "Сколько комбинаций у ${a.code}${b.code} с любыми мастями?",
                "${a.code}${b.code}",
                "4 масти у первой карты × 4 у второй = 16: 4 одномастных и 12 разномастных.",
            )
        }
        val answer = HandRange.parse(hand).comboCount
        val (example, caption) = when {
            hand.length == 2 && hand[0] == hand[1] -> Suit.entries.map { Card(a, it) } to "Все четыре карты этого ранга"
            hand.endsWith("s") -> listOf(Card(a, Suit.SPADES), Card(b, Suit.SPADES)) to "Одна из комбинаций"
            hand.endsWith("o") -> listOf(Card(a, Suit.SPADES), Card(b, Suit.HEARTS)) to "Одна из комбинаций"
            else -> emptyList<Card>() to null
        }
        return AdvancedTask.CombosTask(prompt, emptyList(), emptyList(), answer, options(answer), why, example, caption)
    }

    private fun blockedCombos(): AdvancedTask.CombosTask {
        val deck = Deck.shuffled(random)
        val hole = listOf(deck.removeLast(), deck.removeLast()).sortedByDescending { it.rank }
        if (hole[0].rank == hole[1].rank) return blockedCombos()
        val (a, b) = hole.map { it.rank }
        return if (random.nextBoolean()) {
            val r = if (random.nextBoolean()) a else b
            val answer = Combos.of(StartingHand(r, r, false), hole)
            AdvancedTask.CombosTask(
                "У тебя на руках эти карты. Сколько комбинаций ${r.code}${r.code} может быть у соперника?",
                hole, emptyList(), answer, options(answer),
                "Без твоих карт у пары 6 комбинаций. Но одна карта этого ранга у тебя — осталось 3. Из трёх карт пару можно собрать 3 способами: 3 × 2 ÷ 2 = 3.",
            )
        } else {
            val answer = HandRange.parse("${a.code}${b.code}").combos(hole).size
            AdvancedTask.CombosTask(
                "У тебя на руках эти карты. Сколько комбинаций ${a.code}${b.code} (любые масти) может быть у соперника?",
                hole, emptyList(), answer, options(answer),
                "Обычно у ${a.code}${b.code} 16 комбинаций: 4 × 4. Но по одной карте каждого ранга у тебя — осталось по 3: 3 × 3 = $answer.",
            )
        }
    }

    private fun setCombos(): AdvancedTask.CombosTask {
        val deck = Deck.shuffled(random)
        val kind = random.nextInt(4)
        val size = if (kind == 2) 4 else 3
        val board = List(size) { deck.removeLast() }
        val distinct = board.map { it.rank }.distinct().size
        val shapeOk = if (kind == 3) distinct == 2 else distinct == size
        if (!shapeOk) return setCombos()
        val hole = if (kind == 1) {
            val r = board.random(random).rank
            val mine = Suit.entries.map { Card(r, it) }.first { it !in board }
            listOf(mine, deck.first { c -> c.rank !in board.map { it.rank } })
        } else emptyList()
        val answer = Combos.sets(board, hole)
        val seen = (board + hole).toSet()
        val parts = board.groupingBy { it.rank }.eachCount().toList().sortedByDescending { it.first }.map { (r, n) ->
            if (n > 1) "${r.label} на столе дважды — пара ${r.ruGenPlural} в руке дала бы каре, а не сет"
            else {
                val left = Suit.entries.count { Card(r, it) !in seen }
                val c = left * (left - 1) / 2
                "${r.label}: осталось $left ${plural(left, "карта", "карты", "карт")} → $c ${plural(c, "комбинация", "комбинации", "комбинаций")}"
            }
        }
        val why = "Сет — карманная пара плюс такая же карта на столе.\n" + parts.joinToString(";\n") + ".\nИтого $answer."
        val prompt = when (kind) {
            1 -> "У тебя одна из карт стола. Сколько комбинаций сета может быть у соперника?"
            2 -> "Сколько комбинаций сета может быть у соперника на этом тёрне?"
            else -> "Сколько комбинаций сета может быть у соперника на этом флопе?"
        }
        return AdvancedTask.CombosTask(prompt, hole, board, answer, options(answer), why)
    }

    private fun options(answer: Int): List<Int> {
        val pool = listOf(1, 3, 4, 6, 7, 8, 9, 10, 12, 16) + listOf(answer - 3, answer + 3, answer * 2)
        val wrong = pool.filter { it > 0 && it != answer }.distinct().shuffled(random).take(3)
        return (wrong + answer).sorted()
    }

    // --- Bet sizing -------------------------------------------------------------------

    fun sizing(level: Int): AdvancedTask.SizingTask {
        val streets = when (level) { 1 -> listOf(BetStreet.FLOP); 2 -> listOf(BetStreet.FLOP, BetStreet.TURN); else -> BetStreet.entries.toList() }
        val street = streets.random(random)
        val buckets = if (street == BetStreet.RIVER) listOf("monster", "strong", "medium", "weak") else listOf("monster", "strong", "draw", "medium", "weak")
        val wanted = buckets.random(random)
        val pot = listOf(60, 90, 120, 150, 240).random(random)
        var fallback: AdvancedTask.SizingTask? = null
        repeat(3000) {
            val deck = Deck.shuffled(random)
            val hole = listOf(deck.removeLast(), deck.removeLast())
            val board = List(street.boardCards) { deck.removeLast() }
            if (!clearTexture(board, street)) return@repeat
            val read = HandReader.read(hole, board)
            // A pair with a draw is neither a plain made hand nor a pure draw — skip it.
            if (read.strength == Strength.MEDIUM && read.label.contains(" + ")) return@repeat
            val task = AdvancedTask.SizingTask(hole, board, street, read, HandReader.isWet(board), pot)
            if (fallback == null) fallback = task
            val bucket = when {
                read.monster -> "monster"
                read.strength == Strength.STRONG -> "strong"
                read.strength == Strength.DRAW -> "draw"
                read.strength == Strength.MEDIUM -> "medium"
                else -> "weak"
            }
            if (bucket == wanted) return task
        }
        return fallback ?: sizing(level)
    }

    /**
     * Boards whose texture the simple plan reads correctly: a flop is either wet or a
     * rainbow dry one; later streets have no flush or one-card straight on board.
     */
    private fun clearTexture(board: List<Card>, street: BetStreet): Boolean {
        val suitMax = board.groupingBy { it.suit }.eachCount().values.max()
        if (board.groupingBy { it.rank }.eachCount().values.max() >= 3) return false
        if (street == BetStreet.FLOP) return HandReader.isWet(board) || suitMax == 1
        if (suitMax >= 3) return false
        val values = board.map { it.rank.value }.toMutableSet()
        if (14 in values) values += 1
        return (1..10).none { low -> (low..low + 4).count { it in values } >= 4 }
    }

    // --- Bluff maths ------------------------------------------------------------------

    fun bluffMath(level: Int): AdvancedTask.BluffMathTask = when (level) {
        1 -> breakEven()
        2 -> mdf()
        else -> bluffEv()
    }

    private fun potAndBet(shares: List<Pair<Int, Int>>): Pair<Int, Int> {
        val pot = listOf(60, 90, 120, 150, 180, 300).random(random)
        val (num, den) = shares.random(random)
        return pot to pot * num / den
    }

    private fun breakEven(): AdvancedTask.BluffMathTask {
        val (pot, bet) = potAndBet(listOf(1 to 3, 1 to 2, 2 to 3, 1 to 1))
        val need = BluffMath.breakEvenFold(pot, bet)
        return AdvancedTask.BluffMathTask(
            "В банке $pot. Ты блефуешь ставкой $bet. Как часто соперник должен сбрасывать, чтобы блеф окупился?",
            pct(need), percentOptions(need),
            "Блеф рискует $bet, чтобы выиграть $pot. Нужно: $bet ÷ ($pot + $bet) = ${pct(need)}. Сбрасывает чаще — блеф в плюсе.",
        )
    }

    private fun mdf(): AdvancedTask.BluffMathTask {
        val (pot, bet) = potAndBet(listOf(1 to 3, 1 to 2, 2 to 3, 1 to 1, 3 to 2))
        val mdf = BluffMath.mdf(pot, bet)
        return AdvancedTask.BluffMathTask(
            "В банке $pot, соперник ставит $bet. Какую долю своих рук нужно продолжать (колл или рейз), чтобы блеф с любыми картами не приносил ему прибыли?",
            pct(mdf), percentOptions(mdf),
            "Это защита по MDF: банк ÷ (банк + ставка) = $pot ÷ ($pot + $bet) = ${pct(mdf)}. " +
                "Если сбрасывать чаще, соперник заработает, блефуя с любыми двумя картами.",
        )
    }

    private fun bluffEv(): AdvancedTask.BluffMathTask {
        val pot = listOf(100, 200, 300).random(random)
        val bet = pot * listOf(1, 2).random(random) / 2
        val foldPct = listOf(30, 40, 50, 60).random(random)
        // Pot and bet are multiples of 10, so every product below is a whole number.
        val win = foldPct * pot / 100
        val loss = (100 - foldPct) * bet / 100
        val ev = win - loss
        val answer = signed(ev)
        // Typical slips first (sign flipped, only the win, only the loss), then near misses.
        val slips = listOf(-ev, win, -loss).filter { it != ev }.distinct().shuffled(random)
        val near = listOf(ev + bet / 2, ev - pot / 2, ev + 10, ev - 10, ev + 20, ev - 20).filter { it != ev && it !in slips }.distinct()
        val wrong = (slips + near).take(3).map(::signed)
        return AdvancedTask.BluffMathTask(
            "Банк $pot, ты блефуешь ставкой $bet. Соперник сбрасывает в $foldPct% случаев. Сколько в среднем приносит этот блеф?",
            answer, (wrong + answer).shuffled(random),
            "EV = доля фолдов × банк − доля коллов × ставка = $foldPct% × $pot − ${100 - foldPct}% × $bet = $win − $loss = $answer. " +
                when {
                    ev > 0 -> "Блеф в плюсе."
                    ev < 0 -> "Блеф в минусе — здесь лучше чек."
                    else -> "Блеф в ноль."
                },
        )
    }

    private fun pct(x: Double) = "${(x * 100).roundToInt()}%"

    private fun signed(x: Int): String = when {
        x > 0 -> "+$x"
        x < 0 -> "−${-x}"
        else -> "0"
    }

    private fun percentOptions(x: Double): List<String> {
        val v = (x * 100).roundToInt()
        val candidates = listOf(100 - v, v - 15, v - 8, v + 8, v + 15, v / 2, v + 22, v - 22)
            .filter { it in 5..95 && abs(it - v) >= 5 }.distinct()
        // The mirror answer (MDF ↔ break-even) is the classic mix-up — keep it when it differs.
        val mirror = candidates.firstOrNull { it == 100 - v }
        val rest = (candidates - setOfNotNull(mirror)).shuffled(random)
        val wrong = (listOfNotNull(mirror) + rest).take(3)
        return (wrong + v).sorted().map { "$it%" }
    }
}
