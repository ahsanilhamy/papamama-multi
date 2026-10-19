package id.papamama.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.papamama.app.core.Fmt
import id.papamama.app.core.Level
import id.papamama.app.core.LineStatus
import id.papamama.app.core.StatusEngine
import id.papamama.app.model.AppData
import id.papamama.app.model.DeviceType
import id.papamama.app.model.Line
import id.papamama.app.model.Phone
import java.time.LocalDate

fun statusList(data: AppData): List<Pair<Line, LineStatus>> {
    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    return data.lines.map { it to StatusEngine.evaluate(it, data.settings, now, today) }
        .sortedWith(compareByDescending<Pair<Line, LineStatus>> { it.second.level.rank }.thenBy { it.first.name })
}

/** Ringkasan satu baris seperti di deck: "0,4 GB tersisa · 28 Okt" / "Aktif 40 hari". */
fun subtitle(l: Line, st: LineStatus, today: LocalDate = LocalDate.now()): String {
    val parts = mutableListOf<String>()
    st.quotaMb?.let { q ->
        val low = st.level.rank >= Level.WARN.rank && q < 2048
        parts += (if (st.quotaIsEstimate) "±" else "") + Fmt.mb(q) + if (low) " tersisa" else ""
    }
    val d = st.daysLeft
    when {
        !l.operator.prepaid -> parts += "Pascabayar"
        d != null && d < 0 -> parts += "lewat ${-d} hari"
        d != null && parts.isNotEmpty() -> parts += Fmt.date(l.activeUntil!!, today)
        d != null -> parts += "Aktif $d hari"
    }
    if (st.quotaMb == null) l.pulsa?.let { parts += Fmt.rp(it) }
    if (parts.isEmpty()) parts += "Belum ada data"
    return parts.joinToString(" · ")
}

@Composable
fun HomeScreen(
    data: AppData,
    refreshing: Boolean,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onBuyFamily: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    val items = statusList(data)
    val attention = items.count { it.second.level.rank >= Level.WARN.rank }
    val stale = items.count { it.second.level == Level.STALE }
    Scaffold(
        containerColor = PM.Bg,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = PM.Navy, contentColor = Color.White, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah nomor")
            }
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                BigButton(
                    "Beli paket untuk keluarga", onBuyFamily,
                    Modifier.background(PM.Bg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hai, ${data.settings.userName}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = PM.Navy)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "PapaMama", fontWeight = FontWeight.Bold, color = PM.Navy, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(PM.Yellow).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = PM.Navy)
                        else Icon(Icons.Filled.Refresh, contentDescription = "Cek semua sekarang", tint = PM.Navy)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = PM.Navy) }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PM.Navy).padding(18.dp)
                ) {
                    Text("Keluarga Saya · ${items.size} nomor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    val msg = when {
                        items.isEmpty() -> "Belum ada nomor. Tambah dengan tombol +"
                        attention > 0 -> "$attention nomor perlu perhatian"
                        stale > 0 -> "Semua aman · $stale nomor perlu update data"
                        else -> "Semua aman ✓"
                    }
                    Text(msg, color = PM.Yellow, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (items.isEmpty()) item { Onboarding(onAdd) }
            items(items, key = { it.first.id }) { (line, st) -> LineCard(line, st) { onOpen(line.id) } }
        }
    }
}

@Composable
private fun LineCard(line: Line, st: LineStatus, onClick: () -> Unit) {
    SectionCard(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), border = st.level.colors().border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(line)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.name, fontWeight = FontWeight.Bold, color = PM.Navy, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (line.device == DeviceType.WATCH) { Spacer(Modifier.width(6.dp)); SmallTag("Watch", Color(0xFFFFE7B8)) }
                }
                Text(subtitle(line, st), color = Color(0xFF3A3950), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${line.operator.label} · ${Phone.pretty(line.msisdn)}" + if (line.isLocal) " · di HP ini" else "",
                    color = PM.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(st.level)
        }
    }
}

@Composable
private fun Onboarding(onAdd: () -> Unit) {
    SectionCard {
        Text("Mulai dalam 3 langkah", fontWeight = FontWeight.Bold, color = PM.Navy, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        listOf(
            "1" to "Tambah nomor keluarga (semua operator, berapa pun jumlahnya)",
            "2" to "Tandai nomor di jam anak sebagai \"Jam anak\"",
            "3" to "Izinkan notifikasi, PapaMama yang mengingatkan",
        ).forEach { (n, t) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    n, color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(PM.Navy).padding(horizontal = 9.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(t, color = Color(0xFF3A3950), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        BigButton("+ Tambah nomor pertama", onAdd, color = PM.Navy)
    }
}
