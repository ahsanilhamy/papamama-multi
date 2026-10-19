package id.papamama.app.core

import id.papamama.app.model.Phone
import java.time.LocalDate

/** Hasil baca teks dari operator (USSD, SMS, atau teks yang ditempel pengguna). */
data class ParsedInfo(
    val quotaMb: Double? = null,
    val quotaTotalMb: Double? = null,
    val pulsa: Long? = null,
    val activeUntil: LocalDate? = null,
    val graceUntil: LocalDate? = null,
    val voiceMin: Int? = null,
    /** Hanya untuk laporan "#PM#" dari HP keluarga. */
    val reportMsisdn: String? = null,
) {
    val isEmpty: Boolean
        get() = quotaMb == null && pulsa == null && activeUntil == null && graceUntil == null && voiceMin == null
}

/**
 * Parser teks operator berbasis aturan. Sengaja konservatif: lebih baik tidak terbaca
 * daripada salah baca. Pengguna selalu melihat hasil baca sebelum disimpan (mode tempel).
 */
object OperatorTextParser {

    private val MONTHS = mapOf(
        "jan" to 1, "januari" to 1, "january" to 1,
        "feb" to 2, "peb" to 2, "februari" to 2, "february" to 2,
        "mar" to 3, "maret" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "mei" to 5, "may" to 5,
        "jun" to 6, "juni" to 6, "june" to 6,
        "jul" to 7, "juli" to 7, "july" to 7,
        "agu" to 8, "agt" to 8, "ags" to 8, "agus" to 8, "agustus" to 8, "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "okt" to 10, "oct" to 10, "oktober" to 10, "october" to 10,
        "nov" to 11, "nop" to 11, "november" to 11, "nopember" to 11,
        "des" to 12, "dec" to 12, "desember" to 12, "december" to 12,
    )

