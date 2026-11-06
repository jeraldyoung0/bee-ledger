package com.beeledger.app.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeledger.app.Bus
import com.beeledger.app.D
import com.beeledger.app.Money
import com.beeledger.app.data.Account
import com.beeledger.app.data.Cats
import com.beeledger.app.data.Repo
import com.beeledger.app.data.Type
import com.beeledger.app.data.Tx
import java.util.Calendar

@Composable
fun AddScreen(editing: Tx?, onDone: () -> Unit) {
    val ctx = LocalContext.current

    var type by remember { mutableStateOf(editing?.type ?: Type.EXPENSE) }
    var amountStr by remember {
        mutableStateOf(
            editing?.let {
                val s = Money.fmt(it.amount)
                if (s.endsWith(".00")) s.dropLast(3) else s
            } ?: ""
        )
    }
    var category by remember {
        mutableStateOf(editing?.category ?: Cats.of(type).first().name)
    }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var account by remember { mutableStateOf(editing?.account ?: "微信") }
    var dateMillis by remember { mutableStateOf(editing?.ts ?: System.currentTimeMillis()) }
    var showDelete by remember { mutableStateOf(false) }

    fun switchType(t: Int) {
        type = t
        if (Cats.of(t).none { it.name == category }) {
            category = Cats.of(t).first().name
        }
    }

    fun press(k: String) {
        when (k) {
            "⌫" -> if (amountStr.isNotEmpty()) amountStr = amountStr.dropLast(1)
            "." -> if (!amountStr.contains(".")) {
                amountStr = if (amountStr.isEmpty()) "0." else amountStr + "."
            }

            else -> {
                val digits = amountStr.count { it.isDigit() }
                if (digits >= 9) return
                if (amountStr.contains(".")) {
                    val dec = amountStr.substringAfter(".").length
                    if (dec >= 2) return
                }
                amountStr = if (amountStr == "0") k else amountStr + k
            }
        }
    }

    fun save() {
        val cents = Money.parse(amountStr)
        if (cents <= 0L) {
            Toast.makeText(ctx, "请先输入金额", Toast.LENGTH_SHORT).show()
            return
        }
        val isNew = editing == null
        val tx = Tx(
            id = editing?.id ?: 0L,
            amount = cents,
            type = type,
            category = category,
            merchant = editing?.merchant ?: "",
            note = note.trim(),
            account = account,
            ts = dateMillis,
            source = editing?.source ?: "手动",
            raw = editing?.raw ?: "",
            auto = false
        )
        if (isNew) Repo.insert(tx) else Repo.update(tx)

        // 学习：用户把「自动记」的账改正了分类，就记下这条规则，下次自动分对
        if (!isNew && editing != null && editing.auto &&
            editing.category != category && editing.merchant.isNotBlank()
        ) {
            Repo.addRule(editing.merchant, category, type)
        }

        Bus.bump()
        onDone()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {

        // ---------- 顶部 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("取消") }
            Text(
                if (editing == null) "记一笔" else "编辑账目",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (editing != null) {
                TextButton(onClick = { showDelete = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Spacer(Modifier.width(52.dp))
            }
        }

        // ---------- 收支切换 ----------
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Spacer(Modifier.weight(1f))
            Pill("支出", type == Type.EXPENSE) { switchType(Type.EXPENSE) }
            Spacer(Modifier.width(10.dp))
            Pill("收入", type == Type.INCOME) { switchType(Type.INCOME) }
            Spacer(Modifier.weight(1f))
        }

        // ---------- 金额 ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                if (type == Type.INCOME) "+¥" else "-¥",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp, end = 4.dp)
            )
            Text(
                if (amountStr.isEmpty()) "0.00" else amountStr,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (editing?.auto == true) {
                Text(
                    "  自动识别",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ---------- 中间可滚动区 ----------
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(4.dp))
            Text("分类", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))

            Cats.of(type).chunked(4).forEach { line ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    line.forEach { c ->
                        val selected = (c.name == category)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { category = c.name }
                                .padding(vertical = 7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(c.emoji, fontSize = 19.sp)
                            }
                            Text(
                                c.name,
                                fontSize = 11.sp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("账户", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Account.list.forEach { a ->
                    Pill(a, a == account) { account = a }
                    Spacer(Modifier.width(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("日期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val today = D.dayStart(System.currentTimeMillis())
                val yesterday = today - 24L * 3600 * 1000
                Pill("今天", D.dayStart(dateMillis) == today) {
                    dateMillis = System.currentTimeMillis()
                }
                Spacer(Modifier.width(8.dp))
                Pill("昨天", D.dayStart(dateMillis) == yesterday) {
                    dateMillis = yesterday + 12L * 3600 * 1000
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val c = Calendar.getInstance()
                    c.timeInMillis = dateMillis
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d -> dateMillis = D.fromYmd(y, m + 1, d) },
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH),
                        c.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Text(D.shortDay(dateMillis), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (editing != null && editing.raw.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "原始信息：" + editing.raw,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- 数字键盘 ----------
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫")
        )
        keys.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                line.forEach { k ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .height(48.dp)
                            .clickable { press(k) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(k, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("保存", fontSize = 16.sp)
        }
        Spacer(Modifier.height(4.dp))
    }

    val deleting = editing
    if (showDelete && deleting != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这笔账目？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.delete(deleting.id)
                    Bus.bump()
                    showDelete = false
                    onDone()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}
