package app.mast.poker.progress

import app.mast.poker.content.DrillType
import kotlinx.serialization.Serializable

/** A habit that costs chips, measured as how often the player errs in one kind of spot. */
enum class Leak(val title: String, val advice: String, val drill: DrillType?) {
    LOOSE_PREFLOP(
        "Играешь слишком много рук до флопа",
        "Держись таблицы открытия: с ранних позиций — только сильные руки.",
        DrillType.PREFLOP,
    ),
    TIGHT_PREFLOP(
        "Сбрасываешь хорошие руки до флопа",
        "С поздних позиций и против лимперов играй смелее: повышай руки из таблицы.",
        DrillType.PREFLOP,
    ),
    LIMPING(
        "Входишь в игру коллом",
        "Лимп отдаёт инициативу и не забирает блайнды. Входи рейзом или сбрасывай.",
        DrillType.PREFLOP,
    ),
    MISSED_3BET(
        "Редко делаешь 3-бет",
        "С QQ+ и AK против рейза повышай снова — так лучшие руки приносят больше.",
        null,
    ),
    LOOSE_VS_RAISE(
        "Слишком часто играешь против рейзов",
        "Против рейза играй узко: JJ, TT, AQ — колл, QQ+ и AK — 3-бет, остальное — пас.",
        null,
    ),
    OVERCALL(
        "Переплачиваешь на флопе и тёрне",
        "Без сильной руки или дро с хорошей ценой против ставки — пас.",
        DrillType.POT_ODDS,
    ),
    RIVER_OVERCALL(
        "Переплачиваешь на ривере",
        "Крупная ставка на ривере чаще означает силу. Одной пары часто мало — особенно против осторожных.",
        DrillType.BLUFF_MATH,
    ),
    OVERFOLD(
        "Сбрасываешь слишком часто",
        "Против небольших ставок продолжай со средними руками — помни про MDF.",
        DrillType.BLUFF_MATH,
    ),
    PASSIVE(
        "Упускаешь ставки",
        "С сильной рукой ставь на вэлью, с дро — полублефуй.",
        DrillType.SIZING,
    ),
    OVERBET(
        "Ставишь там, где лучше чек",
        "Со средней рукой — чек. Блефуй только против тех, кто умеет сбрасывать.",
        DrillType.SIZING,
    ),
    CHASING(
        "Гонишься за дро без шансов банка",
        "Перед коллом сравни шанс собрать руку с ценой.",
        DrillType.POT_ODDS,
    ),
    SIZE_WRONG(
        "Ошибаешься с размером ставки",
        "Сухой флоп — треть банка, мокрый — две трети, монстр на ривере — весь банк.",
        DrillType.SIZING,
    ),
    PUSH_LOOSE(
        "Слишком широкий олл-ин",
        "Если соперники часто уравнивают, для олл-ина нужна рука посильнее.",
        DrillType.PUSH_FOLD,
    ),
    PUSH_TIGHT(
        "Слишком узкий олл-ин",
        "С коротким стеком всё решают фолды: олл-ин выгоден и со средними руками.",
        DrillType.PUSH_FOLD,
    ),
}

@Serializable
data class LeakStat(val spots: Int = 0, val mistakes: Int = 0) {
    val rate: Double get() = if (spots == 0) 0.0 else mistakes.toDouble() / spots
}

/** One graded decision in a spot where [leak] could show up. */
data class LeakEvent(val leak: Leak, val mistake: Boolean)

object Leaks {
    const val MIN_SPOTS = 6
    const val MIN_RATE = 0.3

    fun stat(p: UserProgress, leak: Leak): LeakStat = p.leaks[leak.name] ?: LeakStat()

    fun record(p: UserProgress, events: List<LeakEvent>): UserProgress {
        if (events.isEmpty()) return p
        val leaks = p.leaks.toMutableMap()
        for (e in events) {
            val s = leaks[e.leak.name] ?: LeakStat()
            leaks[e.leak.name] = LeakStat(s.spots + 1, s.mistakes + if (e.mistake) 1 else 0)
        }
        return p.copy(leaks = leaks)
    }

    /** Leaks seen often enough to trust, worst first. */
    fun active(p: UserProgress): List<Pair<Leak, LeakStat>> =
        Leak.entries.map { it to stat(p, it) }
            .filter { (_, s) -> s.spots >= MIN_SPOTS && s.rate >= MIN_RATE }
            .sortedWith(compareByDescending<Pair<Leak, LeakStat>> { it.second.rate }.thenByDescending { it.second.spots })

    /** How many spots have been graded at all — to tell "no leaks" from "not enough data". */
    fun spotsSeen(p: UserProgress): Int = p.leaks.values.sumOf { it.spots }
}
