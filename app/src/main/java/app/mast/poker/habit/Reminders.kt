package app.mast.poker.habit

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.mast.poker.MainActivity
import app.mast.poker.MastApplication
import app.mast.poker.R
import app.mast.poker.progress.ProgressReducer
import app.mast.poker.progress.UserProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** A daily nudge at the chosen hour — only on days the player has not practised yet. */
object Reminders {
    private const val CHANNEL = "streak"
    private const val REQUEST = 7
    const val ACTION_REMIND = "app.mast.poker.REMIND"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Schedules (or cancels) the daily alarm to match the settings. */
    fun apply(context: Context, enabled: Boolean, hour: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pending(context)
        alarms.cancel(intent)
        if (!enabled) return
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, intent)
    }

    /** What to say, or nothing if the player has already practised today. */
    fun message(p: UserProgress, today: Long): Pair<String, String>? {
        if (!p.onboarded || p.lastActiveDay == today) return null
        val streak = ProgressReducer.visibleStreak(p, today)
        return if (streak > 0) {
            "Серия $streak ${days(streak)} под угрозой" to "Пара минут сегодня — и серия продолжится. Задача дня уже ждёт."
        } else {
            "Пара минут покера?" to "Задача дня и тренажёры ждут. Начни новую серию."
        }
    }

    private fun days(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> "день"
            m10 in 2..4 && m100 !in 12..14 -> "дня"
            else -> "дней"
        }
    }

    fun post(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Напоминания о серии", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_PUZZLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD2AE5C.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, notification) }
    }
}

/** Fires the daily reminder and re-arms it after a reboot or an app update. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MastApplication
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val p = app.progress.progress.first()
                when (intent.action) {
                    Reminders.ACTION_REMIND -> Reminders.message(p, app.progress.today())?.let { (title, text) -> Reminders.post(context, title, text) }
                    else -> Reminders.apply(context, p.settings.reminders, p.settings.reminderHour)
                }
            } finally {
                result.finish()
            }
        }
    }
}
