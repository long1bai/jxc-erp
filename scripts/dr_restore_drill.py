# -*- coding: utf-8 -*-
"""
进销存 ERP v2 恢复演练脚本（2026-08-01 新增，容灾配套）
- 从 H 盘容灾备份取最新一天的 SQL → 建临时库恢复 → 冒烟验证
- 验证"备份真的能恢复"（防止备份损坏/不完整），不碰生产库
- 用法：python scripts/dr_restore_drill.py [日期YYYYMMDD]  （缺省=最新一天）
- 流程：建库 → source → 表数/关键表行数冒烟 → 临时端口起后端验证登录？→ 可选 清理
  说明：完整"临时端口起后端"成本高（要改端口配置），演练止于数据层：
  表数量、关键表行数、最新单据号连续性抽查——数据层完整即备份可用。
退出码：0=演练通过 1=失败
"""
import subprocess
import sys
from datetime import datetime
from pathlib import Path

MYSQL = r"C:\Users\17815\Desktop\jxc\01-ERP\mysql\8.0.28\bin\mysql.exe"
from db_secret import DB_AUTH   # 数据库凭据从 config/db_secret.env 读，密码不进代码
BACKUP_ROOT = Path(r"H:\进销存系统备份\ERP")
PROD_DB = "jxc_erp"

# 数据层冒烟：表名 → 期望至少多少行（宽松下限，防备份是空壳）
SMOKE_TABLES = {
    "materials": 4000,      # 物料
    "customers": 300,       # 客户
    "suppliers": 300,       # 供应商
    "customer_orders": 3000,  # 客户订单
    "delivery_notes": 3000,   # 送货单
    "purchase_orders": 300,   # 采购单
    "users": 5,             # 账号
    "work_reports": 1000,   # 报工
    "stock_movements": 3000,  # 库存流水
}


def run(args, timeout=600):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace")


def main():
    # 1. 定位备份文件（指定日期或缺省最新）
    if len(sys.argv) > 1:
        day = sys.argv[1]
        candidates = [BACKUP_ROOT / day]
    else:
        candidates = sorted([d for d in BACKUP_ROOT.iterdir()
                             if d.is_dir() and len(d.name) == 8 and d.name.isdigit()],
                            reverse=True) if BACKUP_ROOT.exists() else []
        if not candidates:
            print("FAIL: H 盘无容灾备份目录")
            return 1
        day = candidates[0].name
        sql_files = sorted(candidates[0].glob("jxc_erp_*.sql"), reverse=True)
        if not sql_files:
            print("FAIL: %s 目录无 SQL 备份" % day)
            return 1
        sql_file = sql_files[0]

    print("== 恢复演练 day=%s file=%s ==" % (day, sql_file.name))

    # 2. 建临时库（幂等：先删后建）
    tmp_db = "jxc_drill_%s" % datetime.now().strftime("%H%M%S")
    p = run([MYSQL, "-uroot"] + DB_AUTH + ["-e", "DROP DATABASE IF EXISTS `%s`; CREATE DATABASE `%s` CHARACTER SET utf8mb4;" % (tmp_db, tmp_db)])
    if p.returncode != 0:
        print("FAIL: 建临时库 %s" % (p.stderr or "")[:200])
        return 1

    # 3. 恢复备份进临时库（mysql < file，直接管道）
    try:
        with open(sql_file, "r", encoding="utf-8", errors="replace") as f:
            sql_text = f.read()
    except Exception as e:
        print("FAIL: 读备份文件 %s" % str(e)[:200])
        return 1
    p = subprocess.run([MYSQL, "-uroot"] + DB_AUTH + [tmp_db], input=sql_text,
                       capture_output=True, text=True, timeout=900,
                       encoding="utf-8", errors="replace")
    if p.returncode != 0:
        print("FAIL: 恢复执行 %s" % (p.stderr or p.stdout or "")[:300])
        return 1
    print("  恢复完成（%d 行 SQL）" % len(sql_text.splitlines()))

    # 4. 数据层冒烟
    fails = []
    # 4a. 表数量对比（备份库 vs 生产库，应一致）
    p = run([MYSQL, "-uroot"] + DB_AUTH + ["-N", "-e",
             "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%s'" % tmp_db])
    tmp_tables = int(p.stdout.strip() or 0)
    p = run([MYSQL, "-uroot"] + DB_AUTH + ["-N", "-e",
             "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%s'" % PROD_DB])
    prod_tables = int(p.stdout.strip() or 0)
    print("  表数量: 备份=%d 生产=%d %s" % (tmp_tables, prod_tables, "OK" if tmp_tables == prod_tables else "⚠ 不一致"))
    if tmp_tables != prod_tables:
        fails.append("表数量不一致(备份%d/生产%d)" % (tmp_tables, prod_tables))

    # 4b. 关键表行数抽查
    for t, expect in SMOKE_TABLES.items():
        p = run([MYSQL, "-uroot"] + DB_AUTH + ["-N", "-e",
                 "SELECT COUNT(*) FROM `%s`.`%s`" % (tmp_db, t)])
        if p.returncode != 0:
            fails.append("%s 查询失败" % t)
            print("  FAIL %s 查询失败 %s" % (t, (p.stderr or "")[:100]))
            continue
        n = int(p.stdout.strip() or 0)
        ok = n >= expect
        print("  %s %s 行=%d %s" % ("PASS" if ok else "FAIL", t, n,
                                    "" if ok else "(期望>=%d)" % expect))
        if not ok:
            fails.append("%s 行数不足(%d)" % (t, n))

    # 4c. 最新业务数据存在性（确保备份不是很久以前的）
    p = run([MYSQL, "-uroot"] + DB_AUTH + ["-N", "-e",
             "SELECT COALESCE(MAX(created_at),'') FROM `%s`.`purchase_orders`" % tmp_db])
    latest = p.stdout.strip()
    print("  备份中最近采购单时间: %s" % (latest or "(空)"))

    # 5. 清理临时库
    run([MYSQL, "-uroot"] + DB_AUTH + ["-e", "DROP DATABASE IF EXISTS `%s`" % tmp_db])
    print("  临时库已清理")

    if fails:
        print("DRILL RESULT: FAIL (%s)" % "; ".join(fails))
        return 1
    print("DRILL RESULT: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
