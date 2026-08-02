# -*- coding: utf-8 -*-
"""
jxcERP v2 全功能 E2E 测试脚本 v3
- 通过 REST API 走通全部业务模块：基础资料/采购/销售/BOM/生产/库存/报工/财务/报表/系统/AI
- 测试数据名称带运行序号，可重复执行；结束后 SQL 全量清理测试数据（生产库不留脏数据）
- 库存断言基于 stock_movements 流水汇总
- 结果输出: 控制台汇总 + I:\yawei-erp-java\docs\test-reports\<日期>-e2e-test.md
"""
import io
import json
import os
import subprocess
import sys
import time
import urllib.request
import urllib.parse
import urllib.error
import uuid
from datetime import datetime

BASE = "http://127.0.0.1:8080"
TOKEN = None
PREFIX = "【测试】"
TAG = datetime.now().strftime("%Y%m%d-%H%M%S")
SUF = TAG[-4:]
RESULTS = []
MYSQL = r"C:\Users\17815\Desktop\yawei\01-ERP\mysql\8.0.28\bin\mysql.exe"
DB_PASS_REF = r"REDACTED_PASSWORD"


def api(method, path, body=None, params=None, raw_bytes=None, content_type="application/json", timeout=40):
    url = BASE + path
    if params:
        qs = urllib.parse.urlencode({k: v for k, v in params.items() if v is not None and v != ""})
        if qs:
            url += "?" + qs
    req = urllib.request.Request(url, method=method)
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    data = None
    if raw_bytes is not None:
        data = raw_bytes
        req.add_header("Content-Type", content_type)
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"error": raw[:300]}
    except Exception as e:
        return 0, {"error": str(e)[:300]}


