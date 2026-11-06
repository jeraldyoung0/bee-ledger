package com.beeledger.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeledger.app.Bus
import com.beeledger.app.D
import com.beeledger.app.data.LogRow
import com.beeledger.app.data.Repo

@Composable
fun SimpleTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹ 返回", fontSize = 14.sp) }
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LogScreen(onBack: () -> Unit) {
    val ver by Bus.version.collectAsState()
    val logs = remember(ver) { Repo.logs(150) }

    Column(modifier = Modifier.fillMaxSize()) {
        SimpleTopBar("捕获日志", onBack)

        Text(
            "下面记录了 App 抓到的每一条支付信息。" +
                "如果某笔没记上，这里通常能找到原因；" +
                "如果有误判，直接去「明细」里改分类，App 会自动记住这个商户。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )

        if (logs.isEmpty()) {
            EmptyHint("还没有捕获记录\n授权通知使用权后，去微信/支付宝付一笔试试")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logs.size, key = { i -> logs[i].id }) { i ->
                    LogItem(logs[i])
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LogItem(row: LogRow) {
    val (label, color) = when (row.parsed) {
        1 -> "已记账" to MaterialTheme.colorScheme.tertiary
        2 -> "已忽略" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "未识别" to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.source,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    D.shortDay(row.ts) + " " + D.timeLabel(row.ts),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                (if (row.title.isBlank()) "" else row.title + "：") + row.body,
                fontSize = 12.sp,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (row.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "→ " + row.reason,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
