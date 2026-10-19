package id.papamama.app.core

import id.papamama.app.model.DeviceType
import id.papamama.app.model.Line
import id.papamama.app.model.Operator
import id.papamama.app.model.Purchase
import id.papamama.app.model.Settings
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object Fmt {
    private val ID = Locale("id", "ID")
    private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

    fun mb(mb: Double): String {
        if (mb < 1024) return "${mb.roundToInt()} MB"
        val gb = mb / 1024.0
        val s = if (gb >= 100) String.format(ID, "%.0f", gb) else String.format(ID, "%.1f", gb)
        return s.removeSuffix(",0") + " GB"
    }

    fun gbInput(mb: Double): String {
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f", gb).trimEnd('0').trimEnd('.').replace('.', ',')
    }

    fun rp(v: Long): String = "Rp " + NumberFormat.getIntegerInstance(ID).format(v)

    fun date(d: LocalDate, today: LocalDate = LocalDate.now()): String =
        "${d.dayOfMonth} ${MONTHS[d.monthValue - 1]}" + if (d.year != today.year) " ${d.year}" else ""

    fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0) return "belum pernah"
        val m = (now - millis) / 60_000
        return when {
            m < 1 -> "baru saja"
            m < 60 -> "$m menit lalu"
            m < 1440 -> "${m / 60} jam lalu"
            else -> "${m / 1440} hari lalu"
        }
    }

    fun parseGb(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    fun parseRp(s: String): Long? = s.filter { it.isDigit() }.toLongOrNull()
}

object Codes {
    fun check(s: Settings, op: Operator): List<String> =
        s.codes["${op.name}.check"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: op.checkCodes

    fun buy(s: Settings, op: Operator): String = s.codes["${op.name}.buy"]?.trim() ?: op.buyCode
    fun transfer(s: Settings, op: Operator): String = s.codes["${op.name}.transfer"]?.trim() ?: op.transferCode
}

enum class Level(val label: String, val rank: Int) {
    OK("Aman", 0), STALE("Cek ulang", 1), WARN("Segera", 2), CRIT("Kritis", 3), GRACE("Tenggang", 4)
}

data class LineStatus(
    val level: Level,
    val reasons: List<String>,
    val quotaMb: Double?,
    val quotaIsEstimate: Boolean,
    val quotaPct: Double?,
    val daysLeft: Long?,
    val runOutDays: Double?,
    val usagePerDay: Double?,
)

object StatusEngine {
    /** Perkiraan awal pemakaian jam anak (tracking + chat) sebelum app mempelajari pola aslinya. */
    const val WATCH_DEFAULT_MB_PER_DAY = 30.0
    private const val DAY_MS = 86_400_000.0

    fun usagePerDay(line: Line): Double? =
        line.dailyUsageMb ?: if (line.device == DeviceType.WATCH) WATCH_DEFAULT_MB_PER_DAY else null

