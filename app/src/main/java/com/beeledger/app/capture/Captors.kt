package com.beeledger.app.capture

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.beeledger.app.Bus
import com.beeledger.app.Prefs
import com.beeledger.app.data.AutoCat
import com.beeledger.app.data.Repo
import com.beeledger.app.data.Tx

/**
 * 通知监听：这是「自动记账」最主要的来源。
 * 微信支付、支付宝、云闪付、各家银行 App 支付成功后都会发通知，
 * 我们在通知里抓取金额、商户，自动生成一笔账。
 *
 * 需要用户在系统设置里授予「通知使用权」，代码无法自行申请。
 */
class PayNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            handle(sbn)
        } catch (e: Exception) {
            // 通知监听绝不能崩，否则会被系统解绑
            Repo.addLog("通知", "解析异常", e.message ?: "", 0, "异常")
        }
    }

    private fun handle(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (!Prefs.autoCapture) return

        val pkg = n.packageName ?: return
        if (pkg == packageName) return

        val ex = n.notification?.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val info = ex.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()

        val body = listOf(text, big, sub, info)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        val full = (title + " " + body).trim()
        if (full.length < 4) return

        val appName = appLabel(pkg)
        if (!PayParser.looksInteresting(full)) return

        val p = PayParser.parse(full, appName, System.currentTimeMillis(), "通知")
        if (p == null) {
            Repo.addLog("通知", title, full, 0, "未识别出金额，已跳过")
            return
        }

        if (Repo.existsNear(p.amount, p.ts, 180_000L)) {
            Repo.addLog("通知", title, full, 2, "重复记录，已忽略")
            return
        }

        val (cat, auto) = AutoCat.categorize(
            p.merchant + " " + p.note + " " + p.raw,
            p.type,
            Repo.rules()
        )

        Repo.insert(
            Tx(
                amount = p.amount,
                type = p.type,
                category = cat,
                merchant = p.merchant,
                note = p.note,
                account = PayParser.guessAccount(appName, full),
                ts = p.ts,
                source = "通知",
                raw = p.raw,
                auto = auto
            )
        )
        Repo.addLog("通知", title, full, 1, cat)
        Bus.bump()
    }

    private fun appLabel(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (e: Exception) {
        pkg
    }
}

/**
 * 短信监听：银行储蓄卡/信用卡交易短信。
 * 很多支付走的是银行卡快捷支付，不发 App 通知但会发短信。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        if (!Prefs.autoSms) return
        Repo.init(context)

        val messages = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        }.getOrNull() ?: return

        for (m in messages) {
            val body = m.messageBody ?: continue
            val from = m.originatingAddress ?: "银行短信"
            try {
                handleOne(body, from)
            } catch (e: Exception) {
                Repo.addLog("短信", from, body, 0, "解析异常")
            }
        }
    }

    private fun handleOne(body: String, from: String) {
        if (!PayParser.looksInteresting(body)) return

        val now = System.currentTimeMillis()
        val p = PayParser.parse(body, from, now, "短信")
        if (p == null) {
            Repo.addLog("短信", from, body, 0, "未识别出金额，已跳过")
            return
        }

        if (Repo.existsNear(p.amount, p.ts, 180_000L)) {
            Repo.addLog("短信", from, body, 2, "重复记录，已忽略")
            return
        }

        val (cat, auto) = AutoCat.categorize(p.merchant + " " + p.note + " " + p.raw, p.type, Repo.rules())

        Repo.insert(
            Tx(
                amount = p.amount,
                type = p.type,
                category = cat,
                merchant = p.merchant,
                note = p.note,
                account = PayParser.guessAccount(from, body),
                ts = p.ts,
                source = "短信",
                raw = p.raw,
                auto = auto
            )
        )
        Repo.addLog("短信", from, body, 1, cat)
        Bus.bump()
    }
}
