package id.papamama.app

import id.papamama.app.core.AlertRules
import id.papamama.app.core.Level
import id.papamama.app.core.Merge
import id.papamama.app.core.OperatorTextParser
import id.papamama.app.core.ParsedInfo
import id.papamama.app.core.StatusEngine
import id.papamama.app.model.DeviceType
import id.papamama.app.model.Line
import id.papamama.app.model.Operator
import id.papamama.app.model.Phone
import id.papamama.app.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParserTest {
    private val today = LocalDate.of(2026, 10, 19)
    private fun p(s: String) = OperatorTextParser.parse(s, today)

    @Test fun telkomselPulsaAktifTenggang() {
        val r = p("Sisa Pulsa Rp 12.345 aktif s.d. 12/12/2026. Masa tenggang s.d. 11/01/2027")
        assertEquals(12345L, r.pulsa)
        assertEquals(LocalDate.of(2026, 12, 12), r.activeUntil)
        assertEquals(LocalDate.of(2027, 1, 11), r.graceUntil)
    }

    @Test fun menuOffersAreIgnored() {
        val r = p("Pulsa:Rp5.000 Aktif sd 22-11-2026\n1.Xtra Combo 10GB Rp50rb\n2.Beli Paket\n3.Info")
        assertEquals(5000L, r.pulsa)
        assertEquals(LocalDate.of(2026, 11, 22), r.activeUntil)
        assertNull(r.quotaMb)
    }

    @Test fun multipleQuotasSummedAndCardDatePreferred() {
        val r = p("Kuota Internet 2,5GB s.d. 10/11/2026; Kuota Lokal 1.024MB. Pulsa Rp1.500, masa aktif 01 Des 2026")
        assertEquals(2.5 * 1024 + 1024, r.quotaMb!!, 0.01)
        assertEquals(1500L, r.pulsa)
        assertEquals(LocalDate.of(2026, 12, 1), r.activeUntil)
    }

    @Test fun remainingOfTotal() {
        val r = p("Sisa kuota 400 MB dari 5 GB")
        assertEquals(400.0, r.quotaMb!!, 0.01)
        assertEquals(5120.0, r.quotaTotalMb!!, 0.01)
    }

    @Test fun quotaValidityIsNotCardValidity() {
        val r = p("Sisa kuota Anda 1.23GB, Nelpon 100 menit, berlaku hingga 5 Nov")
        assertEquals(1.23 * 1024, r.quotaMb!!, 0.5)
        assertEquals(100, r.voiceMin)
        assertNull(r.activeUntil)
    }

    @Test fun textMonthWithoutYear() {
        val r = p("Pulsa Anda Rp 20.000. Kartu aktif sampai 3 Jan")
        assertEquals(20000L, r.pulsa)
        assertEquals(LocalDate.of(2027, 1, 3), r.activeUntil)
    }

    @Test fun reportRoundTrip() {
        val info = ParsedInfo(1024.0, 5120.0, 5000, LocalDate.of(2026, 12, 12), null, 100)
        val sms = OperatorTextParser.buildReport("+62 812-3456-7890", info)
        val r = OperatorTextParser.parse("Pesan: $sms")
        assertEquals("081234567890", r.reportMsisdn)
        assertEquals(1024.0, r.quotaMb!!, 0.01)
        assertEquals(5120.0, r.quotaTotalMb!!, 0.01)
        assertEquals(5000L, r.pulsa)
        assertEquals(LocalDate.of(2026, 12, 12), r.activeUntil)
        assertNull(r.graceUntil)
        assertEquals(100, r.voiceMin)
    }

    @Test fun phoneHelpers() {
        assertEquals("081234567890", Phone.normalize("+62 812-3456-7890"))
        assertEquals(Operator.TRI, Operator.guessFromNumber("0896 1234 5678"))
        assertEquals(Operator.SMARTFREN, Operator.guessFromNumber("6288112345678"))
        assertEquals(Operator.XL, Operator.guessFromNumber("0878"))
    }
}

class StatusTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val now = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L
    private val s = Settings()
    private val hour = 3_600_000L

    private fun watch(q: Double, total: Double, days: Long, updatedHoursAgo: Long = 1) = Line(
        "w", "Kakak", "085212345603", Operator.SIMPATI, DeviceType.WATCH,
        quotaMb = q, quotaTotalMb = total, pulsa = 10_000,
        activeUntil = today.plusDays(days), updatedAt = now - updatedHoursAgo * hour,
    )

    @Test fun lowQuotaIsWarn() {
        val st = StatusEngine.evaluate(watch(0.4 * 1024, 5 * 1024.0, 9), s, now, today)
        assertEquals(Level.WARN, st.level)
    }

    @Test fun fivePercentIsCritAndAlerts() {
        val line = watch(200.0, 5 * 1024.0, 20)
        val st = StatusEngine.evaluate(line, s, now, today)
        assertEquals(Level.CRIT, st.level)
        val ev = AlertRules.events(line, st, s, today)
        assertTrue(ev.any { it.key == "q5" && it.urgent })
    }

    @Test fun expiredIsGrace() {
        val st = StatusEngine.evaluate(watch(3000.0, 5000.0, -2), s, now, today)
        assertEquals(Level.GRACE, st.level)
    }

    @Test fun h3AlertKeyedByDate() {
        val line = watch(4000.0, 5000.0, 3)
        val st = StatusEngine.evaluate(line, s, now, today)
        val ev = AlertRules.events(line, st, s, today)
        assertTrue(ev.any { it.key == "a3_${today.plusDays(3).toEpochDay()}" })
    }

    @Test fun watchQuotaIsEstimatedWhenStale() {
        val st = StatusEngine.evaluate(watch(1024.0, 3072.0, 30, updatedHoursAgo = 48), s, now, today)
        assertTrue(st.quotaIsEstimate)
        assertEquals(1024.0 - 2 * StatusEngine.WATCH_DEFAULT_MB_PER_DAY, st.quotaMb!!, 1.0)
    }

    @Test fun autoGuardRepeatsDaily() {
        val line = watch(4000.0, 5000.0, 1).copy(autoGuard = true)
        val st = StatusEngine.evaluate(line, s, now, today)
        val ev = AlertRules.events(line, st, s, today)
        assertTrue(ev.any { it.key.startsWith("a1_") && it.key.endsWith("@${today.toEpochDay()}") })
    }

    @Test fun newPackageResetsQuotaAlertsAndLearnsUsage() {
        val line = watch(1000.0, 5000.0, 20, updatedHoursAgo = 24).copy(notified = setOf("q20", "a7_1"))
        val used = Merge.reading(line, ParsedInfo(quotaMb = 950.0), "SMS", "", now, 5000)
        assertEquals(50.0, used.dailyUsageMb!!, 0.5)
        val refilled = Merge.reading(used, ParsedInfo(quotaMb = 6000.0), "SMS", "", now + hour, 5000)
        assertTrue("q20" !in refilled.notified)
        assertTrue("a7_1" in refilled.notified)
        assertEquals(6000.0, refilled.quotaTotalMb!!, 0.1)
    }

    @Test fun purchaseExtendsActive() {
        val line = watch(100.0, 5000.0, 2)
        val after = Merge.purchase(line, 100.0, 5 * 1024.0, 30, null, null, 25_000, "Paket 5 GB", now, today)
        assertEquals(today.plusDays(30), after.activeUntil)
        assertEquals(100.0 + 5120.0, after.quotaMb!!, 0.1)
        assertEquals(1, after.purchases.size)
    }
}
