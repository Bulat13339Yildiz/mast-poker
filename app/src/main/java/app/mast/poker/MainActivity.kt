package app.mast.poker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.mast.poker.habit.PuzzleWidget
import app.mast.poker.habit.Reminders
import app.mast.poker.ui.AppViewModel
import app.mast.poker.ui.MastApp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { app.progress.value == null }
        // The UI is always dark felt: light system bar icons regardless of the phone's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        app.requestOpen(intent?.getStringExtra(EXTRA_OPEN))
        setContent { MastApp(app) }
        // Alarms do not survive every update or reboot path — re-arm on each start.
        lifecycleScope.launch {
            val p = app.progress.filterNotNull().first()
            Reminders.apply(this@MainActivity, p.settings.reminders, p.settings.reminderHour)
            PuzzleWidget.refresh(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        app.requestOpen(intent.getStringExtra(EXTRA_OPEN))
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_PUZZLE = "puzzle"
    }
}
