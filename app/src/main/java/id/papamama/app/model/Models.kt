package id.papamama.app.model

import java.time.LocalDate

enum class OpGroup(val label: String) {
    TELKOMSEL("Telkomsel"), XL("XL"), INDOSAT("Indosat"), TRI("Tri"), SMARTFREN("Smartfren")
}

/**
 * Operator yang didukung. Kode USSD adalah nilai bawaan dan bisa diubah pengguna
 * di Pengaturan, karena operator kadang mengganti kodenya.
 */
enum class Operator(
    val label: String,
    val group: OpGroup,
    val prepaid: Boolean,
    val checkCodes: List<String>,
    val buyCode: String,
    val transferCode: String,
    val appName: String,
    val appPackages: List<String>,
    val color: Long,
) {
    TELKOMSEL("Telkomsel", OpGroup.TELKOMSEL, true, listOf("*888#"), "*363#", "*858#",
        "MyTelkomsel", listOf("com.telkomsel.telkomselcm"), 0xFFE4002B),
    SIMPATI("simPATI", OpGroup.TELKOMSEL, true, listOf("*888#"), "*363#", "*858#",
        "MyTelkomsel", listOf("com.telkomsel.telkomselcm"), 0xFFE4002B),
    HALO("Halo", OpGroup.TELKOMSEL, false, listOf("*888#"), "*363#", "",
        "MyTelkomsel", listOf("com.telkomsel.telkomselcm"), 0xFFB0001E),
    BYU("by.U", OpGroup.TELKOMSEL, true, listOf("*888#"), "", "",
        "by.U", listOf("com.byu.id"), 0xFF13A89E),
    XL("XL", OpGroup.XL, true, listOf("*123#", "*123*7*1#"), "*123#", "*123*168#",
        "myXL", listOf("com.apps.MyXL"), 0xFF0057B8),
    INDOSAT("Indosat", OpGroup.INDOSAT, true, listOf("*123#"), "*123#", "*123*151#",
        "myIM3", listOf("com.pure.indosat.care"), 0xFFE8A200),
    TRI("Tri", OpGroup.TRI, true, listOf("*111*1#", "*111*4*2*1*1#"), "*123#", "*323#",
        "bima+", listOf("com.hutchison3.bima"), 0xFF8E1F8F),
    SMARTFREN("Smartfren", OpGroup.SMARTFREN, true, listOf("*999#", "*995#"), "*123#", "*879#",
        "MySF", listOf("com.smartfren.mysf", "com.smartfren"), 0xFFE6007E);

    companion object {
        /** Tebakan operator dari prefix nomor (by.U/Halo/simPATI tidak bisa dibedakan dari prefix). */
        fun guessFromNumber(raw: String): Operator? {
            val n = Phone.normalize(raw)
            if (n.length < 4) return null
            return when (n.substring(0, 4)) {
                "0811", "0812", "0813", "0821", "0822", "0823", "0851", "0852", "0853" -> TELKOMSEL
                "0817", "0818", "0819", "0859", "0877", "0878", "0831", "0832", "0833", "0838" -> XL
                "0814", "0815", "0816", "0855", "0856", "0857", "0858" -> INDOSAT
                "0895", "0896", "0897", "0898", "0899" -> TRI
                "0881", "0882", "0883", "0884", "0885", "0886", "0887", "0888", "0889" -> SMARTFREN
                else -> null
            }
        }
    }
}

enum class DeviceType(val label: String) { PHONE("HP"), WATCH("Jam anak"), OTHER("Lainnya") }

data class Purchase(val at: Long, val note: String, val price: Long?)

data class Line(
    val id: String,
    val name: String,
    val msisdn: String,
    val operator: Operator,
    val device: DeviceType = DeviceType.PHONE,
    val avatar: String = "",
    /** subscriptionId SIM bila kartu ini terpasang di HP ini; -1 jika tidak. */
    val subId: Int = -1,
    val quotaMb: Double? = null,
    val quotaTotalMb: Double? = null,
    val pulsa: Long? = null,
    val voiceMin: Int? = null,
    val activeUntil: LocalDate? = null,
    val graceUntil: LocalDate? = null,
    val updatedAt: Long = 0L,
    val source: String = "",
    val lastRaw: String = "",
    /** Rata-rata pemakaian data per hari (MB), dipelajari otomatis atau diisi manual. */
    val dailyUsageMb: Double? = null,
    val alertQuota: Boolean = true,
    val alertActive: Boolean = true,
    val alertPulsa: Boolean = true,
    val autoGuard: Boolean = false,
    val autoCheck: Boolean = true,
    /** Mode Pelapor: nomor HP orang tua tujuan laporan SMS. Kosong = mati. */
    val reportTo: String = "",
    val lastReportAt: Long = 0L,
    val notified: Set<String> = emptySet(),
    val purchases: List<Purchase> = emptyList(),
) {
    val isLocal: Boolean get() = subId >= 0
    val displayName: String get() = if (device == DeviceType.WATCH) "$name (Watch)" else name
}

data class Settings(
    val userName: String = "Papa",
    val checkHours: Int = 6,
    val pulsaThreshold: Long = 5000,
    val staleDays: Int = 7,
    /** Override kode USSD, kunci: "<OPERATOR>.check|buy|transfer". */
    val codes: Map<String, String> = emptyMap(),
)

data class AppData(val lines: List<Line> = emptyList(), val settings: Settings = Settings())

object Phone {
    fun normalize(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("62")) d = "0" + d.drop(2)
        else if (d.startsWith("8")) d = "0$d"
        return d
    }

    fun pretty(raw: String): String {
        val n = normalize(raw)
        if (n.length < 8) return n
        return n.substring(0, 4) + "-" + n.substring(4, 8) + "-" + n.substring(8)
    }

    fun same(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        return x.isNotEmpty() && x == y
    }
}
