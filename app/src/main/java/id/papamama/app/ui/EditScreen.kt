package id.papamama.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.papamama.app.core.Checker
import id.papamama.app.core.Fmt
import id.papamama.app.core.Notifier
import id.papamama.app.core.Sim
import id.papamama.app.core.has
import id.papamama.app.data.Store
import id.papamama.app.model.DeviceType
import id.papamama.app.model.Line
import id.papamama.app.model.Operator
import id.papamama.app.model.Phone
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private val AVATARS = listOf("👨", "👩", "👦", "👧", "👶", "👴", "👵", "⌚", "📱")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(existing: Line?, onBack: () -> Unit, onSaved: (String) -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var number by remember { mutableStateOf(existing?.msisdn ?: "") }
    var op by remember { mutableStateOf(existing?.operator) }
    var opTouched by remember { mutableStateOf(existing != null) }
    var device by remember { mutableStateOf(existing?.device ?: DeviceType.PHONE) }
    var avatar by remember { mutableStateOf(existing?.avatar ?: "") }
    var subId by remember { mutableIntStateOf(existing?.subId ?: -1) }
    var sims by remember { mutableStateOf(Sim.list(ctx)) }
    var quota by remember { mutableStateOf(existing?.quotaMb?.let { Fmt.gbInput(it) } ?: "") }
    var total by remember { mutableStateOf(existing?.quotaTotalMb?.let { Fmt.gbInput(it) } ?: "") }
    var pulsa by remember { mutableStateOf(existing?.pulsa?.toString() ?: "") }
    var active by remember { mutableStateOf(existing?.activeUntil) }
    var usage by remember { mutableStateOf(existing?.dailyUsageMb?.let { it.toInt().toString() } ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val simPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        sims = Sim.list(ctx)
    }

    fun onNumber(v: String) {
        number = v
        if (!opTouched) Operator.guessFromNumber(v)?.let { op = it }
    }

    val valid = name.isNotBlank() && Phone.normalize(number).length >= 9 && op != null

    Scaffold(
        containerColor = PM.Bg,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Tambah nomor" else "Ubah ${existing.name}", fontWeight = FontWeight.Bold, color = PM.Navy) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = PM.Navy) } },
                actions = {
                    if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus", tint = PM.Red) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PM.Bg),
            )
        },
        bottomBar = {
            BigButton(
                "Simpan", enabled = valid, color = PM.Navy,
                modifier = Modifier.background(PM.Bg).navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                onClick = {
                    val operator = op ?: return@BigButton
                    val base = existing ?: Line(UUID.randomUUID().toString(), "", "", operator)
                    val q = Fmt.parseGb(quota)?.times(1024)
                    val t = Fmt.parseGb(total)?.times(1024)
                    var line = base.copy(
                        name = name.trim(),
                        msisdn = Phone.normalize(number),
                        operator = operator,
                        device = device,
                        avatar = avatar,
                        subId = subId,
                        dailyUsageMb = usage.toDoubleOrNull()?.takeIf { it > 0 } ?: base.dailyUsageMb,
                    )
                    val dataChanged = q != existing?.quotaMb || t != existing?.quotaTotalMb ||
                        Fmt.parseRp(pulsa) != existing?.pulsa || active != existing?.activeUntil
                    if (dataChanged && (q != null || Fmt.parseRp(pulsa) != null || active != null)) {
                        line = line.copy(
                            quotaMb = q, quotaTotalMb = t ?: q?.let { maxOf(it, line.quotaTotalMb ?: 0.0) }?.takeIf { it > 0 },
                            pulsa = Fmt.parseRp(pulsa), activeUntil = active,
                            updatedAt = System.currentTimeMillis(), source = "Diisi manual", notified = emptySet(),
                        )
                    }
                    Store.upsert(line)
                    Notifier.evaluateAll(ctx)
                    if (existing == null && line.isLocal && ctx.has(Manifest.permission.CALL_PHONE)) {
                        scope.launch { Checker.checkLine(ctx, line); Notifier.evaluateAll(ctx) }
                    }
                    onSaved(line.id)
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SectionCard {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nama (mis. Kakak, Mama)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = number, onValueChange = ::onNumber, label = { Text("Nomor HP") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = { Operator.guessFromNumber(number)?.let { Text("Terdeteksi: jaringan ${it.group.label}") } },
                )
                Label("Operator")
                ChoiceChips(Operator.entries, op, { it.label }) { op = it; opTouched = true }
                Label("Dipakai di")
                ChoiceChips(DeviceType.entries, device, { it.label }) {
                    device = it
                    if (it == DeviceType.WATCH && avatar.isBlank()) avatar = "⌚"
                }
                if (device == DeviceType.WATCH) {
                    Hint("Jam anak tidak bisa menampilkan notifikasi paket, jadi semua peringatan nomor ini dikirim ke HP kamu.")
                }
                Label("Ikon")
                ChoiceChips(AVATARS, avatar.ifBlank { null }, { it }) { avatar = it }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text("Apakah kartu SIM nomor ini ada di HP ini?", fontWeight = FontWeight.Bold, color = PM.Navy)
                Hint("Jika ya, PapaMama bisa cek pulsa, kuota, dan masa aktif otomatis lewat USSD, tanpa membuka aplikasi operator.")
                Spacer(Modifier.height(8.dp))
                if (!ctx.has(Manifest.permission.READ_PHONE_STATE)) {
                    OutlinedButton(onClick = {
                        simPerm.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.RECEIVE_SMS))
                    }) { Text("Izinkan baca SIM di HP ini") }
                } else {
                    val opts = listOf(-1) + sims.map { it.subId }
                    ChoiceChips(opts, subId, { id -> if (id == -1) "Tidak, di HP/jam lain" else sims.first { it.subId == id }.label }) { subId = it }
                    if (sims.isEmpty()) Hint("Tidak ada SIM aktif terdeteksi.")
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text("Data saat ini (opsional)", fontWeight = FontWeight.Bold, color = PM.Navy)
                Hint(if (subId >= 0) "Boleh dikosongkan, nanti terisi otomatis." else "Isi sekali dari aplikasi/SMS operator. Selanjutnya cukup tempel teks atau catat saat beli paket.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quota, onValueChange = { quota = it }, label = { Text("Sisa kuota (GB)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = total, onValueChange = { total = it }, label = { Text("Total paket (GB)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pulsa, onValueChange = { pulsa = it }, label = { Text("Pulsa (Rp)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                if (op?.prepaid != false) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(active?.let { "Masa aktif sampai ${Fmt.date(it)}" } ?: "Pilih tanggal masa aktif")
                    }
                }
                if (device == DeviceType.WATCH || existing?.dailyUsageMb != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usage, onValueChange = { usage = it.filter { c -> c.isDigit() } },
                        label = { Text("Pemakaian per hari (MB), opsional") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Untuk memperkirakan sisa kuota di antara update. Kosong = otomatis (jam anak ±30 MB/hari).", fontSize = 12.sp) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickDate) DatePick(active ?: LocalDate.now().plusDays(30), { pickDate = false }) { active = it }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus ${existing.name}?") },
            text = { Text("Nomor ini berhenti dipantau. Data di HP ini ikut terhapus.") },
            confirmButton = { TextButton(onClick = { Store.delete(existing.id); confirmDelete = false; onDeleted() }) { Text("Hapus", color = PM.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
