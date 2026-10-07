package app.mast.poker.habit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import app.mast.poker.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** What the widget shows. */
data class WidgetState(
    val title: String,
    val status: String,
    val statusKind: Kind,
    val footer: String,
) {
    enum class Kind { TODO, DONE, MISSED, LOCKED }
}

/**
 * Paints the whole widget into one bitmap: lacquered emerald with a gold glow and
 * art-deco rays, a double gold frame, a tilted queen of hearts and the day's puzzle.
 */
object WidgetArt {
    private val ivory = Color.rgb(0xF8, 0xF3, 0xE8)
    private val gold = Color.rgb(0xD2, 0xAE, 0x5C)
    private val goldLight = Color.rgb(0xF0, 0xD8, 0x96)
    private val green = Color.rgb(0x4B, 0xE3, 0xA0)
    private val muted = Color.argb(0xB0, 0xF7, 0xF3, 0xEA)
    private val heartRed = Color.rgb(0xC0, 0x27, 0x2D)

    fun render(context: Context, width: Int, height: Int, density: Float, state: WidgetState): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = width.toFloat()
        val h = height.toFloat()
        val dp = density
        val radius = 22 * dp
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW) }
        c.clipPath(clip)

        // Lacquered emerald with a warm glow behind the card.
        c.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, w, h, Color.rgb(0x0E, 0x45, 0x2A), Color.rgb(0x02, 0x14, 0x0B), Shader.TileMode.CLAMP)
        })
        c.drawPaint(Paint().apply {
            shader = RadialGradient(w * 0.8f, h * 0.3f, w * 0.65f, Color.argb(0x50, 0xD2, 0xAE, 0x5C), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })

        // Art-deco sunburst from the lower-left corner.
        val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; alpha = 18; strokeWidth = 1 * dp }
        val reach = w * 1.3f
        var a = -90.0
        while (a <= 0.0) {
            val rad = Math.toRadians(a)
            c.drawLine(0f, h, (cos(rad) * reach).toFloat(), (h + sin(rad) * reach).toFloat(), ray)
            a += 6.0
        }

        drawCard(context, c, w, h, dp)

        // Double gold frame with stepped corners.
        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = gold; strokeWidth = 1.4f * dp }
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = gold; alpha = 120; strokeWidth = 0.7f * dp }
        val o = 5 * dp
        val i = 9 * dp
        c.drawRoundRect(RectF(o, o, w - o, h - o), radius - o, radius - o, outer)
        c.drawRoundRect(RectF(i, i, w - i, h - i), radius - i, radius - i, inner)
        val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = goldLight; alpha = 210 }
        for ((x, y) in listOf(i + 8 * dp to i + 8 * dp, w - i - 8 * dp to i + 8 * dp, i + 8 * dp to h - i - 8 * dp, w - i - 8 * dp to h - i - 8 * dp)) {
            val d = 3 * dp
            c.drawPath(Path().apply { moveTo(x, y - d); lineTo(x + d, y); lineTo(x, y + d); lineTo(x - d, y); close() }, corner)
        }

        drawText(context, c, w, h, dp, state)
        return bmp
    }

    private fun drawCard(context: Context, c: Canvas, w: Float, h: Float, dp: Float) {
        val cardH = min(h * 1.05f, w * 0.5f)
        val cardW = cardH / 1.4f
        val cx = w - cardW * 0.42f
        val cy = h * 0.58f
        c.save()
        c.rotate(-11f, cx, cy)
        val rect = RectF(cx - cardW / 2, cy - cardH / 2, cx + cardW / 2, cy + cardH / 2)
        val r = cardW * 0.08f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        shadow.setShadowLayer(10 * dp, 0f, 4 * dp, Color.argb(170, 0, 0, 0))
        c.drawRoundRect(rect, r, r, shadow)
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ivory })
        val art = BitmapFactory.decodeResource(context.resources, R.drawable.court_qh)
        if (art != null) {
            val pad = cardW * 0.11f
            val target = RectF(rect.left + pad, rect.top + pad * 1.2f, rect.right - pad, rect.bottom - pad * 1.2f)
            c.drawBitmap(art, Rect(0, 0, art.width, art.height), target, Paint(Paint.FILTER_BITMAP_FLAG))
            art.recycle()
            c.drawRect(target, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = gold; strokeWidth = 0.8f * dp })
        }
        val index = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = heartRed
            textSize = cardW * 0.13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        c.drawText("Q", rect.left + cardW * 0.04f, rect.top + cardW * 0.14f, index)
        c.drawText("♥", rect.left + cardW * 0.03f, rect.top + cardW * 0.27f, index)
        c.drawRoundRect(rect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(60, 0, 0, 0); strokeWidth = 0.8f * dp })
        c.restore()
    }

    private fun drawText(context: Context, c: Canvas, w: Float, h: Float, dp: Float, s: WidgetState) {
        val serif = runCatching { ResourcesCompat.getFont(context, R.font.playfair_display) }.getOrNull() ?: Typeface.SERIF
        val left = 28 * dp
        // Text stops before the tilted card's left edge.
        val maxW = w * 0.62f - left
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold
            textSize = 10 * dp
            letterSpacing = 0.18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ivory
            textSize = 20 * dp
            typeface = Typeface.create(serif, Typeface.BOLD)
        }
        while (title.measureText(s.title) > maxW && title.textSize > 13 * dp) title.textSize -= 1 * dp
        val status = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * dp
            color = when (s.statusKind) {
                WidgetState.Kind.DONE -> green
                WidgetState.Kind.TODO -> goldLight
                else -> muted
            }
        }
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 11 * dp }
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold
            alpha = 170
            textSize = 9 * dp
            letterSpacing = 0.25f
            typeface = Typeface.create(serif, Typeface.BOLD)
        }
        // Baselines spread with the widget's height.
        c.drawText("ЗАДАЧА ДНЯ", left, h * 0.23f, label)
        c.drawText(TextUtils.ellipsize(s.title, title, maxW, TextUtils.TruncateAt.END).toString(), left, h * 0.47f, title)
        c.drawText(TextUtils.ellipsize(s.status, status, maxW, TextUtils.TruncateAt.END).toString(), left, h * 0.64f, status)
        c.drawText(TextUtils.ellipsize(s.footer, footer, maxW, TextUtils.TruncateAt.END).toString(), left, h * 0.79f, footer)
        c.drawText("HOLD 'EM", left, h * 0.91f, brand)
    }
}
