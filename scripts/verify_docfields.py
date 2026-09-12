# -*- coding: utf-8 -*-
"""单据能力中心（采购单一期）—— 接口链路验证（一次性脚本，可重复跑）
链路: 字段定义CRUD → 传统识别 → AI识别(可选) → 新建单带ext → 导入(幂等) → 导出 → 清理+恢复
只依赖 requests + 标准库（xlsx 用 zipfile 手工构造，避免 openpyxl 安装依赖）
"""
import io
import json
import re
import subprocess
import zipfile
import requests

BASE = 'http://localhost:8080/api'
USERNAME = 'admin'
PASSWORD = 'admin123'


def _login():
    r = requests.post(BASE + '/auth/login', json={'username': USERNAME, 'password': PASSWORD}, timeout=15)
    d = r.json()
    if not d.get('success'):
        raise RuntimeError('登录失败: %s' % d)
    return d['data']['token']


TOKEN = _login()
H = {'Authorization': 'Bearer ' + TOKEN}


# ---------- 最小 xlsx 构造 / 读取（zipfile，POI 可读） ----------
def _colname(i):
    s = ''
    i += 1
    while i:
        i, r = divmod(i - 1, 26)
        s = chr(65 + r) + s
    return s


def make_xlsx(headers, rows):
    """表头 + 数据行 → xlsx bytes（inlineStr，POI WorkbookFactory 可读）"""
    xml = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
           '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>']
    for rn, vals in [(1, headers)] + [(i + 2, r) for i, r in enumerate(rows)]:
        cs = []
        for ci, v in enumerate(vals):
            v = '' if v is None else str(v)
            v = v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            cs.append('<c r="%s%s" t="inlineStr"><is><t>%s</t></is></c>' % (_colname(ci), rn, v))
        xml.append('<row r="%d">%s</row>' % (rn, ''.join(cs)))
    xml.append('</sheetData></worksheet>')
    parts = {
        '[Content_Types].xml': ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            '</Types>'),
        '_rels/.rels': ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '</Relationships>'),
        'xl/workbook.xml': ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            '<sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>'),
        'xl/_rels/workbook.xml.rels': ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
            '</Relationships>'),
        'xl/worksheets/sheet1.xml': '\n'.join(xml),
    }
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, data in parts.items():
            z.writestr(name, data.encode('utf-8'))
    return buf.getvalue()


def xlsx_texts(blob):
    """xlsx bytes → 所有单元格文本（展平，含表头）"""
    with zipfile.ZipFile(io.BytesIO(blob)) as z:
        xml = z.read('xl/worksheets/sheet1.xml').decode('utf-8')
    return re.findall(r'<t(?: xml:space="preserve")?>(.*?)</t>', xml, re.S)


# ---------- HTTP ----------
def req(method, path, body=None):
    r = requests.request(method, BASE + path, json=body, headers=H, timeout=30)
    d = r.json()
    if not d.get('success'):
        raise RuntimeError('%s %s -> %s' % (method, path, d))
    return d


def upload(path, file_bytes, filename, fields=None):
    files = {'file': (filename, io.BytesIO(file_bytes))}
    r = requests.post(BASE + path, files=files, data=fields or {}, headers=H, timeout=90)
    return r.json()


def raw(path, params=None):
    return requests.get(BASE + path, params=params, headers=H, timeout=30)


# ---------- 物理清理（逻辑删除残留占 po_no 唯一键，重传会撞 UNIQUE，必须物理删） ----------
MYSQL_BIN = 'C:/Users/17815/Desktop/jxc/01-ERP/mysql/8.0.28/bin/mysql.exe'
from db_secret import DB_AUTH   # 数据库凭据从 config/db_secret.env 读，密码不进代码


def _mysql(sql):
    subprocess.run([MYSQL_BIN, '-uroot'] + DB_AUTH + ['jxc_erp', '-e', sql],
                   capture_output=True)


