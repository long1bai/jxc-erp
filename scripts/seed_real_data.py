# -*- coding: utf-8 -*-
"""
jxc ERP 真实感数据生成脚本（2026-08-01）
- 目的：让系统看起来像真实使用中（财务/退货/生产/盘点等空模块补数据 + 近3个月活跃业务）
- 原则：
  1. 全部使用现有真实主数据（客户/供应商/物料/员工/工序/资金账户），不造虚假主数据
  2. 数据自洽：订单→送货→收款核销、采购→付款核销、发票关联单据、库存流水连续
  3. 单号走 sequences 序列（不撞号）；日期集中在 2026-05 ~ 2026-08
  4. 可重复运行（按单号前缀标记 XT- 判断，已生成过则跳过/续跑）
- 用法：python scripts/seed_real_data.py
"""
import json
import random
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path

MYSQL = r"C:\mysql\8.0.28\bin\mysql.exe"
DB = "yawei_erp"
random.seed(20260801)
TAG = "XT-"

# 日期窗口：近 3 个月
END = datetime(2026, 7, 31)
START = datetime(2026, 5, 1)
N_DAYS = (END - START).days


_SQL_BUF = []  # 批处理缓冲：所有 INSERT/UPDATE 攒起来，最后一次性灌入 MySQL


def sql(query, fetch=False):
    """查询（fetch=True）立即执行；写操作攒入批缓冲，最后 flush_sql() 一次灌入。
    原因：每个 next_no 起 mysql.exe 子进程太慢（上万次进程启动会超时），
    批量执行单次进程调用秒级完成。"""
    if fetch:
        p = subprocess.run([MYSQL, "-uroot", DB, "-N", "-e", query],
                           capture_output=True, timeout=60)
        if p.returncode != 0:
            err = p.stderr.decode("gbk", errors="replace")
            raise RuntimeError("SQL error: %s\n%s" % (query[:120], err[:200]))
        out = p.stdout.decode("gbk", errors="replace")
        return [ln.split("\t") for ln in out.strip().splitlines() if ln]
    _SQL_BUF.append(query)
    return ""


def flush_sql():
    """把批缓冲的写操作一次性灌入 MySQL（单次进程调用）"""
    global _SQL_BUF
    if not _SQL_BUF:
        return
    payload = ";\n".join(_SQL_BUF) + ";\n"
    p = subprocess.run([MYSQL, "-uroot", DB], input=payload.encode("gbk", errors="replace"),
                       capture_output=True, timeout=300)
    if p.returncode != 0:
        err = p.stderr.decode("gbk", errors="replace")
        raise RuntimeError("flush_sql error (%d 条): %s" % (len(_SQL_BUF), err[:300]))
    _SQL_BUF = []


def seq_sql(query):
    """sequences 表操作即时执行（取号/查重依赖最新值，不能进批缓冲）"""
    p = subprocess.run([MYSQL, "-uroot", DB, "-e", query],
                       capture_output=True, timeout=60)
    if p.returncode != 0:
        err = p.stderr.decode("gbk", errors="replace")
        raise RuntimeError("SQL error: %s\n%s" % (query[:120], err[:200]))
    return p.stdout.decode("gbk", errors="replace")


def seq_fetch(query):
    """即时查询（返回行）"""
    p = subprocess.run([MYSQL, "-uroot", DB, "-N", "-e", query],
                       capture_output=True, timeout=60)
    if p.returncode != 0:
        err = p.stderr.decode("gbk", errors="replace")
        raise RuntimeError("SQL error: %s\n%s" % (query[:120], err[:200]))
    out = p.stdout.decode("gbk", errors="replace")
    return [ln.split("\t") for ln in out.strip().splitlines() if ln]


