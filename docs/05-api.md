# API 文档（全部接口）

- Base URL：`http://<host>:8080/api`
- 认证：`POST /auth/login` 拿 token → 后续请求头 `Authorization: Bearer <token>`
- 统一响应：`{"success": true, "data": ...}` / `{"success": false, "error": "..."}`
- 分页响应：`data = {items: [], total, page, size}`
- 上传：multipart/form-data，单文件 ≤15MB、请求 ≤30MB

## 1. 认证 Auth

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /auth/login | 登录：body `{username, password}` → data.token |
| GET | /auth/me | 当前用户信息 |
| POST | /auth/logout | 登出 |

## 2. 基础资料 Base

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /customers /suppliers /materials /warehouses | 列表：`?keyword=&page=&size=`（warehouses 支持 `?size=100` 全量） |
| POST | /customers /suppliers /materials /warehouses | 新增 |
| PUT | /{模块}/{id} | 修改 |
| DELETE | /{模块}/{id} | 删除（物料有引用会拒绝；仓库有流水会拒绝） |
| GET | /materials?keyword= | 料号/名称搜索（报工/单据选物料用） |

## 3. 客户订单 Orders

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /orders | 列表：`?keyword=&status=&page=&size=` |
| POST | /orders | 新建：`{customerId, orderDate, remark, items:[{materialId, quantity, price}]}` |
| GET | /orders/{id} | 详情（含明细） |
| DELETE | /orders/{id} | 删除（已出货会拒绝） |

## 4. 送货单 Deliveries（销售出库）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /deliveries | 列表：`?keyword=&date=&page=&size=` |
| POST | /deliveries | 新建：`{customerId, deliveryDate, orderId?, remark, items:[{materialId, quantity, price, orderItemId?}]}`；orderId 有=按订单出货（校验不超量），无=临时出货；自动扣库存+BOM扣组件+回写订单 |
| GET | /deliveries/{id} | 详情 |
| GET | /deliveries/open-orders | 可出货的订单列表（按订单出货下拉） |
| DELETE | /deliveries/{id} | 删除（库存自动回补） |

## 5. 采购 Purchase

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /purchases | 列表 |
| POST | /purchases | 新建：`{supplierId, poDate, remark, warehouseId?, items:[{materialId, quantity, price}]}`；自动加库存+流水（warehouseId 空=1号仓） |
| GET | /purchases/{id} | 详情 |
| DELETE | /purchases/{id} | 删除（库存回扣） |
| GET | /purchase-returns | 采购退货列表 |
| POST | /purchase-returns | 新建（扣库存） |

**采购报表 PurchaseStats（10 个接口，2026-07 新增，象过河/金蝶标准维度）**：
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /purchase-stats/by-supplier | 采购统计（按供应商：单数/数量/金额/占比），参数 from/to 日期范围 |
| GET | /purchase-stats/by-material | 采购统计（按商品：数量/金额/均价） |
| GET | /purchase-stats/by-warehouse | 采购统计（按仓库） |
| GET | /purchase-stats/detail | 采购明细查询（kw 搜单号/供应商/商品，全量 LIMIT 5000 前端分页） |
| GET | /purchase-stats/monthly | 采购月度汇总（按月：单数/数量/金额） |
| GET | /purchase-stats/material-monthly | 采购商品月度分析（商品×月） |
| GET | /purchase-stats/supplier-monthly | 供应商供货月度分析（供应商×月） |
| GET | /purchase-stats/price-trend | 采购价格趋势（商品×月均价） |
| GET | /purchase-stats/returns/by-supplier | 采购退货统计（按供应商） |
| GET | /purchase-stats/returns/by-material | 采购退货统计（按商品） |

**销售退货 SalesReturn（5 个接口，2026-07 新增）**：
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /sales-returns | 列表（kw 搜单号/客户，分页） |
| GET | /sales-returns/{id} | 详情（主单+明细） |
| POST | /sales-returns | 新建：`{customerId, returnDate, remark, items:[{materialId, quantity, price}]}`；自动加库存（流水 in）+ 单号 XSTH |
| DELETE | /sales-returns/{id} | 删除（逻辑删，库存自动回退） |

**销售报表 SalesStats（10 个接口，2026-07 新增，象过河/金蝶标准维度）**：
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /sales-stats/by-customer | 销售统计（按客户：单数/数量/金额/占比） |
| GET | /sales-stats/by-material | 销售统计（按商品：数量/金额/毛利） |
| GET | /sales-stats/by-warehouse | 销售统计（按仓库） |
| GET | /sales-stats/gross-profit | 毛利汇总（按客户：销售/成本/毛利/毛利率） |
| GET | /sales-stats/detail | 销售明细查询（kw 搜单号/客户/商品，LIMIT 5000） |
| GET | /sales-stats/monthly | 销售月度汇总（含退货） |
| GET | /sales-stats/customer-monthly | 销售月度分析（按客户×月） |
| GET | /sales-stats/material-monthly | 销售月度分析（按商品×月） |
| GET | /sales-stats/returns/by-customer | 销售退货统计（按客户） |
| GET | /sales-stats/returns/by-material | 销售退货统计（按商品） |
| DELETE | /purchase-returns/{id} | 删除（库存回补） |

## 6. 库存 Stock

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /stock/inventory | 当前库存：`?keyword=&page=&size=&warehouseId=`（按仓筛选，空=全部汇总） |
| GET | /stock/movements | 库存流水：`?materialId=&page=&size=` |

## 7. 报工 Work（29 个接口）

