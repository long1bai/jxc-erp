# -*- coding: utf-8 -*-
"""
jxcERP v2 压力测试脚本
重点场景：
  1. 并发单号生成（30 并发创建订单）—— 查重复单号/失败率
  2. 并发库存扣减（20 并发送货扣同一物料）—— 查丢更新/负库存
  3. 并发报工打卡（10 并发同员工 start）—— 查重复进行中
  4. 单据序列首插竞争（删序列行后 10 并发盘点）—— 查 duplicate key
  5. 接口负载延迟（20 线程×10 次打 6 个核心接口）—— p50/p95/max + 错误率
数据统一「【压测】」前缀，结束 SQL 清理 + 序列重同步。
"""
import json
import statistics
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.parse
import urllib.error
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

BASE = "http://127.0.0.1:8080"
TOKEN = None
PREFIX = "【压测】"
TAG = datetime.now().strftime("%Y%m%d-%H%M%S")
SUF = TAG[-4:]
MYSQL = r"C:\mysql\8.0.28\bin\mysql.exe"
LOCK = threading.Lock()
RESULTS = []


def api(method, path, body=None, params=None, timeout=60):
    url = BASE + path
    if params:
        qs = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None and v != ""})
        if qs:
            url += "?" + qs
    req = urllib.request.Request(url, method=method)
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    if data:
        req.add_header("Content-Type", "application/json")
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return time.time() - t0, resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return time.time() - t0, e.code, json.loads(raw)
        except Exception:
            return time.time() - t0, e.code, {"error": raw[:200]}
    except Exception as e:
        return time.time() - t0, 0, {"error": str(e)[:200]}


def rec(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(("  %s %s%s" % ("PASS" if ok else "FAIL", name, (" | " + detail[:160] if detail else ""))))


def get_stock(material_id):
    _, _, r = api("GET", "/api/stock/inventory", params={"keyword": "STRESS"})
    if r.get("success"):
        for row in (r.get("data") or {}).get("items", []):
            if str(row.get("materialId") or row.get("id")) == str(material_id):
                return row.get("quantity") or row.get("stock")
    return None


def login():
    global TOKEN
    _, _, r = api("POST", "/api/auth/login", {"username": "admin", "password": "admin123"})
    TOKEN = r["data"]["token"]
    print("登录 OK, token=%s..." % TOKEN[:12])


# ---------- 1. 并发单号生成 ----------

def stress_orders(cust_id, fg_id):
    print("\n== 1. 并发单号生成（30 并发创建订单） ==")
    item = {"materialId": fg_id, "materialName": PREFIX + "成品-" + SUF, "spec": "1.0mm", "unit": "个", "quantity": 1, "unitPrice": 10.0}

    def worker(i):
        _, _, r = api("POST", "/api/orders", {"customerId": cust_id, "orderDate": datetime.now().strftime("%Y-%m-%d"),
                                              "remark": PREFIX + "订单" + TAG, "items": [item]})
        return r.get("success"), (r.get("data") or {}).get("coNo") if r.get("success") else r

    barrier = threading.Barrier(30)

    def wrapped(i):
        barrier.wait()
        return worker(i)

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=30) as ex:
        out = list(ex.map(wrapped, range(30)))
    dt = time.time() - t0
    ok_n = sum(1 for s, _ in out if s)
    nos = [n for s, n in out if s]
    dup = len(nos) - len(set(nos))
    errs = [str(r)[:80] for s, r in out if not s]
    rec("30并发创建订单", ok_n == 30 and dup == 0,
        "成功%d/30 重复单号%d 耗时%.1fs %s" % (ok_n, dup, dt, errs[:2]))
    return dup


# ---------- 2. 并发库存扣减 ----------

