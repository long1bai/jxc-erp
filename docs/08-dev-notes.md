# 08 开发备忘（规则速查 + 踩坑记录 + 决策历史）

> 本文档是**开发时先读的速查**：业务规则、UI 规范、架构决策、已知坑。
> 与 01-product（业务口径）互补——本文档偏"怎么实现/怎么维护"。
> **改动实现时必须同步本文档**（与 docs 其他篇一致）。

## 1. 物料数据整理规则（建物料/导数据时）

- 参数不同 = 不同物料；顺序不同 = 同一物料，加**别名列**（方便 Ctrl+F 查找）
- 线规统一：10号 = 10AWG = 10#，一律写成统一格式
- 材质区分：红铜 / 锡铜 / 铜包铝 = **不同材质 = 不同物料**；未注明材质默认锡铜
- **特软硅胶线 = 锡铜线**，同一材质（不要另建物料）
- UL 标识的线 = 特制线，需分开建物料
- 红线、黑线 = 不同物料
- "红黑各半"的进货：不是独立产品，数量对半分到红和黑两个物料
- 数据整理不分多 Sheet：**全部放一个 Sheet + 别名列**

## 2. 单据/报表模板规范

- **送货单**：一式两联（客户联 + 存根联）、金额大写、每页 ≥5 行（不足补空行）、专业无 emoji
- **对账单**（象过河样式，客户/供应商通用）：
  - 抬头：示例公司有限公司
  - 地址：（公司地址）；电话 （联系电话）；联系人 （联系人）；经手人 （经手人）
  - 列：序号 / 单据编号 / 日期 / 订单号码 / 商品编码 / 商品名称 / 规格 / 单位 / 数量 / 含税单价 / 金额
  - 底部："5 日内回传"
- **报表数据源**：月度销售基于 delivery_notes + delivery_items（送货单），不是 customer_orders
- **对账 UI**：搜索式下拉框（保持 select 外观 + 覆盖搜索输入框）

## 3. 报工统计口径与 UI 偏好

- 统计报告：极简无筛选，默认打开**本月**数据
- **日均产量 = 总数量 ÷ 实际有报工天数**（不是日历天数）
- **趋势列**：前后半段日均对比，↑+% / ↓-% / →%；至少 4 天才有意义
- 每人可点"明细"和"趋势图"按钮
- 报工 UI：白底卡片、btn-sm 不撑满、开工前可编辑 → 开工后只读摘要、计时排除午休 12:00-13:00 晚餐 17:30-18:00、开始/结束并排 col-6、手机紧凑

## 4. UI 表格规范（全系统统一，2026-07）

- **主列表**：分页 + 金额/数量/日期列排序（sortable）；数据少全显示；分析须考虑未来数据增长
- 仅统计/布局型表格用 max-height（仪表盘卡片、报表、财务多表格、弹窗选择器）
- 弹窗用 `destroy-on-close`（防表单残留——"第一次打开有内容第二次没有"）
- **复用组件**（web/src/components/）：
  - `PaginatedTable`：前端分页表格（:items / show-index / empty-text / pageSizes），数据量小全量加载本地翻页
  - `PageHeader`：卡片头（title + #icon slot + 右侧操作区），21 页已用
- 新页面一律用这两个组件，禁止复制粘贴旧结构

## 5. 用户管理与报工员工：页面拆分、数据联动（2026-07 决策）

- **报工设置-员工** = 车间工人档案（姓名/工号/分组/电话），**无账号员工可独立存在**（管理员代打卡场景）
- **系统-用户管理** = 登录账号（用户名/密码/角色/禁用），仅显示关联员工，不编辑员工档案
- **双向自动联动**：
  - 正向：新增**员工账号** → 按姓名同名自动关联报工员工；名单无同名 → 自动创建员工并关联（管理员/老板不创建）
  - 反向：报工设置**新增员工** → 姓名与账号同名 → 自动绑定（仅未绑定账号时，linkEmployeeByName）
  - 改名：账号改名同步员工姓名（employeeRename，保持同一记录）；员工改名不反写账号
  - 删除账号：同步删除关联员工（无报工记录时）；有报工记录的员工账号拒绝删除
