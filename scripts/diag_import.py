# -*- coding: utf-8 -*-
"""诊断导入接口失败：下载模板 → 填数据 → 上传 → 打印完整响应"""
import io
import re
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


def xlsx_texts(blob):
    with zipfile.ZipFile(io.BytesIO(blob)) as z:
        xml = z.read('xl/worksheets/sheet1.xml').decode('utf-8')
    return re.findall(r'<t(?: xml:space="preserve")?>(.*?)</t>', xml, re.S)


# 模板
r = requests.get(BASE + '/purchases/import-template', headers=H, timeout=30)
print('模板 STATUS', r.status_code, 'HEAD', r.content[:4])
headers = xlsx_texts(r.content)
print('模板列:', headers)

# 供应商/物料
sup = requests.get(BASE + '/suppliers?page=1&size=1', headers=H, timeout=30).json()['data']['items'][0]
mat = requests.get(BASE + '/materials?page=1&size=1', headers=H, timeout=30).json()['data']['items'][0]
print('供应商:', sup['name'], '物料:', mat['name'], mat.get('spec'), mat.get('unit'))

m = {h: i for i, h in enumerate(headers)}
print('m 索引:', m)


def add(po, qty, usage='', source=''):
    vals = [''] * len(headers)
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
    return vals


rows = []
rows.append(add('VFYIM-001', '2', 'VFY-导入', '内销'))
rows.append(add('VFYIM-001', '3', 'VFY-导入', '外销'))
rows.append(add('VFYIM-002', '5', '', '内销'))
buf2 = make_xlsx(headers, rows)

files = {'file': ('imp.xlsx', io.BytesIO(buf2))}
r = requests.post(BASE + '/purchases/import', files=files, headers=H, timeout=90)
print('导入 STATUS', r.status_code)
print(r.text[:1500])