def stress_delivery(base):
    print("\n== 2. 并发库存扣减（20 并发送货各扣 5） ==")
    rm_id = base["rm"]
    base_qty = 200
    # 预置库存：采购 200
    _, _, r = api("POST", "/api/purchases", {"supplierId": base["sup"], "poDate": datetime.now().strftime("%Y-%m-%d"),
                                             "remark": PREFIX + "备货" + TAG, "warehouseId": 1, "items": [
            {"materialId": rm_id, "materialName": PREFIX + "原料-" + SUF, "spec": "1.0mm", "unit": "个", "quantity": base_qty, "unitPrice": 1.0}]})
    if not r.get("success"):
        rec("预置库存采购", False, str(r)[:120])
        return
    stock0 = get_stock(rm_id)
    item = {"materialId": rm_id, "materialName": PREFIX + "原料-" + SUF, "spec": "1.0mm", "unit": "个", "quantity": 5, "unitPrice": 2.0}
    # 每个送货单不关联订单（关联订单会被剩余量挡住并发），直接独立送货
    def worker(i):
        _, _, r = api("POST", "/api/deliveries", {"customerId": base["cust"], "deliveryDate": datetime.now().strftime("%Y-%m-%d"),
                                                  "remark": PREFIX + "送货" + TAG, "items": [item]})
        return r.get("success"), r

    barrier = threading.Barrier(20)

    def wrapped(i):
        barrier.wait()
        return worker(i)

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=20) as ex:
        out = list(ex.map(wrapped, range(20)))
    dt = time.time() - t0
    ok_n = sum(1 for s, _ in out if s)
    stock1 = get_stock(rm_id)
    expected = float(stock0) - 100.0
    rec("20并发送货扣减", ok_n == 20 and stock1 is not None and float(stock1) == expected,
        "成功%d/20 库存 %s→%s (期望%s) 耗时%.1fs" % (ok_n, stock0, stock1, expected, dt))
    return stock1


# ---------- 3. 并发报工打卡 ----------

def stress_clock(eid, pid):
    print("\n== 3. 并发报工打卡（10 并发同员工 start） ==")
    def worker(i):
        boundary = "----StressBoundary%d" % i
        body = ("--%s\r\nContent-Disposition: form-data; name=\"employeeId\"\r\n\r\n%s\r\n"
                "--%s\r\nContent-Disposition: form-data; name=\"processId\"\r\n\r\n%s\r\n"
                "--%s\r\nContent-Disposition: form-data; name=\"quantity\"\r\n\r\n0\r\n"
                "--%s\r\nContent-Disposition: form-data; name=\"reportDate\"\r\n\r\n%s\r\n"
                "--%s\r\nContent-Disposition: form-data; name=\"remark\"\r\n\r\n%s\r\n"
                "--%s--\r\n" % (boundary, eid, boundary, pid, boundary, boundary,
                                datetime.now().strftime("%Y-%m-%d"), boundary, PREFIX + "打卡" + TAG, boundary)).encode("utf-8")
        req = urllib.request.Request(BASE + "/api/work/reports/start", method="POST", data=body)
        req.add_header("Authorization", "Bearer " + TOKEN)
        req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8", "replace"))
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", "replace")
            try:
                return json.loads(raw)
            except Exception:
                return {"success": False, "error": raw[:100]}
        except Exception as e:
            return {"success": False, "error": str(e)[:100]}

    barrier = threading.Barrier(10)

    def wrapped(i):
        barrier.wait()
        return worker(i)

    with ThreadPoolExecutor(max_workers=10) as ex:
        out = list(ex.map(wrapped, range(10)))
    ok_n = sum(1 for r in out if r.get("success"))
    ids = [r["data"]["id"] for r in out if r.get("success")]
    # 清理：结束/取消所有成功创建的
    for wid in ids:
        _, _, _ = api("POST", "/api/work/reports/%s/cancel" % wid)
    rec("10并发同员工打卡", ok_n == 1, "成功%d/10（应仅1条进行中）" % ok_n)


# ---------- 4. 序列首插竞争 ----------

