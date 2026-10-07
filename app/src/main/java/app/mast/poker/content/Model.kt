package app.mast.poker.content

import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.HandEvaluator
import app.mast.poker.core.poker.Outs
import app.mast.poker.core.poker.PotOdds
import app.mast.poker.core.poker.Showdown
import app.mast.poker.core.poker.Suit

data class Chapter(
    val id: String,
    val number: Int,
    val title: String,
    val subtitle: String,
    val suit: Suit,
    val lessons: List<Lesson>,
)

data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val minutes: Int,
    val steps: List<Step>,
    val unlocksDrill: DrillType? = null,
) {
    val questionCount: Int get() = steps.count { it is Step.Ask }
}

sealed interface Step {
    /** Explanation screen. [body] supports **bold** spans and blank-line paragraphs. */
    data class Theory(val title: String, val body: String, val visual: Visual? = null) : Step

    /** Short highlighted rule to remember. */
    data class Remember(val text: String, val visual: Visual? = null) : Step

    data class Ask(val question: Question) : Step
}

sealed interface Visual {
    data class Cards(val cards: List<Card>, val highlight: Set<Card> = emptySet(), val caption: String? = null) : Visual
    data class Hand(val hole: List<Card>, val board: List<Card>, val showBest: Boolean = false, val caption: String? = null) : Visual
    data class Duel(val board: List<Card>, val hands: List<List<Card>>, val reveal: Boolean = true) : Visual
    data object RankingLadder : Visual
    data object DeckGrid : Visual
    data class Table(val highlight: Position? = null, val showBlinds: Boolean = true) : Visual
    data class Streets(val active: Street) : Visual
    data class PotMath(val pot: Int, val call: Int) : Visual
    data object StartingHandsChart : Visual
    /** Two hands' equity recalculated street by street over a full [board]. */
    data class EquityStreets(val hero: List<Card>, val villain: List<Card>, val board: List<Card>) : Visual
}

enum class Street(val ruName: String, val boardCards: Int) {
    PREFLOP("Префлоп", 0), FLOP("Флоп", 3), TURN("Тёрн", 4), RIVER("Ривер", 5),
}

enum class Position(val short: String, val ruName: String, val description: String) {
    SB("SB", "Малый блайнд", "Ставит обязательную половину ставки ещё до раздачи"),
    BB("BB", "Большой блайнд", "Ставит обязательную полную ставку ещё до раздачи"),
    UTG("UTG", "Первая позиция", "Ходит первым на префлопе, хуже всех информирован"),
    MP("MP", "Средняя позиция", "Между ранними и поздними местами"),
    CO("CO", "Катофф", "Место перед баттоном — уже поздняя позиция"),
    BTN("BTN", "Баттон", "Дилер. Ходит последним после флопа — лучшее место за столом"),
}

sealed interface Question {
    val prompt: String
    val explanation: String
    val concept: Concept

    /** Plain multiple choice. */
    data class Choice(
        override val prompt: String,
        val options: List<String>,
        val correct: Int,
        override val explanation: String,
        override val concept: Concept,
        val visual: Visual? = null,
    ) : Question

    /** Name the best combination in [cards] (5..7). Answer comes from the evaluator. */
    data class NameHand(
        override val prompt: String,
        val hole: List<Card>,
        val board: List<Card>,
        val options: List<HandCategory>,
        override val explanation: String,
        override val concept: Concept = Concept.HAND_RANKINGS,
    ) : Question {
        val answer: HandCategory get() = HandEvaluator.evaluate(hole + board).category
    }

    /** Who wins at showdown — one of the hands or a split. */
    data class PickWinner(
        override val prompt: String,
        val board: List<Card>,
        val hands: List<List<Card>>,
        override val explanation: String,
        override val concept: Concept = Concept.SHOWDOWN,
    ) : Question {
        val winners: List<Int> get() = Showdown.resolve(board, hands).winners
    }

