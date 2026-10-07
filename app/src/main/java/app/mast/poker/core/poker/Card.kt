package app.mast.poker.core.poker

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val code: Char, val symbol: String, val ruName: String, val ruGenitive: String, val isRed: Boolean) {
    CLUBS('c', "♣", "трефы", "треф", false),
    DIAMONDS('d', "♦", "бубны", "бубен", true),
    HEARTS('h', "♥", "червы", "червей", true),
    SPADES('s', "♠", "пики", "пик", false),
}

@Serializable
enum class Rank(
    val value: Int,
    val code: Char,
    val label: String,
    val ruName: String,
    val ruPlural: String,
    /** "пара королей" */
    val ruGenPlural: String,
    /** "стрит до короля" */
    val ruGenitive: String,
) {
    TWO(2, '2', "2", "двойка", "двойки", "двоек", "двойки"),
    THREE(3, '3', "3", "тройка", "тройки", "троек", "тройки"),
    FOUR(4, '4', "4", "четвёрка", "четвёрки", "четвёрок", "четвёрки"),
    FIVE(5, '5', "5", "пятёрка", "пятёрки", "пятёрок", "пятёрки"),
    SIX(6, '6', "6", "шестёрка", "шестёрки", "шестёрок", "шестёрки"),
    SEVEN(7, '7', "7", "семёрка", "семёрки", "семёрок", "семёрки"),
    EIGHT(8, '8', "8", "восьмёрка", "восьмёрки", "восьмёрок", "восьмёрки"),
    NINE(9, '9', "9", "девятка", "девятки", "девяток", "девятки"),
    TEN(10, 'T', "10", "десятка", "десятки", "десяток", "десятки"),
    JACK(11, 'J', "J", "валет", "вальты", "вальтов", "валета"),
    QUEEN(12, 'Q', "Q", "дама", "дамы", "дам", "дамы"),
    KING(13, 'K', "K", "король", "короли", "королей", "короля"),
    ACE(14, 'A', "A", "туз", "тузы", "тузов", "туза");

    companion object {
        fun of(value: Int): Rank = entries.first { it.value == value }
    }
}

@Serializable
data class Card(val rank: Rank, val suit: Suit) {
    val code: String get() = "${rank.code}${suit.code}"
    override fun toString(): String = "${rank.label}${suit.symbol}"

    companion object {
        fun parse(code: String): Card {
            require(code.length == 2) { "Bad card code: $code" }
            val rank = Rank.entries.firstOrNull { it.code == code[0].uppercaseChar() }
                ?: error("Bad rank in $code")
            val suit = Suit.entries.firstOrNull { it.code == code[1].lowercaseChar() }
                ?: error("Bad suit in $code")
            return Card(rank, suit)
        }
    }
}

/** "As Kd 7h" → list of cards. */
fun cards(spec: String): List<Card> =
    spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(Card::parse)

object Deck {
    val full: List<Card> = Suit.entries.flatMap { s -> Rank.entries.map { r -> Card(r, s) } }

    fun shuffled(random: kotlin.random.Random): MutableList<Card> = full.shuffled(random).toMutableList()

    fun without(used: Collection<Card>): List<Card> {
        val set = used.toHashSet()
        return full.filter { it !in set }
    }
}
