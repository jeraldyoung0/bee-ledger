package com.beeledger.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeledger.app.D
import com.beeledger.app.Money
import com.beeledger.app.Prefs
import com.beeledger.app.data.Cats
import com.beeledger.app.data.Repo
import com.beeledger.app.data.Type
import com.beeledger.app.data.Tx
import java.time.YearMonth

@Composable
fun HomeScreen(ver: Int, onEdit: (Tx) -> Unit) {
    val ym = YearMonth.now()
    val from = D.monthStart(ym)
    val to = D.monthEnd(ym)

    val rows = remember(ver) { Repo.listBetween(from, to) }
    val expense = remember(ver) { Repo.sumByType(from, to, Type.EXPENSE) }
    val income = remember(ver) { Repo.sumByType(from, to, Type.INCOME) }
    val budget = Prefs.budget
    val groups = remember(rows) { rows.groupBy { D.dayStart(it.ts) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp)
    ) {
        item { SummaryHeader(expense, income, budget) }

        if (rows.isEmpty()) {
            item { EmptyHint("这个月还没有账目\n点右下角「＋」记一笔，或去「我的」开启自动记账") }
        }

        groups.forEach { (day, list) ->
            item(key = "day_$day") {
                DayHeader(day, list.filter { it.type == Type.EXPENSE }.sumOf { it.amount })
            }
            items(list.size, key = { i -> list[i].id }) { i ->
                TxRow(list[i], onEdit)
            }
        }
    }
}

@Composable
private fun SummaryHeader(expense: Long, income: Long, budget: Long) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "本月支出",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "¥",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 6.dp, end = 2.dp)
                )
                Text(
                    Money.fmt(expense),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(16.dp))

            Row {
                MiniStat("收入", income)
                Spacer(Modifier.width(26.dp))
                MiniStat("结余", income - expense)
                if (budget > 0) {
                    Spacer(Modifier.width(26.dp))
                    MiniStat("预算剩余", budget - expense)
                }
            }

            if (budget > 0) {
                val frac = (expense.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                    )
                }
                Text(
                    "预算 " + Money.fmt(budget) + "  已用 " + (frac * 100).toInt() + "%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, cents: Long) {
    Column {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
        )
        Text(
            Money.fmt(cents),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun DayHeader(day: Long, expense: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            D.dayLabel(day),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (expense > 0) {
            Text(
                "支出 " + Money.fmt(expense),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TxRow(tx: Tx, onEdit: (Tx) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onEdit(tx) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = catColor(tx.category).copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(Cats.emoji(tx.category), fontSize = 17.sp)
                }
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tx.category, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (tx.auto) {
                        Text(
                            " 自动",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                val sub = listOf(tx.merchant, tx.note).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    if (sub.isBlank()) tx.account + "  " + D.timeLabel(tx.ts)
                    else sub + "  " + D.timeLabel(tx.ts),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Text(
                (if (tx.type == Type.INCOME) "+" else "-") + Money.fmt(tx.amount),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (tx.type == Type.INCOME) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
