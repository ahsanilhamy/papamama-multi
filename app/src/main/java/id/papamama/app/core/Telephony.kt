package id.papamama.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import id.papamama.app.data.Store
import id.papamama.app.model.Line
import id.papamama.app.model.Operator
import id.papamama.app.model.Phone
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

fun Context.has(perm: String): Boolean =
    ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

data class SimInfo(val subId: Int, val slot: Int, val carrier: String) {
    val label: String get() = "SIM ${slot + 1} · $carrier"
}

data class CheckResult(val ok: Boolean, val message: String)

/** Semua yang berhubungan dengan SIM di HP ini: daftar SIM, USSD otomatis, membuka menu USSD. */
object Sim {

    @SuppressLint("MissingPermission")
    fun list(ctx: Context): List<SimInfo> {
        if (!ctx.has(Manifest.permission.READ_PHONE_STATE)) return emptyList()
        val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map {
                SimInfo(it.subscriptionId, it.simSlotIndex, it.carrierName?.toString()?.ifBlank { null } ?: "SIM")
            }.sortedBy { it.slot }
        }.getOrDefault(emptyList())
    }

    /** Menjalankan USSD satu-langkah di latar belakang dan mengembalikan teks balasan operator. */
    @SuppressLint("MissingPermission")
    suspend fun ussd(ctx: Context, subId: Int, code: String): Result<String> {
        if (!ctx.has(Manifest.permission.CALL_PHONE)) return Result.failure(Exception("Izin Telepon belum diberikan"))
        val base = ctx.getSystemService(TelephonyManager::class.java)
            ?: return Result.failure(Exception("HP tidak mendukung telepon"))
        val tm = if (subId >= 0) base.createForSubscriptionId(subId) else base
        return withTimeoutOrNull(45_000) {
            suspendCancellableCoroutine { cont ->
                try {
                    tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(t: TelephonyManager, request: String, response: CharSequence) {
                            if (cont.isActive) cont.resume(Result.success(response.toString()))
                        }

                        override fun onReceiveUssdResponseFailed(t: TelephonyManager, request: String, failureCode: Int) {
                            if (cont.isActive) cont.resume(Result.failure(Exception("USSD $request gagal (kode $failureCode)")))
                        }
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Result.failure(e))
                }
            }
        } ?: Result.failure(Exception("Operator tidak merespons USSD $code"))
    }

    /** Membuka sesi USSD interaktif (mis. menu beli paket) di SIM yang tepat. */
    @SuppressLint("MissingPermission")
    fun dial(ctx: Context, subId: Int, code: String) {
        val uri = Uri.parse("tel:" + Uri.encode(code))
        val canCall = ctx.has(Manifest.permission.CALL_PHONE)
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canCall && subId >= 0) handleFor(ctx, subId)?.let {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
        }
        runCatching { ctx.startActivity(intent) }
            .onFailure { Toast.makeText(ctx, "Tidak bisa membuka $code", Toast.LENGTH_SHORT).show() }
    }

    @SuppressLint("MissingPermission")
    private fun handleFor(ctx: Context, subId: Int): PhoneAccountHandle? = runCatching {
        if (!ctx.has(Manifest.permission.READ_PHONE_STATE)) return null
        val telecom = ctx.getSystemService(TelecomManager::class.java) ?: return null
        val handles = telecom.callCapablePhoneAccounts
        if (Build.VERSION.SDK_INT >= 30) {
            val tm = ctx.getSystemService(TelephonyManager::class.java)
            handles.firstOrNull { tm.getSubscriptionId(it) == subId }
        } else {
            handles.firstOrNull { it.id == subId.toString() }
                ?: list(ctx).indexOfFirst { it.subId == subId }.takeIf { it >= 0 }?.let { handles.getOrNull(it) }
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun sendSms(ctx: Context, subId: Int, to: String, body: String): Boolean = runCatching {
        if (!ctx.has(Manifest.permission.SEND_SMS)) return false
        val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
            val m = ctx.getSystemService(SmsManager::class.java)
            if (subId >= 0) m.createForSubscriptionId(subId) else m
        } else {
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
        sms.sendMultipartTextMessage(to, null, sms.divideMessage(body), null, null)
        true
    }.getOrDefault(false)
}

/** Cek otomatis satu nomor yang SIM-nya ada di HP ini. */
object Checker {
    suspend fun checkLine(ctx: Context, line: Line): CheckResult {
        if (!line.isLocal) return CheckResult(false, "Nomor ini tidak terpasang di HP ini")
        if (!ctx.has(Manifest.permission.CALL_PHONE)) return CheckResult(false, "Izinkan akses Telepon dulu di Pengaturan")
        val codes = Codes.check(Store.state.value.settings, line.operator)
        if (codes.isEmpty()) return CheckResult(false, "Kode cek untuk ${line.operator.label} belum diisi")
        val texts = mutableListOf<String>()
        var lastError = ""
        codes.forEachIndexed { i, code ->
            if (i > 0) delay(3500)
            Sim.ussd(ctx, line.subId, code)
                .onSuccess { texts += it }
                .onFailure { lastError = it.message ?: "USSD gagal" }
        }
        if (texts.isEmpty()) return CheckResult(false, "$lastError. Coba lagi atau ubah kode di Pengaturan.")
        val raw = texts.joinToString("\n---\n")
        val info = OperatorTextParser.parse(raw)
        if (info.isEmpty) {
            Store.edit(line.id) { it.copy(lastRaw = raw.take(1500)) }
            return CheckResult(false, "Operator membalas tanpa angka (mungkin dikirim lewat SMS, akan dibaca otomatis).")
        }
        Store.applyReading(line.id, info, "USSD otomatis", raw)
        Store.line(line.id)?.let { Reporter.maybeReport(ctx, it) }
        return CheckResult(true, "${line.displayName} diperbarui")
    }

    suspend fun checkAllLocal(ctx: Context): CheckResult {
        val lines = Store.state.value.lines.filter { it.isLocal && it.autoCheck }
        if (lines.isEmpty()) return CheckResult(false, "Belum ada nomor yang SIM-nya di HP ini")
        var ok = 0
        lines.forEachIndexed { i, l ->
            if (i > 0) delay(3500)
            if (checkLine(ctx, l).ok) ok++
        }
        return CheckResult(ok > 0, "$ok dari ${lines.size} nomor di HP ini berhasil dicek")
    }
}

/**
 * Mode Pelapor: dipasang di HP anggota keluarga lain (mis. HP Mama). Status nomor HP tersebut
 * dikirim lewat 1 SMS ke HP orang tua, lalu dibaca otomatis oleh PapaMama di sana.
 */
object Reporter {
    private const val HOUR = 3_600_000L

    fun maybeReport(ctx: Context, line: Line, force: Boolean = false) {
        if (line.reportTo.isBlank() || !line.isLocal || line.updatedAt == 0L) return
        val now = System.currentTimeMillis()
        val settings = Store.state.value.settings
        val st = StatusEngine.evaluate(line, settings, now, java.time.LocalDate.now())
        val since = now - line.lastReportAt
        val due = force || since >= 20 * HOUR || (st.level.rank >= Level.WARN.rank && since >= 3 * HOUR)
        if (!due) return
        val body = OperatorTextParser.buildReport(
            line.msisdn,
            ParsedInfo(line.quotaMb, line.quotaTotalMb, line.pulsa, line.activeUntil, line.graceUntil, line.voiceMin)
        )
        if (Sim.sendSms(ctx, line.subId, Phone.normalize(line.reportTo), body)) {
            Store.edit(line.id) { it.copy(lastReportAt = now) }
        }
    }
}

/** Membuka aplikasi lain (operator / e-wallet) dan menyalin nomor tujuan. */
object Apps {
    val PAY_APPS = listOf(
        "DANA" to "id.dana",
        "GoPay" to "com.gojek.gopay",
        "Gojek" to "com.gojek.app",
        "OVO" to "ovo.id",
        "Shopee / ShopeePay" to "com.shopee.id",
        "Tokopedia" to "com.tokopedia.tkpd",
        "LinkAja" to "com.telkom.mwallet",
        "BRImo" to "id.co.bri.brimo",
        "Livin' by Mandiri" to "id.bmri.livin",
        "myBCA" to "com.bca.mybca.omni.android",
        "BCA mobile" to "com.bca",
        "BNI Mobile" to "src.com.bni",
        "Flip" to "id.flip",
    )

    fun installed(ctx: Context, pkg: String): Boolean = ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    fun installedPayApps(ctx: Context): List<Pair<String, String>> = PAY_APPS.filter { installed(ctx, it.second) }

    fun operatorAppInstalled(ctx: Context, op: Operator): String? = op.appPackages.firstOrNull { installed(ctx, it) }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i) }.isSuccess
    }

    fun openOperatorApp(ctx: Context, op: Operator) {
        val pkg = operatorAppInstalled(ctx, op)
        if (pkg != null && launch(ctx, pkg)) return
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${op.appPackages.first()}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(op.appName)}&c=apps"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(market) }.onFailure { runCatching { ctx.startActivity(web) } }
    }

    fun copy(ctx: Context, msisdn: String, toast: Boolean = true) {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Nomor", Phone.normalize(msisdn)))
        if (toast) Toast.makeText(ctx, "Nomor ${Phone.pretty(msisdn)} disalin, tinggal tempel", Toast.LENGTH_LONG).show()
    }
}
