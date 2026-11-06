package com.beeledger.app.capture

import com.beeledger.app.data.Type

/** 解析结果（还未落库） */
data class Parsed(
    val amount: Long,
    val type: Int,
    val merchant: String,
    val note: String,
    val ts: Long,
    val source: String,
    val raw: String,
)

/**
 * 支付文本解析器。
 *
 * 设计原则：宁可漏记，也不误记。
 * 因此必须同时满足「有金额标记」+「有支付/收入动词」才认，
 * 并且排除「待支付」「验证码」「优惠券」等干扰文案。
 */
object PayParser {

    private val moneyMark = Regex("""[¥￥]|元|RMB""", RegexOption.IGNORE_CASE)

    private val amt1 = Regex("""[¥￥]\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val amt2 = Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*元""")
    private val amt3 = Regex("""人民币\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    private val incomeWords = listOf("收款", "入账", "转入", "退款", "到账", "收到", "收入", "存入")
    private val expenseWords = listOf("支付", "付款", "消费", "扣款", "支出", "缴费", "扣费", "交易")

    private val negativeWords = listOf(
        "待支付", "待付款", "请尽快", "即将", "将于", "优惠", "账单提醒",
        "还款日", "额度", "验证码", "验证", "登录", "余额不足", "失败", "失效"
    )

    private val mer1 = Regex(
        """(?:向|在|给)\s*([\u4e00-\u9fa5A-Za-z0-9·\-]{2,18}?)(?:支付|付款|消费|转账|充值|购买|下单|扫码)"""
    )
    private val mer2 = Regex("""商户[：:]\s*([^\s，,。]{2,20})""")
    private val mer3 = Regex(
        """([\u4e00-\u9fa5A-Za-z0-9·\-]{2,16}?)(?:旗舰店|专营店|官方店|专卖店)"""
    )

    private fun pickAmount(s: String): Long? {
        for (re in listOf(amt1, amt2, amt3)) {
            val m = re.find(s) ?: continue
            val num = m.groupValues[1].replace(",", "")
            val d = num.toDoubleOrNull() ?: continue
            if (d <= 0.0 || d > 1_000_000.0) continue
            return Math.round(d * 100)
        }
        return null
    }

    private fun pickMerchant(s: String, appName: String): String {
        mer1.find(s)?.let { return it.groupValues[1].trim() }
        mer2.find(s)?.let { return it.groupValues[1].trim() }
        mer3.find(s)?.let { return it.groupValues[0].trim() }
        return appName
    }

    fun looksInteresting(s: String): Boolean =
        moneyMark.containsMatchIn(s) && (expenseWords.any { s.contains(it) } || incomeWords.any { s.contains(it) })

    /**
     * @param full    通知标题 + 正文，或短信全文
     * @param appName 来源 App 名称，用于兜底商户名
     * @param now     当前时间
     * @param source  来源标记：通知 / 短信
     */
    fun parse(full: String, appName: String, now: Long, source: String): Parsed? {
        val s = full.replace("\n", " ").replace("\r", " ").trim()
        if (s.length < 4) return null
        if (!moneyMark.containsMatchIn(s)) return null

        val hasExpense = expenseWords.any { s.contains(it) }
        val hasIncome = incomeWords.any { s.contains(it) }
        if (!hasExpense && !hasIncome) return null

        val stronglyNegative = negativeWords.any { s.contains(it) }
        if (stronglyNegative && !s.contains("成功") && !s.contains("已支付") && !s.contains("已收款")) {
            return null
        }

        // 退款/收款 优先判定为收入
        val type = if (s.contains("退款") || s.contains("收款") || s.contains("入账") ||
            s.contains("转入") || s.contains("存入")
        ) Type.INCOME else Type.EXPENSE

        val amount = pickAmount(s) ?: return null
        val merchant = pickMerchant(s, appName)

        return Parsed(
            amount = amount,
            type = type,
            merchant = merchant,
            note = "",
            ts = now,
            source = source,
            raw = s
        )
    }

    /** 根据文本推断资金账户 */
    fun guessAccount(appName: String, text: String): String {
        val all = appName + " " + text
        return when {
            all.contains("支付宝") -> "支付宝"
            all.contains("微信") -> "微信"
            all.contains("信用卡") -> "信用卡"
            all.contains("储蓄卡") || all.contains("银行") -> "银行卡"
            else -> "银行卡"
        }
    }
}