def hard_delete_po_like(pat):
    """按 po_no LIKE 物理删除测试采购单（先删明细子表再删主表）"""
    _mysql("DELETE FROM purchase_items WHERE po_id IN "
           "(SELECT id FROM purchase_orders WHERE po_no LIKE '%s'); "
           "DELETE FROM purchase_orders WHERE po_no LIKE '%s';" % (pat, pat))


def hard_delete_po(po):
    """按 po_no 精确物理删除"""
    _mysql("DELETE FROM purchase_items WHERE po_id IN "
           "(SELECT id FROM purchase_orders WHERE po_no='%s'); "
           "DELETE FROM purchase_orders WHERE po_no='%s';" % (po, po))


# ============ 1. 字段定义 CRUD ============
backup = req('GET', '/doc-fields?docType=purchase_order')['data']
fields = [
    {'fieldKey': 'usage', 'fieldName': '用途', 'fieldType': 'text', 'options': [], 'sortOrder': 0},
    {'fieldKey': 'source', 'fieldName': '来源', 'fieldType': 'select', 'options': ['内销', '外销'], 'sortOrder': 1},
    {'fieldKey': 'check_date', 'fieldName': '检查日期', 'fieldType': 'date', 'options': [], 'sortOrder': 2},
    {'fieldKey': 'qc_qty', 'fieldName': '抽检数', 'fieldType': 'number', 'options': [], 'sortOrder': 3},
]
req('POST', '/doc-fields', {'docType': 'purchase_order', 'fields': fields})
lst = req('GET', '/doc-fields?docType=purchase_order')['data']
keys = [f['fieldKey'] for f in lst]
assert keys == ['usage', 'source', 'check_date', 'qc_qty'], keys
print('[1] 字段定义 CRUD OK  keys=%s' % keys)

# ============ 2. 传统识别（通道一，默认） ============
tpl_hdr = ['采购单号', '日期', '供应商名称', '物料名称', '规格', '单位', '数量', '单价', '用途', '来源', '检查日期']
tpl_rows = [
    ['', '2026-08-01', '测试供应商', '测试物料', '3mm', '张', '1', '100', '常规', '内销', '2026-08-01'],
    ['', '2026-08-02', '测试供应商', '测试物料', '3mm', '张', '1', '100', '急件', '内销', '2026-08-02'],
    ['', '2026-08-03', '测试供应商', '测试物料', '3mm', '张', '1', '100', '备件', '外销', '2026-08-03'],
]
buf = make_xlsx(tpl_hdr, tpl_rows)
r = upload('/doc-fields/recognize/excel', buf, 'tpl.xlsx', {'docType': 'purchase_order'})
rec = r['data']['fields']


def byname(n):
    return next((x for x in rec if x['fieldName'] == n), None)


usage = byname('用途')
source = byname('来源')
check = byname('检查日期')
assert usage and usage['fieldType'] == 'text', '用途 type 错误: %s' % rec
assert source and source['fieldType'] == 'select', '来源 type 错误: %s' % rec
assert set(source['options']) == {'内销', '外销'}, '来源 options 错误: %s' % source
assert check and check['fieldType'] == 'date', '检查日期 type 错误: %s' % rec
assert byname('数量') is None, '标准列被识别为动态字段: %s' % rec
print('[2] 传统识别 OK  自定义列=%s' % [f['fieldName'] for f in rec])

# ============ 3. AI 识别（通道二，可选） ============
try:
    r = upload('/doc-fields/recognize/ai', buf, 'tpl.xlsx',
               {'docType': 'purchase_order', 'inputMode': 'excel'})
    aif = r['data']['fields']
    assert aif, 'AI 未识别出字段'
    print('[3] AI识别(Excel) OK  %s' % [f['fieldName'] for f in aif])
except Exception as e:
    print('[3] AI识别(Excel) 跳过: %s' % e)

