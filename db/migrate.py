#!/usr/bin/env python3
"""
jxc ERP SQLite → MySQL 数据迁移脚本
用法: python migrate.py [--host 127.0.0.1] [--port 3306] [--user root] [--password ""] [--db yawei_erp]
"""
import sqlite3
import sys
import time

SQLITE_DB = r"I:\yawei-erp\yawei_erp.db"

# SQLite 表名 → MySQL 表名（如有不同）
TABLE_MAP = {"sequence": "sequences"}

# 迁移顺序（父表在前）
TABLES = [
    "users", "suppliers", "customers", "materials", "warehouses",
    "customer_orders", "customer_order_items",
    "purchase_orders", "purchase_items",
    "delivery_notes", "delivery_items",
    "stock_movements", "sequence",
    "payment_vouchers", "receipt_vouchers",
    "purchase_returns", "purchase_return_items",
    "invoices", "settlements",
    "work_groups", "work_employees", "work_group_processes", "processes",
    "process_materials", "work_reports", "work_report_images", "bom_items",
]


def _fix_value(v):
    """SQLite 宽松类型 → MySQL 严格类型：ISO 8601 时间转 MySQL DATETIME 格式"""
    if isinstance(v, str):
        s = v
        if 'T' in s or s.endswith('Z'):
            s = s.replace('T', ' ').replace('Z', '')
            if len(s) > 19:
                s = s[:19]
            return s
    return v


def main():
    try:
        import pymysql
    except ImportError:
        print("需要 pymysql: pip install pymysql")
        sys.exit(1)

    src = sqlite3.connect(SQLITE_DB)
    src.row_factory = sqlite3.Row

    dst = pymysql.connect(
        host="127.0.0.1", port=3306, user="root", password="",
        database="yawei_erp", charset="utf8mb4", autocommit=False,
    )
    cur = dst.cursor()

    # 清空目标表（避免重复迁移）
    for t in TABLES:
        mt = TABLE_MAP.get(t, t)
        try:
            cur.execute(f"DELETE FROM {mt}")
        except Exception as e:
            print(f"清空 {mt} 失败(跳过): {e}")

    start = time.time()
    total_rows = 0
    for t in TABLES:
        mt = TABLE_MAP.get(t, t)
        rows = src.execute(f"SELECT * FROM {t}").fetchall()
        if not rows:
            print(f"{t}: 0 行")
            continue
        src_cols = list(rows[0].keys())
        # MySQL 目标表列（交集，避免 SQLite 多出的列导致报错）
        cur.execute(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s", ("yawei_erp", mt))
        dst_cols = {r[0] for r in cur.fetchall()}
        cols = [c for c in src_cols if c in dst_cols]
        if not cols:
            print(f"{t}: 无交集列，跳过")
            continue
        placeholders = ", ".join(["%s"] * len(cols))
        sql = f"INSERT INTO {mt} ({', '.join(cols)}) VALUES ({placeholders})"
        # 按交集列取值
        idx = [src_cols.index(c) for c in cols]
        batch = []
        for r in rows:
            batch.append(tuple(_fix_value(r[i]) for i in idx))
            if len(batch) >= 500:
                cur.executemany(sql, batch)
                dst.commit()
                batch = []
        if batch:
            cur.executemany(sql, batch)
            dst.commit()
        total_rows += len(rows)
        print(f"{t}: {len(rows)} 行")

    # 主键自增对齐
    for t in TABLES:
        mt = TABLE_MAP.get(t, t)
        try:
            row = cur.execute(f"SELECT COALESCE(MAX(id),0) FROM {mt}").fetchone()
            if row and row[0]:
                cur.execute(f"ALTER TABLE {mt} AUTO_INCREMENT = {row[0] + 1}")
        except Exception:
            pass
    dst.commit()

    print(f"\n完成: 共迁移 {total_rows} 行, 耗时 {time.time()-start:.1f}s")
    src.close()
    dst.close()


if __name__ == "__main__":
    main()
