package app.mast.poker.ui.screens

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.mast.poker.ui.LocalNavScope
import app.mast.poker.ui.LocalSharedScope

/** Shared-element hookup that degrades to a no-op outside the nav host (e.g. in screenshot tests). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementOrSelf(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) { this@sharedElementOrSelf.sharedElement(rememberSharedContentState(key), nav) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsOrSelf(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) { this@sharedBoundsOrSelf.sharedBounds(rememberSharedContentState(key), nav) }
}
