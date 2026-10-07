package app.mast.poker.practice

import app.mast.poker.content.Concept
import app.mast.poker.content.Curriculum
import app.mast.poker.content.DrillType
import app.mast.poker.content.Question
import app.mast.poker.content.Visual
import app.mast.poker.core.poker.HandCategory
import kotlin.random.Random

/** Turns generated drill tasks into regular [Question]s with engine-written explanations. */
class Exercises(private val generator: DrillGenerator = DrillGenerator(), private val random: Random = Random.Default) {

    fun next(type: DrillType): Question = toQuestion(generator.next(type))

    fun next(type: DrillType, level: Int): Question = toQuestion(generator.next(type, level))

    fun toQuestion(task: DrillTask): Question = when (task) {
        is DrillTask.NameHand -> Question.NameHand(
            prompt = if (task.hole.isEmpty()) "Что за комбинация?" else "Какая лучшая комбинация из семи карт?",
            hole = task.hole,
            board = task.board,
            options = task.options,
            explanation = Explainer.anatomy(task.hole, task.board),
        )
        is DrillTask.Winner -> Question.PickWinner(
            prompt = "Кто выиграл?",
            board = task.board,
            hands = task.hands,
            explanation = Explainer.winner(task.board, task.hands, listOf("Игрок 1", "Игрок 2")),
        )
        is DrillTask.BestFive -> Question.PickBestFive(
            prompt = "Отметь пять карт лучшей руки",
            hole = task.hole,
            board = task.board,
            explanation = Explainer.bestFive(task.hole, task.board),
        )
        is DrillTask.OutsTask -> Question.CountOuts(
            prompt = "Сколько аутов до ${task.target.genitive()} или сильнее?",
            hole = task.hole,
            board = task.board,
            target = task.target,
            options = task.options,
            explanation = "У тебя ${task.draw.lowercase()}.\n" + Explainer.outs(task.hole, task.board, task.target),
        )
        is DrillTask.PotOddsTask -> Question.CallOrFold(
            prompt = "В банке ${task.potBeforeBet}, соперник ставит ${task.bet}. У тебя ${task.outs} ${plural(task.outs, "аут", "аута", "аутов")}, " +
                if (task.cardsToCome == 2) "и соперник пошёл олл-ин — увидишь обе оставшиеся карты." else "впереди одна карта.",
            pot = task.pot,
            call = task.bet,
            outs = task.outs,
            cardsToCome = task.cardsToCome,
            explanation = Explainer.potOdds(task.pot, task.bet, task.outs, task.cardsToCome),
        )
        is DrillTask.PreflopTask -> Question.OpenOrFold(
            prompt = "Все сбросили до тебя. Ты — ${task.position.ruName.lowercase()} (${task.position.short}).",
            hole = task.hole,
            position = task.position,
            shouldOpen = task.shouldOpen,
            explanation = Explainer.preflop(task.hand, task.position, task.position.seatGroup, task.shouldOpen),
        )
        is DrillTask.EquityTask -> {
            val street = when (task.board.size) { 0 -> "до флопа"; 3 -> "на флопе"; else -> "на тёрне" }
            Question.Favourite(
                prompt = "Чья рука фаворит $street?",
                hands = task.hands,
                board = task.board,
                equity = task.equity,
                explanation = Explainer.favourite(task.hands, task.board, task.equity),
            )
        }
        is AdvancedTask.RangeTask -> Question.BuildRange(
            prompt = "Отметь в таблице: ${task.title.replaceFirstChar { it.lowercase() }}",
            target = task.target,
            explanation = Explainer.range(task.title, task.target, task.note),
        )
        is AdvancedTask.PushFoldTask -> Question.PushOrFold(
            prompt = "Турнир. Все сбросили до тебя, ты на малом блайнде. Олл-ин или пас?",
            hole = task.hole,
            stackBb = task.stackBb,
            callPercent = task.callPercent,
            callChance = task.result.callChance,
            equityWhenCalled = task.result.equityWhenCalled,
            evPush = task.result.evPush,
            explanation = Explainer.pushFold(task.stackBb, task.callPercent, task.result),
        )
        is AdvancedTask.CombosTask -> Question.Choice(
            prompt = task.prompt,
            options = task.options.map { "$it" },
            correct = task.options.indexOf(task.answer),
            explanation = task.explanation,
            concept = Concept.COMBINATORICS,
            visual = when {
                task.hole.isNotEmpty() || task.board.isNotEmpty() -> Visual.Hand(task.hole, task.board)
                task.example.isNotEmpty() -> Visual.Cards(task.example, caption = task.exampleCaption)
                else -> null
            },
            columns = 4,
        )
        is AdvancedTask.SizingTask -> {
            val sizes = Sizing.entries
            Question.Choice(
                prompt = "Ты повышал до флопа, соперник уравнял. ${task.street.ruName.replaceFirstChar { it.uppercase() }}, " +
                    "в банке ${task.pot}, соперник чекнул тебе. Что делаешь?",
                options = sizes.map { sizingLabel(it, task.pot) },
                correct = sizes.indexOf(task.answer),
                explanation = "У тебя ${task.read.label}" +
                    (if (task.read.outs > 0) " — ${task.read.outs} ${plural(task.read.outs, "аут", "аута", "аутов")}" else "") +
                    ".\n" + SizingRules.reason(task.street, task.read, task.wet),
                concept = Concept.BET_SIZING,
                visual = Visual.Hand(task.hole, task.board),
                columns = 2,
            )
        }
        is AdvancedTask.BluffMathTask -> Question.Choice(
            prompt = task.prompt,
            options = task.options,
            correct = task.options.indexOf(task.answer),
            explanation = task.explanation,
            concept = Concept.BLUFF_MATH,
            columns = 2,
        )
    }

    private fun sizingLabel(s: Sizing, pot: Int): String = when (s) {
        Sizing.CHECK -> "Чек"
        Sizing.THIRD -> "Треть банка · ${pot / 3}"
        Sizing.TWO_THIRDS -> "Две трети · ${pot * 2 / 3}"
        Sizing.POT -> "Весь банк · $pot"
    }

    /** Questions to re-train [concept]: lesson questions plus fresh generated ones. */
    fun forReview(concept: Concept, count: Int = 3): List<Question> {
        val drill = DrillType.entries.firstOrNull { it.concept == concept && it != DrillType.HAND_SIM }
        val fromLessons = Curriculum.questionsByConcept[concept].orEmpty().shuffled(random)
        val generated = drill?.let { d -> List(count) { next(d) } }.orEmpty()
        return (generated.take(count - 1) + fromLessons).take(count).ifEmpty { fromLessons.take(count) }.shuffled(random)
    }

    companion object {
        fun plural(n: Int, one: String, few: String, many: String): String {
            val m10 = n % 10
            val m100 = n % 100
            return when {
                m10 == 1 && m100 != 11 -> one
                m10 in 2..4 && m100 !in 12..14 -> few
                else -> many
            }
        }

        fun HandCategory.genitive(): String = when (this) {
            HandCategory.FLUSH -> "флеша"
            HandCategory.STRAIGHT -> "стрита"
            HandCategory.FULL_HOUSE -> "фулл-хауса"
            else -> ruName.lowercase()
        }
    }
}
