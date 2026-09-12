# -*- coding: utf-8 -*-
"""诊断 AI 识别通道失败原因：登录 → 构造 xlsx → 调 recognize/ai → 打印完整响应"""
import io
import zipfile
import requests

BASE = 'http://localhost:8080/api'

r = requests.post(BASE + '/auth/login', json={'username': 'admin', 'password': 'admin123'}, timeout=20)
tok = r.json()['data']['token']
H = {'Authorization': 'Bearer ' + tok}


def _colname(i):
    s = ''
    i += 1
    while i:
        i, rr = divmod(i - 1, 26)
        s = chr(65 + rr) + s
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


hdr = ['采购单号', '日期', '供应商名称', '物料名称', '规格', '单位', '数量', '单价', '用途', '来源', '检查日期']
rows = [
    ['', '2026-08-01', '测试供应商', '测试物料', '3mm', '张', '1', '100', '常规', '内销', '2026-08-01'],
    ['', '2026-08-02', '测试供应商', '测试物料', '3mm', '张', '1', '100', '急件', '内销', '2026-08-02'],
]
buf = make_xlsx(hdr, rows)

files = {'file': ('tpl.xlsx', io.BytesIO(buf))}
data = {'docType': 'purchase_order', 'inputMode': 'excel'}
r = requests.post(BASE + '/doc-fields/recognize/ai', files=files, data=data, headers=H, timeout=90)
print('STATUS', r.status_code)
print('ELAPSED_MILLIS ~', r.elapsed.total_seconds() * 1000)
print(r.text[:2500])
