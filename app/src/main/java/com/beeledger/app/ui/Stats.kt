package com.beeledger.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeledger.app.D
import com.beeledger.app.Money
import com.beeledger.app.data.Repo
import com.beeledger.app.data.Type
import java.time.YearMonth

@Composable
fun StatsScreen(ver: Int) {
    var ym by remember { mutableStateOf(YearMonth.now()) }

    val from = D.monthStart(ym)
    val to = D.monthEnd(ym)

    val expense = remember(ver, ym) { Repo.sumByType(from, to, Type.EXPENSE) }
    val income = remember(ver, ym) { Repo.sumByType(from, to, Type.INCOME) }
    val count = remember(ver, ym) { Repo.countByType(from, to, Type.EXPENSE) }
    val cats = remember(ver, ym) { Repo.sumByCategory(from, to, Type.EXPENSE) }
    val daily = remember(ver, ym) { Repo.dailyExpense(from, to) }

    val days = ym.lengthOfMonth()
    val values = (1..days).map { d ->
        daily[D.dayStart(D.fromYmd(ym.year, ym.monthValue, d))] ?: 0L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 月份切换
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { ym = ym.minusMonths(1) }) { Text("‹", fontSize = 22.sp) }
            Text(
                D.ymLabel(ym),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            TextButton(onClick = { ym = ym.plusMonths(1) }) { Text("›", fontSize = 22.sp) }
        }

        Spacer(Modifier.height(6.dp))

        CardBox {
            Text("本月支出", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "¥",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    Money.fmt(expense),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                StatCell("收入", Money.fmt(income))
                StatCell("笔数", count.toString())
                StatCell(
                    "日均",
                    Money.fmt(if (days > 0) expense / days else 0L)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        CardBox {
            Text(
                "分类排行",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            if (cats.isEmpty()) {
                EmptyHint("本月还没有支出记录")
            } else {
                val max = cats.maxOf { it.second }
                val rows = remember(ver, ym) { Repo.listBetween(from, to) }
                val counts = remember(rows) {
                    rows.filter { it.type == Type.EXPENSE }
                        .groupingBy { it.category }
                        .eachCount()
                }
                cats.forEach { (name, cents) ->
                    CategoryBar(name, cents, max, counts[name] ?: 0)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每日趋势", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "最多 " + Money.fmt(values.maxOrNull() ?: 0L),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            DailyTrend(values = values)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("1日", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${days}日", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(modifier = Modifier.padding(end = 28.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