def stress_seq_first_insert(sup_id, fg_id):
    print("\n== 4. 单据序列并发（10 并发盘点，序列行已存在） ==")
    ds = datetime.now().strftime("%Y%m%d")
    # 确保序列行存在（真实业务场景：当天已开过单）
    subprocess.run([MYSQL, "-uroot", "yawei_erp", "-e",
                    "INSERT IGNORE INTO sequences (prefix, year, month, seq) VALUES ('PD:%s', '%s', '%s', 0)"
                    % (ds, ds[:4], ds[4:6])], capture_output=True, timeout=30)

    def worker(i):
        _, _, r = api("POST", "/api/stock-takes", {"warehouseId": 1, "takeDate": datetime.now().strftime("%Y-%m-%d"),
                                                   "remark": PREFIX + "盘点" + TAG,
                                                   "items": [{"materialId": fg_id, "actualQty": 1}]})
        return r.get("success"), r

    barrier = threading.Barrier(10)

    def wrapped(i):
        barrier.wait()
        return worker(i)

    with ThreadPoolExecutor(max_workers=10) as ex:
        out = list(ex.map(wrapped, range(10)))
    ok_n = sum(1 for s, _ in out if s)
    errs = [str(r)[:100] for s, r in out if not s]
    dup_err = any("Duplicate" in e or "duplicate" in e or "Deadlock" in e or "deadlock" in e for e in errs)
    rec("10并发首插盘点单号", ok_n == 10 and not dup_err,
        "成功%d/10 %s" % (ok_n, errs[:2]))
    # 清理本次创建的盘点单（压测数据）
    subprocess.run([MYSQL, "-uroot", "yawei_erp", "-e",
                    "DELETE FROM stock_take_items WHERE st_id IN (SELECT id FROM stock_takes WHERE remark LIKE '%【压测】%'); DELETE FROM stock_takes WHERE remark LIKE '%【压测】%';"],
                   capture_output=True, timeout=60)


# ---------- 5. 接口负载延迟 ----------

