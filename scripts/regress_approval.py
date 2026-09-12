# -*- coding: utf-8 -*-
"""审批分支回归：临时开启采购审批 → 新建单应为 pending（不加库存）→ 批准后补库存流水 → 恢复开关"""
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
    d = requests.post(BASE + '/auth/login', json={'username': 'admin', 'password': 'admin123'}, timeout=15).json()
    assert d.get('success'), d
    return d['data']['token']


H = {'Authorization': 'Bearer ' + login()}


def req(method, path, body=None):
    r = requests.request(method, BASE + path, json=body, headers=H, timeout=30)
    d = r.json()
    assert d.get('success'), '%s %s -> %s' % (method, path, d)
    return d


def hard_del(po_like, ref_id=None):
    sql = ("DELETE FROM purchase_items WHERE po_id IN (SELECT id FROM purchase_orders WHERE po_no LIKE '%s'); "
           "DELETE FROM purchase_orders WHERE po_no LIKE '%s';") % (po_like, po_like)
    if ref_id:
        sql += " DELETE FROM stock_movements WHERE ref_type='purchase' AND ref_id=%s;" % ref_id
    _mysql(sql)


hard_del('VFYAP-%')  # 清理上轮失败残留
try:
    # 1. 记录当前开关，打开采购审批
    before = req('GET', '/approvals/config')['data']
    req('POST', '/approvals/config', {'purchase': True, 'delivery': bool(before.get('delivery'))})
    print('审批已开启 purchase=True')

    # 2. 新建单 → 应为 pending
    sup = req('GET', '/suppliers?page=1&size=1')['data']['items'][0]
    mat = req('GET', '/materials?page=1&size=1')['data']['items'][0]
    wid = req('GET', '/warehouses?size=1')['data']['items'][0]['id']
    po = req('POST', '/purchases', {
        'supplierId': sup['id'], 'warehouseId': wid, 'poDate': '2026-08-05', 'handler': 'VFYAP',
        'items': [{'materialId': mat['id'], 'materialName': mat['name'], 'spec': mat.get('spec') or '',
                   'unit': mat.get('unit') or '', 'assemblySystem': '', 'quantity': 3, 'unitPrice': 10}],
    })
    pid = po['data']['id']
    pno = po['data']['poNo']
    assert po['data']['pendingApproval'] is True, po
    print('[1] 审批开启时新建 OK  poNo=%s pendingApproval=True' % pno)

    # 3. 此时不应有库存流水
    m0 = _mysql("SELECT COUNT(*) FROM stock_movements WHERE ref_type='purchase' AND ref_id=%s" % pid)
    assert m0 and m0.splitlines()[-1] == '0', 'pending 单不应加库存: %s' % m0
    print('[2] pending 未加库存 OK')

    # 4. 待审批列表应含该单（data.purchases）
    pend = req('GET', '/approvals/pending')['data']['purchases']
    poNos = [str(x.get('po_no')) for x in pend]
    assert pno in poNos, 'pending 列表缺该单: %s' % poNos
    print('[3] 待审批列表含该单 OK  %s' % pno)

    # 5. 批准 → 补库存流水 + 状态 approved
    req('POST', '/approvals/purchase/%s/approve' % pid)
    m1 = _mysql("SELECT CONCAT(move_type,'/',quantity,'/',remark) FROM stock_movements "
                "WHERE ref_type='purchase' AND ref_id=%s" % pid)
    rows = m1.splitlines()[1:] if m1 else []
    assert any('in' in r for r in rows), '批准后无库存流水: %s' % m1
    st = _mysql("SELECT approve_status FROM purchase_orders WHERE id=%s AND deleted=0" % pid)
    assert 'approved' in st, '状态非 approved: %s' % st
    print('[4] 批准后补库存 OK  流水=%s' % rows)

    # 6. 清理
    hard_del(pno, pid)
    print('[5] 已物理清理 %s' % pno)
    print('REGRESS APPROVAL PASS OK')
finally:
    # 7. 恢复原开关
    req('POST', '/approvals/config', before)
    print('审批开关已恢复 %s' % before)
