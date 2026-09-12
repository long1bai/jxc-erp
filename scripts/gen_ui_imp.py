# -*- coding: utf-8 -*-
"""前端导入联调：登录 → 取供应商/物料 → 构造含动态字段的导入 xlsx → 存 scripts/ui_imp.xlsx"""
import io
import zipfile
import requests

BASE = 'http://localhost:8080/api'
tok = requests.post(BASE + '/auth/login',
                    json={'username': 'admin', 'password': 'admin123'}, timeout=15).json()['data']['token']
H = {'Authorization': 'Bearer ' + tok}
sup = requests.get(BASE + '/suppliers?page=1&size=1', headers=H, timeout=15).json()['data']['items'][0]
mat = requests.get(BASE + '/materials?page=1&size=1', headers=H, timeout=15).json()['data']['items'][0]
print('supplier=%s material=%s spec=%r unit=%r' % (sup['name'], mat['name'], mat.get('spec'), mat.get('unit')))


def _colname(i):
    s = ''
    i += 1
    while i:
        i, r = divmod(i - 1, 26)
        s = chr(65 + r) + s
    return s


def make_xlsx(headers, rows):
    xml = ['<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>']
    for rn, vals in [(1, headers)] + [(i + 2, r) for i, r in enumerate(rows)]:
        cs = []
        for ci, v in enumerate(vals):
            v = '' if v is None else str(v)
            v = v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
            cs.append('<c r="%s%s" t="inlineStr"><is><t>%s</t></is></c>' % (_colname(ci), rn, v))
        xml.append('<row r="%d">%s</row>' % (rn, ''.join(cs)))
    xml.append('</sheetData></worksheet>')
    parts = {
        '[Content_Types].xml': ('<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            '</Types>'),
        '_rels/.rels': ('<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '</Relationships>'),
        'xl/workbook.xml': ('<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            '<sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>'),
        'xl/_rels/workbook.xml.rels': ('<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
            '</Relationships>'),
        'xl/worksheets/sheet1.xml': '\n'.join(xml),
    }
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, d in parts.items():
            z.writestr(n, d.encode('utf-8'))
    return buf.getvalue()


headers = ['采购单号', '日期', '供应商名称', '物料名称', '规格', '单位', '数量', '单价', '经手人', '项目名称', '备注',
           '用途', '来源', '检查日期', '抽检数']
spec = mat.get('spec') or ''
unit = mat.get('unit') or ''


def row(po, qty, usage, source, check, qc):
    return [po, '2026-08-05', sup['name'], mat['name'], spec, unit, qty, '10', '', '', '',
            usage, source, check, qc]


rows = [
    row('VFYUI-001', '2', 'UI-导入', '内销', '2026-08-05', '1'),
    row('VFYUI-001', '3', 'UI-导入', '外销', '2026-08-05', '2'),
    row('VFYUI-002', '5', '', '内销', '', ''),
]
blob = make_xlsx(headers, rows)
open('C:/Users/17815/Desktop/jxc/01-ERP/erp-server/scripts/ui_imp.xlsx', 'wb').write(blob)
print('wrote scripts/ui_imp.xlsx rows=%d cols=%d' % (len(rows), len(headers)))
