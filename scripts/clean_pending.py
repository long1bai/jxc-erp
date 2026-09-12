# -*- coding: utf-8 -*-
"""物理清理残留 pending 单（自动序列号，无流水）"""
import subprocess

from db_secret import DB_AUTH   # 数据库凭据从 config/db_secret.env 读，密码不进代码

r = subprocess.run([
    'C:/Users/17815/Desktop/jxc/01-ERP/mysql/8.0.28/bin/mysql.exe',
    '-uroot'] + DB_AUTH + ['jxc_erp', '-e',
    "DELETE FROM purchase_items WHERE po_id IN (SELECT id FROM purchase_orders WHERE po_no='CGDD-20260805-0003'); "
    "DELETE FROM purchase_orders WHERE po_no='CGDD-20260805-0003'; "
    "SELECT COUNT(*) AS left_cnt FROM purchase_orders WHERE po_no='CGDD-20260805-0003';"
], capture_output=True, text=True)
print(r.stdout.strip())
if r.stderr:
    print('ERR', r.stderr.strip()[:300])