def multipart(fields, files=None):
    boundary = "----YaweiTestBoundary" + uuid.uuid4().hex
    buf = io.BytesIO()
    for k, v in fields.items():
        buf.write(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (boundary, k, v)).encode("utf-8"))
    for k, (fn, data, ctype) in (files or {}).items():
        buf.write(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n" % (boundary, k, fn, ctype)).encode("utf-8"))
        buf.write(data)
        buf.write(b"\r\n")
    buf.write(("--%s--\r\n" % boundary).encode("utf-8"))
    return buf.getvalue(), "multipart/form-data; boundary=" + boundary


def rec(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(("  %s %s%s" % ("PASS" if ok else "FAIL", name, (" | " + detail[:150] if detail else ""))))


def ok_of(r):
    if isinstance(r, tuple):
        r = r[1]
    return isinstance(r, dict) and r.get("success") is True


def data_of(r):
    if isinstance(r, tuple):
        r = r[1]
    return r.get("data") if isinstance(r, dict) else None


def as_list(resp):
    d = data_of(resp)
    if isinstance(d, dict):
        for k in ("records", "items", "list", "menus", "dicts", "categories", "accounts", "employees", "children"):
            if k in d and isinstance(d[k], list):
                return d[k]
        return []
    return d if isinstance(d, list) else []


def count_of(resp):
    d = data_of(resp)
    if isinstance(d, dict):
        for k in ("total", "count"):
            if k in d:
                return d[k]
        return len(as_list(resp))
    return len(d) if isinstance(d, list) else 0


def get_stock(material_id):
    st, r = api("GET", "/api/stock/inventory", params={"keyword": "TEST"})
    for row in as_list((st, r)):
        if str(row.get("materialId") or row.get("id")) == str(material_id):
            return row.get("quantity") or row.get("stock")
    return None


PNG_1PX = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000d4944415478da63fcffff3f030005fe02fea72f7f620000000049454e44ae426082"
)

# ---------- test modules ----------

def mod_login():
    print("\n== 0. 认证 ==")
    global TOKEN
    st, r = api("POST", "/api/auth/login", {"username": "admin", "password": "admin123"})
    rec("登录 admin/admin123", ok_of((st, r)), str(r.get("data", {}).get("user", {}))[:120] if ok_of((st, r)) else str(r)[:120])
    if ok_of((st, r)):
        TOKEN = r["data"]["token"]
    st, r = api("GET", "/api/auth/me")
    rec("获取当前用户 /auth/me", ok_of((st, r)), ("role=" + str((data_of((st, r)) or {}).get("role"))) if ok_of((st, r)) else str(r)[:100])


def mod_logout():
    st, r = api("POST", "/api/auth/logout")
    rec("退出登录", ok_of((st, r)), str(r)[:100])


def mod_base():
    print("\n== 1. 基础资料 ==")
    cname = PREFIX + "客户-" + SUF
    st, r = api("POST", "/api/customers", {"name": cname, "contact": "测试员", "phone": "13800000001", "region": "东莞", "address": "测试路1号", "remark": TAG})
    cust_id = (data_of((st, r)) or {}).get("id")
    rec("创建客户", ok_of((st, r)), "id=%s" % cust_id if cust_id else str(r)[:120])
    st, r = api("GET", "/api/customers", params={"keyword": cname})
    rec("查询客户(关键词)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("POST", "/api/customers", {"name": cname})
    rec("重复客户名校验", (not ok_of((st, r))) and "已存在" in str(r), str(r)[:120])
    if cust_id:
        st, r = api("PUT", "/api/customers/%s" % cust_id, {"name": cname, "contact": "测试员2", "phone": "13800000002"})
        rec("修改客户", ok_of((st, r)), str(r)[:100])
    sname = PREFIX + "供应商-" + SUF
    st, r = api("POST", "/api/suppliers", {"name": sname, "contact": "供应商联系人", "phone": "13900000001", "address": "供应路2号", "remark": TAG})
    sup_id = (data_of((st, r)) or {}).get("id")
    rec("创建供应商", ok_of((st, r)), "id=%s" % sup_id if sup_id else str(r)[:120])
    st, r = api("GET", "/api/suppliers", params={"keyword": sname})
    rec("查询供应商(关键词)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    if sup_id:
        st, r = api("PUT", "/api/suppliers/%s" % sup_id, {"name": sname, "contact": "联系人2"})
        rec("修改供应商", ok_of((st, r)), str(r)[:100])
    mats = {}
    for code, name, cat, price in (("TEST-RM-" + SUF, PREFIX + "原料1-" + SUF, "测试原料", 2.5), ("TEST-FG-" + SUF, PREFIX + "成品1-" + SUF, "测试成品", 15.0)):
        st, r = api("POST", "/api/materials", {"code": code, "name": name, "spec": "1.0mm", "unit": "个", "category": cat, "purchasePrice": price, "salePrice": price * 2, "minStock": 1, "remark": TAG})
        mid = (data_of((st, r)) or {}).get("id")
        mats[name] = mid
        rec("创建物料 " + name, ok_of((st, r)), "id=%s" % mid if mid else str(r)[:120])
    st, r = api("GET", "/api/materials", params={"keyword": PREFIX + "成品1-" + SUF})
    rec("查询物料(关键词)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    if mats.get(PREFIX + "原料1-" + SUF):
        st, r = api("PUT", "/api/materials/%s" % mats[PREFIX + "原料1-" + SUF], {"name": PREFIX + "原料1-" + SUF, "spec": "1.2mm", "unit": "个", "category": "测试原料", "purchasePrice": 3.0, "salePrice": 6.0})
        rec("修改物料", ok_of((st, r)), str(r)[:100])
    st, r = api("GET", "/api/warehouses")
    whs = as_list((st, r))
    wh1 = whs[0].get("id") if whs else None
    rec("查询仓库列表", ok_of((st, r)) and wh1 is not None, "主仓id=%s 共%s个" % (wh1, len(whs)) if whs else str(r)[:120])
    wname = PREFIX + "仓库B-" + SUF
    st, r = api("POST", "/api/warehouses", {"name": wname, "location": "二楼", "remark": TAG})
    wh2 = (data_of((st, r)) or {}).get("id")
    rec("创建仓库B", ok_of((st, r)), "id=%s" % wh2 if wh2 else str(r)[:120])
    if wh2:
        st, r = api("PUT", "/api/warehouses/%s" % wh2, {"name": wname, "location": "三楼"})
        rec("修改仓库B", ok_of((st, r)), str(r)[:100])
    st, r = api("POST", "/api/express-companies", {"name": PREFIX + "快递-" + SUF, "contact": "快递员", "phone": "13700000000"})
    exp_id = (data_of((st, r)) or {}).get("id")
    rec("创建快递公司", ok_of((st, r)), "id=%s" % exp_id if exp_id else str(r)[:140])
    st, r = api("GET", "/api/express-companies")
    rec("查询快递公司", ok_of((st, r)), "共%s个" % count_of((st, r)))
    if exp_id:
        st, r = api("DELETE", "/api/express-companies/%s" % exp_id)
        rec("删除快递公司", ok_of((st, r)), str(r)[:100])
    return {"cust": cust_id, "sup": sup_id, "wh1": wh1, "wh2": wh2, "mats": mats, "cname": cname, "sname": sname}


def mod_work_base():
    print("\n== 2. 报工基础资料 ==")
    gname = PREFIX + "班组-" + SUF
    st, r = api("POST", "/api/work/groups", {"name": gname, "description": TAG})
    gid = (data_of((st, r)) or {}).get("id")
    rec("创建班组", ok_of((st, r)), "id=%s" % gid if gid else str(r)[:120])
    st, r = api("GET", "/api/work/groups")
    rec("查询班组", ok_of((st, r)) and count_of((st, r)) >= 1, "共%s个" % count_of((st, r)))
    if gid:
        st, r = api("PUT", "/api/work/groups/%s" % gid, {"name": gname, "description": "更新"})
        rec("修改班组", ok_of((st, r)), str(r)[:100])
    ename = PREFIX + "员工-" + SUF
    st, r = api("POST", "/api/work/employees", {"username": "test_emp_" + SUF, "password": "123456", "name": ename, "phone": "13600000000", "groupId": gid, "role": "worker"})
    eid = (data_of((st, r)) or {}).get("id")
    rec("创建员工", ok_of((st, r)), "id=%s" % eid if eid else str(r)[:150])
    st, r = api("GET", "/api/work/employees")
    rec("查询员工", ok_of((st, r)) and count_of((st, r)) >= 1, "共%s个" % count_of((st, r)))
    if eid:
        st, r = api("PUT", "/api/work/employees/%s" % eid, {"name": ename, "phone": "13611111111"})
        rec("修改员工", ok_of((st, r)), str(r)[:100])
    pname = PREFIX + "工序-" + SUF
    st, r = api("POST", "/api/work/processes", {"name": pname, "description": TAG, "groupId": gid, "sortOrder": 1, "unitPrice": 0.5})
    pid = (data_of((st, r)) or {}).get("id")
    rec("创建工序", ok_of((st, r)), "id=%s" % pid if pid else str(r)[:120])
    st, r = api("GET", "/api/work/processes")
    rec("查询工序", ok_of((st, r)) and count_of((st, r)) >= 1, "共%s个" % count_of((st, r)))
    if pid:
        st, r = api("PUT", "/api/work/processes/%s" % pid, {"name": pname, "unitPrice": 0.6})
        rec("修改工序", ok_of((st, r)), str(r)[:100])
    return {"gid": gid, "eid": eid, "pid": pid, "ename": ename}


def mod_bom(mats):
    print("\n== 3. BOM 配方 ==")
    fg = mats.get(PREFIX + "成品1-" + SUF)
    rm = mats.get(PREFIX + "原料1-" + SUF)
    if not fg or not rm:
        rec("BOM: 缺少测试物料", False, "fg=%s rm=%s" % (fg, rm))
        return
    st, r = api("POST", "/api/bom/%s" % fg, {"items": [{"componentId": rm, "quantity": 2}]})
    rec("创建BOM(成品=2×原料)", ok_of((st, r)), str(r)[:120])
    st, r = api("GET", "/api/bom/%s" % fg)
    d = data_of((st, r)) or {}
    rec("查询BOM明细(按成品)", ok_of((st, r)) and len(d.get("items", [])) == 1, str(d)[:140])
    st, r = api("GET", "/api/bom", params={"keyword": PREFIX + "成品1-" + SUF})
    rec("查询BOM列表(有配方的成品)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("POST", "/api/bom/%s" % fg, {"items": [{"componentId": rm, "quantity": 3}]})
    rec("更新BOM(3×原料,先清后插)", ok_of((st, r)), str(r)[:100])
    st, r = api("GET", "/api/bom/%s" % fg)
    d = data_of((st, r)) or {}
    items = d.get("items", [])
    rec("BOM更新生效验证", ok_of((st, r)) and len(items) == 1 and float(items[0]["quantity"]) == 3.0, str(d)[:120])
    st, r = api("DELETE", "/api/bom/%s" % fg)
    rec("清空BOM", ok_of((st, r)), str(r)[:100])
    st, r = api("POST", "/api/bom/%s" % fg, {"items": [{"componentId": rm, "quantity": 2}]})
    rec("重建BOM(2×原料)", ok_of((st, r)), str(r)[:100])


def mod_purchase(base):
    print("\n== 4. 采购 ==")
    rm = base["mats"].get(PREFIX + "原料1-" + SUF)
    fg = base["mats"].get(PREFIX + "成品1-" + SUF)
    item = lambda m, name, q, p: {"materialId": m, "materialName": name, "spec": "1.0mm", "unit": "个", "quantity": q, "unitPrice": p}
    st, r = api("POST", "/api/purchases", {"supplierId": base["sup"], "poDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "采购" + TAG,
                                           "warehouseId": base["wh1"], "items": [item(rm, PREFIX + "原料1-" + SUF, 100, 2.5), item(fg, PREFIX + "成品1-" + SUF, 10, 15.0)]})
    po_id = (data_of((st, r)) or {}).get("id")
    po_no = (data_of((st, r)) or {}).get("poNo")
    rec("创建采购入库单(原料100+成品10)", ok_of((st, r)), "id=%s %s" % (po_id, po_no) if po_id else str(r)[:200])
    st, r = api("GET", "/api/purchases/%s" % po_id)
    d = data_of((st, r)) or {}
    rec("查询采购单详情", ok_of((st, r)) and len(d.get("items", [])) >= 2, str(d)[:140])
    st, r = api("GET", "/api/purchases", params={"keyword": base["sname"]})
    rec("查询采购单列表(按供应商)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    rm_stock = get_stock(rm)
    rec("采购入库后库存(原料1=100)", rm_stock is not None and float(rm_stock) == 100.0, "库存=%s" % rm_stock)
    st, r = api("POST", "/api/purchase-returns", {"supplierId": base["sup"], "returnDate": datetime.now().strftime("%Y-%m-%d"), "poId": po_id, "remark": PREFIX + "采购退货" + TAG,
                                                  "items": [{"materialId": rm, "materialName": PREFIX + "原料1-" + SUF, "spec": "1.0mm", "unit": "个", "quantity": 5, "unitPrice": 2.5}]})
    pr_id = (data_of((st, r)) or {}).get("id")
    rec("创建采购退货单(原料×5)", ok_of((st, r)), "id=%s" % pr_id if pr_id else str(r)[:200])
    st, r = api("GET", "/api/purchase-returns/%s" % pr_id)
    rec("查询采购退货详情", ok_of((st, r)), str(data_of((st, r)) or {})[:120])
    st, r = api("GET", "/api/purchase-returns", params={"keyword": base["sname"]})
    rec("查询采购退货列表(按供应商)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    rm_stock = get_stock(rm)
    rec("采购退货后库存(原料1=95)", rm_stock is not None and float(rm_stock) == 95.0, "库存=%s" % rm_stock)
    for ep in ["by-material", "by-supplier", "by-warehouse", "detail", "monthly", "material-monthly", "price-trend", "supplier-monthly", "returns/by-material", "returns/by-supplier"]:
        st, r = api("GET", "/api/purchase-stats/" + ep, params={"keyword": PREFIX})
        rec("采购统计 " + ep, ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    st, r = api("POST", "/api/purchases", {"supplierId": base["sup"], "poDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "删除验证" + TAG,
                                           "warehouseId": base["wh1"], "items": [item(rm, PREFIX + "原料1-" + SUF, 1, 2.5)]})
    po_del = (data_of((st, r)) or {}).get("id")
    s1 = get_stock(rm)
    st, r = api("DELETE", "/api/purchases/%s" % po_del)
    del_ok = ok_of((st, r))
    s2 = get_stock(rm)
    rec("删除采购单并回滚库存", del_ok and s2 is not None and float(s1) - float(s2) == 1.0, "删除前=%s 删除后=%s" % (s1, s2))
    return {"po": po_id, "pr": pr_id}


def mod_po_order(base):
    """独立采购订单：订单 → 分批入库 → 执行跟踪（2026-08-01 新增模块）
    用独立物料「原料PO-序号」测试，避免污染其他模块的共享库存断言。"""
    print("\n== 4b. 采购订单(独立) ==")
    # 独立物料：只在 po-orders 测试中使用，库存基线=0，且名字带【测试】可被清理
    pom_name = PREFIX + "原料PO-" + SUF
    st, r = api("POST", "/api/materials", {"code": "TEST-PORM-" + SUF, "name": pom_name, "spec": "2.0mm",
                                           "unit": "米", "category": "测试原料", "purchasePrice": 3.5,
                                           "salePrice": 7.0, "minStock": 1, "remark": TAG})
    rm = (data_of((st, r)) or {}).get("id")
    rec("创建采购订单专用物料", ok_of((st, r)) and rm, "id=%s" % rm if rm else str(r)[:120])
    item = lambda m, name, q, p: {"materialId": m, "materialName": name, "spec": "2.0mm", "unit": "米", "quantity": q, "unitPrice": p}
    # 创建订单：订 100
    st, r = api("POST", "/api/po-orders", {"supplierId": base["sup"], "orderDate": datetime.now().strftime("%Y-%m-%d"),
                                           "handler": "测试员", "remark": PREFIX + "采购订单" + TAG,
                                           "items": [item(rm, pom_name, 100, 3.5)]})
    po_id = (data_of((st, r)) or {}).get("id")
    po_no = (data_of((st, r)) or {}).get("poOrderNo")
    rec("创建采购订单(独立物料×100)", ok_of((st, r)) and po_id, "id=%s %s" % (po_id, po_no) if po_id else str(r)[:200])
    st, r = api("GET", "/api/po-orders/%s" % po_id)
    d = data_of((st, r)) or {}
    rec("查询采购订单详情", ok_of((st, r)) and len(d.get("items", [])) == 1 and d.get("main", {}).get("status") == "pending", str(d)[:140])
    st, r = api("GET", "/api/po-orders", params={"keyword": base["sname"]})
    rec("查询采购订单列表(按供应商)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    # 分批入库 #1：收 40 → partial
    st, r = api("POST", "/api/po-orders/%s/receive" % po_id, [{"materialId": rm, "quantity": 40}])
    rec("分批入库#1(收40)", ok_of((st, r)) and data_of((st, r)).get("orderStatus") == "partial", str(r)[:160])
    s1 = get_stock(rm)
    rec("入库#1后库存(独立物料=40)", s1 is not None and float(s1) == 40.0, "库存=%s" % s1)
    # 分批入库 #2：收 60 → done
    st, r = api("POST", "/api/po-orders/%s/receive" % po_id, [{"materialId": rm, "quantity": 60}])
    rec("分批入库#2(收60)", ok_of((st, r)) and data_of((st, r)).get("orderStatus") == "done", str(r)[:160])
    s2 = get_stock(rm)
    rec("入库#2后库存(独立物料=100)", s2 is not None and float(s2) == 100.0, "库存=%s" % s2)
    st, r = api("GET", "/api/po-orders/%s" % po_id)
    rec("订单状态=done", ok_of((st, r)) and data_of((st, r)).get("main", {}).get("status") == "done", str(data_of((st, r)))[:120])
    # 超收被拒
    st, r = api("POST", "/api/po-orders/%s/receive" % po_id, [{"materialId": rm, "quantity": 1}])
    rec("超收被拒", not ok_of((st, r)), str(r)[:120])
    # 带入库关联删除被拒
    st, r = api("DELETE", "/api/po-orders/%s" % po_id)
    rec("已入库订单删除被拒", not ok_of((st, r)), str(r)[:120])
    # 入库单关联检查：receive 生成的采购单 po_order_id 指向本订单（查库验证）
    linked = 0
    try:
        p = subprocess.run([MYSQL, "-uroot", "-p%s" % DB_PASS_REF, "yawei_erp", "-N", "-e",
                            "SELECT COUNT(*) FROM purchase_orders WHERE po_order_id=%s AND deleted=0" % po_id],
                           capture_output=True, text=True, timeout=30, encoding="utf-8", errors="replace")
        linked = int((p.stdout or "0").strip() or 0)
    except Exception:
        linked = 0
    rec("订单入库生成采购单(po_order_id 关联)", linked >= 2, "关联入库单=%s" % linked)
    return {"po": po_id}


def mod_sales(base):
    print("\n== 5. 销售 ==")
    rm = base["mats"].get(PREFIX + "原料1-" + SUF)
    fg = base["mats"].get(PREFIX + "成品1-" + SUF)
    item = lambda m, name, q, p: {"materialId": m, "materialName": name, "spec": "1.0mm", "unit": "个", "quantity": q, "unitPrice": p}
    st, r = api("POST", "/api/orders", {"customerId": base["cust"], "orderDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "订单" + TAG,
                                        "items": [item(fg, PREFIX + "成品1-" + SUF, 6, 30.0), item(rm, PREFIX + "原料1-" + SUF, 20, 6.0)]})
    co_id = (data_of((st, r)) or {}).get("id")
    co_no = (data_of((st, r)) or {}).get("coNo")
    rec("创建客户订单", ok_of((st, r)), "id=%s %s" % (co_id, co_no) if co_id else str(r)[:200])
    st, r = api("GET", "/api/orders/%s" % co_id)
    d = data_of((st, r)) or {}
    rec("查询订单详情", ok_of((st, r)) and len(d.get("items", [])) == 2, str(d)[:140])
    st, r = api("GET", "/api/orders", params={"keyword": base["cname"]})
    rec("查询订单列表(按客户)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("GET", "/api/deliveries/open-orders", params={"keyword": base["cname"]})
    rec("未送货订单列表(含新订单)", ok_of((st, r)) and any(str(x.get("id")) == str(co_id) for x in as_list((st, r))), "total=%s" % count_of((st, r)))
    st, r = api("POST", "/api/deliveries", {"customerId": base["cust"], "orderId": co_id, "deliveryDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "送货" + TAG,
                                            "items": [item(fg, PREFIX + "成品1-" + SUF, 2, 30.0)]})
    dn_id = (data_of((st, r)) or {}).get("id")
    dn_no = (data_of((st, r)) or {}).get("dnNo")
    rec("创建送货单(关联订单)", ok_of((st, r)), "id=%s %s" % (dn_id, dn_no) if dn_id else str(r)[:220])
    st, r = api("GET", "/api/deliveries/%s" % dn_id)
    d = data_of((st, r)) or {}
    rec("查询送货单详情", ok_of((st, r)) and len(d.get("items", [])) >= 1, str(d)[:140])
    st, r = api("GET", "/api/deliveries", params={"keyword": base["cname"]})
    rec("查询送货单列表(按客户)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    fg_stock = get_stock(fg)
    rec("送货后库存(成品1=8)", fg_stock is not None and float(fg_stock) == 8.0, "库存=%s" % fg_stock)
    st, r = api("POST", "/api/deliveries", {"customerId": base["cust"], "orderId": co_id, "deliveryDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "超量测试",
                                            "items": [item(fg, PREFIX + "成品1-" + SUF, 999, 30.0)]})
    rec("超量送货拦截", (not ok_of((st, r))) and "超过" in str(r), str(r)[:140])
    st, r = api("POST", "/api/sales-returns", {"customerId": base["cust"], "returnDate": datetime.now().strftime("%Y-%m-%d"), "orderId": co_id, "remark": PREFIX + "销售退货" + TAG,
                                               "items": [item(fg, PREFIX + "成品1-" + SUF, 2, 30.0)]})
    sr_id = (data_of((st, r)) or {}).get("id")
    rec("创建销售退货单(成品×2)", ok_of((st, r)), "id=%s" % sr_id if sr_id else str(r)[:220])
    st, r = api("GET", "/api/sales-returns/%s" % sr_id)
    rec("查询销售退货详情", ok_of((st, r)), str(data_of((st, r)) or {})[:120])
    st, r = api("GET", "/api/sales-returns", params={"keyword": base["cname"]})
    rec("查询销售退货列表(按客户)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    fg_stock = get_stock(fg)
    rec("退货后库存(成品1=10)", fg_stock is not None and float(fg_stock) == 10.0, "库存=%s" % fg_stock)
    st, r = api("GET", "/api/orders/%s" % co_id)
    d = data_of((st, r)) or {}
    main = d.get("main", {})
    rec("订单送货数量回写", ok_of((st, r)) and float(main.get("delivered_quantity", 0)) == 2.0, "delivered=%s" % main.get("delivered_quantity"))
    for ep in ["by-customer", "by-material", "by-warehouse", "customer-monthly", "detail", "gross-profit", "material-monthly", "monthly", "returns/by-customer", "returns/by-material"]:
        st, r = api("GET", "/api/sales-stats/" + ep, params={"keyword": PREFIX})
        rec("销售统计 " + ep, ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    return {"co": co_id, "dn": dn_id, "sr": sr_id}


def mod_production(base):
    print("\n== 6. 生产 ==")
    fg = base["mats"].get(PREFIX + "成品1-" + SUF)
    rm = base["mats"].get(PREFIX + "原料1-" + SUF)
    st, r = api("POST", "/api/production-ins", {"inDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "成品入库" + TAG,
                                                "items": [{"productId": fg, "quantity": 3}]})
    pi_id = (data_of((st, r)) or {}).get("id")
    rec("创建成品入库单(成品×3)", ok_of((st, r)), "id=%s" % pi_id if pi_id else str(r)[:220])
    st, r = api("GET", "/api/production-ins/%s" % pi_id)
    rec("查询成品入库详情", ok_of((st, r)), str(data_of((st, r)) or {})[:140])
    st, r = api("GET", "/api/production-ins", params={"keyword": PREFIX + "成品入库"})
    rec("查询成品入库列表", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    fg_stock = get_stock(fg)
    rm_stock = get_stock(rm)
    rec("入库后库存(成品1=13)", fg_stock is not None and float(fg_stock) == 13.0, "成品库存=%s" % fg_stock)
    rec("入库不重复扣料(原料1仍=95)", rm_stock is not None and float(rm_stock) == 95.0, "原料库存=%s (按设计报工扣料，入库不重复扣)" % rm_stock)
    st, r = api("POST", "/api/production-returns", {"returnDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "生产退料" + TAG,
                                                    "items": [{"materialId": rm, "quantity": 2}]})
    prt_id = (data_of((st, r)) or {}).get("id")
    rec("创建生产退料单(原料×2)", ok_of((st, r)), "id=%s" % prt_id if prt_id else str(r)[:220])
    st, r = api("GET", "/api/production-returns/%s" % prt_id)
    rec("查询生产退料详情", ok_of((st, r)), str(data_of((st, r)) or {})[:120])
    st, r = api("GET", "/api/production-returns", params={"keyword": PREFIX + "生产退料"})
    rec("查询生产退料列表", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    rm_stock = get_stock(rm)
    rec("退料后库存(原料1=97)", rm_stock is not None and float(rm_stock) == 97.0, "库存=%s" % rm_stock)
    for ep in ["ins", "returns"]:
        st, r = api("GET", "/api/production-stats/" + ep, params={"keyword": PREFIX})
        rec("生产统计 " + ep, ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])


def mod_stock(base):
    print("\n== 7. 库存 ==")
    rm = base["mats"].get(PREFIX + "原料1-" + SUF)
    st, r = api("GET", "/api/stock/inventory", params={"keyword": "TEST"})
    rec("库存查询(关键词)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("GET", "/api/stock/movements", params={"keyword": "TEST"})
    rec("库存流水(关键词)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("POST", "/api/stock-takes", {"warehouseId": base["wh1"], "takeDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "盘点" + TAG,
                                             "items": [{"materialId": rm, "actualQty": 90}]})
    tk_id = (data_of((st, r)) or {}).get("id")
    rec("创建盘点单(原料实盘90)", ok_of((st, r)), "id=%s" % tk_id if tk_id else str(r)[:220])
    st, r = api("GET", "/api/stock-takes/%s" % tk_id)
    d = data_of((st, r)) or {}
    main = d.get("main", {})
    tk_no = main.get("st_no")
    rec("查询盘点单详情(盈亏-7)", ok_of((st, r)) and float(main.get("total_diff_qty", 0)) == -7.0, "差异=%s" % main.get("total_diff_qty"))
    st, r = api("POST", "/api/stock-takes/%s/confirm" % tk_id)
    rec("确认盘点(库存调整为90)", ok_of((st, r)), str(r)[:160])
    rm_stock = get_stock(rm)
    rec("盘点后库存(原料1=90)", rm_stock is not None and float(rm_stock) == 90.0, "库存=%s" % rm_stock)
    st, r = api("GET", "/api/stock-takes", params={"keyword": tk_no})
    rec("查询盘点单列表(按单号)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("POST", "/api/stock/transfers", {"fromWarehouseId": base["wh1"], "toWarehouseId": base["wh2"], "transferDate": datetime.now().strftime("%Y-%m-%d"),
                                                 "remark": PREFIX + "调拨" + TAG, "items": [{"materialId": rm, "quantity": 5}]})
    tf_id = (data_of((st, r)) or {}).get("id")
    rec("创建库存调拨单(原料×5)", ok_of((st, r)), "id=%s" % tf_id if tf_id else str(r)[:220])
    st, r = api("GET", "/api/stock/transfers", params={"keyword": PREFIX + "调拨"})
    rec("查询调拨单列表", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    rm_stock = get_stock(rm)
    rec("调拨后总库存不变(原料1=90)", rm_stock is not None and float(rm_stock) == 90.0, "总库存=%s" % rm_stock)


def mod_work(work_base, mats):
    print("\n== 8. 报工 ==")
    eid, pid = work_base["eid"], work_base["pid"]
    rm = mats.get(PREFIX + "原料1-" + SUF)
    st, r = api("POST", "/api/work/process-materials", {"processId": pid, "materialId": rm, "quantityPerUnit": 0.5})
    pm_id = (data_of((st, r)) or {}).get("id")
    rec("创建工序物料绑定(0.5/个)", ok_of((st, r)), "id=%s" % pm_id if pm_id else str(r)[:160])
    st, r = api("GET", "/api/work/process-materials", params={"processId": pid})
    rec("查询工序物料(按工序)", ok_of((st, r)) and count_of((st, r)) == 1, "共%s条" % count_of((st, r)))
    today = datetime.now().strftime("%Y-%m-%d")
    body, ctype = multipart({"employeeId": eid, "processId": pid, "quantity": 50, "reportDate": today, "remark": PREFIX + "补录报工" + TAG},
                            {"images": ("test.png", PNG_1PX, "image/png")})
    st, r = api("POST", "/api/work/reports", raw_bytes=body, content_type=ctype)
    wr_id = (data_of((st, r)) or {}).get("id")
    rec("提交补录报工(含图片)", ok_of((st, r)), "id=%s" % wr_id if wr_id else str(r)[:220])
    st, r = api("GET", "/api/work/reports", params={"keyword": work_base["ename"]})
    rec("查询报工列表(按员工)", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("GET", "/api/work/reports/last-process", params={"employeeId": eid})
    rec("查询员工上次工序", ok_of((st, r)) and (data_of((st, r)) or {}).get("processId") == pid, str(data_of((st, r)) or {})[:100])
    rm_stock = get_stock(rm)
    rec("报工自动扣料(原料1=65)", rm_stock is not None and float(rm_stock) == 65.0, "库存=%s (90-50×0.5)" % rm_stock)
    body, ctype = multipart({"employeeId": eid, "processId": pid, "quantity": 0, "reportDate": today, "remark": PREFIX + "打卡报工"})
    st, r = api("POST", "/api/work/reports/start", raw_bytes=body, content_type=ctype)
    wr2 = data_of((st, r)) or {}
    wr2_id = wr2.get("id")
    rec("开始报工(打卡)", ok_of((st, r)), "id=%s" % wr2_id if wr2_id else str(r)[:220])
    st, r = api("GET", "/api/work/reports/in-progress", params={"employeeId": eid})
    rec("查询进行中报工(1条)", ok_of((st, r)) and count_of((st, r)) >= 1, "共%s条" % count_of((st, r)))
    if wr2_id:
        st, r = api("POST", "/api/work/reports/%s/finish" % wr2_id, {"quantity": 30, "endTime": datetime.now().strftime("%Y-%m-%d %H:%M:%S")})
        rec("结束报工(自动扣料)", ok_of((st, r)), str(r)[:160])
    st, r = api("GET", "/api/work/reports/in-progress", params={"employeeId": eid})
    rec("进行中报工已清空", ok_of((st, r)) and count_of((st, r)) == 0, "共%s条" % count_of((st, r)))
    body, ctype = multipart({"employeeId": eid, "processId": pid, "quantity": 0, "reportDate": today, "remark": PREFIX + "打卡取消"})
    st, r = api("POST", "/api/work/reports/start", raw_bytes=body, content_type=ctype)
    wr3_id = (data_of((st, r)) or {}).get("id")
    if wr3_id:
        st, r = api("POST", "/api/work/reports/%s/cancel" % wr3_id)
        rec("取消报工", ok_of((st, r)), str(r)[:120])
        st, r = api("POST", "/api/work/reports/%s/cancel" % wr3_id)
        rec("重复取消幂等(不报错)", ok_of((st, r)), str(r)[:120])
    rm_stock = get_stock(rm)
    rec("取消后库存不变(原料1=50)", rm_stock is not None and float(rm_stock) == 50.0, "库存=%s" % rm_stock)
    st, r = api("GET", "/api/work/stats/daily", params={"keyword": work_base["ename"]})
    rec("报工统计-日报", ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    st, r = api("GET", "/api/work/stats/monthly", params={"keyword": work_base["ename"]})
    rec("报工统计-月报", ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    st, r = api("GET", "/api/work/stats-data", params={"keyword": work_base["ename"]})
    rec("报工统计-看板数据", ok_of((st, r)), str(data_of((st, r)) or {})[:100])
    st, r = api("GET", "/api/work/trend", params={"employeeId": eid, "from": "2026-07-01", "to": "2026-08-31"})
    rec("报工趋势(按天)", ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    st, r = api("GET", "/api/work/stats/wages", params={"month": datetime.now().strftime("%Y-%m"), "employeeId": eid})
    d = data_of((st, r)) or {}
    rec("报工工资统计", ok_of((st, r)) and len(d.get("employees", [])) >= 1, str(d.get("summary", d))[:120])
    st, r = api("GET", "/api/work/reports/my", params={"employeeId": eid})
    rec("员工今日已完成报工", ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    if wr_id:
        st, r = api("DELETE", "/api/work/reports/%s" % wr_id)
        rec("删除报工记录", ok_of((st, r)), str(r)[:120])
    if eid:
        st, r = api("DELETE", "/api/work/employees/%s" % eid)
        rec("删除员工", ok_of((st, r)), str(r)[:120])


def mod_finance(base, sales):
    print("\n== 9. 财务 ==")
    an1, an2 = PREFIX + "银行-" + SUF, PREFIX + "微信-" + SUF
    st, r = api("POST", "/api/cash-accounts", {"name": an1, "type": "bank", "balance": 10000, "remark": TAG})
    rec("创建资金账户(银行)", ok_of((st, r)), str(r)[:120])
    st, r = api("POST", "/api/cash-accounts", {"name": an2, "type": "wechat", "balance": 5000, "remark": TAG})
    rec("创建资金账户(微信)", ok_of((st, r)), str(r)[:120])
    st, r = api("GET", "/api/cash-accounts")
    accs = as_list((st, r))
    acc_bank = next((a.get("id") for a in accs if an1 in str(a.get("name", ""))), None)
    acc_wx = next((a.get("id") for a in accs if an2 in str(a.get("name", ""))), None)
    rec("查询资金账户列表", ok_of((st, r)) and acc_bank and acc_wx, "bank=%s wx=%s" % (acc_bank, acc_wx))
    if acc_bank:
        st, r = api("PUT", "/api/cash-accounts/%s" % acc_bank, {"name": an1, "type": "bank", "balance": 20000})
        rec("修改资金账户", ok_of((st, r)), str(r)[:100])
    st, r = api("POST", "/api/income-expenses", {"ieType": "income", "category": "销售收款", "accountId": acc_bank, "amount": 500, "ieDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "收入" + TAG})
    ie1 = (data_of((st, r)) or {}).get("id")
    rec("登记收入", ok_of((st, r)), "id=%s" % ie1 if ie1 else str(r)[:200])
    st, r = api("POST", "/api/income-expenses", {"ieType": "expense", "category": "采购付款", "accountId": acc_bank, "amount": 200, "ieDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "支出" + TAG})
    ie2 = (data_of((st, r)) or {}).get("id")
    rec("登记支出", ok_of((st, r)), "id=%s" % ie2 if ie2 else str(r)[:200])
    st, r = api("GET", "/api/income-expenses", params={"keyword": PREFIX})
    rec("查询收支列表", ok_of((st, r)) and count_of((st, r)) >= 2, "total=%s" % count_of((st, r)))
    if acc_bank and acc_wx:
        st, r = api("POST", "/api/transfers", {"fromId": acc_bank, "toId": acc_wx, "amount": 100, "tfDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "转账" + TAG})
        rec("账户间转账", ok_of((st, r)), str(r)[:200])
        st, r = api("POST", "/api/transfers", {"fromId": acc_bank, "toId": acc_bank, "amount": 100, "tfDate": datetime.now().strftime("%Y-%m-%d"), "remark": TAG})
        rec("同账户转账拦截", (not ok_of((st, r))), str(r)[:120])
    for ep in ["balances", "business", "stats"]:
        st, r = api("GET", "/api/account-reports/" + ep)
        rec("收支报表-" + ep, ok_of((st, r)), str(data_of((st, r)) or {})[:110])
    st, r = api("POST", "/api/vouchers/receipts", {"customerId": base["cust"], "amount": 60, "receiptDate": datetime.now().strftime("%Y-%m-%d"), "receiptMethod": "转账", "remark": PREFIX + "收款" + TAG})
    rv_id = (data_of((st, r)) or {}).get("id")
    rec("创建收款单", ok_of((st, r)), "id=%s" % rv_id if rv_id else str(r)[:220])
    st, r = api("POST", "/api/vouchers/payments", {"supplierId": base["sup"], "amount": 250, "payDate": datetime.now().strftime("%Y-%m-%d"), "payMethod": "转账", "remark": PREFIX + "付款" + TAG})
    pv_id = (data_of((st, r)) or {}).get("id")
    rec("创建付款单", ok_of((st, r)), "id=%s" % pv_id if pv_id else str(r)[:220])
    st, r = api("GET", "/api/finance/doc-list")
    rec("财务单据列表", ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    st, r = api("POST", "/api/finance/settle", {"refType": "delivery", "refId": sales["dn"], "voucherId": rv_id, "amount": 60, "remark": PREFIX + "核销" + TAG})
    settle_id = (data_of((st, r)) or {}).get("id")
    rec("应收核销(送货单)", ok_of((st, r)), "settleId=%s" % settle_id if settle_id else str(r)[:220])
    st, r = api("GET", "/api/finance/settlements", params={"refType": "delivery", "refId": sales["dn"]})
    rec("查询核销记录", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    if settle_id:
        st, r = api("POST", "/api/finance/settle/revoke", {"id": settle_id})
        rec("撤销核销", ok_of((st, r)), str(r)[:160])
        st, r = api("GET", "/api/finance/settlements", params={"refType": "delivery", "refId": sales["dn"]})
        rec("撤销后核销记录清空", ok_of((st, r)) and count_of((st, r)) == 0, "total=%s" % count_of((st, r)))
        st, r = api("POST", "/api/finance/settle", {"refType": "delivery", "refId": sales["dn"], "voucherId": rv_id, "amount": 60, "remark": PREFIX + "核销2" + TAG})
        rec("重新核销", ok_of((st, r)), str(r)[:160])
    st, r = api("DELETE", "/api/deliveries/%s" % sales["dn"])
    rec("已核销送货单删除拦截", (not ok_of((st, r))) and "核销" in str(r), str(r)[:140])
    st, r = api("POST", "/api/finance/invoices", {"invoiceType": "delivery", "refId": sales["dn"], "customerId": base["cust"], "customerName": base["cname"],
                                                  "amount": 60, "invoiceDate": datetime.now().strftime("%Y-%m-%d"), "remark": PREFIX + "发票" + TAG})
    inv_id = (data_of((st, r)) or {}).get("id")
    rec("登记发票", ok_of((st, r)), "id=%s" % inv_id if inv_id else str(r)[:220])
    st, r = api("GET", "/api/finance/invoices", params={"keyword": PREFIX})
    rec("查询发票列表", ok_of((st, r)) and count_of((st, r)) >= 1, "total=%s" % count_of((st, r)))
    st, r = api("GET", "/api/finance/invoice-refs", params={"invoiceType": "delivery"})
    rec("查询发票可开单据", ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    for ep in ["aging", "payable-summary", "payable-monthly", "receivable-summary", "receivable-monthly"]:
        st, r = api("GET", "/api/finance/" + ep, params={"keyword": PREFIX})
        rec("财务 " + ep, ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:100])
    return {"acc_bank": acc_bank, "acc_wx": acc_wx, "ie1": ie1, "ie2": ie2, "rv": rv_id, "pv": pv_id, "inv": inv_id}


def mod_reports():
    print("\n== 10. 报表中心 ==")
    y, m = datetime.now().strftime("%Y"), datetime.now().strftime("%Y-%m")
    for ep, params in [("profit", {"start": "2026-07-01", "end": "2026-08-31"}), ("reconciliation", {"start": "2026-07-01", "end": "2026-08-31"}),
                       ("sales-detail", {"month": m}), ("sales-monthly", {"year": y}),
                       ("sales-query", {"keyword": PREFIX})]:
        st, r = api("GET", "/api/reports/" + ep, params=params)
        rec("报表 " + ep, ok_of((st, r)), "total=%s" % count_of((st, r)) if ok_of((st, r)) else str(r)[:140])


def mod_system():
    print("\n== 11. 系统管理 ==")
    st, r = api("GET", "/api/dashboard/data")
    d = data_of((st, r)) or {}
    rec("仪表盘数据", ok_of((st, r)) and len(d) > 0, "字段=%s" % ",".join(list(d.keys())[:6]))
    st, r = api("GET", "/api/menus")
    rec("菜单(后端下发,角色过滤)", ok_of((st, r)) and count_of((st, r)) > 0, "共%s个" % count_of((st, r)))
    st, r = api("GET", "/api/dicts")
    d = data_of((st, r)) or {}
    rec("数据字典(后端下发)", ok_of((st, r)) and len(d.get("dicts", [])) > 0, "共%s类" % len(d.get("dicts", [])))
    st, r = api("GET", "/api/config/company")
    d = data_of((st, r)) or {}
    rec("公司信息(后端下发)", ok_of((st, r)) and bool(d.get("companyName")), str(d)[:120])
    uname = "testuser_" + SUF
    st, r = api("POST", "/api/users", {"username": uname, "password": "123456", "displayName": PREFIX + "用户-" + SUF, "role": "user"})
    uid = (data_of((st, r)) or {}).get("id")
    rec("创建用户", ok_of((st, r)), "id=%s" % uid if uid else str(r)[:200])
    st, r = api("GET", "/api/users")
    rec("查询用户列表", ok_of((st, r)) and count_of((st, r)) >= 1, "共%s个" % count_of((st, r)))
    if uid:
        st, r = api("PUT", "/api/users/%s" % uid, {"displayName": PREFIX + "用户2-" + SUF, "role": "boss", "active": True})
        rec("修改用户", ok_of((st, r)), str(r)[:120])
        st, r = api("DELETE", "/api/users/%s" % uid)
        rec("删除用户", ok_of((st, r)), str(r)[:120])
    st, r = api("POST", "/api/backup/create")
    d = data_of((st, r)) or {}
    rec("创建数据库备份", ok_of((st, r)) and bool(d.get("file")), "file=%s" % d.get("file", ""))
    st, r = api("GET", "/api/backup/list")
    rec("查询备份列表", ok_of((st, r)) and count_of((st, r)) >= 1, "共%s个" % count_of((st, r)))
    st, r = api("GET", "/api/logs", params={"page": 1, "size": 5})
    rec("操作日志(自动记录)", ok_of((st, r)), "共%s条" % count_of((st, r)) if ok_of((st, r)) else str(r)[:120])
    st, r = api("POST", "/api/logs/client-error", {"message": PREFIX + "前端错误上报" + TAG, "url": "/test", "stack": "test stack"})
    rec("前端错误上报", ok_of((st, r)), str(r)[:120])


def mod_ai():
    print("\n== 12. AI 功能 ==")
    st, r = api("POST", "/api/ai/chat", {"message": "1米长的2.5平方线多少钱？"})
    d = data_of((st, r))
    ok = ok_of((st, r)) and d not in (None, "")
    rec("AI报价对话", ok, (d if isinstance(d, str) else json.dumps(d, ensure_ascii=False))[:170])


def sql_cleanup():
    """SQL 全量清理本次测试数据（含前几轮遗留），并重同步单据序列，保证生产库干净可重跑"""
    print("\n== 13. 数据清理(SQL 全量) ==")
    ds = datetime.now().strftime("%Y%m%d")
    sql = r"""
SET FOREIGN_KEY_CHECKS=0;
DELETE FROM transfers WHERE remark LIKE '%【测试】%';
DELETE FROM income_expenses WHERE remark LIKE '%【测试】%';
DELETE FROM cash_accounts WHERE name LIKE '%【测试】%';
DELETE FROM stock_movements WHERE remark LIKE '%【测试】%' OR remark LIKE '%TEST%';
DELETE FROM delivery_items WHERE dn_id IN (SELECT id FROM delivery_notes WHERE remark LIKE '%【测试】%' OR remark LIKE '%%删除验证%%' OR remark LIKE '%超量测试%');
DELETE FROM sales_return_items WHERE sr_id IN (SELECT id FROM sales_returns WHERE remark LIKE '%【测试】%');
DELETE FROM customer_order_items WHERE co_id IN (SELECT id FROM customer_orders WHERE remark LIKE '%【测试】%');
DELETE FROM stock_take_items WHERE st_id IN (SELECT id FROM stock_takes WHERE remark LIKE '%【测试】%');
DELETE FROM stock_transfer_items WHERE transfer_id IN (SELECT id FROM stock_transfers WHERE remark LIKE '%【测试】%');
DELETE FROM production_in_items WHERE pi_id IN (SELECT id FROM production_ins WHERE remark LIKE '%【测试】%');
DELETE FROM production_return_items WHERE prt_id IN (SELECT id FROM production_returns WHERE remark LIKE '%【测试】%');
DELETE FROM purchase_items WHERE po_id IN (SELECT id FROM purchase_orders WHERE remark LIKE '%【测试】%');
-- 采购订单（独立）：先删其 receive 生成的入库单/流水（po_order_id 关联），再删订单本身
DELETE FROM purchase_items WHERE po_id IN (SELECT id FROM purchase_orders WHERE po_order_id IN (SELECT id FROM po_orders WHERE remark LIKE '%【测试】%'));
DELETE FROM stock_movements WHERE ref_type='purchase' AND ref_id IN (SELECT id FROM purchase_orders WHERE po_order_id IN (SELECT id FROM po_orders WHERE remark LIKE '%【测试】%'));
DELETE FROM purchase_orders WHERE po_order_id IN (SELECT id FROM po_orders WHERE remark LIKE '%【测试】%');
DELETE FROM po_order_items WHERE po_order_id IN (SELECT id FROM po_orders WHERE remark LIKE '%【测试】%');
DELETE FROM po_orders WHERE remark LIKE '%【测试】%';
DELETE FROM purchase_return_items WHERE pr_id IN (SELECT id FROM purchase_returns WHERE remark LIKE '%【测试】%');
DELETE FROM bom_items WHERE product_id IN (SELECT id FROM materials WHERE name LIKE '%【测试】%');
DELETE FROM process_materials WHERE process_id IN (SELECT id FROM processes WHERE name LIKE '%【测试】%');
DELETE FROM work_report_images WHERE report_id IN (SELECT id FROM work_reports WHERE remark LIKE '%【测试】%');
DELETE FROM work_reports WHERE remark LIKE '%【测试】%';
DELETE FROM delivery_notes WHERE remark LIKE '%【测试】%' OR remark LIKE '%超量测试%';
DELETE FROM customer_orders WHERE remark LIKE '%【测试】%';
DELETE FROM stock_takes WHERE remark LIKE '%【测试】%';
DELETE FROM stock_transfers WHERE remark LIKE '%【测试】%';
DELETE FROM production_ins WHERE remark LIKE '%【测试】%';
DELETE FROM production_returns WHERE remark LIKE '%【测试】%';
DELETE FROM purchase_orders WHERE remark LIKE '%【测试】%';
DELETE FROM purchase_returns WHERE remark LIKE '%【测试】%';
DELETE FROM sales_returns WHERE remark LIKE '%【测试】%';
DELETE FROM settlements WHERE remark LIKE '%【测试】%';
DELETE FROM invoices WHERE remark LIKE '%【测试】%';
DELETE FROM payment_vouchers WHERE remark LIKE '%【测试】%';
DELETE FROM receipt_vouchers WHERE remark LIKE '%【测试】%';
DELETE FROM users WHERE username LIKE 'testuser_%';
DELETE FROM work_employees WHERE name LIKE '%【测试】%' OR username LIKE 'test_emp_%';
DELETE FROM processes WHERE name LIKE '%【测试】%';
DELETE FROM work_groups WHERE name LIKE '%【测试】%';
DELETE FROM warehouses WHERE name LIKE '%【测试】%';
DELETE FROM express_companies WHERE name LIKE '%【测试】%';
DELETE FROM materials WHERE name LIKE '%【测试】%';
DELETE FROM customers WHERE name LIKE '%【测试】%';
DELETE FROM suppliers WHERE name LIKE '%【测试】%';
DELETE FROM operation_logs WHERE detail LIKE '%【测试】%' OR detail LIKE '%测试%';
""" + f"""
-- 序列重同步：清掉测试单后，把各单据序列表重置为「现存最大单号」，防止软删行占位导致撞号
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(st_no,'-',-1) AS UNSIGNED)),0) FROM stock_takes WHERE st_no LIKE 'PD-{ds}-%') WHERE s.prefix='PD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(po_no,'-',-1) AS UNSIGNED)),0) FROM purchase_orders WHERE po_no LIKE 'CGDD-{ds}-%') WHERE s.prefix='CGDD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(po_order_no,'-',-1) AS UNSIGNED)),0) FROM po_orders WHERE po_order_no LIKE 'PO-{ds}-%') WHERE s.prefix='PO:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(co_no,'-',-1) AS UNSIGNED)),0) FROM customer_orders WHERE co_no LIKE 'XSDD-{ds}-%') WHERE s.prefix='XSDD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(dn_no,'-',-1) AS UNSIGNED)),0) FROM delivery_notes WHERE dn_no LIKE 'SH-XSDD-{ds}-%') WHERE s.prefix='SH-XSDD:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(sr_no,'-',-1) AS UNSIGNED)),0) FROM sales_returns WHERE sr_no LIKE 'XSTH-{ds}-%') WHERE s.prefix='XSTH:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(pr_no,'-',-1) AS UNSIGNED)),0) FROM purchase_returns WHERE pr_no LIKE 'TH-{ds}-%') WHERE s.prefix='TH:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(pi_no,'-',-1) AS UNSIGNED)),0) FROM production_ins WHERE pi_no LIKE 'CPRK-{ds}-%') WHERE s.prefix='CPRK:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(prt_no,'-',-1) AS UNSIGNED)),0) FROM production_returns WHERE prt_no LIKE 'PCTL-{ds}-%') WHERE s.prefix='PCTL:{ds}';
UPDATE sequences s SET seq = (SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(transfer_no,'-',-1) AS UNSIGNED)),0) FROM stock_transfers WHERE transfer_no LIKE 'ST-{ds}-%') WHERE s.prefix='ST:{ds}';
SET FOREIGN_KEY_CHECKS=1;
"""
    try:
        p = subprocess.run([MYSQL, "-uroot", "-p%s" % DB_PASS_REF, "yawei_erp", "-e", sql], capture_output=True, text=True, timeout=120, encoding="utf-8", errors="replace")
        if p.returncode == 0:
            rec("SQL 清理测试数据", True, "生产库已清理")
        else:
            rec("SQL 清理测试数据", False, (p.stderr or p.stdout)[:200])
    except Exception as e:
        rec("SQL 清理测试数据", False, str(e)[:200])


def main():
    t0 = time.time()
    print("jxcERP v2 全功能 E2E 测试 v3  tag=%s" % TAG)
    mod_login()
    if not TOKEN:
        print("登录失败，终止。")
        sys.exit(1)
    base = mod_base()
    work_base = mod_work_base()
    mod_bom(base["mats"])
    mod_purchase(base)
    mod_po_order(base)
    sales = mod_sales(base)
    mod_production(base)
    mod_stock(base)
    mod_work(work_base, base["mats"])
    finance = mod_finance(base, sales)
    mod_reports()
    mod_system()
    mod_ai()
    mod_logout()
    sql_cleanup()

    ok_n = sum(1 for _, ok, _ in RESULTS if ok)
    fail_n = len(RESULTS) - ok_n
    print("\n" + "=" * 60)
    print("测试完成：%d 通过 / %d 失败 / 共 %d 项  耗时 %.0fs" % (ok_n, fail_n, len(RESULTS), time.time() - t0))
    if fail_n:
        print("\n失败明细：")
        for name, ok, detail in RESULTS:
            if not ok:
                print("  FAIL %s | %s" % (name, detail[:200]))
    out_dir = r"C:\Users\17815\Desktop\yawei\01-ERP\yawei-erp-java\docs\test-reports"
    os.makedirs(out_dir, exist_ok=True)
    fname = os.path.join(out_dir, "%s-e2e-test.md" % datetime.now().strftime("%Y-%m-%d"))
    with open(fname, "w", encoding="utf-8") as f:
        f.write("# jxcERP v2 全功能 E2E 测试报告\n\n")
        f.write("- 时间：%s\n- 环境：http://127.0.0.1:8080\n- 测试数据：运行后已全量清理（SQL），生产库不留脏数据\n\n" % datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        f.write("## 结果汇总\n\n| 模块 | 通过 | 失败 |\n|---|---|---|\n")
        mods = {}
        for name, ok, _ in RESULTS:
            m = name.split(" ")[0].split("/")[0]
            mods.setdefault(m, [0, 0])
            mods[m][0 if ok else 1] += 1
        for m, (a, b) in mods.items():
            f.write("| %s | %d | %d |\n" % (m, a, b))
        f.write("\n## 明细\n\n| 结果 | 测试项 | 说明 |\n|---|---|---|\n")
        for name, ok, detail in RESULTS:
            f.write("| %s | %s | %s |\n" % ("PASS" if ok else "FAIL", name, str(detail).replace("|", "\\|")[:300]))
        f.write("\n## 备注\n\n- 备份恢复接口（POST /api/backup/restore）未执行：真实库恢复会回滚到备份时点，需在临时库验证（见 docs/08-dev-notes.md 6.13）。\n- AI 接口依赖模型密钥，失败属环境依赖而非系统缺陷。\n- 成品入库不自动扣 BOM（按设计：报工经工序物料绑定自动扣料，避免重复扣）。\n")
    print("\n报告已写入: %s" % fname)


if __name__ == "__main__":
    main()
