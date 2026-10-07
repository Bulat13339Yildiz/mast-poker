package app.mast.poker

import android.app.Application
import app.mast.poker.progress.ProgressRepository
import app.mast.poker.ui.theme.SoundBank

class MastApplication : Application() {
    val progress: ProgressRepository by lazy { ProgressRepository.create(this) }

    /** Built the first time sounds are switched on. */
    val sounds: SoundBank by lazy { SoundBank(this) }
}