    fun evaluate(line: Line, settings: Settings, now: Long, today: LocalDate): LineStatus {
        val reasons = mutableListOf<String>()
        var level = Level.OK
        fun raise(l: Level, why: String) {
            if (l.rank > level.rank) level = l
            reasons += why
        }

        // Kuota (dengan estimasi bila data sudah lama)
        val usage = usagePerDay(line)
        var q = line.quotaMb
        var estimate = false
        if (q != null && line.updatedAt > 0 && usage != null && usage > 0) {
            val days = (now - line.updatedAt) / DAY_MS
            if (days >= 0.5) {
                q = max(0.0, q - usage * days)
                estimate = true
            }
        }
        val total = line.quotaTotalMb
        val pct = if (q != null && total != null && total > 0) (q / total).coerceIn(0.0, 1.0) else null
        if (q != null && line.alertQuota) {
            val crit = pct?.let { it <= 0.05 } ?: (q <= 100)
            val warn = pct?.let { it <= 0.20 } ?: (q <= 500)
            when {
                q <= 0.0 -> raise(Level.CRIT, if (estimate) "Kuota diperkirakan sudah habis" else "Kuota habis")
                crit -> raise(Level.CRIT, "Kuota hampir habis")
                warn -> raise(Level.WARN, "Kuota menipis")
            }
        }
        val runOut = if (q != null && usage != null && usage > 0) q / usage else null
        if (runOut != null && q != null && q > 0 && runOut <= 3 && line.alertQuota && level.rank < Level.WARN.rank) {
            raise(Level.WARN, "Kuota diperkirakan habis ±${ceil(runOut).toInt()} hari lagi")
        }

        // Masa aktif & tenggang
        val daysLeft = line.activeUntil?.let { ChronoUnit.DAYS.between(today, it) }
        if (line.operator.prepaid && daysLeft != null && line.alertActive) {
            when {
                daysLeft < 0 -> {
                    val g = line.graceUntil
                    if (g != null && today.isAfter(g)) raise(Level.GRACE, "Masa tenggang sudah lewat, nomor bisa hangus")
                    else raise(Level.GRACE, "Masa aktif habis, sekarang masa tenggang")
                }
                daysLeft == 0L -> raise(Level.CRIT, "Masa aktif habis hari ini")
                daysLeft == 1L -> raise(Level.CRIT, "Masa aktif habis besok")
                daysLeft <= 7 -> raise(Level.WARN, "Masa aktif $daysLeft hari lagi")
            }
        }

        // Pulsa
        val p = line.pulsa
        if (line.alertPulsa && p != null && p < settings.pulsaThreshold) {
            raise(Level.WARN, "Pulsa di bawah ${Fmt.rp(settings.pulsaThreshold)}")
        }

        // Data kedaluwarsa
        if (line.updatedAt == 0L) raise(Level.STALE, "Belum ada data, perbarui sekali")
        else {
            val d = ((now - line.updatedAt) / DAY_MS).toInt()
            if (d >= settings.staleDays) raise(Level.STALE, "Data terakhir diperbarui $d hari lalu")
        }

        return LineStatus(level, reasons, q, estimate, pct, daysLeft, runOut, usage)
    }
}

enum class AlertAction { BUY, UPDATE }

data class AlertEvent(
    val key: String,
    val title: String,
    val body: String,
    val urgent: Boolean,
    val action: AlertAction,
    val actionLabel: String,
)

/**
 * Aturan notifikasi (mengikuti PapaMama: kuota 20% & 5%, masa aktif H-7/H-3/H-1, tenggang),
 * ditambah pulsa minimum dan pengingat data yang sudah lama tidak diperbarui.
 * Kunci (key) mencegah notifikasi yang sama dikirim berulang. Dengan Penjaga Otomatis
 * (autoGuard), peringatan kritis diulang setiap hari sampai beres.
 */
object AlertRules {
    fun events(line: Line, st: LineStatus, settings: Settings, today: LocalDate): List<AlertEvent> {
        val out = mutableListOf<AlertEvent>()
        val name = line.displayName
        val day = today.toEpochDay()
        fun daily(base: String) = if (line.autoGuard) "$base@$day" else base
        val guardNote = if (line.autoGuard) " Penjaga Otomatis akan mengingatkan tiap hari sampai beres." else ""

        val q = st.quotaMb
        if (line.alertQuota && q != null) {
            val est = if (st.quotaIsEstimate) " (perkiraan)" else ""
            val pct = st.quotaPct
            val critical = q <= 0 || (pct?.let { it <= 0.05 } ?: (q <= 100))
            val low = pct?.let { it <= 0.20 } ?: (q <= 500)
            when {
                critical -> out += AlertEvent(
                    daily("q5"),
                    if (q <= 0) "Kuota $name habis$est" else "Kuota $name tinggal ${pct?.let { "${(it * 100).roundToInt()}%" } ?: Fmt.mb(q)}",
                    "Sisa ${Fmt.mb(q)}$est. Kirim kuota atau beli paket sekarang?$guardNote",
                    true, AlertAction.BUY, "Kirim kuota"
                )
                low -> out += AlertEvent(
                    "q20",
                    "Kuota $name tinggal ${pct?.let { "${(it * 100).roundToInt()}%" } ?: Fmt.mb(q)}",
                    "Sisa ${Fmt.mb(q)}$est. Siapkan paket berikutnya.",
                    false, AlertAction.BUY, "Beli paket"
                )
                st.runOutDays != null && st.runOutDays <= 3 -> out += AlertEvent(
                    "r_${line.updatedAt}",
                    "Kuota $name diperkirakan habis ±${ceil(st.runOutDays).toInt()} hari lagi",
                    "Berdasarkan pemakaian rata-rata ${Fmt.mb(st.usagePerDay ?: 0.0)}/hari.",
                    false, AlertAction.BUY, "Beli paket"
                )
            }
        }

        val d = st.daysLeft
        val until = line.activeUntil
        if (line.operator.prepaid && line.alertActive && d != null && until != null) {
            val tag = until.toEpochDay()
            when {
                d < 0 -> out += AlertEvent(
                    daily("g$tag"),
                    "$name sudah masuk masa tenggang",
                    (line.graceUntil?.let { "Isi pulsa/paket sebelum ${Fmt.date(it, today)} agar nomor tidak hangus." }
                        ?: "Isi pulsa/paket secepatnya agar nomor tidak hangus.") + guardNote,
                    true, AlertAction.BUY, "Perpanjang"
                )
                d <= 1 -> out += AlertEvent(
                    daily("a1_$tag"),
                    "Masa aktif $name " + if (d == 0L) "habis hari ini" else "habis besok",
                    "Perpanjang sekarang supaya nomor tidak terputus.$guardNote",
                    true, AlertAction.BUY, "Perpanjang"
                )
                d <= 3 -> out += AlertEvent(
                    "a3_$tag", "Masa aktif $name $d hari lagi",
                    "Aktif sampai ${Fmt.date(until, today)}. Perpanjang sekarang?",
                    false, AlertAction.BUY, "Perpanjang"
                )
                d <= 7 -> out += AlertEvent(
                    "a7_$tag", "Masa aktif $name $d hari lagi",
                    "Aktif sampai ${Fmt.date(until, today)}.",
                    false, AlertAction.BUY, "Perpanjang"
                )
            }
        }

        val p = line.pulsa
        if (line.alertPulsa && p != null && p < settings.pulsaThreshold) {
            out += AlertEvent(
                "p", "Pulsa $name tinggal ${Fmt.rp(p)}",
                "Di bawah batas ${Fmt.rp(settings.pulsaThreshold)}.",
                false, AlertAction.BUY, "Isi pulsa"
            )
        }

        if (line.updatedAt > 0 && st.level.rank <= Level.STALE.rank && st.reasons.any { it.startsWith("Data terakhir") }) {
            out += AlertEvent(
                "s${day / settings.staleDays.coerceAtLeast(1)}",
                "Data $name perlu diperbarui",
                "Sudah lama tidak diperbarui. Ketuk untuk update cepat (tempel SMS/teks operator).",
                false, AlertAction.UPDATE, "Update data"
            )
        }
        return out
    }
}

/** Penggabungan data baru ke data nomor yang ada (murni, mudah diuji). */
object Merge {
    fun reading(line: Line, info: ParsedInfo, source: String, raw: String, now: Long, pulsaThreshold: Long): Line {
        var l = line
        val clear = mutableSetOf<String>()
        info.quotaMb?.let { q ->
            val old = line.quotaMb
            var total = info.quotaTotalMb ?: line.quotaTotalMb
            if (old != null && line.updatedAt > 0) {
                val hours = (now - line.updatedAt) / 3_600_000.0
                if (q < old && hours >= 6) {
                    val rate = (old - q) / (hours / 24.0)
                    if (rate in 0.5..20_000.0) {
                        l = l.copy(dailyUsageMb = line.dailyUsageMb?.let { it * 0.5 + rate * 0.5 } ?: rate)
                    }
                }
            }
            if (old == null || q > old * 1.1 + 50) {
                clear += "q"; clear += "r_"
                if (info.quotaTotalMb == null && (total == null || q > total)) total = q
            }
            if (total != null && q > total) total = q
            l = l.copy(quotaMb = q, quotaTotalMb = total)
        }
        info.pulsa?.let { p ->
            val oldP = line.pulsa
            if (p >= pulsaThreshold || (oldP != null && p > oldP)) clear += "p"
            l = l.copy(pulsa = p)
        }
        info.voiceMin?.let { l = l.copy(voiceMin = it) }
        info.activeUntil?.let { l = l.copy(activeUntil = it) }
        info.graceUntil?.let { l = l.copy(graceUntil = it) }
        val notified = l.notified.filterNot { k -> clear.any { k.startsWith(it) } }.toSet()
        return l.copy(updatedAt = now, source = source, lastRaw = raw.take(1500), notified = notified)
    }

