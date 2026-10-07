package app.mast.poker.core.poker

import kotlinx.serialization.Serializable

@Serializable
enum class HandCategory(val ruName: String, val short: String) {
    HIGH_CARD("Старшая карта", "старшая карта"),
    PAIR("Пара", "пара"),
    TWO_PAIR("Две пары", "две пары"),
    THREE_OF_A_KIND("Тройка", "тройка"),
    STRAIGHT("Стрит", "стрит"),
    FLUSH("Флеш", "флеш"),
    FULL_HOUSE("Фулл-хаус", "фулл-хаус"),
    FOUR_OF_A_KIND("Каре", "каре"),
    STRAIGHT_FLUSH("Стрит-флеш", "стрит-флеш"),
    ROYAL_FLUSH("Роял-флеш", "роял-флеш");

    val strength: Int get() = ordinal + 1
}
