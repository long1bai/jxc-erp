# -*- coding: utf-8 -*-
"""项目材料成本台账 —— 接口链路验证（一次性脚本，可重复跑）
链路: 客户 → 项目 → 采购单挂项目(带 assemblySystem) → 项目列表 total_amount → 台账 items/summary/totals
"""
import json
import urllib.request

BASE = 'http://localhost:8080/api'
TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJlcnAtc2VydmVyIiwic3ViIjoiMTc4NTgwOTA5MjY5MDAwIiwidXNlcm5hbWUiOiJhZG1pbiIsImRpc3BsYXlOYW1lIjoi566h55CG5ZGYIiwicm9sZSI6ImFkbWluIiwiaWF0IjoxNzg1ODMxMjI1LCJleHAiOjE3ODY0MzYwMjV9.azh1OSpJ3ufAJAQ80Pec0jDcZYYizKcgXwWTncVlv2U'


def req(method, path, body=None):
    r = urllib.request.Request(BASE + path, method=method,
                               data=json.dumps(body).encode() if body is not None else None,
                               headers={'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(r) as resp:
        return json.load(resp)


# 1. 取或建客户
custs = req('GET', '/customers?page=1&size=5')['data']['items']
if custs:
    cust = custs[0]
    print('[1] 复用客户 id=%s %s' % (cust['id'], cust['name']))
else:
    cust = req('POST', '/customers', {'name': '测试客户-项目成本'})['data']
    print('[1] 新建客户 id=%s %s' % (cust['id'], cust['name']))

# 2. 建/复用项目
proj = next((x for x in req('GET', '/projects?keyword=PT-001&page=1&size=5')['data']['items'] if x['code'] == 'PT-001'), None)
if not proj:
    proj = req('POST', '/projects', {'name': 'P-TEST-设备项目', 'code': 'PT-001', 'customerId': cust['id']})['data']
pid = proj['id']
print('[2] 项目 id=%s name=%s' % (pid, proj['name']))

# 3. 基础数据（缺则自动补）
sup = (req('GET', '/suppliers?page=1&size=5')['data']['items'] or [None])[0]
if not sup:
    sup = req('POST', '/suppliers', {'name': '测试供应商-松凌'})['data']
whs = req('GET', '/warehouses?size=5')['data']['items']
wid = whs[0]['id'] if whs else req('POST', '/warehouses', {'name': '测试仓库'})['data']['id']
mat = (req('GET', '/materials?page=1&size=5')['data']['items'] or [None])[0]
if not mat:
    mat = req('POST', '/materials', {'name': '测试物料-钢板', 'spec': '3mm', 'unit': '张'})['data']
print('[3] 供应商=%s 仓库=%s 物料=%s(%s)' % (sup['id'], wid, mat['id'], mat['name']))

# 4. 清理该项目下旧采购单（幂等），再新建采购单挂项目 + 装配系统
for po in req('GET', '/purchases?page=1&size=100')['data']['items']:
    if po.get('project_id') == pid:
        req('DELETE', '/purchases/%s' % po['id'])
print('[4] 已清理该项目旧采购单')
po = req('POST', '/purchases', {
    'supplierId': sup['id'], 'warehouseId': wid, 'poDate': '2026-08-04',
    'projectId': pid, 'projectName': proj['name'], 'handler': '测试',
    'items': [
        {'materialId': mat['id'], 'materialName': mat['name'], 'spec': mat.get('spec') or '', 'unit': mat.get('unit') or '',
         'assemblySystem': '装配系统A', 'quantity': 2, 'unitPrice': 100},
        {'materialId': mat['id'], 'materialName': mat['name'], 'spec': mat.get('spec') or '', 'unit': mat.get('unit') or '',
         'assemblySystem': '装配系统B', 'quantity': 3, 'unitPrice': 50},
    ]})
print('[4] 采购单创建 poNo=%s' % po['data'].get('poNo'))

# 5. 项目列表 total_amount / po_count
lst = req('GET', '/projects?keyword=PT-001&page=1&size=10')['data']['items']
p = next(x for x in lst if x['id'] == pid)
print('[5] 项目列表 total_amount=%s po_count=%s' % (p['total_amount'], p['po_count']))

# 6. 台账
cost = req('GET', '/projects/%s/cost' % pid)['data']
print('[6] 台账: items=%d 行, summary=%s, totals=%s' % (len(cost['items']), cost['summary'], cost['totals']))

# 断言
assert float(cost['totals'][0]['total_amount']) == 350.0, '整表合计错误: %s' % cost['totals']
assert float(p['total_amount']) == 350.0, '列表 total_amount 错误'
assert float(cost['totals'][0]['total_quantity']) == 5.0, '数量合计错误'
by_a = next(s for s in cost['summary'] if s['assembly_system'] == '装配系统A')
by_b = next(s for s in cost['summary'] if s['assembly_system'] == '装配系统B')
assert float(by_a['total_amount']) == 200.0 and float(by_b['total_amount']) == 150.0, '按装配系统小计错误'
print('ALL PASS ✔')
