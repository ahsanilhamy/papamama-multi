package id.papamama.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.papamama.app.core.Apps
import id.papamama.app.core.Codes
import id.papamama.app.core.Fmt
import id.papamama.app.core.Merge
import id.papamama.app.core.Notifier
import id.papamama.app.core.OperatorTextParser
import id.papamama.app.core.ParsedInfo
import id.papamama.app.core.Sim
import id.papamama.app.core.StatusEngine
import id.papamama.app.data.Store
import id.papamama.app.model.AppData
import id.papamama.app.model.Line
import id.papamama.app.model.Phone
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) { content() }
    }
}

@Composable
private fun SheetTitle(title: String, sub: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = PM.Navy)
    Text(sub, color = PM.Muted, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
}

// ---------------------------------------------------------------------------------------
// Beli / perpanjang: semua jalan pintas dalam satu layar
// ---------------------------------------------------------------------------------------
@Composable
fun BuySheet(line: Line, data: AppData, onDismiss: () -> Unit, onLaunched: () -> Unit, onRecord: () -> Unit) {
    val ctx = LocalContext.current
    val s = data.settings
    val buyCode = Codes.buy(s, line.operator)
    val donors = data.lines.filter {
        it.isLocal && it.id != line.id && it.operator.group == line.operator.group && Codes.transfer(s, it.operator).isNotBlank()
    }
    val payApps = remember { Apps.installedPayApps(ctx) }
    val opInstalled = remember(line.operator) { Apps.operatorAppInstalled(ctx, line.operator) != null }

    Sheet(onDismiss) {
        SheetTitle("Beli / perpanjang", "${line.displayName} · ${line.operator.label} · ${Phone.pretty(line.msisdn)}")

        if (line.isLocal && buyCode.isNotBlank()) {
            OptionRow("📶", "Menu paket ${line.operator.label}", "Langsung dari SIM di HP ini ($buyCode). Pilih paket di layar yang muncul.") {
                Sim.dial(ctx, line.subId, buyCode); onLaunched()
            }
        }
        donors.forEach { d ->
            val code = Codes.transfer(s, d.operator)
            OptionRow("🎁", "Kirim pulsa dari nomor ${d.name}", "Transfer pulsa sesama ${line.operator.group.label} ($code). Nomor tujuan disalin, tempel saat diminta.") {
                Apps.copy(ctx, line.msisdn); Sim.dial(ctx, d.subId, code); onLaunched()
            }
        }
        if (payApps.isNotEmpty()) {
            Label("Lewat aplikasi pembayaran")
            Hint("Bisa untuk nomor operator apa pun tanpa login ke nomor itu. Nomor otomatis disalin, tinggal tempel di menu Pulsa / Paket Data.")
            Spacer(Modifier.height(6.dp))
            payApps.forEach { (name, pkg) ->
                OptionRow("💳", name, "Pulsa & paket data") { Apps.copy(ctx, line.msisdn); Apps.launch(ctx, pkg); onLaunched() }
            }
        }
        Label("Aplikasi operator")
        OptionRow("📱", line.operator.appName, if (opInstalled) "Nomor disalin otomatis" else "Belum terpasang, buka Play Store") {
            Apps.copy(ctx, line.msisdn, toast = opInstalled); Apps.openOperatorApp(ctx, line.operator); onLaunched()
        }
        if (payApps.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Hint("Tips: pasang e-wallet atau m-banking (DANA, GoPay, OVO, ShopeePay, BRImo, dll.) agar bisa beli paket untuk nomor mana pun, termasuk jam anak, dalam 2-3 ketukan.")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRecord, modifier = Modifier.fillMaxWidth()) { Text("Sudah beli? Catat paket barunya", fontWeight = FontWeight.Bold) }
    }
}