    private val IC = setOf(RegexOption.IGNORE_CASE)
    private val numericDate = Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})(?![\d])""")
    private val isoDate = Regex("""(?<!\d)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
    private val textDate = Regex("""(?<!\d)(\d{1,2})[\s\-]*([A-Za-z]{3,9})\.?(?:[\s\-,]+(\d{4}|\d{2}))?(?![\d])""")
    private val amountUnit = Regex("""(?<![\d.,])(\d+(?:[.,]\d+)?)\s*(TB|GB|MB|KB)\b""", IC)
    private val ofPattern = Regex(
        """(?<![\d.,])(\d+(?:[.,]\d+)?)\s*(TB|GB|MB|KB)?\s*(?:dari|/|of)\s*(\d+(?:[.,]\d+)?)\s*(TB|GB|MB|KB)\b""", IC
    )
    private val voicePattern = Regex("""(?<![\d.,])(\d+)\s*(menit|mnt|mins?)\b""", IC)
    private val pulsaKeyword = Regex("""pulsa|saldo|balance""", IC)
    private val rpNumber = Regex("""(?:rp\.?|idr)\s*:?\s*(\d[\d.,]*)""", IC)
    private val plainNumber = Regex("""^\s*[:=]?\s*(\d[\d.,]*)(?!\s*(gb|mb|kb|hari|hr|menit|mnt|sms|x)\b)""", IC)
    private val menuLine = Regex("""^\s*\d{1,2}\s*[.):]\s*\S""")
    private val priceMarker = Regex("""\brp|\d\s*rb\b|\brb\b|ribu|\d+k\b|harga|beli|promo""", IC)

    private val graceKw = Regex("""tenggang|grace""")
    private val activeKw = Regex("""aktif|active|masa berlaku|kartu|pulsa|saldo|expired|exp\b""")
    private val quotaKw = Regex("""kuota|internet|data|paket|\d\s*(gb|mb)|nelpon|telp|sms|menit""")
    private val skipAmountKw = Regex("""dari|total|awal|terpakai|pemakaian|dipakai|used|kecepatan|speed""")

    fun parse(raw: String, today: LocalDate = LocalDate.now()): ParsedInfo {
        val pm = raw.indexOf("#PM#")
        if (pm >= 0) return parseReport(raw.substring(pm))

        val text = cleanMenu(raw)
        val lower = text.lowercase()

        // --- Tanggal: masa aktif & masa tenggang -------------------------------
        var active: LocalDate? = null
        var grace: LocalDate? = null
        val unknown = mutableListOf<LocalDate>()
        var prevEnd = 0
        for (hit in findDates(text, today)) {
            val from = maxOf(prevEnd, hit.start - 60).coerceAtMost(hit.start)
            val window = lower.substring(from, hit.start)
            prevEnd = hit.end
            when (classify(window)) {
                Kind.GRACE -> if (grace == null) grace = hit.date
                Kind.ACTIVE -> if (active == null) active = hit.date
                Kind.QUOTA -> Unit
                Kind.UNKNOWN -> unknown += hit.date
            }
        }
        if (active == null && unknown.isNotEmpty() && Regex("pulsa|saldo|kartu|aktif").containsMatchIn(lower)) {
            active = unknown.first()
        }

        // --- Kuota data -----------------------------------------------------------
        var quota: Double? = null
        var total: Double? = null
        val ofMatches = ofPattern.findAll(text).toList()
        if (ofMatches.isNotEmpty()) {
            var q = 0.0
            var t = 0.0
            for (m in ofMatches) {
                val unitB = m.groupValues[4]
                val unitA = m.groupValues[2].ifEmpty { unitB }
                q += toMb(m.groupValues[1], unitA)
                t += toMb(m.groupValues[3], unitB)
            }
            quota = q
            total = t
        } else {
            var sum = 0.0
            var found = false
            for (m in amountUnit.findAll(text)) {
                val before = lower.substring(maxOf(0, m.range.first - 25), m.range.first)
                val after = lower.substring(m.range.last + 1, minOf(lower.length, m.range.last + 4))
                if (skipAmountKw.containsMatchIn(before)) continue
                if (after.startsWith("/s") || after.startsWith("ps")) continue
                sum += toMb(m.groupValues[1], m.groupValues[2])
                found = true
            }
            if (found) quota = sum
        }

        // --- Paket telepon ---------------------------------------------------------
        var voice: Int? = null
        for (m in voicePattern.findAll(text)) {
            val before = lower.substring(maxOf(0, m.range.first - 25), m.range.first)
            if (skipAmountKw.containsMatchIn(before)) continue
            voice = (voice ?: 0) + (m.groupValues[1].toIntOrNull() ?: 0)
        }

        return ParsedInfo(
            quotaMb = quota,
            quotaTotalMb = total,
            pulsa = findPulsa(text),
            activeUntil = active,
            graceUntil = grace,
            voiceMin = voice,
        )
    }

    // ---------------------------------------------------------------------------------

    /** Buang baris menu penawaran paket (mis. "1. Combo 10GB Rp50rb") agar tidak terbaca sebagai sisa kuota. */
    fun cleanMenu(raw: String): String =
        raw.replace("\r", "").split('\n')
            .filterNot { menuLine.containsMatchIn(it) && priceMarker.containsMatchIn(it) }
            .joinToString("\n")

    private enum class Kind { GRACE, ACTIVE, QUOTA, UNKNOWN }

    private fun lastIndex(r: Regex, s: String): Int = r.findAll(s).lastOrNull()?.range?.first ?: -1

    private fun classify(window: String): Kind {
        val g = lastIndex(graceKw, window)
        val a = lastIndex(activeKw, window)
        val q = lastIndex(quotaKw, window)
        val best = maxOf(g, a, q)
        return when {
            best < 0 -> Kind.UNKNOWN
            best == g -> Kind.GRACE
            best == a -> Kind.ACTIVE
            else -> Kind.QUOTA
        }
    }

    data class DateHit(val date: LocalDate, val start: Int, val end: Int)

    fun findDates(text: String, today: LocalDate): List<DateHit> {
        val hits = mutableListOf<DateHit>()
        for (m in isoDate.findAll(text)) {
            val (y, mo, d) = m.destructured
            safeDate(y.toInt(), mo.toInt(), d.toInt())?.let { hits += DateHit(it, m.range.first, m.range.last + 1) }
        }
        for (m in numericDate.findAll(text)) {
            if (hits.any { overlaps(it, m.range) }) continue
            val (d, mo, y) = m.destructured
            safeDate(year(y), mo.toInt(), d.toInt())?.let { hits += DateHit(it, m.range.first, m.range.last + 1) }
        }
        for (m in textDate.findAll(text)) {
            if (hits.any { overlaps(it, m.range) }) continue
            val word = m.groupValues[2].lowercase()
            val month = MONTHS[word] ?: MONTHS[word.take(3)] ?: continue
            val day = m.groupValues[1].toInt()
            val y = m.groupValues[3]
            val date = if (y.isNotEmpty()) safeDate(year(y), month, day) else {
                safeDate(today.year, month, day)?.let { if (it.isBefore(today.minusDays(60))) it.plusYears(1) else it }
            }
            date?.let { hits += DateHit(it, m.range.first, m.range.last + 1) }
        }
        return hits.sortedBy { it.start }
    }

    private fun overlaps(h: DateHit, r: IntRange) = r.first < h.end && r.last + 1 > h.start
    private fun year(y: String): Int = if (y.length == 2) 2000 + y.toInt() else y.toInt()
    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        if (y !in 2000..2100) null else runCatching { LocalDate.of(y, m, d) }.getOrNull()

    fun toMb(num: String, unit: String): Double {
        val u = unit.uppercase()
        val v: Double = if (num.contains('.') || num.contains(',')) {
            val sep = maxOf(num.lastIndexOf('.'), num.lastIndexOf(','))
            val frac = num.substring(sep + 1)
            if (frac.length == 3 && (u == "MB" || u == "KB")) {
                num.filter { it.isDigit() }.toDouble()
            } else {
                num.replace(',', '.').toDoubleOrNull() ?: 0.0
            }
        } else num.toDouble()
        return when (u) {
            "TB" -> v * 1024 * 1024
            "GB" -> v * 1024
            "KB" -> v / 1024
            else -> v
        }
    }

    private fun findPulsa(text: String): Long? {
        for (k in pulsaKeyword.findAll(text)) {
            val sub = text.substring(k.range.last + 1, minOf(text.length, k.range.last + 1 + 30))
            val rp = rpNumber.find(sub)
            if (rp != null) return parseRupiah(rp.groupValues[1])
            val plain = plainNumber.find(sub)
            if (plain != null) return parseRupiah(plain.groupValues[1])
        }
        return null
    }

    fun parseRupiah(s: String): Long? {
        var t = s.trimEnd('.', ',')
        t = t.replace(Regex("""[.,]\d{1,2}$"""), "")
        return t.filter { it.isDigit() }.toLongOrNull()
    }

    // --- Laporan antar-HP (Mode Pelapor) -----------------------------------------------

    fun buildReport(msisdn: String, info: ParsedInfo): String {
        fun d(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "-"
        val k = if (info.quotaMb == null) "-" else d(info.quotaMb) + "/" + d(info.quotaTotalMb)
        return "#PM# ${Phone.normalize(msisdn)} K=$k P=${info.pulsa ?: "-"} " +
            "A=${info.activeUntil ?: "-"} T=${info.graceUntil ?: "-"} V=${info.voiceMin ?: "-"} (PapaMama)"
    }

    fun parseReport(text: String): ParsedInfo {
        val tokens = text.removePrefix("#PM#").trim().split(Regex("""\s+"""))
        val msisdn = tokens.firstOrNull()?.let { Phone.normalize(it) }?.takeIf { it.length >= 8 }
        val map = tokens.drop(1).mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).uppercase() to it.substring(i + 1)
        }.toMap()
        fun v(k: String) = map[k]?.takeIf { it != "-" && it.isNotBlank() }
        val k = v("K")
        val q = k?.substringBefore('/')?.toDoubleOrNull()
        val t = k?.substringAfter('/', "")?.toDoubleOrNull()
        return ParsedInfo(
            quotaMb = q,
            quotaTotalMb = t,
            pulsa = v("P")?.toLongOrNull(),
            activeUntil = v("A")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            graceUntil = v("T")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            voiceMin = v("V")?.toIntOrNull(),
            reportMsisdn = msisdn,
        )
    }
}
