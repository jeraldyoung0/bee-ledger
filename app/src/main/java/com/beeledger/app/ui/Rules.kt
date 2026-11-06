package com.beeledger.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beeledger.app.Bus
import com.beeledger.app.data.Cats
import com.beeledger.app.data.Repo
import com.beeledger.app.data.Rule
import com.beeledger.app.data.Type

@Composable
fun RulesScreen(onBack: () -> Unit) {
    val ver by Bus.version.collectAsState()
    val rules = remember(ver) { Repo.rules() }

    var showAdd by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(Type.EXPENSE) }
    var category by remember { mutableStateOf("餐饮") }

    Column(modifier = Modifier.fillMaxSize()) {
        SimpleTopBar("分类规则", onBack)

        Text(
            "规则优先于内置词库。比如添加「星巴克 → 餐饮」，" +
                "以后通知里出现「星巴克」就自动记成餐饮。" +
                "在明细里修改自动记账的分类，也会自动生成规则。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )

        if (rules.isEmpty()) {
            EmptyHint("还没有自定义规则\nApp 已内置 200+ 常用商户关键词，可以直接用")
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
                items(rules.size, key = { i -> rules[i].id }) { i ->
                    RuleItem(rules[i]) {
                        Repo.deleteRule(rules[i].id)
                        Bus.bump()
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {
                    keyword = ""
                    type = Type.EXPENSE
                    category = "餐饮"
                    showAdd = true
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("添加规则")
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加分类规则") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill("支出", type == Type.EXPENSE) {
                            type = Type.EXPENSE
                            category = "餐饮"
                        }
                        Spacer(Modifier.width(8.dp))
                        Pill("收入", type == Type.INCOME) {
                            type = Type.INCOME
                            category = "工资"
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("关键词 / 商户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("记为什么分类", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Cats.of(type).forEach { c ->
                            Pill(c.name, c.name == category) { category = c.name }
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (keyword.isNotBlank()) {
                        Repo.addRule(keyword, category, type)
                        Bus.bump()
                    }
                    showAdd = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RuleItem(rule: Rule, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.keyword, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    (if (rule.type == Type.INCOME) "收入 · " else "支出 · ") +
                        Cats.emoji(rule.category) + " " + rule.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