### 基础资料
| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | /work/groups | 分组 CRUD |
| GET/POST/PUT/DELETE | /work/employees | 员工 CRUD（`{name, groupId, ...}`） |
| GET/POST/PUT/DELETE | /work/processes | 工序 CRUD（`{name, unitPrice, groupId, ...}`） |
| GET/POST/DELETE | /work/process-materials | 工序扣料配置（`{processId, materialId, quantityPerUnit}`） |

### 打卡报工
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /work/reports/start | **multipart**：`employeeId, processId, startTime?(YYYY-MM-DD HH:mm:ss), remark?, materialId?, materialName?, images?(≤3张)`；重复开始被拒；返回 id |
| POST | /work/reports/{id}/finish | body `{quantity, endTime?}`；自动算时长(扣午休/晚餐)+扣料 |
| POST | /work/reports/{id}/cancel | 取消（删 in_progress 行+删照片文件） |
| GET | /work/reports/in-progress?employeeId= | 进行中报工（恢复状态用） |
| GET | /work/reports/my?employeeId=&date= | 某人某天已完成 |
| GET | /work/reports/last-process?employeeId= | 上次工序（预选用） |

### 记录/补录/删除
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /work/reports | 列表：`?date=&groupId=&employeeId=&keyword=&page=&size=`；返回 image_count/first_image/image_paths |
| POST | /work/reports | **multipart** 补录：`employeeId, processId, quantity, startTime?, endTime?, reportDate?, remark?, images?`；自动算时长+扣料 |
| DELETE | /work/reports/{id} | 删除（库存流水回补+删照片文件） |

### 统计
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /work/stats/daily?date= | 日报（每人数量/工资汇总） |
| GET | /work/stats/monthly?month=YYYY-MM | 月报 |
| GET | /work/stats-data | 统计报告（区间：?start=&end=&groupId=） |
| GET | /work/trend | 趋势（前后半段日均对比） |
| GET | /work/stats/wages | 工资表 |

## 8. 财务 Finance / Vouchers

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /finance/receivable-summary /payable-summary | 应收/应付汇总（总额+分客户） |
| GET | /finance/receivable-monthly /payable-monthly | 分月明细 |
| GET | /finance/aging | 账龄 |
| GET | /finance/doc-list | 单据列表（金蝶式） |
| GET | /finance/settlements | 核销记录 |
| POST | /finance/settle | 核销：`{docType, docId, amount}` |
| POST | /finance/settle/revoke | 撤销核销 |
| GET/POST/DELETE | /finance/invoices | 发票登记（POST body `{customerId, invoiceNo, amount, invoiceDate}`） |
| GET | /finance/invoice-refs | 发票关联单据 |
| GET/POST/DELETE | /vouchers/receipts | 收款单（POST `{customerId, amount, date, remark, settleId?}`） |
| GET/POST/DELETE | /vouchers/payments | 付款单 |

## 9. 报表 Report

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /reports/sales-monthly?month= | 月度销售（**基于送货单**） |
| GET | /reports/sales-detail?month= | 销售明细 |
| GET | /reports/reconciliation?type=sales\|purchase&partyId=&start=&end= | 对账单：partyId **可空**（空=返回全部往来单位汇总：party_name/bill_count/total，点行进明细）；非空=该单位明细（打印用） |
| GET | /reports/profit?month= | 利润分析（销售额-成本） |
| GET | /reports/sales-query | 销售查询（客户/物料维度） |

## 10. 拍照入库 Photo / AI

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /photo/recognize | body `{imageBase64}` → `{supplierName, supplierId(可null), poNo, date, items:[{name, spec?, unit?, quantity, price}]}` |
| POST | /photo/confirm | 核对后确认：`{supplierId, poDate, remark?, warehouseId?, items:[{name, spec?, unit?, quantity, price, materialId?}]}`；自动建物料(缺)+采购单+库存流水 |
| POST | /ai/chat | 报价聊天：`{message, history?}` → AI 回复 |

## 11. 其他

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /dashboard/data | 仪表盘：待处理订单(pending+partial)/库存预警/本月销售/报工汇总等 |
| GET | /bom/{productId} | BOM 配方明细 |
| POST | /bom/{productId} | 保存 BOM（先清后插：`items:[{materialId, quantity}]`） |
| DELETE | /bom/{productId} | 清空 BOM |
| GET | /users | 用户列表（admin/dev；含 workEmployeeId 自动关联的报工员工） |
| POST | /users | 新增用户：`{username, password, displayName, role, workEmployeeId?}`（role: employee/admin/boss/dev；**员工账号自动关联报工员工**：姓名与名单同名→关联；名单无同名→自动创建同名报工员工并关联；管理员/老板不创建） |
| PUT | /users/{id} | 修改（改密/角色/禁用；**改名时同步关联员工姓名**（保持同一记录，报工/工资归属不变）；无关联时按新名匹配，员工角色无同名自动创建） |
| DELETE | /users/{id} | 删除用户（**同步删除关联报工员工**；有报工记录的员工账号拒绝删除） |
| GET | /auth/me | 当前用户（含 workEmployeeId/workEmployeeName） |
| POST | /backup/create | 手动备份（mysqldump → backup/） |
| GET | /backup/list | 备份列表 |

## 12. 常用调用示例

```bash
# 登录拿 token
curl -X POST http://127.0.0.1:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 打卡开工（multipart，含 1 张照片）
curl -X POST http://127.0.0.1:8080/api/work/reports/start \
  -H "Authorization: Bearer $TOKEN" \
  -F "employeeId=1765360746413" -F "processId=1" \
  -F "startTime=2026-07-31 09:00:00" -F "images=@photo.jpg"

# 结束
curl -X POST http://127.0.0.1:8080/api/work/reports/123/finish \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"quantity":300,"endTime":"2026-07-31 11:30:00"}'
```
