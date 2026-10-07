package app.mast.poker.content

import app.mast.poker.core.poker.Card
import app.mast.poker.core.poker.HandCategory
import app.mast.poker.core.poker.cards
import kotlin.math.roundToInt

/**
 * Example cards for each hand category; [kickers] are shown dimmed. [frequency] is how
 * often this is the best hand from 7 cards — what a hold'em player sees by the river.
 */
data class HandExample(val category: HandCategory, val cards: List<Card>, val kickers: Set<Card>, val description: String, val frequency: Double) {
    val odds: String get() = "1 на ${formatOneIn(1 / frequency)}"
}

private val ru = java.util.Locale.forLanguageTag("ru")

private fun formatOneIn(x: Double): String = when {
    x >= 10_000 -> String.format(ru, "%,d", (x / 1000).roundToInt() * 1000)
    x >= 100 -> String.format(ru, "%,d", (x / 10).roundToInt() * 10)
    x >= 10 -> "${x.roundToInt()}"
    else -> String.format(ru, "%.1f", x)
}

object Reference {
    /** 7-card frequencies of the best five (out of C(52,7) = 133 784 560 hands). */
    val ladder: List<HandExample> = listOf(
        HandExample(HandCategory.ROYAL_FLUSH, cards("Ts Js Qs Ks As"), emptySet(), "10-J-Q-K-A одной масти", 4_324 / 133_784_560.0),
        HandExample(HandCategory.STRAIGHT_FLUSH, cards("5h 6h 7h 8h 9h"), emptySet(), "Пять подряд одной масти", 37_260 / 133_784_560.0),
        HandExample(HandCategory.FOUR_OF_A_KIND, cards("9c 9d 9h 9s Kd"), cards("Kd").toSet(), "Четыре карты одного ранга", 224_848 / 133_784_560.0),
        HandExample(HandCategory.FULL_HOUSE, cards("Qc Qd Qh 5s 5d"), emptySet(), "Тройка + пара", 3_473_184 / 133_784_560.0),
        HandExample(HandCategory.FLUSH, cards("Ad Jd 8d 5d 2d"), emptySet(), "Пять карт одной масти", 4_047_644 / 133_784_560.0),
        HandExample(HandCategory.STRAIGHT, cards("6c 7d 8h 9s Tc"), emptySet(), "Пять подряд, масти любые", 6_180_020 / 133_784_560.0),
        HandExample(HandCategory.THREE_OF_A_KIND, cards("7c 7d 7h Ks 2d"), cards("Ks 2d").toSet(), "Три карты одного ранга", 6_461_620 / 133_784_560.0),
        HandExample(HandCategory.TWO_PAIR, cards("Jc Jd 4h 4s Ad"), cards("Ad").toSet(), "Две разные пары", 31_433_400 / 133_784_560.0),
        HandExample(HandCategory.PAIR, cards("Ac Ad 9h 6s 3d"), cards("9h 6s 3d").toSet(), "Две карты одного ранга", 58_627_800 / 133_784_560.0),
        HandExample(HandCategory.HIGH_CARD, cards("Ah Jd 8c 5s 3h"), cards("Jd 8c 5s 3h").toSet(), "Ничего не собралось", 23_294_460 / 133_784_560.0),
    )

    /** One short tip per day on the home screen. */
    val tips: List<String> = listOf(
        "Играй меньше рук, но сильнее — главный секрет новичка.",
        "Сбрасывать не стыдно: каждая сброшенная слабая рука экономит фишки.",
        "Позиция важнее карт: на баттоне можно играть больше рук.",
        "Флеш сильнее стрита, а фулл-хаус сильнее флеша.",
        "Флеш-дро — 9 аутов, двусторонний стрит-дро — 8, гатшот — 4.",
        "Оценивай решения, а не результат одной раздачи.",
        "Против чужого рейза играй уже, чем когда открываешь сам.",
        "Пара на столе — общая для всех игроков.",
        "Ставка в полбанка требует 25% шансов для колла.",
        "Не отыгрывайся: тилт стоит дороже любой раздачи.",
        "Рейз лучше колла: ставкой можно выиграть, даже не собрав руку.",
        "В руке всегда ровно пять карт — шестая ничего не решает.",
    )

    data class Term(val word: String, val meaning: String)

    val glossary: List<Term> = listOf(
        Term("Аут", "Карта, которая улучшит твою руку до, скорее всего, выигрышной."),
        Term("Банк", "Все фишки, поставленные в раздаче. Его и разыгрывают."),
        Term("Банкролл", "Деньги, отложенные только на покер."),
        Term("Баттон", "Позиция дилера. Ходит последним после флопа — лучшее место."),
        Term("Бет", "Первая ставка в круге торговли."),
        Term("Блайнд", "Обязательная ставка вслепую: малый (SB) и большой (BB)."),
        Term("Блеф", "Ставка со слабой рукой, чтобы соперник сбросил лучшую."),
        Term("Борд", "Общие карты на столе."),
        Term("Вскрытие", "Открытие карт в конце раздачи, если осталось больше одного игрока."),
        Term("Гатшот", "Стрит-дро с дырой в середине: 4 аута."),
        Term("Дисперсия", "Колебания результатов из-за случайности."),
        Term("Доминирование", "Когда у соперника та же карта, но кикер старше."),
        Term("Дро", "Недособранная рука: не хватает одной карты до стрита или флеша."),
        Term("Катофф", "Место перед баттоном, поздняя позиция."),
        Term("Кикер", "Карта, которая не входит в комбинацию, но решает при равенстве."),
        Term("Колл", "Уравнять ставку соперника."),
        Term("Коннекторы", "Стартовые карты подряд по рангу: 8-7, J-T."),
        Term("Лимп", "Войти в раздачу, просто уравняв большой блайнд."),
        Term("Натс", "Сильнейшая возможная рука на данном борде."),
        Term("Олл-ин", "Поставить все свои фишки."),
        Term("Полублеф", "Ставка с дро: можно выиграть сразу или собрать руку позже."),
        Term("Префлоп", "Круг торговли до выкладки общих карт."),
        Term("Продолженная ставка", "Ставка на флопе от того, кто повышал на префлопе."),
        Term("Рейз", "Повышение ставки."),
        Term("Ривер", "Пятая, последняя общая карта."),
        Term("Сет", "Тройка из карманной пары и карты со стола."),
        Term("Тёрн", "Четвёртая общая карта."),
        Term("Тилт", "Игра на эмоциях после неудач."),
        Term("Трипс", "Тройка из пары на столе и карты в руке."),
        Term("Флоп", "Первые три общие карты."),
        Term("Фолд", "Сбросить карты и выйти из раздачи."),
        Term("Чек", "Пропустить ход без ставки, если ставок ещё не было."),
        Term("Шансы банка", "Сколько процентов победы нужно, чтобы колл окупался: колл ÷ (банк + колл)."),
        Term("Эквити", "Твоя доля банка, если сыграть раздачу до конца много раз."),
        Term("3-бет", "Повторное повышение после чужого рейза."),
    )
}