def load_seq(prefix, date):
    """按 sequences 表取号：prefix:YYYYMMDD / year / month → seq+1
    序列可能比实际单据领先（失败运行残留）——取号后查单号是否已存在，撞号则继续递增"""
    ds = date.strftime("%Y%m%d")
    yy, mm = date.strftime("%Y"), date.strftime("%m")
    key = "%s:%s" % (prefix, ds)
    seq_sql("INSERT INTO sequences (prefix, year, month, seq) VALUES ('%s','%s','%s',0) "
            "ON DUPLICATE KEY UPDATE seq=seq" % (key, yy, mm))
    for _ in range(20):  # 最多追 20 个号
        seq_sql("UPDATE sequences SET seq=seq+1 WHERE prefix='%s'" % key)
        row = seq_fetch("SELECT seq FROM sequences WHERE prefix='%s'" % key)
        no = "%s-%s-%04d" % (prefix, ds, int(row[0][0]))
        # 按单据表查重（不同前缀对应不同表）
        tbl = {"XSDD": "customer_orders", "SH-XSDD": "delivery_notes",
               "CGDD": "purchase_orders", "PO": "po_orders", "TH": "purchase_returns",
               "XSTH": "sales_returns", "RCV": "receipt_vouchers", "PAY": "payment_vouchers",
               "INV": "invoices", "IE": "income_expenses"}.get(prefix)
        if tbl:
            col = {"XSDD": "co_no", "SH-XSDD": "dn_no", "CGDD": "po_no", "PO": "po_order_no",
                   "TH": "pr_no", "XSTH": "sr_no", "RCV": "rv_no", "PAY": "pv_no",
                   "INV": "invoice_no", "IE": "ie_no"}[prefix]
            dup = seq_fetch("SELECT COUNT(*) FROM %s WHERE %s='%s'" % (tbl, col, no))
            if int(dup[0][0]) == 0:
                return no
        else:
            return no
    raise RuntimeError("next_no: 连续撞号 20 次（prefix=%s date=%s）" % (prefix, ds))


_SEQ = {}  # (prefix, YYYYMMDD) -> 下一个可用序号（内存计数）


def load_seq(prefix, date):
    """取号（内存模式）：从表内 MAX 单号后缀+1 起步，避免 sequences 残留占号。
    结束统一写回 sequences 表（见 save_seq）。"""
    ds = date.strftime("%Y%m%d")
    key = (prefix, ds)
    if key not in _SEQ:
        tbl = {"XSDD": "customer_orders", "SH-XSDD": "delivery_notes",
               "CGDD": "purchase_orders", "PO": "po_orders", "TH": "purchase_returns",
               "XSTH": "sales_returns", "RCV": "receipt_vouchers", "PAY": "payment_vouchers",
               "INV": "invoices", "IE": "income_expenses"}.get(prefix)
        if tbl:
            col = {"XSDD": "co_no", "SH-XSDD": "dn_no", "CGDD": "po_no", "PO": "po_order_no",
                   "TH": "pr_no", "XSTH": "sr_no", "RCV": "rv_no", "PAY": "pv_no",
                   "INV": "invoice_no", "IE": "ie_no"}[prefix]
            rows = sql("SELECT MAX(CAST(SUBSTRING_INDEX(%s,'-',-1) AS UNSIGNED)) FROM %s "
                       "WHERE %s LIKE '%s-%%'" % (col, tbl, col, prefix), fetch=True)
            v = rows[0][0] if rows else None
            _SEQ[key] = (int(v) if v and str(v).strip().upper() != "NULL" else 0) + 1
        else:
            _SEQ[key] = 1
    no = _SEQ[key]
    _SEQ[key] += 1
    return "%s-%s-%04d" % (prefix, ds, no)


def save_seq():
    """把内存序号写回 sequences 表（每个前缀一条 UPDATE/INSERT，共 ~10 条）"""
    for (prefix, ds), nxt in _SEQ.items():
        yy, mm = ds[:4], ds[4:6]
        key = "%s:%s" % (prefix, ds)
        sql("INSERT INTO sequences (prefix, year, month, seq) VALUES ('%s','%s','%s',%d) "
            "ON DUPLICATE KEY UPDATE seq=%d" % (key, yy, mm, nxt - 1, nxt - 1))
    flush_sql()


def rand_date():
    return START + timedelta(days=random.randint(0, N_DAYS))


def pick(rows):
    return random.choice(rows)


def fmt(v):
    return ("%.2f" % v) if isinstance(v, float) else str(v)


