package id.papamama.app.data

import android.content.Context
import id.papamama.app.core.Merge
import id.papamama.app.core.ParsedInfo
import id.papamama.app.model.AppData
import id.papamama.app.model.DeviceType
import id.papamama.app.model.Line
import id.papamama.app.model.Operator
import id.papamama.app.model.Purchase
import id.papamama.app.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Penyimpanan lokal sederhana (JSON di memori internal HP). Tidak ada data yang dikirim ke server. */
object Store {
    private var file: File? = null
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "papamama.json")
        file = f
        _state.value = runCatching { if (f.exists()) fromJson(JSONObject(f.readText())) else AppData() }
            .getOrDefault(AppData())
    }

    @Synchronized
    fun update(f: (AppData) -> AppData) {
        val next = f(_state.value)
        _state.value = next
        save(next)
    }

    fun line(id: String): Line? = _state.value.lines.find { it.id == id }

    fun upsert(line: Line) = update { d ->
        val exists = d.lines.any { it.id == line.id }
        d.copy(lines = if (exists) d.lines.map { if (it.id == line.id) line else it } else d.lines + line)
    }

    fun delete(id: String) = update { d -> d.copy(lines = d.lines.filterNot { it.id == id }) }

    fun edit(id: String, f: (Line) -> Line) = update { d -> d.copy(lines = d.lines.map { if (it.id == id) f(it) else it }) }

    fun applyReading(id: String, info: ParsedInfo, source: String, raw: String) {
        val now = System.currentTimeMillis()
        update { d ->
            d.copy(lines = d.lines.map {
                if (it.id == id) Merge.reading(it, info, source, raw, now, d.settings.pulsaThreshold) else it
            })
        }
    }

    fun markNotified(id: String, keys: Collection<String>) = edit(id) { l ->
        // simpan maksimal 60 kunci terakhir agar file tidak membengkak
        l.copy(notified = (l.notified + keys).toList().takeLast(60).toSet())
    }

    fun settings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    private fun save(d: AppData) {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(toJson(d).toString())
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText()); tmp.delete()
            }
        }
    }

    // --- JSON ------------------------------------------------------------------------

    private fun toJson(d: AppData): JSONObject = JSONObject().apply {
        put("v", 1)
        put("lines", JSONArray(d.lines.map { lineJson(it) }))
        put("settings", JSONObject().apply {
            put("name", d.settings.userName)
            put("ch", d.settings.checkHours)
            put("pt", d.settings.pulsaThreshold)
            put("sd", d.settings.staleDays)
            put("codes", JSONObject(d.settings.codes as Map<*, *>))
        })
    }

    private fun lineJson(l: Line) = JSONObject().apply {
        put("id", l.id); put("name", l.name); put("msisdn", l.msisdn)
        put("op", l.operator.name); put("dev", l.device.name); put("av", l.avatar); put("sub", l.subId)
        l.quotaMb?.let { put("q", it) }
        l.quotaTotalMb?.let { put("qt", it) }
        l.pulsa?.let { put("p", it) }
        l.voiceMin?.let { put("v", it) }
        l.activeUntil?.let { put("a", it.toEpochDay()) }
        l.graceUntil?.let { put("g", it.toEpochDay()) }
        put("u", l.updatedAt); put("src", l.source); put("raw", l.lastRaw)
        l.dailyUsageMb?.let { put("du", it) }
        put("aq", l.alertQuota); put("aa", l.alertActive); put("ap", l.alertPulsa)
        put("ag", l.autoGuard); put("ac", l.autoCheck)
        put("rt", l.reportTo); put("lr", l.lastReportAt)
        put("n", JSONArray(l.notified.toList()))
        put("pur", JSONArray(l.purchases.map { p ->
            JSONObject().apply { put("at", p.at); put("note", p.note); p.price?.let { put("price", it) } }
        }))
    }

    private fun JSONObject.dbl(k: String): Double? = if (has(k)) optDouble(k).takeIf { !it.isNaN() } else null
    private fun JSONObject.lng(k: String): Long? = if (has(k)) optLong(k) else null

    private fun fromJson(o: JSONObject): AppData {
        val arr = o.optJSONArray("lines") ?: JSONArray()
        val lines = (0 until arr.length()).mapNotNull { i -> runCatching { lineFrom(arr.getJSONObject(i)) }.getOrNull() }
        val s = o.optJSONObject("settings")
        val settings = if (s == null) Settings() else {
            val c = s.optJSONObject("codes")
            val codes = mutableMapOf<String, String>()
            c?.keys()?.forEach { k -> codes[k] = c.optString(k) }
            Settings(
                userName = s.optString("name", "Papa"),
                checkHours = s.optInt("ch", 6),
                pulsaThreshold = s.optLong("pt", 5000),
                staleDays = s.optInt("sd", 7),
                codes = codes,
            )
        }
        return AppData(lines, settings)
    }

    private fun lineFrom(o: JSONObject): Line {
        val n = o.optJSONArray("n") ?: JSONArray()
        val pur = o.optJSONArray("pur") ?: JSONArray()
        return Line(
            id = o.getString("id"),
            name = o.optString("name"),
            msisdn = o.optString("msisdn"),
            operator = runCatching { Operator.valueOf(o.optString("op")) }.getOrDefault(Operator.TELKOMSEL),
            device = runCatching { DeviceType.valueOf(o.optString("dev")) }.getOrDefault(DeviceType.PHONE),
            avatar = o.optString("av"),
            subId = o.optInt("sub", -1),
            quotaMb = o.dbl("q"),
            quotaTotalMb = o.dbl("qt"),
            pulsa = o.lng("p"),
            voiceMin = if (o.has("v")) o.optInt("v") else null,
            activeUntil = o.lng("a")?.let { LocalDate.ofEpochDay(it) },
            graceUntil = o.lng("g")?.let { LocalDate.ofEpochDay(it) },
            updatedAt = o.optLong("u"),
            source = o.optString("src"),
            lastRaw = o.optString("raw"),
            dailyUsageMb = o.dbl("du"),
            alertQuota = o.optBoolean("aq", true),
            alertActive = o.optBoolean("aa", true),
            alertPulsa = o.optBoolean("ap", true),
            autoGuard = o.optBoolean("ag", false),
            autoCheck = o.optBoolean("ac", true),
            reportTo = o.optString("rt"),
            lastReportAt = o.optLong("lr"),
            notified = (0 until n.length()).map { n.getString(it) }.toSet(),
            purchases = (0 until pur.length()).map { i ->
                val p = pur.getJSONObject(i)
                Purchase(p.optLong("at"), p.optString("note"), if (p.has("price")) p.optLong("price") else null)
            },
        )
    }
}