- **工号规则**（work_employees.username，UNIQUE）：`W + 3位流水`（W001 起，按入职顺序，**删号不重用**）；留空自动生成（当前最大号+1），也可手动填/改；账号自动创建的员工同样走 W 流水（不沿用登录名）；存量已重编 W001-W038
- 工人登录打卡页自动带出本人（优先账号关联，其次 localStorage wr_last_emp）

## 6. 主键策略（雪花，2026-07-31）

- 全部 27 表主键为 **15 位时间戳型雪花**：`毫秒时间戳 × 100 + 2 位序列`（≈1.78e14 < 2^53，前端 JS 精度安全，零前端改造）
- 生成双轨（`db/snowflake_migration.sql`，可重复执行）：
  1. **MySQL 触发器兜底**：每表 `BEFORE INSERT ... SET NEW.id = IFNULL(NEW.id, sfid())`——手动 SQL 不带 id 自动生成
  2. **Java 显式生成**：`mapper.nextId()`（`SELECT sfid()`，同源同序列）——Controller 创建单据/报工时预生成，保证返回 id 与 DB 一致
- **坑（必须遵守）**：
  - 触发器场景下 `LAST_INSERT_ID()` 返回**旧自增值**（不可靠）→ 单据创建一律**按业务单号回查**（lastOrderId(coNo)/lastDeliveryId(dnNo)/lastPurchaseId(poNo)/lastReceiptId(rvNo)/lastPaymentId(pvNo)/lastInvoiceId(invoiceNo)/lastReturnId(prNo)）或无唯一号的用 `nextId()` 显式插入
  - **触发器禁止无条件覆盖 NEW.id**（v1 的坑：Java 显式 id 被触发器新值覆盖导致返回与 DB 不一致）——必须 IFNULL 形式
  - 存量自增 id（1,2,3...）保留共存，不迁移

## 6.5 预留字段与逻辑删除（2026-07-31）

  **预留字段（27 表统一）**：`ext_json JSON NULL` + `remark VARCHAR(500)`——**ext_json 纪律**：
  - 只放**不确定/低频/一次性/自定义**数据（任意键值，加键不用改表）
  - **禁止**放要查询/统计/排序的字段
  - **演进规则**：某键一旦固定下来且要查询 → 立刻 `ALTER TABLE` 提升为正式列 + 从 ext_json 迁移数据（MySQL 8 INSTANT ADD COLUMN 秒级，加正式列成本极低，所以 ext_json 永远只是过渡层）

  **逻辑删除（27 表 `deleted TINYINT DEFAULT 0`）**：
  - MP 实体 @TableLogic 自动过滤；手写 SQL 查询加 `deleted = 0`、DELETE 改 `UPDATE SET deleted = 1`
  - **库存回补自动化**：删除单据 → 流水标记 deleted → `currentStock` SUM 过滤 deleted → 库存自动回补（不再手动算）
  - **报工图片回收站模式**：取消/删除报工只标记 deleted，**图片文件保留**（业界主流：软删+恢复；彻底清理时才删文件）
  - 唯一索引冲突：软删后唯一字段（物料 code/用户名）仍占位 → 删除时加后缀
  - sequences（编号生成器）不做逻辑删除
## 6.6 项目结构与上传目录（2026-07-31）

