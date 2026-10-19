package id.papamama.app.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.papamama.app.core.Checker
import id.papamama.app.core.Fmt
import id.papamama.app.core.Level
import id.papamama.app.core.Notifier
import id.papamama.app.core.Reporter
import id.papamama.app.core.StatusEngine
import id.papamama.app.core.has
import id.papamama.app.core.declares
import id.papamama.app.data.Store
import id.papamama.app.model.Line
import id.papamama.app.model.Phone
import id.papamama.app.model.Settings
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    line: Line,
    settings: Settings,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onBuy: () -> Unit,
    onUpdate: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val st = StatusEngine.evaluate(line, settings, System.currentTimeMillis(), today)
    val colors = st.level.colors()
    var checking by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    var askReport by remember { mutableStateOf(false) }

    val callPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val smsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(ctx, "Tanpa izin kirim SMS, Mode Pelapor tidak bisa jalan", Toast.LENGTH_LONG).show()
    }

    Scaffold(
        containerColor = PM.Bg,
        topBar = {
            TopAppBar(
                title = { Text(line.displayName, fontWeight = FontWeight.Bold, color = PM.Navy) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = PM.Navy) } },
                actions = { IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Ubah", tint = PM.Navy) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PM.Bg),
            )
        },
        bottomBar = {
            val label = when {
                st.level == Level.GRACE || (st.daysLeft != null && st.daysLeft <= 7) -> "Perpanjang sekarang"
                st.level.rank >= Level.WARN.rank -> "Kirim kuota / beli paket"
                else -> "Beli / perpanjang paket"
            }
            BigButton(label, onBuy, Modifier.background(PM.Bg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp))
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Ringkasan kuota / masa aktif -------------------------------------
            SectionCard(border = colors.border) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(line, 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${line.operator.label} · ${Phone.pretty(line.msisdn)}", color = PM.Muted, fontSize = 13.sp)
                        Text(line.device.label + if (line.isLocal) " · SIM di HP ini" else "", color = PM.Muted, fontSize = 13.sp)
                    }
                    StatusPill(st.level)
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = PM.Stroke)
                Text("Kuota tersisa", color = PM.Muted, fontSize = 13.sp)
                val q = st.quotaMb
                if (q != null) {
                    Text(
                        (if (st.quotaIsEstimate) "± " else "") + Fmt.mb(q) + (line.quotaTotalMb?.let { " dari ${Fmt.mb(it)}" } ?: ""),
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = PM.Navy,
                    )
                    st.quotaPct?.let { p ->
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { p.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = if (p <= 0.20) PM.Red else PM.Navy,
                            trackColor = Color(0xFFE3E1F7),
                        )
                    }
                    if (st.quotaIsEstimate) {
                        Spacer(Modifier.height(4.dp))
                        Hint("Perkiraan dari data terakhir (${Fmt.mb(line.quotaMb ?: 0.0)}, ${Fmt.ago(line.updatedAt)}) dan pemakaian ±${Fmt.mb(st.usagePerDay ?: 0.0)}/hari.")
                    }
                } else Text("Belum diketahui", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PM.Muted)

                Spacer(Modifier.height(10.dp))
                InfoRow("Masa aktif", when {
                    !line.operator.prepaid -> "Pascabayar (tanpa masa aktif)"
                    line.activeUntil == null -> "Belum diketahui"
                    st.daysLeft != null && st.daysLeft < 0 -> "Lewat ${-st.daysLeft} hari (${Fmt.date(line.activeUntil, today)})"
                    else -> "Sampai ${Fmt.date(line.activeUntil, today)} · ${st.daysLeft} hari lagi"
                })
                line.graceUntil?.let { InfoRow("Masa tenggang", "Sampai ${Fmt.date(it, today)}") }
                InfoRow("Pulsa", line.pulsa?.let { Fmt.rp(it) } ?: "Belum diketahui")
                line.voiceMin?.let { InfoRow("Paket telepon", "$it menit") }
                InfoRow("Diperbarui", Fmt.ago(line.updatedAt) + if (line.source.isNotBlank()) " · ${line.source}" else "")
            }

            if (st.reasons.isNotEmpty()) {
                SectionCard(border = colors.border) {
                    st.reasons.forEach { Text("• $it", color = if (st.level.rank >= Level.WARN.rank) colors.fg else PM.StaleFg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                }
            }

            // --- Perbarui data ---------------------------------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (line.isLocal) {
                    OutlinedButton(
                        onClick = {
                            if (!ctx.has(Manifest.permission.CALL_PHONE)) {
                                callPerm.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECEIVE_SMS))
                                return@OutlinedButton
                            }
                            checking = true
                            scope.launch {
                                val r = Checker.checkLine(ctx, line)
                                checking = false
                                Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                                Notifier.evaluateAll(ctx)
                            }
                        },
                        enabled = !checking,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Cek sekarang", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onUpdate,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (line.isLocal) "Update manual" else "Update data", fontWeight = FontWeight.Bold) }
            }
            if (!line.isLocal) {
                Hint("Nomor ini tidak ada di HP ini, jadi tidak bisa dicek otomatis. Cara cepat: salin SMS/teks dari operator lalu tempel, " +
                    "atau tekan \"Bagikan → PapaMama\" dari aplikasi SMS. Untuk HP keluarga lain, pasang PapaMama di sana dan nyalakan Mode Pelapor.")
            }

            // --- Pengaturan peringatan ------------------------------------------------
            SectionCard {
                ToggleRow("Alert kuota", "Sisa 20% dan 5%", line.alertQuota) { v -> Store.edit(line.id) { it.copy(alertQuota = v) } }
                if (line.operator.prepaid) {
                    ToggleRow("Alert masa aktif", "H-7, H-3, H-1, dan masa tenggang", line.alertActive) { v -> Store.edit(line.id) { it.copy(alertActive = v) } }
                }
                ToggleRow("Alert pulsa", "Di bawah ${Fmt.rp(settings.pulsaThreshold)}", line.alertPulsa) { v -> Store.edit(line.id) { it.copy(alertPulsa = v) } }
                ToggleRow("Penjaga Otomatis", "Ingatkan tiap hari saat kritis sampai beres, plus tombol perpanjang 1 tap", line.autoGuard) { v ->
                    Store.edit(line.id) { it.copy(autoGuard = v) }
                }
                if (line.isLocal) {
                    ToggleRow("Cek otomatis", "Tiap ${settings.checkHours} jam lewat USSD, tanpa buka apa pun", line.autoCheck) { v -> Store.edit(line.id) { it.copy(autoCheck = v) } }
                    if (ctx.declares(Manifest.permission.SEND_SMS)) ToggleRow(
                        "Mode Pelapor",
                        if (line.reportTo.isBlank()) "Kirim status nomor ini ke HP orang tua lewat SMS (maks. ±1 SMS/hari)"
                        else "Aktif → ${Phone.pretty(line.reportTo)}" + (if (line.lastReportAt > 0) " · terakhir ${Fmt.ago(line.lastReportAt)}" else ""),
                        line.reportTo.isNotBlank(),
                    ) { v -> if (v) askReport = true else Store.edit(line.id) { it.copy(reportTo = "") } }
                }
            }

            // --- Belanja ---------------------------------------------------------------
            val ym = YearMonth.now()
            val zone = ZoneId.systemDefault()
            val monthSpend = line.purchases.filter {
                YearMonth.from(java.time.Instant.ofEpochMilli(it.at).atZone(zone)) == ym
            }.sumOf { it.price ?: 0L }
            if (line.purchases.isNotEmpty()) {
                SectionCard {
                    Text("Riwayat beli", fontWeight = FontWeight.Bold, color = PM.Navy)
                    Text("Bulan ini: ${Fmt.rp(monthSpend)}", color = PM.Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    line.purchases.take(4).forEach { p ->
                        val d = java.time.Instant.ofEpochMilli(p.at).atZone(zone).toLocalDate()
                        InfoRow(Fmt.date(d, today), p.note + (p.price?.let { " · ${Fmt.rp(it)}" } ?: ""))
                    }
                }
            }

            if (line.lastRaw.isNotBlank()) {
                Text(
                    if (showRaw) "Sembunyikan teks asli dari operator" else "Lihat teks asli dari operator",
                    color = PM.Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    modifier = Modifier.clickable { showRaw = !showRaw }.padding(vertical = 4.dp),
                )
                if (showRaw) SectionCard { Text(line.lastRaw, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF3A3950)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (askReport) {
        var to by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askReport = false },
            title = { Text("Mode Pelapor") },
            text = {
                Column {
                    Hint("Nyalakan ini di HP anggota keluarga (mis. HP Mama). Status nomor ini dikirim lewat SMS ke HP orang tua yang juga memasang PapaMama, " +
                        "maks. ±1 SMS per hari (lebih cepat bila kritis). Tarif SMS normal operator berlaku.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = to, onValueChange = { to = it }, label = { Text("Nomor HP orang tua") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = Phone.normalize(to).length >= 9, onClick = {
                    Store.edit(line.id) { it.copy(reportTo = Phone.normalize(to), lastReportAt = 0) }
                    askReport = false
                    if (!ctx.has(Manifest.permission.SEND_SMS)) smsPerm.launch(Manifest.permission.SEND_SMS)
                    else Store.line(line.id)?.let { Reporter.maybeReport(ctx, it, force = true) }
                }) { Text("Aktifkan") }
            },
            dismissButton = { TextButton(onClick = { askReport = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = PM.Muted, fontSize = 14.sp, modifier = Modifier.width(118.dp))
        Text(value, color = Color(0xFF1B1A2E), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
