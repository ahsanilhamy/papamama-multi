package id.papamama.app.data

import id.papamama.app.model.DeviceType
import id.papamama.app.model.Line
import id.papamama.app.model.Operator
import id.papamama.app.model.Purchase
import java.time.LocalDate

/** Data contoh (fiktif) untuk presentasi, mengikuti skenario di deck PapaMama. */
object Demo {
    fun load() {
        val now = System.currentTimeMillis()
        val t = LocalDate.now()
        val h = 3_600_000L
        val lines = listOf(
            Line("demo-papa", "Papa", "081234567801", Operator.TELKOMSEL, DeviceType.PHONE, "👨",
                quotaMb = 28.0 * 1024, quotaTotalMb = 50.0 * 1024, pulsa = 45_000, voiceMin = 120,
                activeUntil = t.plusDays(54), updatedAt = now - 2 * h, source = "USSD otomatis"),
            Line("demo-mama", "Mama", "087812345602", Operator.XL, DeviceType.PHONE, "👩",
                quotaMb = 15.0 * 1024, quotaTotalMb = 30.0 * 1024, pulsa = 22_500,
                activeUntil = t.plusDays(47), updatedAt = now - 5 * h, source = "Laporan SMS dari HP Mama"),
            Line("demo-kakak", "Kakak", "085212345603", Operator.SIMPATI, DeviceType.WATCH, "👦",
                quotaMb = 0.4 * 1024, quotaTotalMb = 5.0 * 1024, pulsa = 3_000,
                activeUntil = t.plusDays(9), updatedAt = now - 3 * h, source = "Tempel teks",
                dailyUsageMb = 35.0, autoGuard = true,
                purchases = listOf(Purchase(now - 20 * 24 * h, "Paket 5 GB · 30 hari", 25_000))),
            Line("demo-adik", "Adik", "089612345604", Operator.TRI, DeviceType.WATCH, "👧",
                quotaMb = 2.1 * 1024, quotaTotalMb = 3.0 * 1024, pulsa = 8_000,
                activeUntil = t.plusDays(3), updatedAt = now - 8 * 24 * h, source = "Dicatat setelah beli",
                dailyUsageMb = 28.0),
            Line("demo-nenek", "Nenek", "088112345605", Operator.SMARTFREN, DeviceType.PHONE, "👵",
                pulsa = 12_000, activeUntil = t.plusDays(40), updatedAt = now - 26 * h, source = "Diperbarui manual"),
        )
        Store.update { it.copy(lines = lines) }
    }
}
