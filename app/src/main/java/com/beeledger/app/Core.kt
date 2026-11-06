package com.beeledger.app

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow

/** 金额统一用「分」存储，避免浮点误差。 */
object Money {

    /** 25.00 形式 */
    fun fmt(cents: Long): String {
        val v = abs(cents)
        val s = (v / 100).toString() + "." + (v % 100).toString().padStart(2, '0')
        return if (cents < 0) "-$s" else s
    }

    /** 整数金额省略小数位：25 / 25.50 */
    fun short(cents: Long): String =
        if (cents % 100 == 0L) (cents / 100).toString() else fmt(cents)

    /** 输入框文本 -> 分 */
    fun parse(text: String): Long {
        val t = text.trim().replace(",", "").replace("¥", "").replace("￥", "")
        if (t.isEmpty() || t == ".") return 0L
        val d = t.toDoubleOrNull() ?: return 0L
        if (d <= 0) return 0L
        return Math.round(d * 100)
    }
}

object D {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun monthStart(ym: YearMonth): Long =
        ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthEnd(ym: YearMonth): Long =
        ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayStart(ts: Long): Long =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun toDate(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    fun toLocal(ts: Long): LocalDateTime = Instant.ofEpochMilli(ts).atZone(zone).toLocalDateTime()

    private val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun dayLabel(ts: Long): String {
        val d = toDate(ts)
        val dow = weekdays[d.dayOfWeek.value - 1]
        return "${d.monthValue}月${d.dayOfMonth}日  $dow"
    }

    fun shortDay(ts: Long): String {
        val d = toDate(ts)
        return "${d.monthValue}月${d.dayOfMonth}日"
    }

    fun timeLabel(ts: Long): String {
        val t = toLocal(ts)
        return String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
    }

    fun ymLabel(ym: YearMonth): String = "${ym.year}年${ym.monthValue}月"

    fun fromYmd(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    fun ymdText(ts: Long): String {
        val d = toDate(ts)
        return String.format(Locale.US, "%04d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)
    }

    fun fullText(ts: Long): String {
        val t = toLocal(ts)
        return String.format(
            Locale.US, "%04d-%02d-%02d %02d:%02d:%02d",
            t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.second
        )
    }

    fun daysInMonth(ym: YearMonth): Int = ym.lengthOfMonth()

    val csvTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}

object Prefs {
    private var sp: SharedPreferences? = null

    fun init(c: Context) {
        if (sp == null) sp = c.applicationContext.getSharedPreferences("bee", Context.MODE_PRIVATE)
    }

    var autoCapture: Boolean
        get() = sp?.getBoolean("auto_capture", true) ?: true
        set(v) { sp?.edit()?.putBoolean("auto_capture", v)?.apply() }

    var autoSms: Boolean
        get() = sp?.getBoolean("auto_sms", true) ?: true
        set(v) { sp?.edit()?.putBoolean("auto_sms", v)?.apply() }

    var budget: Long
        get() = sp?.getLong("budget_cents", 0L) ?: 0L
        set(v) { sp?.edit()?.putLong("budget_cents", v)?.apply() }
}

/** 极简的事件总线：自动记账写入数据后通知界面刷新。 */
object Bus {
    val version = MutableStateFlow(0)

    fun bump() {
        version.value = version.value + 1
    }
}
