package id.papamama.app.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import id.papamama.app.MainActivity
import id.papamama.app.R
import id.papamama.app.data.Store
import id.papamama.app.model.Line
import id.papamama.app.model.Phone
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object Notifier {
    const val CH_URGENT = "papamama_urgent"
    const val CH_INFO = "papamama_info"
    const val EXTRA_LINE = "line"
    const val EXTRA_ACT = "act"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_URGENT, "Peringatan penting", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Kuota/masa aktif hampir habis, masa tenggang" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Pengingat", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Kuota menipis, masa aktif H-7/H-3, data perlu diperbarui" }
        )
    }

    fun canNotify(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Hitung status semua nomor dan kirim notifikasi yang belum pernah dikirim. */
    @Synchronized
    fun evaluateAll(ctx: Context) {
        if (!canNotify(ctx)) return
        val data = Store.state.value
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        for (line in data.lines) {
            val st = StatusEngine.evaluate(line, data.settings, now, today)
            val events = AlertRules.events(line, st, data.settings, today).filter { it.key !in line.notified }
            if (events.isEmpty()) continue
            events.forEach { post(ctx, line, it) }
            Store.markNotified(line.id, events.map { it.key })
        }
    }

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, line: Line, e: AlertEvent) {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val base = e.key.substringBefore('@')
        val open = PendingIntent.getActivity(
            ctx, (line.id + base).hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_LINE, line.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val act = PendingIntent.getActivity(
            ctx, (line.id + base + "#act").hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_LINE, line.id)
                .putExtra(EXTRA_ACT, if (e.action == AlertAction.BUY) "buy" else "update")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags
        )
        val n = NotificationCompat.Builder(ctx, if (e.urgent) CH_URGENT else CH_INFO)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF1F1A5C.toInt())
            .setContentTitle(e.title)
            .setContentText(e.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (e.urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, e.actionLabel, act)
            .build()
        NotificationManagerCompat.from(ctx).notify((line.id + base.take(2)).hashCode(), n)
    }

    @SuppressLint("MissingPermission")
    fun test(ctx: Context) {
        val line = Store.state.value.lines.firstOrNull()
        if (line == null) {
            val n = NotificationCompat.Builder(ctx, CH_URGENT).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Notifikasi PapaMama aktif").setContentText("Peringatan kuota & masa aktif akan muncul seperti ini.")
                .setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(1, n)
        } else {
            post(ctx, line, AlertEvent("test", "Contoh: Kuota ${line.displayName} tinggal 5%",
                "Begini tampilan peringatan dari PapaMama. Tombol di bawah langsung membuka menu beli.",
                true, AlertAction.BUY, "Kirim kuota"))
        }
    }
}

/** Membaca SMS info kuota dari operator (untuk SIM di HP ini) dan laporan "#PM#" dari HP keluarga. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val sender = msgs[0].originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        Store.init(ctx)
        SmsIntake.handle(ctx, sender, body, subId)
    }
}

object SmsIntake {
    fun looksLikeOperator(sender: String): Boolean {
        val digits = sender.filter { it.isDigit() }
        return sender.any { it.isLetter() } || digits.length in 1..6
    }

    fun handle(ctx: Context, sender: String, body: String, subId: Int) {
        val lines = Store.state.value.lines
        if (body.contains("#PM#")) {
            val info = OperatorTextParser.parse(body)
            val target = info.reportMsisdn ?: return
            // Hanya terima laporan yang benar-benar dikirim dari nomor itu sendiri.
            if (!Phone.same(target, sender)) return
            val line = lines.firstOrNull { Phone.same(it.msisdn, target) } ?: return
            Store.applyReading(line.id, info, "Laporan SMS dari HP ${line.name}", body)
            Notifier.evaluateAll(ctx)
            return
        }
        if (!looksLikeOperator(sender)) return
        val info = OperatorTextParser.parse(body)
        if (info.isEmpty) return
        val local = lines.filter { it.isLocal }
        val line = local.firstOrNull { subId >= 0 && it.subId == subId } ?: local.singleOrNull() ?: return
        Store.applyReading(line.id, info, "SMS operator", body)
        Store.line(line.id)?.let { Reporter.maybeReport(ctx, it) }
        Notifier.evaluateAll(ctx)
    }
}

/** Pekerjaan berkala: cek USSD nomor di HP ini, hitung peringatan, kirim laporan Mode Pelapor. */
class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Store.init(ctx)
        val local = Store.state.value.lines.filter { it.isLocal && it.autoCheck }
        local.forEachIndexed { i, l ->
            if (i > 0) delay(4000)
            runCatching { Checker.checkLine(ctx, l) }
        }
        Notifier.evaluateAll(ctx)
        Store.state.value.lines.forEach { Reporter.maybeReport(ctx, it) }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context, hours: Int) {
            val req = PeriodicWorkRequestBuilder<CheckWorker>(hours.coerceIn(1, 24).toLong(), TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("papamama-check", ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runOnce(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "papamama-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<CheckWorker>().build()
            )
        }
    }
}
