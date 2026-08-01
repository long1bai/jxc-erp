# 数据库文档（MySQL 8，库名 yawei_erp）

- 连接：`127.0.0.1:3306`，root 无密码（内网）
- 字符集：UTF-8（连接 URL 已带 characterEncoding=utf8）
- 初始 schema：`db/init.sql`（建表语句）；历史迁移：旧 Python 版 SQLite → MySQL 全量搬库

## 1. 表清单（27 张，含当前行数）

### 基础资料
| 表 | 行数 | 说明 |
|---|---|---|
| customers | 321 | 客户 |
| suppliers | 340 | 供应商 |
| materials | 4757 | 物料/产品（code 料号唯一；含 CP-00001~00587 成品） |
| warehouses | 1 | 仓库（新增后采购入库可选仓） |
| users | 35 | 登录账号（role: employee/admin/boss/dev） |
| sequences | 8 | 单据编号序列 |

### 进销存
| 表 | 行数 | 说明 |
|---|---|---|
| customer_orders | 3398 | 客户订单主表 |
| customer_order_items | 5235 | 订单明细 |
| delivery_notes | 3385 | 送货单主表 |
| delivery_items | 5213 | 送货明细 |
| purchase_orders | 392 | 采购单主表（含 po_order_id 关联列，回填采购订单分批入库来源） |
| purchase_items | 892 | 采购明细 |
| purchase_returns / purchase_return_items | 0 / 0 | 采购退货 |
| po_orders / po_order_items | 0 / 0 | 独立采购订单（订单→分批入库→执行跟踪，2026-08-01 新增） |
| bom_items | 21 | BOM 配方（成品→组件） |

### 报工
| 表 | 行数 | 说明 |
|---|---|---|
| work_reports | 1173 | 报工记录（status: in_progress/completed） |
| work_report_images | 1329 | 报工照片（含旧版 /uploads/2026-xx/ 路径记录） |
| work_employees | 35 | 报工员工（车间名单，独立于 users） |
| work_groups | 6 | 分组（裁线/端子/组装/后焊/太阳能线材等） |
| work_group_processes | 25 | 工序分组关联 |
| processes | 6 | 工序（unit_price 计件单价） |
| process_materials | 0 | 工序扣料配置 |

### 库存/财务
| 表 | 行数 | 说明 |
|---|---|---|
| stock_movements | 3851 | 库存流水（in/out、ref_type/ref_id、before/after 快照） |
| settlements | 0 | 核销记录 |
| invoices | 0 | 发票登记 |
| receipt_vouchers / payment_vouchers | 0 / 0 | 收/付款单 |

## 2. 主键策略（雪花）

- **全部 27 张表主键为 15 位时间戳型雪花**：`毫秒时间戳 × 100 + 2 位序列`（≈1.78e14，**低于 JS 精度上限 2^53≈9e15**，前端零改造）
- 生成方式双轨（`db/snowflake_migration.sql`）：
  - **MySQL 触发器兜底**：每表 BEFORE INSERT 触发器 `SET NEW.id = IFNULL(NEW.id, sfid())`——手动 SQL 不带 id 时自动生成
  - **Java 显式生成**：`mapper.nextId()`（`SELECT sfid()` 同源同序列）——Controller 创建单据/报工时预生成 id 显式插入，保证返回的 id 与 DB 一致
- **已修复的坑**：触发器场景下 `LAST_INSERT_ID()` 返回旧自增值（不可靠）→ 全部改为单号回查（`lastOrderId(coNo)` 等）或 Java 预生成；触发器 v1 无条件覆盖 `NEW.id` 导致 Java 显式 id 失效 → v2 改 `IFNULL` 仅空值生成
- 存量自增 id（1,2,3...）保留不动，与新雪花 id 无冲突（雪花远大于自增）

### stock_movements（库存流水，最核心）
```
id, material_id, warehouse_id(默认1), move_type(in/out), ref_type(purchase/delivery/work_report/...),
ref_id, quantity, before_stock, after_stock, unit_price, amount, move_date, remark, created_at
```
- 删除单据 → 按 ref_type+ref_id 删流水 = 库存自动回补
- 备注约定：`采购入库#CGDD-xxx`、`送货出库#XSDD-xxx`、`报工扣料#员工-工序`

### work_reports（报工记录）
```
id, employee_id, employee_name, group_id, group_name, process_id, process_name,
quantity(数量), start_time, end_time(varchar "YYYY-MM-DD HH:mm:ss"), duration(varchar "X小时Y分钟"),
remark, image_count(冗余列,随图片保存同步), status(in_progress/completed), report_date,
material_id, material_name, created_at, updated_at
```

### work_report_images（报工照片）
```
id, report_id(FK→work_reports ON DELETE CASCADE), image_path(/uploads/work/...), sort_order
```

### customer_orders（订单）
```
id, order_no(XSDD-...), customer_id, customer_name, order_date, total_amount,
status(pending/partial/completed), remark, ...
```

### delivery_notes（送货单）
```
id, delivery_no(SHDD-...), customer_id, customer_name, delivery_date, total_amount,
order_id(关联订单,空=临时出货), remark, ...
```

## 3. 表关系要点

```
customers/suppliers/materials/warehouses ──┐
                                           ├──> customer_orders ──> delivery_notes（order_id 关联）
                                           ├──> purchase_orders ──> stock_movements（ref）
                                           └──> bom_items（product_id 成品）
work_groups ──> work_employees（group_id）
work_group_processes ──> processes（分组限定工序）
processes ──> process_materials（material_id）──> 报工扣料
work_reports ──> work_report_images（report_id, CASCADE）
stock_movements.ref_type+ref_id ──> 各单据（逻辑关联，无物理外键）
```

## 4. 备份与恢复

```bash
# 手动备份（系统页"数据备份"按钮 = 执行这个）
mysqldump -uroot yawei_erp > backup/yawei_erp_YYYYMMDD_HHMMSS.sql

# 恢复
mysql -uroot yawei_erp < backup/xxx.sql

# 或全库导出（含建库）
mysqldump -uroot --databases yawei_erp > backup/full.sql
```

备份文件在 `I:\yawei-erp-java\backup\`（BackupController 调用 mysqldump，需 mysqldump 在 PATH 或配置路径）。

## 5. 数据注意

- 报工照片文件在 `web/public/uploads/`（work/ 为新拍，其余为旧版迁移 197MB/1848 文件），**删记录时文件自动清理（仅 work/ 下）**，旧路径文件不动
- **图片文件随报工/入库持续增长（每月约 100-300 张）**：备份时带上 uploads 目录；已核对过的旧照片可定期归档压缩（移出 web/public 即可）；孤儿文件（记录已删但文件残留的早期数据）可手工清理
- 历史 image_count 列已回填（1144 条），新记录由后端 saveImages 同步
- 删除物料/客户/供应商有引用保护（countRefs 检查）；删除仓库有流水保护

## 6. 表格功能约定（2026-07 全系统统一）

- **分页**：持续增长的主列表（物料/订单/送货/采购/库存/流水/报工记录/退货）已有分页+搜索
- **排序**：主列表与报表的金额/数量/日期/库存列已加 sortable（点列头排序，92 处）
- **高度限制**：仅统计报表/财务多表格/仪表盘卡片/弹窗选择器保留 max-height；主列表一律全显示（用户/员工/BOM/备份已去限制）
