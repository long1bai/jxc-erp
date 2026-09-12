# -*- coding: utf-8 -*-
"""回归冒烟：验证抽取 PurchaseCreationService 后现有链路不被破坏。
链路: 普通采购新建(无ext) → 详情 → 库存流水（审批开/关两分支） → 清理
清理: 物理删 purchase_items/purchase_orders + stock_movements（ref_id 关联，无 po_no 列）
"""
import subprocess
import requests

BASE = 'http://localhost:8080/api'
MYSQL_BIN = 'C:/Users/17815/Desktop/jxc/01-ERP/mysql/8.0.28/bin/mysql.exe'
from db_secret import DB_AUTH   # 数据库凭据从 config/db_secret.env 读，密码不进代码


def _mysql(sql):
    r = subprocess.run([MYSQL_BIN, '-uroot'] + DB_AUTH + ['jxc_erp', '-e', sql],
                       capture_output=True, text=True)
    return (r.stdout or '').strip()


def login():
    r = requests.post(BASE + '/auth/login', json={'username': 'admin', 'password': 'admin123'}, timeout=15)
    d = r.json()
    assert d.get('success'), d
    return d['data']['token']


TOKEN = login()
H = {'Authorization': 'Bearer ' + TOKEN}


def req(method, path, body=None):
    r = requests.request(method, BASE + path, json=body, headers=H, timeout=30)
    d = r.json()
    assert d.get('success'), '%s %s -> %s' % (method, path, d)
    return d


# 清理上轮联调残留（VFYUI）与脚本自身残留（VFYRG）
def hard_del(po_like, ref_id=None):
    sql = ("DELETE FROM purchase_items WHERE po_id IN (SELECT id FROM purchase_orders WHERE po_no LIKE '%s'); "
           "DELETE FROM purchase_orders WHERE po_no LIKE '%s';") % (po_like, po_like)
    if ref_id:
        sql += " DELETE FROM stock_movements WHERE ref_type='purchase' AND ref_id=%s;" % ref_id
    _mysql(sql)


hard_del('VFYUI-%')

# 审批配置
ac = req('GET', '/approvals/config')['data']
approval_on = ac.get('purchase', False)
print('审批配置: purchase=%s delivery=%s' % (ac.get('purchase'), ac.get('delivery')))

# 取基础资料
sup = req('GET', '/suppliers?page=1&size=1')['data']['items'][0]
mat = req('GET', '/materials?page=1&size=1')['data']['items'][0]
whs = req('GET', '/warehouses?size=1')['data']['items']
wid = whs[0]['id']

# 新建普通采购单（无 ext）
po = req('POST', '/purchases', {
    'supplierId': sup['id'], 'warehouseId': wid, 'poDate': '2026-08-05', 'handler': 'VFYRG',
    'items': [{'materialId': mat['id'], 'materialName': mat['name'], 'spec': mat.get('spec') or '',
               'unit': mat.get('unit') or '', 'assemblySystem': '', 'quantity': 2, 'unitPrice': 10}],
})
pid = po['data']['id']
pno = po['data']['poNo']
pending = po['data']['pendingApproval']
assert po['data']['created'] == 1, po
print('[1] 普通采购新建 OK  poNo=%s id=%s pendingApproval=%s' % (pno, pid, pending))

# 详情：主单 + 明细 + ext_json 空
det = req('GET', '/purchases/%s' % pid)['data']
main_ext = det['main'].get('ext_json')
assert main_ext in (None, '', 'null'), '普通单不应有 ext: %s' % main_ext
assert len(det.get('items') or []) == 1, det
print('[2] 详情 OK  ext_json=%r items=%d' % (main_ext, len(det['items'])))

# 库存流水分支
moves = None
if pending:
    # 审批流分支：批准后应补库存流水
    req('POST', '/approvals/purchase/%s/approve' % pid)
    print('[3] 审批通过 OK（链路走审批，批准后补库存）')
else:
    print('[3] 审批未开启，直接入库（不走审批）')

r = _mysql("SELECT CONCAT(move_type,'/',ref_type,'/',quantity,'/',remark) FROM stock_movements "
           "WHERE ref_type='purchase' AND ref_id=%s ORDER BY id" % pid)
moves = r.splitlines() if r else []
print('[4] 库存流水: %s' % (moves if moves else '（无）'))
assert moves, '审批/库存链路异常：无流水'

# 清理（物理删单 + 流水）
hard_del(pno, pid)
print('[5] 已物理清理 %s（单+流水）' % pno)
print('REGRESS PASS OK  approval_on=%s' % approval_on)