# ============ 4. 新建采购单带 ext ============
sup = req('GET', '/suppliers?page=1&size=1')['data']['items'][0]
mat = req('GET', '/materials?page=1&size=1')['data']['items'][0]
whs = req('GET', '/warehouses?size=1')['data']['items']
wid = whs[0]['id']
po = req('POST', '/purchases', {
    'supplierId': sup['id'], 'warehouseId': wid, 'poDate': '2026-08-04', 'handler': 'VFY',
    'items': [{'materialId': mat['id'], 'materialName': mat['name'], 'spec': mat.get('spec') or '',
               'unit': mat.get('unit') or '', 'assemblySystem': '', 'quantity': 1, 'unitPrice': 10}],
    'ext': {'usage': 'VFY-常规', 'source': '内销', 'check_date': '2026-08-05'},
})
poId = po['data']['id']
poNo = po['data']['poNo']
det = req('GET', '/purchases/%s' % poId)['data']
ext = json.loads(det['main'].get('ext_json') or '{}')
assert ext.get('usage') == 'VFY-常规' and ext.get('source') == '内销', 'ext_json 未落库: %s' % ext
print('[4] 新建采购单带ext OK  poNo=%s ext_json=%s' % (poNo, ext))

# ============ 5. 导入（模板 → 上传 → 幂等重传） ============
hard_delete_po_like('VFYIM-%')  # 清理上次验证残留（逻辑删占 po_no 唯一键）
tpl = raw('/purchases/import-template')
assert tpl.status_code == 200 and tpl.content[:2] == b'PK', '模板下载失败'
headers = xlsx_texts(tpl.content)
assert '采购单号' in headers and '用途' in headers, '模板缺动态列: %s' % headers


def add(po, qty, usage='', source=''):
    vals = [''] * len(headers)
    m = {h: i for i, h in enumerate(headers)}
    if po:
        vals[m['采购单号']] = po
    vals[m['日期']] = '2026-08-04'
    vals[m['供应商名称']] = sup['name']
    vals[m['物料名称']] = mat['name']
    if '规格' in m:
        vals[m['规格']] = mat.get('spec') or ''
    if '单位' in m:
        vals[m['单位']] = mat.get('unit') or ''
    vals[m['数量']] = qty
    if '单价' in m:
        vals[m['单价']] = '10'
    if '用途' in m:
        vals[m['用途']] = usage
    if '来源' in m:
        vals[m['来源']] = source
    rows_imp.append(vals)


rows_imp = []
add('VFYIM-001', '2', 'VFY-导入', '内销')
add('VFYIM-001', '3', 'VFY-导入', '外销')
add('VFYIM-002', '5', '', '内销')
buf2 = make_xlsx(headers, rows_imp)
d1 = upload('/purchases/import', buf2, 'imp.xlsx')['data']
assert d1['created'] == 2, '导入建单数错误: %s' % d1
print('[5] 导入 OK  created=%s skipped=%s failCount=%s' % (d1['created'], d1['skipped'], d1['failCount']))
d2 = upload('/purchases/import', buf2, 'imp.xlsx')['data']
assert d2['skipped'] == 3, '幂等跳过数错误: %s' % d2
print('[5] 导入幂等 OK  skipped=%s' % d2['skipped'])

# ============ 6. 导出 ============
ex = raw('/purchases/export', {'keyword': 'VFYIM'})
assert ex.status_code == 200 and ex.content[:2] == b'PK', '导出失败'
eh = xlsx_texts(ex.content)
assert '用途' in eh and '来源' in eh, '导出缺动态列: %s' % eh
print('[6] 导出 OK  动态列=%s' % [h for h in ('用途', '来源', '检查日期', '抽检数') if h in eh])

# ============ 7. 清理 + 恢复字段定义 ============
try:
    hard_delete_po(poNo)
    print('  已物理删除步骤4单 %s' % poNo)
except Exception as e:
    print('  cleanup 步骤4单跳过:', e)
hard_delete_po_like('VFYIM-%')
req('POST', '/doc-fields', {'docType': 'purchase_order', 'fields': backup})
print('[7] 已清理测试数据并恢复字段定义(%d条)' % len(backup))
print('ALL PASS OK')
