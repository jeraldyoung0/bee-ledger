package com.beeledger.app.data

/** 一条账目。amount 永远为正数，收支方向由 type 决定。 */
data class Tx(
    val id: Long = 0L,
    val amount: Long,
    val type: Int,
    val category: String,
    val merchant: String = "",
    val note: String = "",
    val account: String = "现金",
    val ts: Long,
    val source: String = "手动",
    val raw: String = "",
    val auto: Boolean = false,
)

/** 用户自定义分类规则：文案里出现 keyword，就记为 category。 */
data class Rule(
    val id: Long = 0L,
    val keyword: String,
    val category: String,
    val type: Int,
    val enabled: Boolean = true,
)

/** 自动捕获日志：用来让用户信任或纠正自动记账。 */
data class LogRow(
    val id: Long = 0L,
    val ts: Long,
    val source: String,
    val title: String,
    val body: String,
    val parsed: Int,
    val reason: String,
)

object Type {
    const val EXPENSE = 0
    const val INCOME = 1
    fun name(t: Int): String = if (t == INCOME) "收入" else "支出"
}

object Account {
    val list = listOf("微信", "支付宝", "现金", "银行卡", "信用卡", "其他")
}
