package id.papamama.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import id.papamama.app.core.Checker
import id.papamama.app.core.Notifier
import id.papamama.app.core.has
import id.papamama.app.data.Demo
import id.papamama.app.data.Store
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String?) : Screen
    data object Settings : Screen
}

private enum class PickMode { FAMILY_BUY, SHARE }

@Composable
fun PapaMamaRoot(intents: StateFlow<Intent?>, consumeIntent: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by Store.state.collectAsState()

    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Home)) }
    fun push(s: Screen) { stack = stack + s }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    var buyFor by remember { mutableStateOf<String?>(null) }
    var updateFor by remember { mutableStateOf<String?>(null) }
    var updatePrefill by remember { mutableStateOf("") }
    var recordFor by remember { mutableStateOf<String?>(null) }
    var askRecordFor by remember { mutableStateOf<String?>(null) }
    var awaitingReturn by remember { mutableStateOf<String?>(null) }
    var pickMode by remember { mutableStateOf<PickMode?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Notifier.evaluateAll(ctx) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !ctx.has(Manifest.permission.POST_NOTIFICATIONS)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Notifier.evaluateAll(ctx)
    }

    // Kembali dari aplikasi pembayaran/operator → tawarkan mencatat paket baru.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        awaitingReturn?.let { askRecordFor = it; awaitingReturn = null }
        Notifier.evaluateAll(ctx)
    }

    // Intent dari notifikasi, "Bagikan ke PapaMama", atau mode demo.
    val pending by intents.collectAsState()
    LaunchedEffect(pending) {
        val i = pending ?: return@LaunchedEffect
        if (i.getBooleanExtra("demo", false)) Demo.load()
        if (i.hasExtra(Notifier.EXTRA_LINE) || i.hasExtra("screen") || i.action == Intent.ACTION_SEND) {
            buyFor = null; updateFor = null; recordFor = null; pickMode = null; askRecordFor = null
        }
        if (i.action == Intent.ACTION_SEND) {
            val t = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (t.isNotBlank()) {
                updatePrefill = t
                val lines = Store.state.value.lines
                if (lines.size == 1) updateFor = lines[0].id else pickMode = PickMode.SHARE
            }
        }
        if (i.getStringExtra("screen") == "settings") stack = listOf(Screen.Home, Screen.Settings)
        i.getStringExtra(Notifier.EXTRA_LINE)?.let { id ->
            if (Store.line(id) != null) {
                stack = listOf(Screen.Home, Screen.Detail(id))
                when (i.getStringExtra(Notifier.EXTRA_ACT)) {
                    "buy" -> buyFor = id
                    "update" -> { updatePrefill = ""; updateFor = id }
                }
            }
        }
        consumeIntent()
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(
            data = data,
            refreshing = refreshing,
            onOpen = { push(Screen.Detail(it)) },
            onAdd = { push(Screen.Edit(null)) },
            onBuyFamily = { pickMode = PickMode.FAMILY_BUY },
            onSettings = { push(Screen.Settings) },
            onRefresh = {
                if (data.lines.none { it.isLocal }) {
                    Toast.makeText(ctx, "Belum ada nomor yang SIM-nya di HP ini. Nomor lain diperbarui lewat tempel teks atau Mode Pelapor.", Toast.LENGTH_LONG).show()
                    Notifier.evaluateAll(ctx)
                } else {
                    refreshing = true
                    scope.launch {
                        val r = Checker.checkAllLocal(ctx)
                        refreshing = false
                        Notifier.evaluateAll(ctx)
                        Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
        is Screen.Detail -> {
            val line = data.lines.find { it.id == s.id }
            if (line == null) LaunchedEffect(s) { stack = listOf(Screen.Home) }
            else DetailScreen(
                line = line,
                settings = data.settings,
                onBack = { pop() },
                onEdit = { push(Screen.Edit(line.id)) },
                onBuy = { buyFor = line.id },
                onUpdate = { updatePrefill = ""; updateFor = line.id },
            )
        }
        is Screen.Edit -> EditScreen(
            existing = s.id?.let { id -> data.lines.find { it.id == id } },
            onBack = { pop() },
            onSaved = { id -> stack = stack.dropLast(1); if (s.id == null) push(Screen.Detail(id)) },
            onDeleted = { stack = listOf(Screen.Home) },
        )
        Screen.Settings -> SettingsScreen(data.settings) { pop() }
    }

    // --- Lembar (bottom sheet) ---------------------------------------------------------
    buyFor?.let { id ->
        val line = data.lines.find { it.id == id }
        if (line == null) buyFor = null
        else BuySheet(
            line, data,
            onDismiss = { buyFor = null },
            onLaunched = { awaitingReturn = line.id; buyFor = null },
            onRecord = { buyFor = null; recordFor = line.id },
        )
    }
    recordFor?.let { id ->
        val line = data.lines.find { it.id == id }
        if (line == null) recordFor = null else RecordSheet(line, data) { recordFor = null }
    }
    updateFor?.let { id ->
        val line = data.lines.find { it.id == id }
        if (line == null) updateFor = null else UpdateSheet(line, data, updatePrefill) { updateFor = null; updatePrefill = "" }
    }
    pickMode?.let { mode ->
        PickLineSheet(
            data,
            title = if (mode == PickMode.FAMILY_BUY) "Beli paket untuk siapa?" else "Teks ini untuk nomor siapa?",
            sub = if (mode == PickMode.FAMILY_BUY) "Yang perlu perhatian ada di paling atas" else "Angka di teks akan dibaca otomatis",
            onPick = { id -> if (mode == PickMode.FAMILY_BUY) buyFor = id else updateFor = id; pickMode = null },
            onDismiss = { pickMode = null; if (mode == PickMode.SHARE) updatePrefill = "" },
        )
    }
    askRecordFor?.let { id ->
        val line = data.lines.find { it.id == id }
        if (line == null) askRecordFor = null
        else AlertDialog(
            onDismissRequest = { askRecordFor = null },
            title = { Text("Sudah beli untuk ${line.displayName}?") },
            text = { Text("Catat paketnya sekarang supaya kuota dan masa aktif langsung terhitung. Cukup 2 ketukan.") },
            confirmButton = { TextButton(onClick = { askRecordFor = null; recordFor = id }) { Text("Ya, catat") } },
            dismissButton = { TextButton(onClick = { askRecordFor = null }) { Text("Belum") } },
        )
    }
}