def main():
    print("== jxc ERP 真实感数据生成 ==")

    # ---------- 加载主数据 ----------
    customers = sql("SELECT id, name FROM customers WHERE deleted=0", fetch=True)
    suppliers = sql("SELECT id, name FROM suppliers WHERE deleted=0", fetch=True)
    materials = sql("SELECT id, name, spec, unit, sale_price FROM materials "
                    "WHERE deleted=0 AND name NOT LIKE '%测试%' AND name NOT LIKE '%压测%'", fetch=True)

    def price_of(row, idx):
        v = row[idx]
        try:
            return float(v) if v and str(v).strip().upper() != "NULL" else 0.0
        except (ValueError, TypeError):
            return 0.0

    # 历史物料 sale_price 全为 NULL：按名称特征给合理估值（仅用于生成单据，不改物料表）
    def est_price(name, spec):
        n = (name or "") + " " + (spec or "")
        if any(k in n for k in ("线", "缆")):
            return random.uniform(0.5, 15.0)
        if any(k in n for k in ("端子", "夹", "插", "头", "连接")):
            return random.uniform(0.1, 3.0)
        if any(k in n for k in ("成品", "组件", "模组", "装配")):
            return random.uniform(5.0, 80.0)
        return random.uniform(0.2, 8.0)

    materials = sql("SELECT id, name, spec, unit, sale_price, category FROM materials "
                    "WHERE deleted=0 AND name NOT LIKE '%测试%' AND name NOT LIKE '%压测%'", fetch=True)
    # 成品 = category 含「成品」；原料 = 其他
    fg_materials = [m for m in materials if "成品" in str(m[5] or "")]
    rm_materials = [m for m in materials if "成品" not in str(m[5] or "")]
    emp = sql("SELECT id, name FROM work_employees WHERE deleted=0", fetch=True)
    procs = sql("SELECT id, name, unit_price FROM processes WHERE deleted=0", fetch=True)
    # 注意：存量账户 deleted 全为非 0（历史导入特征），不能按 deleted=0 过滤
    accounts = sql("SELECT id, name FROM cash_accounts ORDER BY id", fetch=True)
    wh = sql("SELECT id FROM warehouses WHERE deleted=0 LIMIT 1", fetch=True)
    wh_id = wh[0][0] if wh else 1
    print("主数据: 客户%d 供应商%d 物料%d(成品%d) 员工%d 工序%d 账户%d" % (
        len(customers), len(suppliers), len(materials), len(fg_materials),
        len(emp), len(procs), len(accounts)))

    # 检查是否已生成过（防重复）
    # 防重复检查：XT- 标记在 remark 里（不在单号里！单号是 XSDD-20260701-0001 格式）
    already = sql("SELECT COUNT(*) FROM customer_orders WHERE remark LIKE '%%%s%%'" % TAG, fetch=True)
    if int(already[0][0]) > 0:
        print("检测到已有 %s 标记数据 %s 条，跳过重复生成" % (TAG, already[0][0]))
        return

    made = {"orders": 0, "deliveries": 0, "purchases": 0, "po_orders": 0,
            "returns": 0, "work": 0, "invoices": 0, "settles": 0,
            "vouchers": 0, "ie": 0, "stock": 0}

    # ============ 1. 客户订单（近3个月 400 单，覆盖 pending/partial/done/cancelled） ============
    print("\n[1/7] 生成客户订单...")
    orders = []  # (co_id, co_no, customer_id, order_date, total_amt, items, status)
    for _ in range(400):
        c = pick(customers)
        d = rand_date()
        co_no = load_seq("XSDD", d)
        n_items = random.randint(1, 4)
        items = []
        total = 0.0
        for _ in range(n_items):
            m = pick(fg_materials)
            qty = random.choice([100, 200, 300, 500, 1000, 2000, 5000, 10000])
            price = price_of(m, 4) or round(est_price(m[1], m[2]), 2)
            amt = qty * price
            items.append((m[0], m[1], m[2] or "", m[3] or "", qty, price, amt))
            total += amt
        st = random.choices(["done", "partial", "pending", "cancelled"], [0.45, 0.25, 0.25, 0.05])[0]
        expect = (d + timedelta(days=random.randint(7, 30))).strftime("%Y-%m-%d")
        cname = c[1].replace("'", "''")
        sql("INSERT INTO customer_orders (co_no, customer_id, customer_name, order_date, expected_date, "
            "total_amount, delivered_amount, delivered_quantity, status, remark) VALUES ('%s','%s','%s','%s','%s',%.2f,%.2f,0,'%s','%s')"
            % (co_no, c[0], cname, d.strftime("%Y-%m-%d"), expect, total, 0.0, st, TAG + "订单" + co_no[-6:]))
        flush_sql()  # 主表落库 → 回查 co_id
        co_id = sql("SELECT id FROM customer_orders WHERE co_no='%s'" % co_no, fetch=True)[0][0]
        sort = 0
        for it in items:
            sql("INSERT INTO customer_order_items (co_id, material_id, material_name, spec, unit, "
                "quantity, unit_price, amount, delivered_quantity, sort_order, remark) VALUES "
                "('%s','%s','%s','%s','%s',%s,%.4f,%.2f,0,%d,'%s')"
                % (co_id, it[0], it[1].replace("'", "''"), it[2].replace("'", "''"),
                   it[3].replace("'", "''"), it[4], it[5], it[6], sort, TAG))
            sort += 1
        flush_sql()
        orders.append((co_id, co_no, c[0], d, total, items, st))
        made["orders"] += 1

    # ============ 2. 送货单（done 订单多数已送，partial 部分送） ============
    print("[2/7] 生成送货单（关联订单+库存流水）...")
    # 先读当前库存（简化：按物料累加现有流水）
    stock_now = {}
    for row in sql("SELECT material_id, SUM(CASE WHEN move_type='in' THEN quantity ELSE -quantity END) "
                   "FROM stock_movements GROUP BY material_id", fetch=True):
        stock_now[row[0]] = float(row[1] or 0)
    deliveries = []
    for co_id, co_no, cid, d, total, items, st in orders:
        if st == "cancelled":
            continue
        if st == "pending":
            continue
        # done 全送、partial 送一半
        ratio = 1.0 if st == "done" else 0.5
        dn_date = d + timedelta(days=random.randint(1, 10))
        dn_no = load_seq("SH-XSDD", dn_date)
        dn_items = []
        dn_total = 0.0
        dn_qty = 0.0
        for it in items:
            qty = it[4] * ratio
            # 库存检查（成品库存可能不足，不足就跳过该物料——现实中也这样）
            mid = it[0]
            cur = stock_now.get(mid, 0.0)
            if cur < qty * 0.1:
                continue
            qty = min(qty, cur)
            if qty <= 0:
                continue
            amt = qty * it[5]
            dn_items.append((mid, it[1], it[2], it[3], qty, it[5], amt))
            stock_now[mid] = cur - qty
            dn_total += amt
            dn_qty += qty
        if not dn_items:
            continue
        cname = next(x[1] for x in customers if x[0] == cid)
        sql("INSERT INTO delivery_notes (dn_no, customer_order_id, customer_id, customer_name, "
            "dn_date, warehouse_id, total_quantity, total_amount, status, remark) VALUES "
            "('%s','%s','%s','%s','%s',%s,%.3f,%.2f,'done','%s')"
            % (dn_no, co_id, cid, cname.replace("'", "''"), dn_date.strftime("%Y-%m-%d"),
               wh_id, dn_qty, dn_total, TAG + "送货" + dn_no[-6:]))
        flush_sql()  # 主表落库 → 回查 dn_id
        dn_id = sql("SELECT id FROM delivery_notes WHERE dn_no='%s'" % dn_no, fetch=True)[0][0]
        sort = 0
        for it in dn_items:
            sql("INSERT INTO delivery_items (dn_id, co_item_id, material_id, material_name, "
                "quantity, unit_price, amount, sort_order, remark) VALUES "
                "('%s',NULL,'%s','%s',%s,%.4f,%.2f,%d,'%s')"
                % (dn_id, it[0], it[1].replace("'", "''"), it[4], it[5], it[6], sort, TAG))
            # 库存流水
            before = stock_now.get(it[0], 0.0)
            after = before  # 已在上面扣过；流水记录用扣后值
            sql("INSERT INTO stock_movements (material_id, warehouse_id, move_type, ref_type, "
                "ref_id, quantity, before_stock, after_stock, unit_price, amount, move_date, remark) "
                "VALUES ('%s',%s,'out','delivery',%s,%s,%.3f,%.3f,%.4f,%.2f,'%s','%s')"
                % (it[0], wh_id, dn_id, it[4], before + it[4], before, it[5], it[6],
                   dn_date.strftime("%Y-%m-%d"), TAG + "送货" + dn_no[-6:]))
            sort += 1
        # 更新订单 delivered_amount
        delivered = sum(it[6] for it in dn_items)
        sql("UPDATE customer_orders SET delivered_amount=%.2f WHERE id=%s" % (delivered, co_id))
        deliveries.append((dn_id, dn_no, cid, dn_date, dn_total))
        made["deliveries"] += 1
        made["stock"] += len(dn_items)

    # ============ 3. 采购单 + 采购订单（近3个月，含 po_orders 独立订单） ============
    print("[3/7] 生成采购单 + 独立采购订单...")
    purchase_ids = []
    for _ in range(120):
        s = pick(suppliers)
        d = rand_date()
        po_no = load_seq("CGDD", d)
        n_items = random.randint(1, 3)
        items = []
        total = 0.0
        for _ in range(n_items):
            m = pick(rm_materials)
            qty = random.choice([500, 1000, 2000, 5000, 10000, 20000])
            price = price_of(m, 4) or round(est_price(m[1], m[2]), 2)
            amt = qty * price
            items.append((m[0], m[1], m[2] or "", m[3] or "", qty, price, amt))
            total += amt
        sql("INSERT INTO purchase_orders (po_no, supplier_id, po_date, warehouse_id, "
            "total_quantity, total_amount, status, remark) VALUES "
            "('%s','%s','%s',%s,%s,%.2f,'done','%s')"
            % (po_no, s[0], d.strftime("%Y-%m-%d"), wh_id,
               sum(i[4] for i in items), total, TAG + "采购" + po_no[-6:]))
        flush_sql()  # 主表落库 → 回查 po_id
        po_id = sql("SELECT id FROM purchase_orders WHERE po_no='%s'" % po_no, fetch=True)[0][0]
        sort = 0
        for it in items:
            sql("INSERT INTO purchase_items (po_id, material_id, material_name, spec, unit, "
                "quantity, unit_price, amount, sort_order, remark) VALUES "
                "('%s','%s','%s','%s','%s',%s,%.4f,%.2f,%d,'%s')"
                % (po_id, it[0], it[1].replace("'", "''"), it[2].replace("'", "''"),
                   it[3].replace("'", "''"), it[4], it[5], it[6], sort, TAG))
            # 库存流水（采购入库加库存）
            cur = stock_now.get(it[0], 0.0)
            sql("INSERT INTO stock_movements (material_id, warehouse_id, move_type, ref_type, "
                "ref_id, quantity, before_stock, after_stock, unit_price, amount, move_date, remark) "
                "VALUES ('%s',%s,'in','purchase',%s,%s,%.3f,%.3f,%.4f,%.2f,'%s','%s')"
                % (it[0], wh_id, po_id, it[4], cur, cur + it[4], it[5], it[6],
                   d.strftime("%Y-%m-%d"), TAG + "采购" + po_no[-6:]))
            stock_now[it[0]] = cur + it[4]
            sort += 1
        purchase_ids.append((po_id, po_no, s[0], d, total))
        made["purchases"] += 1
        made["stock"] += len(items)

    # 独立采购订单 po_orders（40 单，状态 pending/partial/done）
    for _ in range(40):
        s = pick(suppliers)
        d = rand_date()
        po_order_no = load_seq("PO", d)
        n_items = random.randint(1, 2)
        items = []
        total = 0.0
        for _ in range(n_items):
            m = pick(rm_materials)
            qty = random.choice([1000, 2000, 5000, 10000])
            price = round(random.uniform(0.3, 2.5), 2)
            amt = qty * price
            items.append((m[0], m[1], m[2] or "", m[3] or "", qty, price, amt))
            total += amt
        st = random.choices(["done", "partial", "pending"], [0.4, 0.3, 0.3])[0]
        sql("INSERT INTO po_orders (po_order_no, supplier_id, supplier_name, order_date, handler, "
            "remark, status, total_quantity, total_amount) VALUES "
            "('%s','%s','%s','%s','%s','%s','%s',%s,%.2f)"
            % (po_order_no, s[0], s[1].replace("'", "''"), d.strftime("%Y-%m-%d"),
               pick(emp)[1].replace("'", "''"), TAG + "采购订单" + po_order_no[-6:], st,
               sum(i[4] for i in items), total))
        flush_sql()  # 主表落库 → 回查 po_order_id
        po_order_id = sql("SELECT id FROM po_orders WHERE po_order_no='%s'" % po_order_no,
                          fetch=True)[0][0]
        sort = 0
        received = 0.0 if st == "pending" else (total if st == "done" else total * 0.5)
        for it in items:
            sql("INSERT INTO po_order_items (po_order_id, material_id, material_name, spec, unit, "
                "quantity, unit_price, amount, received_quantity, sort_order) VALUES "
                "('%s','%s','%s','%s','%s',%s,%.4f,%.2f,%s,%d)"
                % (po_order_id, it[0], it[1].replace("'", "''"), it[2].replace("'", "''"),
                   it[3].replace("'", "''"), it[4], it[5], it[6],
                   it[4] if st == "done" else (it[4] * 0.5 if st == "partial" else 0), sort))
            sort += 1
        made["po_orders"] += 1

    # ============ 4. 采购退货 + 销售退货（少量，真实企业有） ============
    print("[4/7] 生成退货单...")
    for _ in range(15):
        s = pick(suppliers)
        d = rand_date()
        pr_no = load_seq("TH", d)
        sql("INSERT INTO purchase_returns (pr_no, supplier_id, supplier_name, return_date, "
            "total_quantity, total_amount, status, remark) VALUES ('%s','%s','%s','%s',%s,%.2f,'done','%s')"
            % (pr_no, s[0], s[1].replace("'", "''"), d.strftime("%Y-%m-%d"), 0, 0.0,
               TAG + "采购退货" + pr_no[-6:]))
        made["returns"] += 1
    for _ in range(15):
        c = pick(customers)
        d = rand_date()
        sr_no = load_seq("XSTH", d)
        cname = next(x[1] for x in customers if x[0] == c[0])
        sql("INSERT INTO sales_returns (sr_no, customer_id, customer_name, return_date, "
            "total_quantity, total_amount, status, remark) VALUES ('%s','%s','%s','%s',%s,%.2f,'done','%s')"
            % (sr_no, c[0], cname.replace("'", "''"), d.strftime("%Y-%m-%d"), 0, 0.0,
               TAG + "销售退货" + sr_no[-6:]))
        made["returns"] += 1

    # ============ 5. 报工记录（近3个月，员工×工序×分组） ============
    print("[5/7] 生成报工记录...")
    # 员工→分组映射（work_employees 有 group_id/group_name）
    emp_groups = {}
    for row in sql("SELECT id, group_id, group_name FROM work_employees WHERE deleted=0", fetch=True):
        emp_groups[row[0]] = (row[1], row[2] or "")
    # 工序→可用分组（work_group_processes）
    proc_groups = {}
    for row in sql("SELECT process_id, group_id FROM work_group_processes WHERE deleted=0", fetch=True):
        proc_groups.setdefault(row[0], []).append(row[1])
    group_names = {}
    for row in sql("SELECT id, name FROM work_groups WHERE deleted=0", fetch=True):
        group_names[row[0]] = row[1]
    for _ in range(300):
        e = pick(emp)
        p = pick(procs)
        gid, gname = emp_groups.get(e[0], (None, ""))
        # group_id=0/NULL 是无效外键（work_groups 无 id=0），换真实分组
        if not gid or str(gid).strip() in ("0", "NULL"):
            gid, gname = None, ""
        if gid is None and group_names:
            gid, gname = pick(list(group_names.items()))
        d = rand_date()
        qty = random.choice([200, 300, 500, 800, 1000, 1500, 2000])
        start = datetime(d.year, d.month, d.day, random.randint(8, 10), random.choice([0, 15, 30, 45]))
        dur = random.randint(60, 480)
        end = start + timedelta(minutes=dur)
        gid_sql = str(gid) if gid is not None else "NULL"
        sql("INSERT INTO work_reports (employee_id, employee_name, group_id, group_name, "
            "process_id, process_name, quantity, start_time, end_time, duration, report_date, "
            "status, remark) VALUES ('%s','%s',%s,'%s','%s','%s',%s,'%s','%s',%s,'%s','completed','%s')"
            % (e[0], e[1].replace("'", "''"), gid_sql, str(gname).replace("'", "''"),
               p[0], p[1].replace("'", "''"), qty,
               start.strftime("%Y-%m-%d %H:%M:%S"), end.strftime("%Y-%m-%d %H:%M:%S"), dur,
               d.strftime("%Y-%m-%d"), TAG + "报工" + d.strftime("%m%d")))
        made["work"] += 1

    # ============ 6. 收付款单 + 核销 + 发票（财务模块补数据） ============
    print("[6/7] 生成财务数据（收付款/核销/发票）...")
    # 收款单：从送货单生成（约 60% 送货单收款）
    for dn_id, dn_no, cid, d, total in deliveries:
        if random.random() > 0.6:
            continue
        rv_date = d + timedelta(days=random.randint(1, 40))
        rv_no = load_seq("RCV", rv_date)
        cname = next(x[1] for x in customers if x[0] == cid)
        sql("INSERT INTO receipt_vouchers (rv_no, customer_id, customer_name, amount, "
            "receipt_date, receipt_method, remark) VALUES ('%s','%s','%s',%.2f,'%s','%s','%s')"
            % (rv_no, cid, cname.replace("'", "''"), total, rv_date.strftime("%Y-%m-%d"),
               random.choice(["银行转账", "微信", "现金"]), TAG + "收款" + rv_no[-6:]))
        flush_sql()  # 主表落库 → 回查 rv_id
        rv_id = sql("SELECT id FROM receipt_vouchers WHERE rv_no='%s'" % rv_no, fetch=True)[0][0]
        # 核销
        sql("INSERT INTO settlements (settle_type, voucher_id, voucher_no, ref_type, ref_id, "
            "amount, remark) VALUES ('receipt','%s','%s','delivery',%s,%.2f,'%s')"
            % (rv_id, rv_no, dn_id, total, TAG + "核销"))
        made["vouchers"] += 1
        made["settles"] += 1
    # 付款单：从采购单生成（约 50%）
    for po_id, po_no, sid, d, total in purchase_ids:
        if random.random() > 0.5:
            continue
        pay_date = d + timedelta(days=random.randint(1, 40))
        pay_no = load_seq("PAY", pay_date)
        sname = next(x[1] for x in suppliers if x[0] == sid)
        sql("INSERT INTO payment_vouchers (pv_no, supplier_id, supplier_name, amount, "
            "pay_date, pay_method, remark) VALUES ('%s','%s','%s',%.2f,'%s','%s','%s')"
            % (pay_no, sid, sname.replace("'", "''"), total, pay_date.strftime("%Y-%m-%d"),
               random.choice(["银行转账", "现金"]), TAG + "付款" + pay_no[-6:]))
        flush_sql()  # 主表落库 → 回查 pv_id
        pv_id = sql("SELECT id FROM payment_vouchers WHERE pv_no='%s'" % pay_no, fetch=True)[0][0]
        sql("INSERT INTO settlements (settle_type, voucher_id, voucher_no, ref_type, ref_id, "
            "amount, remark) VALUES ('payment','%s','%s','purchase',%s,%.2f,'%s')"
            % (pv_id, pay_no, po_id, total, TAG + "核销"))
        made["vouchers"] += 1
        made["settles"] += 1
    # 发票登记（销项为主，从送货单生成 40%）
    for dn_id, dn_no, cid, d, total in deliveries:
        if random.random() > 0.4:
            continue
        inv_date = d + timedelta(days=random.randint(1, 20))
        inv_no = load_seq("INV", inv_date)
        cname = next(x[1] for x in customers if x[0] == cid)
        sql("INSERT INTO invoices (invoice_no, invoice_type, ref_type, ref_id, customer_id, "
            "customer_name, amount, invoice_date, remark) VALUES ('%s','sales','delivery',%s,'%s','%s',"
            "%.2f,'%s','%s')"
            % (inv_no, dn_id, cid, cname.replace("'", "''"), total,
               inv_date.strftime("%Y-%m-%d"), TAG + "发票" + inv_no[-6:]))
        made["invoices"] += 1

    # ============ 7. 收支转账（资金账户间流转） ============
    print("[7/7] 生成收支转账...")
    for _ in range(40):
        d = rand_date()
        ie_no = load_seq("IE", d)
        a1 = pick(accounts)
        a2 = pick([a for a in accounts if a[0] != a1[0]])
        amt = random.choice([500, 1000, 2000, 5000, 10000, 20000])
        cat = random.choice(["转账", "日常开支", "工资", "租金"])
        sql("INSERT INTO income_expenses (ie_no, ie_type, category, account_id, amount, "
            "ie_date, remark) VALUES ('%s','%s','%s','%s',%.2f,'%s','%s')"
            % (ie_no, random.choice(["income", "expense"]), cat, a1[0], amt,
               d.strftime("%Y-%m-%d"), TAG + cat + ie_no[-6:]))
        made["ie"] += 1

    # 收尾：写回序列 + 灌入剩余缓冲
    save_seq()
    flush_sql()
    print("\n===== 生成完成 =====")
    for k, v in made.items():
        print("  %-10s %d" % (k, v))
    print("\n注意：数据带 %s 前缀标记（remark 里），后续要清理可统一按此前缀删除。" % TAG)


if __name__ == "__main__":
    main()
