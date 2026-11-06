package com.beeledger.app.data

/**
 * 内置分类。emoji 用作图标，避免额外引入图标库；
 * color 为 ARGB 数值，用于统计页配色。
 */
data class Cat(
    val name: String,
    val emoji: String,
    val color: Long,
    val type: Int,
)

object Cats {

    val expense = listOf(
        Cat("餐饮", "🍜", 0xFFEF6C00, Type.EXPENSE),
        Cat("交通", "🚌", 0xFF1E88E5, Type.EXPENSE),
        Cat("购物", "🛍️", 0xFFD81B60, Type.EXPENSE),
        Cat("日用", "🧴", 0xFF00897B, Type.EXPENSE),
        Cat("娱乐", "🎮", 0xFF8E24AA, Type.EXPENSE),
        Cat("居住", "🏠", 0xFF6D4C41, Type.EXPENSE),
        Cat("医疗", "💊", 0xFF43A047, Type.EXPENSE),
        Cat("教育", "📚", 0xFF3949AB, Type.EXPENSE),
        Cat("通讯", "📱", 0xFF039BE5, Type.EXPENSE),
        Cat("人情", "🎁", 0xFFE53935, Type.EXPENSE),
        Cat("宠物", "🐾", 0xFFFB8C00, Type.EXPENSE),
        Cat("旅行", "✈️", 0xFF00ACC1, Type.EXPENSE),
        Cat("美容", "💅", 0xFFEC407A, Type.EXPENSE),
        Cat("数码", "💻", 0xFF546E7A, Type.EXPENSE),
        Cat("运动", "🏃", 0xFF7CB342, Type.EXPENSE),
        Cat("服饰", "👕", 0xFF9E9D24, Type.EXPENSE),
        Cat("其他", "📦", 0xFF90A4AE, Type.EXPENSE),
    )

    val income = listOf(
        Cat("工资", "💰", 0xFF2E7D32, Type.INCOME),
        Cat("奖金", "🎉", 0xFFF9A825, Type.INCOME),
        Cat("红包", "🧧", 0xFFC62828, Type.INCOME),
        Cat("退款", "↩️", 0xFF00838F, Type.INCOME),
        Cat("理财", "📈", 0xFF283593, Type.INCOME),
        Cat("兼职", "💼", 0xFF5E35B1, Type.INCOME),
        Cat("其他收入", "💵", 0xFF6D4C41, Type.INCOME),
    )

    val all: List<Cat> = expense + income

    fun of(type: Int): List<Cat> = if (type == Type.INCOME) income else expense

    fun byName(name: String): Cat? = all.firstOrNull { it.name == name }

    fun emoji(name: String): String = byName(name)?.emoji ?: "📦"

    fun color(name: String): Long = byName(name)?.color ?: 0xFF90A4AE
}