def stress_load():
    print("\n== 5. 接口负载延迟（20 线程×10 次 × 6 接口） ==")
    endpoints = [("/api/dashboard/data", None),
                 ("/api/materials", {"page": 1, "size": 20}),
                 ("/api/orders", {"page": 1, "size": 20}),
                 ("/api/purchase-stats/monthly", {"keyword": ""}),
                 ("/api/finance/receivable-summary", {}),
                 ("/api/reports/reconciliation", {"start": "2026-07-01", "end": "2026-08-31"})]
    lat = {ep: [] for ep, _ in endpoints}

    def worker(ep, params):
        for _ in range(10):
            dt, code, r = api("GET", ep, params=params)
            lat[ep].append((dt, code))

    barrier = threading.Barrier(20)

    def wrapped(i):
        barrier.wait()
        for ep, params in endpoints:
            worker(ep, params)

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=20) as ex:
        list(ex.map(wrapped, range(20)))
    total = time.time() - t0
    for ep, params in endpoints:
        rows = lat[ep]
        dts = [d for d, c in rows]
        # 错误判定：http code != 200
        errs = sum(1 for d, c in rows if c != 200)
        dts.sort()
        p50 = dts[len(dts) // 2]
        p95 = dts[int(len(dts) * 0.95)]
        rec("负载 " + ep.split("/api/")[1], errs == 0,
            "p50=%.0fms p95=%.0fms max=%.0fms 错误%d" % (p50 * 1000, p95 * 1000, dts[-1] * 1000, errs))
    print("  总耗时 %.1fs（20 并发 × 60 请求）" % total)


# ---------- 数据准备 + 清理 ----------

def prepare():
    print("== 准备压测基础数据 ==")
    cname, sname = PREFIX + "客户-" + SUF, PREFIX + "供应商-" + SUF
    _, _, r = api("POST", "/api/customers", {"name": cname, "contact": "压测", "phone": "13000000000"})
    cust_id = (r.get("data") or {}).get("id") if r.get("success") else None
    _, _, r = api("POST", "/api/suppliers", {"name": sname, "contact": "压测", "phone": "13100000000"})
    sup_id = (r.get("data") or {}).get("id") if r.get("success") else None
    _, _, r = api("POST", "/api/materials", {"code": "STRESS-RM-" + SUF, "name": PREFIX + "原料-" + SUF, "spec": "1.0mm", "unit": "个",
                                             "category": "压测", "purchasePrice": 1.0, "salePrice": 2.0})
    rm_id = (r.get("data") or {}).get("id") if r.get("success") else None
    _, _, r = api("POST", "/api/materials", {"code": "STRESS-FG-" + SUF, "name": PREFIX + "成品-" + SUF, "spec": "1.0mm", "unit": "个",
                                             "category": "压测", "purchasePrice": 5.0, "salePrice": 10.0})
    fg_id = (r.get("data") or {}).get("id") if r.get("success") else None
    _, _, r = api("POST", "/api/work/groups", {"name": PREFIX + "班组-" + SUF})
    gid = (r.get("data") or {}).get("id") if r.get("success") else None
    _, _, r = api("POST", "/api/work/employees", {"username": "stress_emp_" + SUF, "password": "123456",
                                                  "name": PREFIX + "员工-" + SUF, "groupId": gid, "role": "worker"})
    eid = (r.get("data") or {}).get("id") if r.get("success") else None
    _, _, r = api("POST", "/api/work/processes", {"name": PREFIX + "工序-" + SUF, "groupId": gid, "sortOrder": 1, "unitPrice": 0.5})
    pid = (r.get("data") or {}).get("id") if r.get("success") else None
    return {"cust": cust_id, "sup": sup_id, "rm": rm_id, "fg": fg_id, "eid": eid, "pid": pid}


def cleanup(base):
    print("\n== 清理压测数据 ==")
    ds = datetime.now().strftime("%Y%m%d")
    sql = """
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM transfers WHERE remark LIKE '%【压测】%';
DELETE FROM income_expenses WHERE remark LIKE '%【压测】%';
DELETE FROM cash_accounts WHERE name LIKE '%【压测】%';
DELETE FROM stock_movements WHERE remark LIKE '%【压测】%' OR remark LIKE '%STRESS%';
DELETE FROM delivery_items WHERE dn_id IN (SELECT id FROM delivery_notes WHERE remark LIKE '%【压测】%');
DELETE FROM sales_return_items WHERE sr_id IN (SELECT id FROM sales_returns WHERE remark LIKE '%【压测】%');
DELETE FROM customer_order_items WHERE co_id IN (SELECT id FROM customer_orders WHERE remark LIKE '%【压测】%');
DELETE FROM stock_take_items WHERE st_id IN (SELECT id FROM stock_takes WHERE remark LIKE '%【压测】%');
DELETE FROM stock_transfer_items WHERE transfer_id IN (SELECT id FROM stock_transfers WHERE remark LIKE '%【压测】%');
DELETE FROM production_in_items WHERE pi_id IN (SELECT id FROM production_ins WHERE remark LIKE '%【压测】%');
DELETE FROM production_return_items WHERE prt_id IN (SELECT id FROM production_returns WHERE remark LIKE '%【压测】%');
DELETE FROM purchase_items WHERE po_id IN (SELECT id FROM purchase_orders WHERE remark LIKE '%【压测】%');
DELETE FROM purchase_return_items WHERE pr_id IN (SELECT id FROM purchase_returns WHERE remark LIKE '%【压测】%');
DELETE FROM process_materials WHERE process_id IN (SELECT id FROM processes WHERE name LIKE '%【压测】%');
DELETE FROM work_report_images WHERE report_id IN (SELECT id FROM work_reports WHERE remark LIKE '%【压测】%');
DELETE FROM work_reports WHERE remark LIKE '%【压测】%';
DELETE FROM delivery_notes WHERE remark LIKE '%【压测】%';
DELETE FROM customer_orders WHERE remark LIKE '%【压测】%';
DELETE FROM stock_takes WHERE remark LIKE '%【压测】%';
DELETE FROM stock_transfers WHERE remark LIKE '%【压测】%';
DELETE FROM production_ins WHERE remark LIKE '%【压测】%';
DELETE FROM production_returns WHERE remark LIKE '%【压测】%';
DELETE FROM purchase_orders WHERE remark LIKE '%【压测】%';
DELETE FROM purchase_returns WHERE remark LIKE '%【压测】%';
DELETE FROM sales_returns WHERE remark LIKE '%【压测】%';
DELETE FROM settlements WHERE remark LIKE '%【压测】%';
DELETE FROM invoices WHERE remark LIKE '%【压测】%';
DELETE FROM payment_vouchers WHERE remark LIKE '%【压测】%';
DELETE FROM receipt_vouchers WHERE remark LIKE '%【压测】%';
DELETE FROM users WHERE username LIKE 'stress_user_%';
DELETE FROM work_employees WHERE name LIKE '%【压测】%' OR username LIKE 'stress_emp_%';
DELETE FROM processes WHERE name LIKE '%【压测】%';
DELETE FROM work_groups WHERE name LIKE '%【压测】%';
DELETE FROM warehouses WHERE name LIKE '%【压测】%';
DELETE FROM express_companies WHERE name LIKE '%【压测】%';
DELETE FROM materials WHERE name LIKE '%【压测】%';
DELETE FROM customers WHERE name LIKE '%【压测】%';
DELETE FROM suppliers WHERE name LIKE '%【压测】%';
""" + f"""
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(st_no,'-',-1) AS UNSIGNED)),0) FROM stock_takes WHERE st_no LIKE 'PD-{ds}-%') WHERE s.prefix='PD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(co_no,'-',-1) AS UNSIGNED)),0) FROM customer_orders WHERE co_no LIKE 'XSDD-{ds}-%') WHERE s.prefix='XSDD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(dn_no,'-',-1) AS UNSIGNED)),0) FROM delivery_notes WHERE dn_no LIKE 'SH-XSDD-{ds}-%') WHERE s.prefix='SH-XSDD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(po_no,'-',-1) AS UNSIGNED)),0) FROM purchase_orders WHERE po_no LIKE 'CGDD-{ds}-%') WHERE s.prefix='CGDD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(sr_no,'-',-1) AS UNSIGNED)),0) FROM sales_returns WHERE sr_no LIKE 'XSTH-{ds}-%') WHERE s.prefix='XSTH:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(pr_no,'-',-1) AS UNSIGNED)),0) FROM purchase_returns WHERE pr_no LIKE 'TH-{ds}-%') WHERE s.prefix='TH:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(pi_no,'-',-1) AS UNSIGNED)),0) FROM production_ins WHERE pi_no LIKE 'CPRK-{ds}-%') WHERE s.prefix='CPRK:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(prt_no,'-',-1) AS UNSIGNED)),0) FROM production_returns WHERE prt_no LIKE 'PCTL-{ds}-%') WHERE s.prefix='PCTL:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(transfer_no,'-',-1) AS UNSIGNED)),0) FROM stock_transfers WHERE transfer_no LIKE 'ST-{ds}-%') WHERE s.prefix='ST:{ds}';
SET FOREIGN_KEY_CHECKS=1;
"""
    p = subprocess.run([MYSQL, "-uroot", "yawei_erp", "-e", sql], capture_output=True, text=True, timeout=120,
                       encoding="utf-8", errors="replace")
    rec("清理压测数据", p.returncode == 0, (p.stderr or p.stdout)[:150] if p.returncode else "已清理")


def main():
    print("jxcERP v2 压力测试  tag=%s\n" % TAG)
    login()
    base = prepare()
    stress_orders(base["cust"], base["fg"])
    stress_delivery(base)
    stress_clock(base["eid"], base["pid"])
    stress_seq_first_insert(base["sup"], base["fg"])
    stress_load()
    cleanup(base)

    ok_n = sum(1 for _, ok, _ in RESULTS if ok)
    print("\n" + "=" * 60)
    print("压测完成：%d 通过 / %d 失败 / 共 %d 项" % (ok_n, len(RESULTS) - ok_n, len(RESULTS)))
    for name, ok, detail in RESULTS:
        if not ok:
            print("  FAIL %s | %s" % (name, detail[:180]))


if __name__ == "__main__":
    main()
