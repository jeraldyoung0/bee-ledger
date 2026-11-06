package com.beeledger.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.beeledger.app.D

class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "bee.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tx(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount INTEGER NOT NULL,
                type INTEGER NOT NULL,
                category TEXT NOT NULL,
                merchant TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                account TEXT NOT NULL DEFAULT '现金',
                ts INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT '手动',
                raw TEXT NOT NULL DEFAULT '',
                auto INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX idx_tx_ts ON tx(ts)")
        db.execSQL("CREATE INDEX idx_tx_cat ON tx(category)")

        db.execSQL(
            """CREATE TABLE rule(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                keyword TEXT NOT NULL,
                category TEXT NOT NULL,
                type INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1)"""
        )
        db.execSQL(
            """CREATE TABLE log(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts INTEGER NOT NULL,
                source TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                parsed INTEGER NOT NULL DEFAULT 0,
                reason TEXT NOT NULL DEFAULT '')"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 首个版本，无需迁移
    }
}

/**
 * 数据仓库。个人记账数据量很小（每月几十上百条），
 * 所以直接同步查询，不做线程切换，代码更简单可靠。
 */
object Repo {

    private var helper: DbHelper? = null

    fun init(ctx: Context) {
        if (helper == null) helper = DbHelper(ctx.applicationContext)
    }

    private fun db(): SQLiteDatabase = helper!!.writableDatabase

    // ---------------- 账目 ----------------

    fun insert(t: Tx): Long {
        val cv = ContentValues()
        cv.put("amount", t.amount)
        cv.put("type", t.type)
        cv.put("category", t.category)
        cv.put("merchant", t.merchant)
        cv.put("note", t.note)
        cv.put("account", t.account)
        cv.put("ts", t.ts)
        cv.put("source", t.source)
        cv.put("raw", t.raw)
        cv.put("auto", if (t.auto) 1 else 0)
        return db().insert("tx", null, cv)
    }

    fun update(t: Tx) {
        val cv = ContentValues()
        cv.put("amount", t.amount)
        cv.put("type", t.type)
        cv.put("category", t.category)
        cv.put("merchant", t.merchant)
        cv.put("note", t.note)
        cv.put("account", t.account)
        cv.put("ts", t.ts)
        cv.put("source", t.source)
        cv.put("raw", t.raw)
        cv.put("auto", if (t.auto) 1 else 0)
        db().update("tx", cv, "id=?", arrayOf(t.id.toString()))
    }

    fun delete(id: Long) {
        db().delete("tx", "id=?", arrayOf(id.toString()))
    }

    fun get(id: Long): Tx? {
        db().rawQuery("SELECT * FROM tx WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) return readTx(c)
        }
        return null
    }

    fun listBetween(from: Long, to: Long): List<Tx> {
        val out = ArrayList<Tx>()
        db().rawQuery(
            "SELECT * FROM tx WHERE ts>=? AND ts<? ORDER BY ts DESC",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(readTx(c))
        }
        return out
    }

    fun sumByType(from: Long, to: Long, type: Int): Long {
        db().rawQuery(
            "SELECT SUM(amount) FROM tx WHERE ts>=? AND ts<? AND type=?",
            arrayOf(from.toString(), to.toString(), type.toString())
        ).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
        }
        return 0L
    }

    fun countByType(from: Long, to: Long, type: Int): Int {
        db().rawQuery(
            "SELECT COUNT(*) FROM tx WHERE ts>=? AND ts<? AND type=?",
            arrayOf(from.toString(), to.toString(), type.toString())
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 0
    }

    /** 按分类汇总，金额从大到小 */
    fun sumByCategory(from: Long, to: Long, type: Int): List<Pair<String, Long>> {
        val out = ArrayList<Pair<String, Long>>()
        db().rawQuery(
            "SELECT category, SUM(amount) AS s FROM tx WHERE ts>=? AND ts<? AND type=? " +
                "GROUP BY category ORDER BY s DESC",
            arrayOf(from.toString(), to.toString(), type.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
        }
        return out
    }

    /** 每日支出：key 为当天 00:00 的时间戳 */
    fun dailyExpense(from: Long, to: Long): Map<Long, Long> {
        val map = HashMap<Long, Long>()
        db().rawQuery(
            "SELECT ts, amount FROM tx WHERE ts>=? AND ts<? AND type=0",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val day = D.dayStart(c.getLong(0))
                map[day] = (map[day] ?: 0L) + c.getLong(1)
            }
        }
        return map
    }

    /** 去重用：附近时间是否已有同金额账目 */
    fun existsNear(amount: Long, ts: Long, windowMs: Long): Boolean {
        db().rawQuery(
            "SELECT COUNT(*) FROM tx WHERE amount=? AND ts BETWEEN ? AND ?",
            arrayOf(amount.toString(), (ts - windowMs).toString(), (ts + windowMs).toString())
        ).use { c ->
            return c.moveToFirst() && c.getLong(0) > 0
        }
    }

    // ---------------- 规则 ----------------

    fun rules(): List<Rule> {
        val out = ArrayList<Rule>()
        db().rawQuery("SELECT * FROM rule ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Rule(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        keyword = c.getString(c.getColumnIndexOrThrow("keyword")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        type = c.getInt(c.getColumnIndexOrThrow("type")),
                        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1
                    )
                )
            }
        }
        return out
    }

    /** 已存在同关键词则更新分类，不重复添加 */
    fun addRule(keyword: String, category: String, type: Int) {
        val k = keyword.trim()
        if (k.isEmpty()) return
        val cv = ContentValues()
        cv.put("keyword", k)
        cv.put("category", category)
        cv.put("type", type)
        cv.put("enabled", 1)
        val existing = db().rawQuery(
            "SELECT id FROM rule WHERE keyword=? AND type=?",
            arrayOf(k, type.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        if (existing > 0) {
            db().update("rule", cv, "id=?", arrayOf(existing.toString()))
        } else {
            db().insert("rule", null, cv)
        }
    }

    fun deleteRule(id: Long) {
        db().delete("rule", "id=?", arrayOf(id.toString()))
    }

    // ---------------- 日志 ----------------

    fun addLog(source: String, title: String, body: String, parsed: Int, reason: String) {
        val cv = ContentValues()
        cv.put("ts", System.currentTimeMillis())
        cv.put("source", source)
        cv.put("title", title)
        cv.put("body", body)
        cv.put("parsed", parsed)
        cv.put("reason", reason)
        db().insert("log", null, cv)
        // 只保留最近 300 条
        db().execSQL(
            "DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 300)"
        )
    }

    fun logs(limit: Int = 150): List<LogRow> {
        val out = ArrayList<LogRow>()
        db().rawQuery(
            "SELECT * FROM log ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    LogRow(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        ts = c.getLong(c.getColumnIndexOrThrow("ts")),
                        source = c.getString(c.getColumnIndexOrThrow("source")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        body = c.getString(c.getColumnIndexOrThrow("body")),
                        parsed = c.getInt(c.getColumnIndexOrThrow("parsed")),
                        reason = c.getString(c.getColumnIndexOrThrow("reason"))
                    )
                )
            }
        }
        return out
    }

    fun clearAll() {
        db().delete("tx", null, null)
        db().delete("log", null, null)
    }

    // ---------------- 内部 ----------------

    private fun readTx(c: Cursor): Tx = Tx(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        amount = c.getLong(c.getColumnIndexOrThrow("amount")),
        type = c.getInt(c.getColumnIndexOrThrow("type")),
        category = c.getString(c.getColumnIndexOrThrow("category")),
        merchant = c.getString(c.getColumnIndexOrThrow("merchant")),
        note = c.getString(c.getColumnIndexOrThrow("note")),
        account = c.getString(c.getColumnIndexOrThrow("account")),
        ts = c.getLong(c.getColumnIndexOrThrow("ts")),
        source = c.getString(c.getColumnIndexOrThrow("source")),
        raw = c.getString(c.getColumnIndexOrThrow("raw")),
        auto = c.getInt(c.getColumnIndexOrThrow("auto")) == 1
    )
}