// ---------------------------------------------------------------------------------------
// Catat paket setelah beli (agar masa aktif & kuota langsung akurat)
// ---------------------------------------------------------------------------------------
@Composable
fun RecordSheet(line: Line, data: AppData, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var gb by remember { mutableStateOf<Double?>(null) }
    var gbText by remember { mutableStateOf("") }
    var days by remember { mutableStateOf<Int?>(30) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var price by remember { mutableStateOf("") }
    var pulsaAdd by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }

    Sheet(onDismiss) {
        SheetTitle("Catat paket baru", "${line.displayName} · cukup sekali, PapaMama menghitung sisanya")
        Label("Kuota yang dibeli")
        ChoiceChips(listOf(0.0, 1.0, 2.0, 3.0, 5.0, 10.0, 15.0, 25.0, 50.0), gb, { if (it == 0.0) "Tanpa kuota" else "${it.toInt()} GB" }) {
            gb = it; gbText = ""
        }
        OutlinedTextField(
            value = gbText, onValueChange = { gbText = it; gb = Fmt.parseGb(it) }, label = { Text("Atau ketik (GB)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        if (line.operator.prepaid) {
            Label("Masa aktif bertambah")
            ChoiceChips(listOf(1, 3, 7, 14, 30, 60), days, { "$it hari" }) { days = it; date = null }
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.padding(top = 6.dp)) {
                Text(date?.let { "Sampai ${Fmt.date(it)}" } ?: "Pilih tanggal persis")
            }
        }
        Label("Lainnya (opsional)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = price, onValueChange = { price = it }, label = { Text("Harga (Rp)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = pulsaAdd, onValueChange = { pulsaAdd = it }, label = { Text("Isi pulsa (Rp)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        BigButton("Simpan", color = PM.Navy, onClick = {
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            val est = StatusEngine.evaluate(line, data.settings, now, today).quotaMb
            val addMb = gb?.takeIf { it > 0 }?.times(1024)
            val note = buildString {
                append(addMb?.let { "Paket ${Fmt.mb(it)}" } ?: "Isi ulang")
                if (date != null) append(" · s.d. ${Fmt.date(date!!)}") else days?.let { append(" · $it hari") }
            }
            Store.edit(line.id) {
                Merge.purchase(
                    it, est, addMb, if (line.operator.prepaid && date == null) days else null,
                    if (line.operator.prepaid) date else null, Fmt.parseRp(pulsaAdd), Fmt.parseRp(price), note, now, today,
                )
            }
            Notifier.evaluateAll(ctx)
            onDismiss()
        })
    }
    if (pickDate) DatePick(date ?: LocalDate.now().plusDays(30), { pickDate = false }) { date = it; days = null }
}

// ---------------------------------------------------------------------------------------
// Update cepat: tempel teks dari SMS/aplikasi operator, atau isi manual
// ---------------------------------------------------------------------------------------
@Composable
fun UpdateSheet(line: Line, data: AppData, prefill: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val now = System.currentTimeMillis()
    val st = remember(line) { StatusEngine.evaluate(line, data.settings, now, LocalDate.now()) }
    var raw by remember { mutableStateOf(prefill) }
    var quota by remember { mutableStateOf(st.quotaMb?.let { Fmt.gbInput(it) } ?: "") }
    var total by remember { mutableStateOf(line.quotaTotalMb?.let { Fmt.gbInput(it) } ?: "") }
    var pulsa by remember { mutableStateOf(line.pulsa?.toString() ?: "") }
    var active by remember { mutableStateOf(line.activeUntil) }
    var parsed by remember { mutableStateOf(ParsedInfo()) }
    var note by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }

    fun applyParse(text: String) {
        raw = text
        val info = OperatorTextParser.parse(text)
        parsed = info
        if (info.isEmpty) {
            note = if (text.isBlank()) "" else "Belum ada angka yang terbaca. Isi manual di bawah."
            return
        }
        info.quotaMb?.let { quota = Fmt.gbInput(it) }
        info.quotaTotalMb?.let { total = Fmt.gbInput(it) }
        info.pulsa?.let { pulsa = it.toString() }
        info.activeUntil?.let { active = it }
        note = "Terbaca: " + listOfNotNull(
            info.quotaMb?.let { "kuota ${Fmt.mb(it)}" },
            info.pulsa?.let { "pulsa ${Fmt.rp(it)}" },
            info.activeUntil?.let { "aktif s.d. ${Fmt.date(it)}" },
            info.graceUntil?.let { "tenggang s.d. ${Fmt.date(it)}" },
            info.voiceMin?.let { "telepon $it menit" },
        ).joinToString(", ") + ". Cek lalu simpan."
    }
    LaunchedEffect(Unit) { if (prefill.isNotBlank()) applyParse(prefill) }

    Sheet(onDismiss) {
        SheetTitle("Update ${line.displayName}", "${line.operator.label} · ${Phone.pretty(line.msisdn)} · terakhir ${Fmt.ago(line.updatedAt)}")

        SectionCard(border = PM.Stroke) {
            Text("Cara tercepat: tempel teks operator", fontWeight = FontWeight.Bold, color = PM.Navy)
            Hint("Salin SMS info kuota atau teks dari aplikasi ${line.operator.appName}, lalu tempel. Angkanya dibaca otomatis.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { applyParse(clipboard.getText()?.text.orEmpty()) }, modifier = Modifier.weight(1f)) { Text("Tempel") }
                OutlinedButton(onClick = { Apps.openOperatorApp(ctx, line.operator) }, modifier = Modifier.weight(1f)) { Text("Buka ${line.operator.appName}") }
            }
            OutlinedTextField(
                value = raw, onValueChange = { applyParse(it) }, label = { Text("Teks dari operator") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, maxLines = 6,
            )
            if (note.isNotBlank()) {
                Text(note, color = if (parsed.isEmpty) PM.CritFg else PM.OkFg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
        }

        Label("Atau isi / koreksi manual")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = quota, onValueChange = { quota = it }, label = { Text("Sisa kuota (GB)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = total, onValueChange = { total = it }, label = { Text("Total (GB)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pulsa, onValueChange = { pulsa = it }, label = { Text("Pulsa (Rp)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        if (line.operator.prepaid) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(active?.let { "Masa aktif sampai ${Fmt.date(it)}" } ?: "Pilih tanggal masa aktif")
            }
        }
        Spacer(Modifier.height(14.dp))
        BigButton("Simpan", color = PM.Navy, onClick = {
            val info = ParsedInfo(
                quotaMb = Fmt.parseGb(quota)?.times(1024),
                quotaTotalMb = Fmt.parseGb(total)?.times(1024),
                pulsa = Fmt.parseRp(pulsa),
                activeUntil = active,
                graceUntil = parsed.graceUntil,
                voiceMin = parsed.voiceMin,
            )
            Store.applyReading(line.id, info, if (!parsed.isEmpty) "Tempel teks" else "Diperbarui manual", raw)
            Notifier.evaluateAll(ctx)
            onDismiss()
        })
    }
    if (pickDate) DatePick(active ?: LocalDate.now().plusDays(30), { pickDate = false }) { active = it }
}

// ---------------------------------------------------------------------------------------
// Pilih nomor (untuk "Beli paket untuk keluarga" & teks yang dibagikan dari app lain)
// ---------------------------------------------------------------------------------------
@Composable
fun PickLineSheet(data: AppData, title: String, sub: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        SheetTitle(title, sub)
        statusList(data).forEach { (line, st) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(line.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(line, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.displayName, fontWeight = FontWeight.Bold, color = PM.Navy)
                    Text(subtitle(line, st), color = PM.Muted, fontSize = 13.sp)
                }
                StatusPill(st.level)
            }
        }
        if (data.lines.isEmpty()) Hint("Belum ada nomor. Tambahkan dulu dengan tombol +.")
    }
}
