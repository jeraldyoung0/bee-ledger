package com.beeledger.app.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.beeledger.app.Bus
import com.beeledger.app.Prefs
import com.beeledger.app.data.AutoCat
import com.beeledger.app.data.Repo
import com.beeledger.app.data.Tx

/**
 * 通道三：读屏识别「支付成功」页面。
 *
 * 为什么需要它：
 *   微信、支付宝、抖音、拼多多在**付款**时都是前台操作，安卓不会弹通知，
 *   所以通知监听抓不到。唯一能拿到金额的地方，就是那个「支付成功」页面本身。
 *
 * 隐私边界（三道闸门）：
 *   1. 系统级：accessibility_service_config.xml 里的 packageNames 只列了 5 个包，
 *      其他应用的界面事件系统根本不会发过来。
 *   2. 代码级：这里再校验一次包名，双重保险。
 *   3. 内容级：页面文本必须先命中「支付成功」这类词、且长度很短、¥ 不超过 3 个，
 *      才会进入解析；否则立即丢弃，不记录、不落盘。
 *
 * 存下来的只有：金额、来源 App、时间、最多 160 字的片段（用于你在日志里核对）。
 */
class PayReadService : AccessibilityService() {

    companion object {
        /** 与 accessibility_service_config.xml 中的 packageNames 保持一致 */
        val WHITELIST: Set<String> = setOf(
            "com.tencent.mm",              // 微信
            "com.eg.android.AlipayGphone", // 支付宝
            "com.ss.android.ugc.aweme",    // 抖音
            "com.xunmeng.pinduoduo",       // 拼多多
            "com.unionpay"                 // 云闪付
        )

        private val SUCCESS_WORDS = listOf(
            "支付成功", "付款成功", "支付完成", "付款完成",
            "交易成功", "已支付", "转账成功", "缴费成功"
        )

        /** 这些是「查历史账单」页面的特征词，出现就跳过，避免重复记账 */
        private val HISTORY_WORDS = listOf(
            "账单详情", "交易记录", "收支明细", "订单详情", "全部账单",
            "交易单号", "商户单号"
        )
    }

    private var lastScanAt = 0L
    private var lastKey = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            scan(event)
        } catch (e: Exception) {
            // 读屏服务绝不能崩，崩了会被系统关闭
        }
    }

    override fun onInterrupt() {
        // 系统要求实现，无需处理
    }

    private fun scan(event: AccessibilityEvent?) {
        val e = event ?: return
        if (!Prefs.autoRead) return

        val pkg = e.packageName?.toString() ?: return
        if (!WHITELIST.contains(pkg)) return

        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            e.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastScanAt < 1000L) return
        lastScanAt = now

        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val sb = StringBuilder()
        walk(root, sb, 0)
        val page = sb.toString().trim()
        if (page.length < 4) return

        // 闸门 3：必须像「支付结果页」
        if (SUCCESS_WORDS.none { page.contains(it) }) return
        if (page.length > 700) return
        if (page.count { it == '¥' || it == '￥' } > 3) return
        if (HISTORY_WORDS.any { page.contains(it) }) return

        val label = appLabel(pkg)
        val p = PayParser.parse(page, label, now, "读屏")
        if (p == null) {
            Repo.addLog("读屏", label, page.take(160), 0, "识别到支付页，但没解析出金额")
            return
        }

        // 同一次支付可能连续触发多次事件，30 秒内同一金额只处理一次
        val key = pkg + "|" + p.amount + "|" + (now / 30_000L)
        if (key == lastKey) return
        lastKey = key

        if (Repo.existsNear(p.amount, p.ts, 120_000L)) return

        val (cat, auto) = AutoCat.categorize(p.merchant + " " + page.take(80), p.type, Repo.rules())

        Repo.insert(
            Tx(
                amount = p.amount,
                type = p.type,
                category = cat,
                merchant = p.merchant,
                note = "",
                account = PayParser.guessAccount(label, page),
                ts = p.ts,
                source = "读屏",
                raw = page.take(160),
                auto = auto
            )
        )
        Repo.addLog("读屏", label, page.take(160), 1, cat)
        Bus.bump()
    }

    /** 遍历界面节点，收集可见文字（最多 2000 字，深度 14 层） */
    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 14 || sb.length > 2000) return
        node.text?.let { if (it.isNotEmpty()) sb.append(it).append(' ') }
        node.contentDescription?.let { if (it.isNotEmpty()) sb.append(it).append(' ') }
        val count = node.childCount
        for (i in 0 until count) {
            walk(node.getChild(i), sb, depth + 1)
        }
    }

    private fun appLabel(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (e: Exception) {
        pkg
    }
}