    /** Tap the 5 cards that make the best hand out of 7. */
    data class PickBestFive(
        override val prompt: String,
        val hole: List<Card>,
        val board: List<Card>,
        override val explanation: String,
        override val concept: Concept = Concept.BEST_FIVE,
    ) : Question {
        fun isCorrect(selection: Set<Card>): Boolean =
            selection.size == 5 &&
                HandEvaluator.evaluate5(selection.toList()).sameStrength(HandEvaluator.evaluate(hole + board))
    }

    /** Put categories from weakest to strongest. */
    data class OrderHands(
        override val prompt: String,
        val categories: List<HandCategory>,
        override val explanation: String,
        override val concept: Concept = Concept.HAND_RANKINGS,
    ) : Question {
        val answer: List<HandCategory> get() = categories.sorted()
    }

    /** How many outs to reach [target] or better. */
    data class CountOuts(
        override val prompt: String,
        val hole: List<Card>,
        val board: List<Card>,
        val target: HandCategory,
        val options: List<Int>,
        override val explanation: String,
        override val concept: Concept = Concept.OUTS,
    ) : Question {
        val answer: Int get() = Outs.toCategory(hole, board, target).size
    }

    /** Call or fold a draw using pot odds. */
    data class CallOrFold(
        override val prompt: String,
        val pot: Int,
        val call: Int,
        val outs: Int,
        val cardsToCome: Int,
        override val explanation: String,
        override val concept: Concept = Concept.POT_ODDS,
    ) : Question {
        /** Exact chance to hit: 47 unseen cards on the flop, 46 on the turn. */
        val exactChance: Double get() = PotOdds.exactHitChance(outs, if (cardsToCome == 2) 47 else 46, cardsToCome)
        val shouldCall: Boolean get() = exactChance >= PotOdds.requiredEquity(pot, call)
    }

    /** Everyone folded to you: open-raise or fold by the beginner chart. */
    data class OpenOrFold(
        override val prompt: String,
        val hole: List<Card>,
        val position: Position,
        val shouldOpen: Boolean,
        override val explanation: String,
        override val concept: Concept = Concept.STARTING_HANDS,
    ) : Question

    /** Which of two hands is the favourite; [equity] is the first hand's share. */
    data class Favourite(
        override val prompt: String,
        val hands: List<List<Card>>,
        val board: List<Card>,
        val equity: Double,
        override val explanation: String,
        override val concept: Concept = Concept.EQUITY,
    ) : Question {
        val answer: Int get() = if (equity > 0.5) 0 else 1
    }
}

/** What a question trains — used for stats and the mistakes queue. */
enum class Concept(val ruName: String) {
    CARDS_BASICS("Карты и колода"),
    GAME_RULES("Правила холдема"),
    HAND_RANKINGS("Комбинации"),
    KICKERS("Кикеры"),
    BEST_FIVE("Лучшие пять карт"),
    SHOWDOWN("Вскрытие"),
    BETTING("Ставки и действия"),
    POSITIONS("Позиции"),
    STARTING_HANDS("Стартовые руки"),
    OUTS("Ауты"),
    POT_ODDS("Шансы банка"),
    EQUITY("Кто фаворит"),
    STRATEGY("Стратегия"),
    MINDSET("Банкролл и тилт"),
}

enum class DrillType(val title: String, val subtitle: String, val concept: Concept) {
    NAME_HAND("Что за комбинация?", "Узнай руку с первого взгляда", Concept.HAND_RANKINGS),
    WINNER("Кто выиграл?", "Сравни две руки на одном столе", Concept.SHOWDOWN),
    BEST_FIVE("Собери руку", "Выбери 5 лучших карт из 7", Concept.BEST_FIVE),
    OUTS("Ауты", "Сколько карт тебя спасут", Concept.OUTS),
    POT_ODDS("Шансы банка", "Колл или фолд — посчитай", Concept.POT_ODDS),
    PREFLOP("Префлоп", "Играть руку или сбросить", Concept.STARTING_HANDS),
    EQUITY("Фаворит?", "У кого больше шансов на победу", Concept.EQUITY),
    HAND_SIM("Сыграй раздачу", "Полная раздача против соперника", Concept.STRATEGY),
}