- **上传图片目录：`I:\yawei-uploads\`**（报工照片存 `I:\yawei-uploads\work\`，旧日期目录保留）——**已移出 web/public**（vite build 不再复制 189MB 图片，dist 5.2MB）
- 访问链路：`/uploads/**` → 后端静态映射（WebConfig `file:I:/yawei-uploads/`）；vite dev 走 5173 代理 `/uploads → 8080`（vite.config.js 已配）
- 后端结构约定：单包平铺（48 文件，4670 行）——**小厂体量保持单包**，不拆分层；命名 XxxController/XxxMapper/实体 一一对应
- 前端：views 按业务域命名；复用 PageHeader/PaginatedTable；HelloWorld 脚手架残留已删
- 临时脚本归 `scripts/`（test_photo_flow.py/download_mysql.py）；顶层只留 backend/web/docs/db/backup/scripts + README + build_backend.bat

## 6.7 采购报表（2026-07-31，象过河/金蝶标准维度）

- 页面：**采购报表**（菜单：库存报表组 + boss 视图）；后端 PurchaseStatsController + TradeMapper 10 个统计 SQL
- 维度：按供应商/商品/仓库采购统计、采购明细查询（单号/供应商/商品搜索）、月度汇总、商品月度、供应商月度、价格趋势（商品×月均价）、退货按供应商/商品
- 口径：日期范围默认本月、已删单据不计（deleted=0）、金额与库存流水口径一致（实测与手工 SQL 对账一致）
- 未做：按经手人（单据无该字段）、订单执行情况（本系统采购单=下单+入库一体，无独立订单）

## 6.8 销售退货与销售报表（2026-07-31，象过河/金蝶标准维度）

- **销售退货**（新功能）：sales_returns/sales_return_items 两表（雪花+deleted 标准字段）；SalesReturnController + SalesReturnMapper
  - 创建：客户退货 → **库存回补**（流水 move_type='in' 加库存）+ 单号 XSTH；删除=逻辑删，流水标记 deleted 库存自动回退
  - **应收自动冲减**：receivable-summary / receivable-monthly / aging 的应收 = 送货 - 收款 - 退货（return_amount 列）
  - 页面：销售退货（admin 单据组菜单）
- **销售报表**（新页面）：SalesStatsController + ReportMapper 10 个统计 SQL；菜单库存报表组 + boss/dev 视图
  - 维度：按客户/商品/仓库销售统计、毛利（按客户：销售/成本/毛利/毛利率，成本=物料进价）、销售明细查询、月度汇总（含退货）、客户×月/商品×月、退货按客户/商品
- **顺手修复**：采购退货流水符号 bug（quantity 取负导致 currentStock 算反——退货后库存显示增加而非减少），已改为正值 out 流水

## 6.9 部署与访问（2026-07-31，8080 一体部署）

- **一体部署（推荐日常使用/手机/内网穿透）**：前端 dist 已打进 backend static/，**8080 一个端口 = 页面 + /api + /uploads 图片**；局域网访问 `http://192.168.1.88:8080/`
- **开发模式**：vite dev 5173 照常（热更新改代码用）；改完前端更新一体部署的流程：`cd web && npm run build` → 复制 `dist/*` 到 `backend/src/main/resources/static/`（先清空）→ 重编译后端 jar → 重启
- 前端 baseURL 是相对 `/api`，同源部署无需改任何代码；路由是 hash 模式（#/），无 history 404 问题
- **内网穿透**：只需穿透 8080 一个端口（页面/API/图片全包）。推荐 cpolar（免费版 1 个隧道够用，需注册账号）或花生壳；frp 适合有公网服务器的情况。穿透后外网地址直接访问
- 浏览器缓存坑：改版后看不到新功能 → Ctrl+F5 强刷（vite 长时间运行 HMR 失效时重启 vite）

## 6.10 库存盘点（2026-08-01）

- **库存盘点单**：stock_takes + stock_take_items 两表（雪花+deleted 标准字段）；StockTakeController + StockTakeMapper
  - 流程：新建盘点单（选物料，自动带账面库存）→ 录入实盘 → **确认**后按差异调整库存（move_type='adjust' 流水，盘盈+盘亏-）；草稿可重复确认拦截；删除=逻辑删+流水回退
  - **期初库存 = 第一次盘点录入实盘数即建立**（无需单独期初功能）
  - **move_type 新增 'adjust'**：currentStock / inventory SQL 均加了 `WHEN move_type='adjust' THEN quantity` 分支（盘点调整与流水口径一致）
  - 页面：库存盘点（admin 库存报表组 + dev 视图）
- **顺手修复存量 bug**：StockMapper.inventory 的 keyword 条件缺 `AND` 前缀（带搜索词查询就 SQL 语法 500）

## 6.11 资金账户与收支（2026-08-01，象过河财务模块）

- **三张新表**：cash_accounts（资金账户：现金/银行/微信支付宝，期初余额）、income_expenses（收支单：收入/支出+分类+账户）、transfers（转账单）——均雪花+deleted 标准字段
- **余额聚合**（无流水表，直接聚合）：余额 = 期初 + 收入 − 支出 + 转入 − 转出（AccountMapper.accountBalances）；删除单据自动回退（deleted=0 过滤）
- **账户删除保护**：有收支/转账记录的账户禁止删除（accountUsage 检查）
- 单号：收支 IE、转账 ZZ；分类前端可下拉可自定义（预置：水电费/房租/运费/人工工资/杂费/废料收入/利息）
- 报表：账户余额表、收支统计（分类×月）、经营状况月报（收入/支出/净收支）
- 页面：资金账户/收支转账（admin 财务组）、收支报表（admin+boss 财务组）
- 完整利润口径：销售毛利（销售报表）− 支出 + 其他收入 = 净利

## 6.16 接口文档 + Git 分支规范（2026-08-01，企业级清单落地）

- **接口文档（springdoc 3.0.3）**：pom 加 `springdoc-openapi-starter-webmvc-ui`；访问 `/swagger-ui/index.html`（界面）/ `/v3/api-docs`（JSON）
  - **⚠️ 坑**：Knife4j 4.5.0 内置 springdoc 2.x，与 SpringBoot 4.1 **不兼容**（`NoSuchMethodError: ControllerAdviceBean.<init>`，api-docs 500）——knife4j 4.x 是 SB3 时代产物，SB4 必须用 **springdoc 3.x**
  - 自动生成 20+ 接口文档；生产环境可配 `springdoc.api-docs.enabled=false` 关闭
- **Git 分支规范**：`git init -b main` + .gitignore（target/node_modules/dist/backup/*.log）+ 首次提交（255 文件基线）+ `git branch dev`
  - 以后：新功能在 **dev** 分支开发 → 验证通过 → 合并 main（`git checkout main && git merge dev`）
  - 本地 git 用户：YaweiDev <dev@yawei.local>（git config user.name/email）

## 6.15 前端增量改进（2026-08-01，多角色分析后实施）

- **结论**：不全面重构（页面少/无技术债/生产中使用/业界 Strangler Fig 渐进式）；做增量规范化
- **API 层**：新增 5 个模块（work/finance/reports/inventory/catalog）+ trade 扩展；Receivables/Payables/Vouchers 迁移（其余页面随功能改动逐步迁移）
- **FilterBar 组件**：报表筛选栏（日期范围+查询+slot），采购/销售/收支/生产统计 4 报表页复用
- **手机卡片化 19 页**：统一模式（isMobile ref + resize 监听 + el-table v-if="!isMobile" + v-else .m-cards 卡片列表）；单据/库存/生产/财务/基础/系统全覆盖
- **手机弹窗全局适配**（style.css @media max-width:767px）：弹窗全屏 92vh + body 内部滚动 + **footer sticky 固定（保存按钮不用滑到底）** + 表单紧凑
- **详情弹窗手机优化**：el-descriptions 手机单列（isMobile ? 1 : 3）+ 日期格式化（fmtDate ISO→yyyy-MM-dd）+ 金额￥千分位 + 明细改字段行（.m-detail-items，避免 6 列表格横滑丢金额列）
- 剩余不做卡片化：报表页（数据密集横滑合理）、拍照/报工登记（手机交互页）、打印页、使用指南（Tab 化）

## 6.14 动态菜单/数据字典/公司配置（2026-08-01，业内规范改造）

- **动态菜单（方案 B）**：CatalogController.MENU_TREE 后端菜单树（每节点 roles 控制可见性）；GET /api/menus 按登录角色过滤返回树；前端 Layout 删除 4 套硬编码 v-if，改 MenuNode 递归组件 v-for 渲染（icon 名下发，main.js 已全量注册图标）
  - 统一树原则（RuoYi 式）：菜单位置固定，角色只控可见性——boss 的采购/销售报表从顶层并入"库存报表"组；employee 保持精简顶层（报工登记/库存查询/拍照/指南）
  - 后端接口才是权限边界（路由守卫仍按 meta.roles 拦截）
- **数据字典**：GET /api/dicts 下发（income_categories/expense_categories/customer_regions/account_types，{value,label} 结构）；Funds 收支分类、Customers 区域、CashAccounts 账户类型 改字典源——**加分类不用改前端代码**
- **公司配置**：sys_config 表（company_name/company_address/company_phone）+ GET /api/config/company；DeliveryPrint 送货单抬头改动态——**改公司抬头 = UPDATE sys_config，不用改代码**
- 未字典化：物料分类（自由输入合理，非固定字典）；按钮/接口权限（小厂内部用，菜单入口控制即可）

## 6.13 基本信息模块 + 触发器 v3 修复（2026-08-01）

- **快递物流**：express_companies 表 + ExpressController（CRUD）；页面快递物流（admin/dev 基础资料组）
- **客户区域**：customers 加 region 列；客户表单/列表加区域（可下拉可自定义：珠三角/长三角/华东/华北/华南/西南）
- **数据恢复**：备份页加下载/恢复（恢复=mysql source 导入备份 SQL，**二次确认**；防目录穿越校验）
- **⚠️ 恢复测试重大教训**：在真实库执行恢复会把数据库回滚到备份时点（缺列/缺新表/触发器回退）——**恢复功能验证必须用临时库**，绝不能在开发库直接恢复旧备份。本次事故恢复方案：重放全部 DDL（deleted/ext_json/时间字段）+ 重建 39 触发器 + 补 receipt_vouchers/payment_vouchers 的 deleted（27 表清单里 vouchers 表名错误）
- **⚠️ 触发器 v3（IF 版）修复（重要）**：v2 的 `IFNULL(NEW.id, sfid())` 在 MySQL 8 下**兜底失效**——AUTO_INCREMENT 列在 BEFORE INSERT 触发器执行时 NEW.id **已被预分配自增值**（非 NULL）→ IFNULL 短路 → 无显式 id 的插入（MP 实体 insert）一直用自增值。修复：`SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id)`——自增值(<1e14)覆盖为雪花，Java 显式雪花 id(15位)保留。**39 表触发器已全部重建为 v3**（验证：MP 插入雪花 ✓ + 显式 id 保留 ✓）

## 6.12 生产管理（2026-08-01，象过河生产模块）

- **成品入库单**：production_ins + production_in_items 两表（雪花+deleted）；ProductionController + ProductionMapper
  - 生产完成的产品入库（in 流水 ref_type='production_in'，自动加成品库存）；**不重复扣料**（报工已按 BOM 自动扣材料）
  - 单号 CPRK；金额 = 数量 × 物料进价；删除=逻辑删+流水回退
- **生产退料**：production_returns + production_return_items；车间剩余/多扣材料退回仓库（in 流水 ref_type='production_return'）；单号 PCTL
- **生产统计**：成品入库按产品×月、退料按物料×月（/production-stats/ins|returns）
- 页面：成品入库/生产退料/生产统计（admin+dev「生产」子菜单）；boss 只显示生产统计
- 生产领料无需单独单据——**报工自动扣料即领料**（process_materials）
- sfid() 用 GET_LOCK 保证同毫秒并发唯一（每次插入加锁，小厂量级无压力）

## 7. AI 集成

- **拍照入库**：阿里云 DashScope `qwen3-vl-plus`（3-5 秒快速），key 在 `backend/src/main/resources/application.yml` 与旧版 `I:\yawei-erp\photo_config.json`
  - 备选：SiliconFlow 的 Qwen3-VL-32B-Instruct
  - **坑**：电线类物料的"料号/规格"列常被识别重复，需人工校正数量和单价
  - qwen3.7-max 手写识别更好但 20-80 秒太慢，用户不接受
- **AI 报价**：`ai_config.json`（DashScope qwen-plus），/api/chat 接口 + chat.html 聊天页；料号 CP-00001~CP-00587（587 个有规格成品）已生成

## 8. 开发环境与构建

- 后端：Java 21（`E:\Program Files\Java\jdk-21\bin\java.exe`），Maven `C:\maven\apache-maven-3.9.9`（**mvnw 被墙不能用**）
- 构建命令：`cmd /c "C:\maven\apache-maven-3.9.9\bin\mvn.cmd -o -DskipTests package"`（后端）；`npm run build`（前端，恒 3168-3170 modules）
- MySQL：`C:/mysql/8.0.28/bin/mysql.exe -uroot yawei_erp`（**Windows Python/脚本不识别 /c/mysql 路径**）
- 服务：8080 后端 jar；5173 vite dev；8000 旧 Python 版（`I:\yawei-erp`，启动 `PYTHONPATH= .venv/Scripts/python run.py`）
- 权限：4 角色（员工/管理员/老板/开发）菜单级；**后端 API 不鉴权**（内部系统）
- 仓库：入库可选仓 + 按仓筛选；出库仍从 1 号仓扣
- 报工打卡：拍照 ≤3 张存 `web/public/uploads/work/`、时间可改；使用指南 Help 页 Mermaid 流程图

## 9. 已知坑速查

- Map.of() 不能含 null 值（AuthController login/me 用 HashMap）
- MyBatis-Plus updateById 对 null 字段不生成 SET → 需要清空时加 `updateStrategy = FieldStrategy.ALWAYS`
- 改名判断必须先存 oldName 再比较（先 set 再 equals 恒等）
- search_files 在 I:\ 盘失效 → 用 terminal grep
- taskkill 需要 `MSYS_NO_PATHCONV=1` 前缀
- Windows Python 不识别 /c/mysql → 用 C:/mysql/...
- el-table 带 fixed 列在容器隐藏/切换时报 `parentNode null`（Element Plus 已知）→ 分页表格组件内不用 fixed
- 调试清理脚本按前缀删文件会误删用户实测照片 → 清理只删自己脚本的精确文件名

## 6.17 扩展开发指南（前后端可扩展设计，2026-08-01）

### 新增一个功能模块的标准流程（前后端 4 步）

**后端（新模块三层结构）：**
1. 建表（DDL，复制现有表结构模式：id 雪花 + deleted + ext_json）
2. `XxxMapper.java`（@Mapper 接口 + @Select/@Insert 注解 SQL，分页用 IPage 参数）
3. `XxxController.java` **继承 BaseController**（模板方法模式：pageParams/ok/fail/pageResult 统一）
   - 写操作会被 OperationLogInterceptor 自动记录（无需手动加日志）
4. 触发器（如需雪花主键，复制 v3 IF 版触发器模式）

**前端：**
1. 复制 `web/src/templates/ListPageTemplate.vue` → `views/XxxXxx.vue`，改 API 路径/字段/表格列
2. `router/index.js` 加一行路由（roles 权限）
3. 后端 `CatalogController.MENU_TREE` 加菜单项（`new MenuNode("/xxx/yyy", "名称", "图标", 角色列表, null)`）——菜单自动出现，无需改前端
4. 手机端：模板已含 m-cards 卡片化；FilterBar/PageHeader/PaginatedTable 公共组件直接复用

### 设计模式应用（能用则用，不过度设计）

| 模式 | 落地位置 | 用途 |
|---|---|---|
| 模板方法 | BaseController（新 Controller 继承） | 分页/响应/边界统一 |
| 工厂 | 前端 api/ 模块（trade.js/finance.js 等） | 接口封装统一出口 |
| 适配器 | 前端公共组件（FilterBar/PageHeader/PaginatedTable） | 跨页面复用一致交互 |
| 观察者 | OperationLogInterceptor（拦截器自动记日志） | 写操作自动审计，零侵入 |
| 单例 | SessionStore / Spring 单例 Bean | 全局唯一状态 |
| 策略（可选） | 后续多规则场景（如不同单据不同审批流） | 规则可插拔 |

### 关键约定（扩展时必守）

- 新 Controller 一律 `extends BaseController`；响应走 ApiResponse.ok/fail
- 路径配置走 application.yml `app:` 节（@Value 注入），不硬编码
- 业务魔法值进 Constants.java（单号前缀/角色/move_type）
- 写操作自动入操作日志表（operation_logs），无需手动
- 新菜单进 MENU_TREE（角色可见性在这里控制），前端零改动
- 分页接口统一返回 {items, total, current, size}（PageResult.toMap()）
