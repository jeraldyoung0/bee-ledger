package com.beeledger.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.beeledger.app.Bus
import com.beeledger.app.Money
import com.beeledger.app.Prefs
import com.beeledger.app.capture.Importer
import com.beeledger.app.capture.PayParser
import com.beeledger.app.data.AutoCat
import com.beeledger.app.data.Repo
import com.beeledger.app.data.Tx

@Composable
fun MineScreen(ver: Int, onOpenLog: () -> Unit, onOpenRules: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var showBudget by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    // 从系统设置页返回时刷新权限状态
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val notifOn = remember(refresh) { hasNotifAccess(ctx) }
    val smsOn = remember(refresh) {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    var autoOn by remember { mutableStateOf(Prefs.autoCapture) }
    var smsAutoOn by remember { mutableStateOf(Prefs.autoSms) }
    var readAutoOn by remember { mutableStateOf(Prefs.autoRead) }
    val a11yOn = remember(refresh) { hasA11y(ctx) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = Importer.readText(ctx.contentResolver, uri)
        val list = Importer.parse(text)
        if (list.isEmpty()) {
            Toast.makeText(ctx, "没有解析到账单，请确认导出的是 CSV 文件", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        var added = 0
        for (p in list) {
            if (Repo.existsNear(p.amount, p.ts, 60_000L)) continue
            val (cat, auto) = AutoCat.categorize(p.merchant + " " + p.note, p.type, Repo.rules())
            Repo.insert(
                Tx(
                    amount = p.amount,
                    type = p.type,
                    category = cat,
                    merchant = p.merchant,
                    note = p.note,
                    account = PayParser.guessAccount("导入", p.merchant + " " + p.note),
                    ts = p.ts,
                    source = "导入",
                    raw = p.raw,
                    auto = auto
                )
            )
            added++
        }
        Bus.bump()
        Toast.makeText(ctx, "成功导入 $added 笔账目", Toast.LENGTH_LONG).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val rows = Repo.listBetween(0L, Long.MAX_VALUE)
            val csv = Importer.toCsv(rows)
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(csv.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(ctx, "已导出 ${rows.size} 笔账目", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "导出失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            "自动记账",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
        )

        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开启自动记账", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "支付成功后自动生成一笔账目",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoOn,
                    onCheckedChange = {
                        autoOn = it
                        Prefs.autoCapture = it
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            PermissionRow(
                title = "通知使用权",
                desc = if (notifOn) "已开启，可以读取微信 / 支付宝 / 银行的支付通知"
                else "未开启：这是自动记账的核心权限，必须手动授权",
                ok = notifOn,
                action = "去开启"
            ) {
                openNotificationSettings(ctx)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            PermissionRow(
                title = "短信读取权限",
                desc = if (smsOn) "已开启，可以识别银行卡交易短信"
                else "未开启：只能靠通知识别，银行卡消费会漏记",
                ok = smsOn,
                action = "去授权"
            ) {
                smsLauncher.launch(
                    arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("短信自动记账", fontSize = 14.sp)
                    Text(
                        "关闭后只监听通知，不读取短信",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = smsAutoOn,
                    onCheckedChange = {
                        smsAutoOn = it
                        Prefs.autoSms = it
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "读屏自动记账",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开启读屏记账", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "识别 微信 / 支付宝 / 抖音 / 拼多多 的「支付成功」页",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = readAutoOn,
                    onCheckedChange = {
                        readAutoOn = it
                        Prefs.autoRead = it
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            PermissionRow(
                title = "无障碍权限",
                desc = if (a11yOn) "已开启，付款后能自动识别金额"
                else "未开启：微信 / 支付宝 / 抖音 付款不会自动记账",
                ok = a11yOn,
                action = "去开启"
            ) {
                openA11ySettings(ctx)
            }

            Text(
                "本服务只在这 5 个 App 内运行，其他应用的界面系统不会转发过来；" +
                    "只处理「支付成功」这类很短的页面，其他内容立即丢弃、不保存。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "账本设置",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        CardBox {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBudget = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("月度预算", fontSize = 14.sp)
                    Text(
                        if (Prefs.budget > 0) "当前 " + Money.fmt(Prefs.budget) + " 元"
                        else "未设置，设置后可在首页看进度",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("设置", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenRules() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("分类规则", fontSize = 14.sp)
                    Text(
                        "教会 App：看到某个商户就记成某个分类",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("管理", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenLog() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("捕获日志", fontSize = 14.sp)
                    Text(
                        "看看自动记账抓到了什么、为什么没抓",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("查看", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "数据",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        CardBox {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { importLauncher.launch(arrayOf("*/*")) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("导入账单", fontSize = 14.sp)
                    Text(
                        "选择支付宝 / 微信导出的 CSV，补全历史账单",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("选择文件", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exportLauncher.launch("蜜蜂记账账单.csv") },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("导出账单", fontSize = 14.sp)
                    Text(
                        "导出全部账目为 CSV，可用 Excel 打开",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("导出", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().clickable { showClear = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("清空数据", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    Text(
                        "删除全部账目与日志，不可恢复",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "蜜蜂记账 1.0.0\n数据只保存在本机，不上传任何服务器。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(28.dp))
    }

    if (showBudget) {
        var text by remember { mutableStateOf(if (Prefs.budget > 0) Money.short(Prefs.budget) else "") }
        AlertDialog(
            onDismissRequest = { showBudget = false },
            title = { Text("设置月度预算") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("每月预算（元）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.budget = Money.parse(text)
                    showBudget = false
                    Bus.bump()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBudget = false }) { Text("取消") }
            }
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("确定清空全部数据？") },
            text = { Text("所有账目和捕获日志都会被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.clearAll()
                    showClear = false
                    Bus.bump()
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    ok: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                desc,
                fontSize = 11.sp,
                color = if (ok) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error
            )
        }
        if (!ok) {
            TextButton(onClick = onClick) { Text(action, fontSize = 13.sp) }
        } else {
            Text("已开启", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

private fun hasNotifAccess(ctx: Context): Boolean = try {
    val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
    enabled != null && enabled.contains(ctx.packageName)
} catch (e: Exception) {
    false
}

private fun hasA11y(ctx: Context): Boolean = try {
    val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services")
    enabled != null && enabled.contains(ctx.packageName) && enabled.contains("PayReadService")
} catch (e: Exception) {
    false
}

private fun openA11ySettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "请手动到 设置 → 无障碍 中开启", Toast.LENGTH_LONG).show()
    }
}

private fun openNotificationSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            Toast.makeText(ctx, "请手动到系统设置中开启通知使用权", Toast.LENGTH_LONG).show()
        }
    }
}
