package app.mast.poker

import android.app.Application
import app.mast.poker.progress.ProgressRepository

class MastApplication : Application() {
    val progress: ProgressRepository by lazy { ProgressRepository.create(this) }
}
