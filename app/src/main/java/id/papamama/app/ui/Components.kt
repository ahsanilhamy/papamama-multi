package id.papamama.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.papamama.app.core.Level
import id.papamama.app.model.DeviceType
import id.papamama.app.model.Line
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object PM {
    val Navy = Color(0xFF1F1A5C)
    val Red = Color(0xFFD0342C)
    val Bg = Color(0xFFF3F2FA)
    val Muted = Color(0xFF6B6A80)
    val Stroke = Color(0xFFE6E4F0)
    val Yellow = Color(0xFFFFD45C)
    val OkBg = Color(0xFFDDF3E4); val OkFg = Color(0xFF1E7B3A)
    val WarnBg = Color(0xFFFFF1C2); val WarnFg = Color(0xFF8A6100); val WarnBorder = Color(0xFFF0B429)
    val CritBg = Color(0xFFFDE0DE); val CritFg = Color(0xFFB3261E); val CritBorder = Color(0xFFE5534B)
    val StaleBg = Color(0xFFE9E8F0); val StaleFg = Color(0xFF55546A)
}

data class LevelColors(val bg: Color, val fg: Color, val border: Color?)

fun Level.colors(): LevelColors = when (this) {
    Level.OK -> LevelColors(PM.OkBg, PM.OkFg, null)
    Level.STALE -> LevelColors(PM.StaleBg, PM.StaleFg, null)
    Level.WARN -> LevelColors(PM.WarnBg, PM.WarnFg, PM.WarnBorder)
    Level.CRIT, Level.GRACE -> LevelColors(PM.CritBg, PM.CritFg, PM.CritBorder)
}

@Composable
fun PapaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PM.Navy, onPrimary = Color.White,
            primaryContainer = Color(0xFFE3E1F7), onPrimaryContainer = PM.Navy,
            secondary = PM.Red, onSecondary = Color.White,
            background = PM.Bg, onBackground = Color(0xFF1B1A2E),
            surface = Color.White, onSurface = Color(0xFF1B1A2E),
            surfaceVariant = Color(0xFFEDECF6), onSurfaceVariant = PM.Muted,
            surfaceContainerLow = Color.White, surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEDECF6),
            outline = Color(0xFFBDBBD0), outlineVariant = PM.Stroke,
        ),
        content = content,
    )
}

@Composable
fun Avatar(line: Line, size: Int = 44) {
    val bg = if (line.device == DeviceType.WATCH) Color(0xFFFFE7B8) else Color(0xFFE3E1F7)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val a = line.avatar.ifBlank { line.name.take(1).uppercase() }
        Text(a, fontSize = (size * 0.45).sp, fontWeight = FontWeight.Bold, color = PM.Navy)
    }
}

@Composable
fun StatusPill(level: Level) {
    val c = level.colors()
    Text(
        level.label,
        color = c.fg,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.bg).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun SmallTag(text: String, bg: Color = Color(0xFFEDECF6), fg: Color = PM.Navy) {
    Text(
        text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, border: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = border?.let { BorderStroke(1.5.dp, it) },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = PM.Red, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = PM.Navy, fontSize = 15.sp)
            Text(subtitle, color = PM.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = PM.Navy, checkedThumbColor = Color.White),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PM.Navy, selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
fun OptionRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, PM.Stroke, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = PM.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = PM.Muted, fontSize = 13.sp)
        }
        Text("›", fontSize = 24.sp, color = PM.Muted)
    }
}

@Composable
fun Label(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = PM.Navy, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, color = PM.Muted, fontSize = 13.sp, lineHeight = 18.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    ) { DatePicker(state = state) }
}
