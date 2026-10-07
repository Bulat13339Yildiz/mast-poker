package app.mast.poker.ui.theme

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Quiet table sounds. Off by default; [Silent] when the player has not turned them on. */
interface SoundFx {
    fun card()
    fun chip()

    object Silent : SoundFx {
        override fun card() = Unit
        override fun chip() = Unit
    }
}

val LocalSounds = staticCompositionLocalOf<SoundFx> { SoundFx.Silent }

/**
 * Two short sounds synthesised on first use — a card's swish and a chip's click —
 * written once to the cache as WAV and played through a [SoundPool]. No audio files ship
 * with the app.
 */
class SoundBank(context: Context) : SoundFx {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile private var cardId = 0
    @Volatile private var chipId = 0

    init {
        val dir = File(context.cacheDir, "sounds").apply { mkdirs() }
        thread(name = "sounds") {
            val card = File(dir, "card_v1.wav").also { if (!it.exists()) it.writeBytes(SoundSynth.wav(SoundSynth.card())) }
            val chip = File(dir, "chip_v1.wav").also { if (!it.exists()) it.writeBytes(SoundSynth.wav(SoundSynth.chip())) }
            cardId = pool.load(card.path, 1)
            chipId = pool.load(chip.path, 1)
        }
    }

    override fun card() {
        if (cardId != 0) pool.play(cardId, 0.28f, 0.28f, 1, 0, 0.95f + Random.nextFloat() * 0.1f)
    }

    override fun chip() {
        if (chipId != 0) pool.play(chipId, 0.32f, 0.32f, 1, 0, 0.97f + Random.nextFloat() * 0.06f)
    }
}

/** Tiny synthesiser for the two sounds; 16-bit mono PCM. */
object SoundSynth {
    const val RATE = 44_100

    /** A card sliding onto the felt: band-limited noise with a quick swell and fade. */
    fun card(): ShortArray {
        val n = (0.075 * RATE).toInt()
        val rnd = Random(11)
        var low = 0.0
        var prev = 0.0
        val out = DoubleArray(n) { i ->
            val t = i.toDouble() / RATE
            val env = if (t < 0.006) t / 0.006 else exp(-(t - 0.006) / 0.02)
            low += 0.32 * (rnd.nextDouble() * 2 - 1 - low)
            val band = low - prev
            prev = low
            band * env
        }
        return normalise(out, 0.55)
    }

    /** Two clay chips touching: a pair of short, bright, damped clicks. */
    fun chip(): ShortArray {
        val n = (0.09 * RATE).toInt()
        val rnd = Random(7)
        val out = DoubleArray(n)
        for ((start, gain) in listOf(0.0 to 1.0, 0.032 to 0.7)) {
            val s = (start * RATE).toInt()
            for (i in s until n) {
                val t = (i - s).toDouble() / RATE
                val tone = 0.6 * sin(2 * PI * 3150 * t) * exp(-t / 0.006) + 0.4 * sin(2 * PI * 4720 * t) * exp(-t / 0.004)
                val tick = if (t < 0.002) (rnd.nextDouble() * 2 - 1) * (1 - t / 0.002) else 0.0
                out[i] += gain * (tone + 0.5 * tick)
            }
        }
        return normalise(out, 0.6)
    }

    private fun normalise(x: DoubleArray, peak: Double): ShortArray {
        val max = x.maxOf { abs(it) }.coerceAtLeast(1e-9)
        return ShortArray(x.size) { (x[it] / max * peak * Short.MAX_VALUE).toInt().toShort() }
    }

    fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        samples.forEach { b.putShort(it) }
        return b.array()
    }
}
