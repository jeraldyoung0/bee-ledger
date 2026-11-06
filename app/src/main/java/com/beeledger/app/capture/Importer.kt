package com.beeledger.app.capture

import android.content.ContentResolver
import android.net.Uri
import com.beeledger.app.data.Type
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 支付宝 / 微信 账单 CSV 导入。
 *
 * 使用方式：
 *   支付宝：我的 -> 账单 -> 右上角 -> 开具交易流水证明 -> 导出 CSV
 *   微信  ：我 -> 服务 -> 钱包 -> 账单 -> 常见问题 -> 下载账单 -> 用于个人对账
 * 拿到 CSV 后在 App 里「导入账单」选择即可。
 */
object Importer {

    private val formats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy年MM月dd日 HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
    )

    fun readText(cr: ContentResolver, uri: Uri): String {
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        return decode(bytes)
    }

    /** 支付宝导出的 CSV 是 GBK 编码，微信是 UTF-8，这里自动判断 */
    private fun decode(b: ByteArray): String {
        var bytes = b
        // 去掉 UTF-8 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes = bytes.copyOfRange(3, bytes.size)
        }
        return try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            dec.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            String(bytes, Charset.forName("GBK"))
        }
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    sb.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> quoted = true
                    ',' -> {
                        out.add(sb.toString())
                        sb.setLength(0)
                    }
                    else -> sb.append(ch)
                }
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseTime(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        for (f in formats) {
            try {
                val ldt = LocalDateTime.parse(t, DateTimeFormatter.ofPattern(f))
                return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                // 试下一个格式
            }
        }
        return null
    }

    private fun parseAmount(s: String): Long? {
        val t = s.replace("¥", "").replace("￥", "").replace("元", "").replace(",", "").trim()
        if (t.isEmpty()) return null
        val d = t.toDoubleOrNull() ?: return null
        if (d <= 0) return null
        return Math.round(d * 100)
    }

    fun parse(text: String): List<Parsed> {
        val lines = text.split("\n").map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val headerIdx = lines.indexOfFirst {
            it.contains("交易时间") && (it.contains("收/支") || it.contains("金额"))
        }
        if (headerIdx < 0) return emptyList()

        val header = splitCsv(lines[headerIdx]).map { it.trim().replace("\"", "") }

        fun col(vararg names: String): Int {
            for (n in names) {
                val idx = header.indexOfFirst { it.replace(" ", "").contains(n) }
                if (idx >= 0) return idx
            }
            return -1
        }

        val cTime = col("交易时间", "时间")
        val cDir = col("收/支")
        val cAmt = col("金额")
        val cMer = col("交易对方", "对方")
        val cNote = col("商品说明", "商品", "备注")
        val cAcc = col("收/付款方式", "支付方式", "付款方式")
        val cStatus = col("交易状态", "当前状态")

        if (cTime < 0 || cAmt < 0) return emptyList()

        val out = ArrayList<Parsed>()
        for (i in headerIdx + 1 until lines.size) {
            val f = splitCsv(lines[i]).map { it.trim() }
            fun g(idx: Int): String = if (idx in f.indices) f[idx] else ""

            val dir = g(cDir)
            if (dir.isEmpty() || dir.contains("不计")) continue

            val status = g(cStatus)
            if (status.isNotEmpty() &&
                !status.contains("成功") && !status.contains("已收钱") && !status.contains("已转账")
            ) {
                continue
            }

            val ts = parseTime(g(cTime)) ?: continue
            val amt = parseAmount(g(cAmt)) ?: continue

            out.add(
                Parsed(
                    amount = amt,
                    type = if (dir.contains("收入")) Type.INCOME else Type.EXPENSE,
                    merchant = g(cMer),
                    note = g(cNote),
                    ts = ts,
                    source = "导入",
                    raw = lines[i]
                )
            )
        }
        return out
    }

    /** 把账目导出为 CSV 文本（Excel 可直接打开，带 BOM） */
    fun toCsv(rows: List<com.beeledger.app.data.Tx>): String {
        val sb = StringBuilder()
        sb.append("时间,类型,分类,商户,备注,账户,金额,来源\n")
        for (r in rows) {
            sb.append(esc(com.beeledger.app.D.fullText(r.ts))).append(',')
            sb.append(esc(Type.name(r.type))).append(',')
            sb.append(esc(r.category)).append(',')
            sb.append(esc(r.merchant)).append(',')
            sb.append(esc(r.note)).append(',')
            sb.append(esc(r.account)).append(',')
            sb.append(com.beeledger.app.Money.fmt(r.amount)).append(',')
            sb.append(esc(r.source)).append('\n')
        }
        return sb.toString()
    }

    private fun esc(s: String): String {
        val t = s.replace("\"", "\"\"")
        return if (t.contains(',') || t.contains('\n') || t.contains('"')) "\"$t\"" else t
    }
}
