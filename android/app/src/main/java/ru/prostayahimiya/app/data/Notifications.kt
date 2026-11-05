package ru.prostayahimiya.app.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.prostayahimiya.app.HimiyaApp
import ru.prostayahimiya.app.MainActivity
import ru.prostayahimiya.app.R
import java.util.concurrent.TimeUnit

/*
 * Уведомления. Без Google-сервисов и без ключей: раз в 15 минут (так часто разрешает Android)
 * приложение само спрашивает сервер, что нового, и показывает уведомление.
 * Ученику — новые практики и контрольные, оценки, сообщения в чате и напоминание о сроке сдачи.
 * Учителю — новые работы на проверку и сообщения учеников.
 */

/** Группы уведомлений. Каждую можно выключить в настройках телефона отдельно. */
enum class NoticeChannel(val id: String, val title: String, val description: String) {
    PRACTICES("practices", "Новые задания", "Учитель задал новую практику или контрольную"),
    GRADES("grades", "Оценки", "Учитель проверил работу"),
    DEADLINES("deadlines", "Сроки сдачи", "Напоминание, когда срок сдачи завтра или сегодня"),
    CHAT("chat", "Чат", "Новые сообщения в чате класса и личные"),
    WORKS("works", "Работы учеников", "Ученики сдали работы на проверку"),
}

/** Одно уведомление. [open] — какой экран открыть по нажатию (адрес в AppNavigation). */
data class Notice(val channel: NoticeChannel, val key: String, val title: String, val text: String, val open: String)

object Notifier {
    const val EXTRA_OPEN = "open"
    private const val WORK = "updates"

    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        NoticeChannel.entries.forEach { ch ->
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(ch.id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(ch.title)
                    .setDescription(ch.description)
                    .build(),
            )
        }
    }

    /** Разрешено ли приложению показывать уведомления (на Android 13+ это спрашивают у человека). */
    fun permitted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Проверка раз в 15 минут, только когда есть интернет. Повторный вызов ничего не меняет. */
    fun schedule(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun post(context: Context, notices: List<Notice>) {
        if (!permitted(context)) return
        val manager = NotificationManagerCompat.from(context)
        notices.forEach { n ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, n.open)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                n.key.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, n.channel.id)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF187A5F.toInt())
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(n.key.hashCode(), notification)
            } catch (_: SecurityException) {
                // Разрешение забрали между проверкой и показом — просто не показываем.
            }
        }
    }
}

/** Фоновая проверка: что нового на сервере. */
class UpdatesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HimiyaApp
        val repository = app.repository
        if (!repository.notificationsEnabled) return Result.success()
        val notices = runCatching { repository.checkUpdates() }.getOrElse { return Result.success() }
        // Когда приложение открыто, всё видно и так — уведомления не нужны.
        if (!app.inForeground) Notifier.post(applicationContext, notices)
        return Result.success()
    }
}
