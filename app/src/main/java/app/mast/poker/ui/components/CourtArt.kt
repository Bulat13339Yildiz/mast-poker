package app.mast.poker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import app.mast.poker.R
import app.mast.poker.core.poker.Card

// Court figures: Dmitry Fomin's English pattern deck (Wikimedia Commons, CC0), recoloured
// by art/build_courts.py. Cards without an entry fall back to the drawn letter design.
private val courtArt: Map<String, Int> = mapOf(
    "Jh" to R.drawable.court_jh, "Jd" to R.drawable.court_jd, "Jc" to R.drawable.court_jc, "Js" to R.drawable.court_js,
    "Qh" to R.drawable.court_qh, "Qd" to R.drawable.court_qd, "Qc" to R.drawable.court_qc, "Qs" to R.drawable.court_qs,
    "Kh" to R.drawable.court_kh, "Kd" to R.drawable.court_kd, "Kc" to R.drawable.court_kc, "Ks" to R.drawable.court_ks,
)

/** Bundled illustration for a court card, or null to use the drawn fallback. */
@Composable
fun rememberCourtArt(card: Card): ImageBitmap? = courtArt[card.code]?.let { ImageBitmap.imageResource(it) }
