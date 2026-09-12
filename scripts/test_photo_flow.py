# -*- coding: utf-8 -*-
"""拍照入库闭环实测：识别 → 确认入库 → 验证 → 清理"""
import base64, json, sys, urllib.request

BASE = "http://127.0.0.1:8080/api"

def api(method, path, body=None, token=""):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode("utf-8") if body is not None else None
    try:
        with urllib.request.urlopen(req, data) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"http_error": e.code, "body": e.read().decode("utf-8")[:300]}

# 登录
tok = api("POST", "/auth/login", {"username": "admin", "password": "admin123"})
token = tok["data"]["token"]
print("登录 OK")

# 1. 识别一张真实送货单
img_path = sys.argv[1] if len(sys.argv) > 1 else r"I:\erp-server\uploads\photo_0f1d117e1f78.jpg"
with open(img_path, "rb") as f:
    b64 = base64.b64encode(f.read()).decode()
print("识别图片:", img_path)
res = api("POST", "/photo/recognize", {"imageBase64": b64}, token)
if not res.get("success"):
    print("识别失败:", res); sys.exit(1)
d = res["data"]
print("供应商:", d.get("supplierId"), d.get("supplierName"))
print("单号:", d.get("docNo"), "| 日期:", d.get("docDate"))
items = d.get("items") or []
print("明细行数:", len(items))
for it in items[:5]:
    print("  -", it.get("name"), "|", it.get("spec"), "|", it.get("unit"), "|", it.get("quantity"), "x", it.get("unit_price"))

# 2. 确认入库（构造 items：带 supplierId，物料不预匹配，测试自动新建路径）
if items:
    clean = []
    for it in items[:3]:  # 只取前3行做测试，避免大量建物料
        qty = float(it.get("quantity") or 0)
        if qty > 0:
            clean.append({
                "materialId": None,
                "name": it.get("name") or "",
                "spec": it.get("spec") or "",
                "unit": it.get("unit") or "",
                "quantity": qty,
                "unitPrice": float(it.get("unit_price") or 0),
            })
    supplier_id = d.get("supplierId")
    if not supplier_id:
        # 识别没匹配到供应商 → 取第一个供应商测试
        sups = api("GET", "/suppliers?keyword=&size=1", token=token)
        supplier_id = sups["data"]["items"][0]["id"]
        print("识别未匹配供应商，改用库中第一个:", supplier_id)
    print("\n确认入库:", len(clean), "行")
    conf = api("POST", "/photo/confirm", {
        "supplierId": supplier_id, "poDate": "2026-07-31", "remark": "拍照入库闭环实测",
        "items": clean,
    }, token)
    print("确认结果:", json.dumps(conf, ensure_ascii=False)[:300])
    if conf.get("success"):
        po_id = conf["data"]["id"]
        po_no = conf["data"]["poNo"]
        print("\n采购单:", po_no, "id=", po_id)
        # 3. 验证采购单明细
        det = api("GET", f"/purchases/{po_id}", token=token)
        main = det["data"]["main"]
        dets = det["data"]["items"]
        print("采购单明细:", len(dets), "行, 总金额:", main.get("total_amount"))
        for it in dets[:5]:
            print("  -", it.get("material_id"), it.get("material_name"), "|", it.get("quantity"), "x", it.get("unit_price"), "=", it.get("amount"))
        # 4. 验证库存流水
        mid = dets[0]["material_id"]
        mov = api("GET", f"/stock/movements?materialId={mid}&size=3", token=token)
        print("物料", mid, "最近流水:")
        for m in mov["data"]["items"][:3]:
            print("  -", m["move_type"], m["quantity"], m["remark"])
        # 5. 清理测试采购单（回删库存流水）
        rm = api("DELETE", f"/purchases/{po_id}", token=token)
        print("\n清理测试采购单:", "OK" if rm.get("success") else rm)
        # 验证流水已删
        mov2 = api("GET", f"/stock/movements?materialId={mid}&size=5", token=token)
        print("清理后物料", mid, "流水数:", len(mov2["data"]["items"]))
else:
    print("识别无明细，无法测试确认")
