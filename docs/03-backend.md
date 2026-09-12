# 后端文档（Spring Boot + MyBatis-Plus）

## 1. 类清单（backend/src/main/java/com/jxc/erp/）

### Controller（REST 入口，统一前缀 /api）
| 类 | 职责 | 接口数 |
|---|---|---|
| AuthController | 登录/登出/当前用户 | 3 |
| DashboardController | 仪表盘综合数据 | 1 |
| CustomerController / SupplierController / MaterialController / WarehouseController | 基础资料 CRUD | 各 2-3 |
| OrderController | 客户订单（销售下单） | 2 |
| DeliveryController | 送货单（出库+扣库存+回写订单） | 3 |
| PurchaseController | 采购入库（加库存+流水） | 2 |
| PurchaseReturnController | 采购退货（扣库存，删=回补） | 2 |
| BomController | BOM 配方（保存=先清后插） | 3 |
| StockController | 库存查询（按仓筛选）/流水 | 2 |
| WorkController | 报工全套（基础资料/打卡/统计） | 29 |
| FinanceController | 应收应付汇总/核销/发票 | 13 |
| VoucherController | 收付款单 | 6 |
| ReportController | 月度销售/对账/利润/销售查询 | 5 |
| PhotoController | 拍照入库（识别/确认） | 2 |
| AiController | AI 报价聊天 | 1 |
| BackupController | 数据备份（mysqldump） | 2 |
| UserController | 用户管理（4 角色） | 2 |

### Mapper（MyBatis 注解 SQL）
- 各业务模块一个 Mapper（如 WorkMapper/TradeMapper/SysMapper/StockMapper/...），全部注解 SQL + `<script>` 动态 SQL，无 XML
- `WarehouseMapper` 等新模块用 MyBatis-Plus `BaseMapper`（LambdaQueryWrapper）

### 其他
- `ApiResponse`：统一返回包装（ok/fail）
- `PageResult`：分页结果包装
- `MybatisPlusConfig`：手动 SqlSessionFactory + 分页插件 + SQL 日志
- `GlobalExceptionHandler`：未捕获异常统一处理（含 multipart 超限）
- `JxcErpApplication`：启动类

## 2. 核心业务逻辑

### 2.1 库存流水（TradeMapper.movementInsert）
所有库存变动写 `stock_movements`：
```
movementInsert(materialId, warehouseId, moveType, refType, refId, qty, before, after, price, amt, date, remark)
```
- `before_stock`/`after_stock` 为变动前后快照（currentStock 子查询）
- 单据删除 → `movementsDeleteByRef(refType, refId)` → 流水删除 = 库存自动回补
- 仓库：`movementInsertWh` 带 warehouseId（采购入库选仓）；普通出库用仓 1

### 2.2 报工（WorkController，详见 docs/work-punch-design.md）
- 状态机：`in_progress`(数量0) → `completed`；取消=删行；同员工同时仅 1 条进行中
- 时长：`calcWorkDuration` 扣除午休 12:00-13:00、晚餐 17:30-18:00
- 扣料：`deductMaterials`（打卡 finish 和补录共用）——process_materials × 数量 → movements
- 照片：`saveImages`（保存文件+写表+同步 image_count 列）、`deleteImageFiles`（删除/取消时清理文件，旧路径 /uploads/2026-xx/ 不删）
- 列表：返回 image_count / first_image / image_paths（GROUP_CONCAT）

### 2.3 单据状态机
- **客户订单**：pending（未出）→ partial（部分出）→ completed（出完）；送货单按订单出库时回写
- **送货单**：创建即扣库存（按订单出货校验不超量）；删除回补
- **采购单**：创建即加库存+流水；删除回扣
- **采购退货**：创建扣库存；删除回补
- **财务**：settlements 核销（可撤销）、invoices 发票登记、receipt/payment_vouchers 收付款单

### 2.4 拍照入库（PhotoController）
1. `POST /api/photo/recognize`：base64 图片 → DashScope qwen3-vl-plus 结构化识别（供应商名匹配 + items 明细）
2. `POST /api/photo/confirm`：核对后的数据 → 自动补齐规格/自动新建物料（materialId 为空时）+ 生成采购单 + 加库存流水
- 注意：供应商未匹配时 supplierId 为 null（前端提示手动选），**不可用 Map.of() 传 null 值**（NPE 教训）

### 2.5 工资计算
`processes.unit_price` × `work_reports.quantity`（列表 wage 列、统计工资表）

## 3. 已知坑（改代码必读）

1. **`SELECT wr.*` + 子查询别名同名列冲突**：work_reports 有 image_count 列，列表查询再加子查询 `AS image_count` 会重复列名，MyBatis 取到表列值（恒 0）。带别名的列表查询必须显式列名
2. **Map.of() 不允许 null 值**：`Map.of("supplierId", null)` 直接 NPE，用 HashMap
3. **mvnw 不可用**：用 C:\maven\apache-maven-3.9.9（wrapper 下载被墙）
4. **Git Bash 下 taskkill**：必须 `MSYS_NO_PATHCONV=1 taskkill /PID <pid> /F`（bash 包装 PID 无效，先 netstat 找真实 PID）
5. **curl 发中文 JSON**：编码问题，用 `printf` 写 UTF-8 文件再 `-d @file`
6. **MyBatis `<if>` 空格**：动态 SQL 拼接注意空格（`AND wr.report_date = #{date}` 前导空格）
7. **ONLY_FULL_GROUP_BY**：聚合查询 GROUP BY 需包含功能依赖列
8. **multipart 上传**：默认 1MB 限制，已调 15MB/30MB；同名多文件用 `List<MultipartFile>`
9. **时间字段**：start_time/end_time 为 varchar(32)，格式 `YYYY-MM-DD HH:mm:ss`，前端传 `reportDate + ' ' + HH:mm:ss`
10. **文件路径**：保存用 `I:/erp-server/web/public/...` 绝对路径（写死），迁移服务器需同步改
