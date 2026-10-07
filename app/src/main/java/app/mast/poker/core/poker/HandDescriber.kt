package app.mast.poker.core.poker

/** Human Russian names for a hand value: «пара королей», «фулл-хаус: дамы через пятёрки». */
object HandDescriber {

    private fun r(v: Int) = Rank.of(v)

    fun describe(v: HandValue): String {
        val t = v.tiebreak
        return when (v.category) {
            HandCategory.HIGH_CARD -> "старшая карта — ${r(t[0]).ruName}"
            HandCategory.PAIR -> "пара ${r(t[0]).ruGenPlural}"
            HandCategory.TWO_PAIR -> "две пары: ${r(t[0]).ruPlural} и ${r(t[1]).ruPlural}"
            HandCategory.THREE_OF_A_KIND -> "тройка ${r(t[0]).ruGenPlural}"
            HandCategory.STRAIGHT -> if (t[0] == 5) "стрит-колесо (A-2-3-4-5)" else "стрит до ${r(t[0]).ruGenitive}"
            HandCategory.FLUSH -> "флеш до ${r(t[0]).ruGenitive}"
            HandCategory.FULL_HOUSE -> "фулл-хаус: ${r(t[0]).ruPlural} через ${r(t[1]).ruPlural}"
            HandCategory.FOUR_OF_A_KIND -> "каре ${r(t[0]).ruGenPlural}"
            HandCategory.STRAIGHT_FLUSH -> if (t[0] == 5) "стрит-флеш до пятёрки" else "стрит-флеш до ${r(t[0]).ruGenitive}"
            HandCategory.ROYAL_FLUSH -> "роял-флеш"
        }
    }
}
