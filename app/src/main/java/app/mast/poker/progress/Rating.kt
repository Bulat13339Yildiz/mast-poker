package app.mast.poker.progress

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Elo-style skill rating per concept. Every answer is a "game" against the question:
 * beating a hard question moves the rating up more than an easy one.
 */
object Rating {
    const val START = 1000
    const val K = 24

    /** Difficulty of a generated question of [level] 1..3; lesson questions count as level 1. */
    fun difficulty(level: Int): Int = when (level) {
        1 -> 1000
        2 -> 1200
        else -> 1400
    }

    fun expected(rating: Int, difficulty: Int): Double = 1.0 / (1.0 + 10.0.pow((difficulty - rating) / 400.0))

    fun update(rating: Int, difficulty: Int, correct: Boolean): Int =
        (rating + K * ((if (correct) 1.0 else 0.0) - expected(rating, difficulty))).roundToInt()

    /** The generator level that fits [rating]: mostly the matching one, sometimes a step either way. */
    fun level(rating: Int, random: Random): Int {
        val base = baseLevel(rating)
        val roll = random.nextInt(10)
        return when {
            roll < 7 -> base
            roll < 9 -> (base + 1).coerceAtMost(3)
            else -> (base - 1).coerceAtLeast(1)
        }
    }

    fun baseLevel(rating: Int): Int = when {
        rating < 1100 -> 1
        rating < 1300 -> 2
        else -> 3
    }

    fun title(rating: Int): String = when {
        rating < 1050 -> "Учусь"
        rating < 1200 -> "Уверенно"
        rating < 1350 -> "Сильно"
        else -> "Мастер"
    }
}