    fun purchase(
        line: Line,
        currentQuotaMb: Double?,
        addQuotaMb: Double?,
        addDays: Int?,
        setActive: LocalDate?,
        addPulsa: Long?,
        price: Long?,
        note: String,
        now: Long,
        today: LocalDate,
    ): Line {
        var l = line
        if (addQuotaMb != null && addQuotaMb > 0) {
            val q = (currentQuotaMb ?: 0.0).coerceAtLeast(0.0) + addQuotaMb
            l = l.copy(quotaMb = q, quotaTotalMb = q)
        } else if (currentQuotaMb != null) {
            l = l.copy(quotaMb = currentQuotaMb)
        }
        val newActive = setActive ?: addDays?.let {
            val cand = today.plusDays(it.toLong())
            val cur = line.activeUntil
            if (cur != null && cur.isAfter(cand)) cur else cand
        }
        if (newActive != null) l = l.copy(activeUntil = newActive)
        if (addPulsa != null && addPulsa > 0) l = l.copy(pulsa = (line.pulsa ?: 0) + addPulsa)
        val notified = l.notified.filterNot { it.startsWith("q") || it.startsWith("p") || it.startsWith("r_") }.toSet()
        return l.copy(
            updatedAt = now,
            source = "Dicatat setelah beli",
            notified = notified,
            purchases = (listOf(Purchase(now, note, price)) + line.purchases).take(50),
        )
    }
}
