package id.papamama.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.papamama.app.core.CheckWorker
import id.papamama.app.core.Codes
import id.papamama.app.core.Fmt
import id.papamama.app.core.Notifier
import id.papamama.app.core.has
import id.papamama.app.core.declares
import id.papamama.app.data.Demo
import id.papamama.app.data.Store
import id.papamama.app.model.Operator
import id.papamama.app.model.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(settings.userName) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmWipe by remember { mutableStateOf(false) }
    var showCodes by remember { mutableStateOf(false) }
    var codesVersion by remember { mutableIntStateOf(0) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    Scaffold(
        containerColor = PM.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold, color = PM.Navy) },
                navigationIcon = {
                    IconButton(onClick = {
                        Store.settings { it.copy(userName = name.trim().ifBlank { "Papa" }) }
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = PM.Navy) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PM.Bg),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()
        ) {
            SectionCard {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; Store.settings { s -> s.copy(userName = it.trim().ifBlank { "Papa" }) } },
                    label = { Text("Panggilan kamu (mis. Papa, Mama, Ayah)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Label("Cek otomatis nomor di HP ini setiap")
                ChoiceChips(listOf(3, 6, 12, 24), settings.checkHours, { "$it jam" }) { h ->
                    Store.settings { it.copy(checkHours = h) }
                    CheckWorker.schedule(ctx, h)
                }
                Label("Ingatkan bila pulsa di bawah")
                ChoiceChips(listOf(2_000L, 5_000L, 10_000L, 20_000L), settings.pulsaThreshold, { Fmt.rp(it) }) { v ->
                    Store.settings { it.copy(pulsaThreshold = v) }
                }
                Label("Ingatkan update data bila lebih dari")
                ChoiceChips(listOf(3, 7, 14), settings.staleDays, { "$it hari" }) { v -> Store.settings { it.copy(staleDays = v) } }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text("Izin aplikasi", fontWeight = FontWeight.Bold, color = PM.Navy)
                Hint("Semua opsional. Tanpa izin, fitur terkait beralih ke cara manual.")
                key(refresh) {
                    val items = buildList {
                        if (Build.VERSION.SDK_INT >= 33) add(Triple("Notifikasi", "Peringatan kuota & masa aktif", Manifest.permission.POST_NOTIFICATIONS))
                        add(Triple("Telepon", "Cek otomatis via USSD & buka menu paket", Manifest.permission.CALL_PHONE))
                        add(Triple("Status telepon", "Mengenali SIM di HP ini", Manifest.permission.READ_PHONE_STATE))
                        if (ctx.declares(Manifest.permission.RECEIVE_SMS)) add(Triple("Terima SMS", "Membaca SMS info kuota & laporan keluarga", Manifest.permission.RECEIVE_SMS))
                        if (ctx.declares(Manifest.permission.SEND_SMS)) add(Triple("Kirim SMS", "Hanya untuk Mode Pelapor", Manifest.permission.SEND_SMS))
                    }
                    items.forEach { (title, sub, p) ->
                        val ok = ctx.has(p)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.SemiBold, color = PM.Navy)
                                Text(sub, color = PM.Muted, fontSize = 12.sp)
                            }
                            if (ok) Text("Aktif ✓", color = PM.OkFg, fontWeight = FontWeight.Bold)
                            else OutlinedButton(onClick = { perm.launch(arrayOf(p)) }) { Text("Izinkan") }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { Notifier.test(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Kirim notifikasi contoh") }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Row(Modifier.fillMaxWidth().clickable { showCodes = !showCodes }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kode USSD operator (lanjutan)", fontWeight = FontWeight.Bold, color = PM.Navy)
                        Hint("Ubah bila operator mengganti kodenya. Pisahkan beberapa kode cek dengan koma.")
                    }
                    Text(if (showCodes) "▲" else "▼", color = PM.Navy)
                }
                if (showCodes) key(codesVersion) {
                    Operator.entries.forEach { op ->
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = PM.Stroke)
                        Text(op.label, fontWeight = FontWeight.Bold, color = PM.Navy)
                        CodeField("Cek pulsa/kuota", Codes.check(settings, op).joinToString(", ")) { v -> setCode("${op.name}.check", v) }
                        CodeField("Menu beli paket", Codes.buy(settings, op)) { v -> setCode("${op.name}.buy", v) }
                        CodeField("Transfer pulsa", Codes.transfer(settings, op)) { v -> setCode("${op.name}.transfer", v) }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { Store.settings { it.copy(codes = emptyMap()) }; codesVersion++ }) { Text("Kembalikan semua kode bawaan") }
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text("Data", fontWeight = FontWeight.Bold, color = PM.Navy)
                Hint("Semua data hanya tersimpan di HP ini. PapaMama tidak punya server dan tidak meminta login operator.")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { Demo.load(); Toast.makeText(ctx, "Data contoh dimuat", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Isi data contoh (untuk presentasi)")
                }
                TextButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) { Text("Hapus semua nomor", color = PM.Red) }
            }
            Spacer(Modifier.height(12.dp))
            Hint("PapaMama Multi-Operator v1.0 · Kode USSD bawaan dari sumber publik dan bisa berubah sewaktu-waktu.")
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Hapus semua nomor?") },
            text = { Text("Semua nomor dan riwayatnya dihapus dari HP ini.") },
            confirmButton = { TextButton(onClick = { Store.update { it.copy(lines = emptyList()) }; confirmWipe = false }) { Text("Hapus", color = PM.Red) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Batal") } },
        )
    }
}

private fun setCode(key: String, value: String) {
    Store.settings { s -> s.copy(codes = s.codes + (key to value.trim())) }
}

@Composable
private fun CodeField(label: String, value: String, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = v, onValueChange = { v = it; onSave(it) }, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
