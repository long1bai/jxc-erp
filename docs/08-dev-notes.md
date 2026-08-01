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

## 6.18 全功能 E2E 测试 + 3 个 Bug 修复（2026-08-01）

### 测试资产
- 自动化脚本：`scripts/e2e_test.py`（REST API 全链路，168 项断言，可重复执行）
  - 测试数据统一「【测试】」前缀 + 运行序号（可并发跑不撞名）
  - 结束自动 SQL 清理（生产库不留脏数据）+ **单据序列重同步**（见下）
  - 报告输出：`docs/test-reports/<日期>-e2e-test.md`
  - 运行：`cd /i/yawei-erp-java/scripts && python e2e_test.py`（后端须在 8080 运行）

### Bug 1：所有关键词搜索 500（严重）
- 现象：客户/供应商/物料/订单/送货/采购/退货/员工/工序/BOM 列表带 keyword 搜索全部报 SQL 语法错误
- 根因：`<where>deleted = 0 <if test='kw...'>` 后接 `(name LIKE ...)` 缺 `AND`，拼出 `WHERE deleted = 0 (name LIKE...)`
- 修复：9 个 Mapper（Customer/Material/Supplier/Finance×2/Return/SalesReturn/Work×2/Bom/Trade×3）共 13 处补 `AND `
- 教训：**MyBatis `<where>` 不会自动在条件间补 AND**，多条件时第二个 `<if>` 必须自带 `AND` 前缀

### Bug 2：BOM 更新/重建必报唯一键冲突（严重）
- 现象：`POST /api/bom/{productId}` 第二次保存报 `Duplicate entry ... for key 'bom_items.uk_bom'`
- 根因：保存是「先清后插」，但清是软删（`UPDATE bom_items SET deleted=1`），唯一索引 (product_id, component_id) 仍被软删行占用 → 重插必冲突
- 修复：`BomMapper.bomDeleteByProduct` 改为硬删 `DELETE FROM bom_items WHERE product_id=...`
- 教训：**软删 + 唯一索引的组合要小心**——软删行仍占唯一键，重插同键数据会 500

### Bug 3：库存调拨虚增库存（严重）
- 现象：调拨 5 个物料后总库存反而 +10（调出仓+5、调入仓+5）
- 根因：调拨出库流水存了 `quantity.negate()`（负数）配 `move_type='out'`，而库存汇总对 out 类型取 `-quantity` → 双重取反变入库
- 修复：`StockTransferController` 出库流水去掉 negate，与其他单据一致（out 记正数数量）
- 教训：**move_type='out' 的流水一律记正数数量**，汇总端统一 `-quantity`；写负数数量是隐藏雷

### 单据序列重同步（测试清理后必做）
- 场景：测试/误删硬删了单据行但序列表继续自增没问题；**若库里残留软删单据行（deleted=1）而序列表回退，下一单号会撞唯一键，且事务回滚让序号永远卡死**（盘点单 PD-20260801-0003 实踩，盘点功能当天锁死）
- 修复：清理脚本末尾对每个单据前缀执行 `seq = MAX(现存单号数字)`（含软删行）→ 下一号 = MAX+1 永不复用已占用号
- 涉及前缀：PD(盘点)/CGDD(采购)/XSDD(订单)/SH-XSDD(送货)/XSTH(销售退货)/TH(采购退货)/CPRK(成品入库)/PCTL(生产退料)/ST(调拨)

### 已确认按设计的行为（非 Bug）
- 成品入库**不**自动扣 BOM 组件——报工按「工序物料绑定(process_materials)」自动扣料，避免重复扣（见 6.12）
- 删除采购/送货单会删对应库存流水 → 库存自动回滚（逻辑删除流水=库存回补）
- 资金账户有收支/转账记录时禁止删除（提示改名称），属保护逻辑
- 重复取消报工幂等成功（UPDATE ... WHERE status='in_progress' 再执行 0 行也返回成功）

## 6.19 压力测试 + 2 个并发 Bug 修复（2026-08-01）

### 测试资产
- 自动化压测：`scripts/stress_test.py`（5 场景：30并发订单/20并发送货扣减/10并发打卡/10并发盘点/20×60接口负载）
- 运行：`cd /i/yawei-erp-java/scripts && python stress_test.py`（自动清理压测数据 + 序列重同步）

### 压测结论（11/11 通过）
| 场景 | 结果 |
|---|---|
| 30 并发创建订单 | ✅ 30/30 成功、单号 0 重复 |
| 20 并发送货扣库存 | ✅ 20/20，库存 200→100 精确（流水聚合模型天然并发安全） |
| 10 并发同员工报工打卡 | ✅ 仅 1 条进行中（修复后） |
| 10 并发盘点单号生成 | ✅ 10/10 唯一 |
| 接口负载 20 并发×60 请求 | ✅ 0 错误，p50 10~89ms，p95 28~326ms（dashboard 首压稍高） |

### Bug A：并发报工打卡重复开启（严重）
- 现象：同员工 10 并发 start，成功 5 条进行中（应仅 1 条）
- 根因：SELECT 判空 + INSERT 两步，TOCTOU 窗口
- 修复：WorkMapper.reportStart 改 `INSERT...SELECT...WHERE NOT EXISTS`（原子防重，走 employee_id 索引），控制器用影响行数兜底提示

### Bug B：单据序列并发（踩坑 4 轮才收敛）
- 现象：删 sequences 行后 10 并发创建盘点单，仅 2/10 成功
- **关键教训：失败原因是历史软删单据占着单号唯一键**——序列回退到 0003 时撞上旧软删盘点单（PD-20260801-0003，deleted=1）→ takeInsert 撞 uk_st_no。**序列生成本身一直是对的；错的是测试脚本删序列行 + 历史脏数据占位**
- 尝试过的方案（均因 MySQL 锁语义失败）：
  1. INSERT...ON DUPLICATE KEY UPDATE → 并发插同一键是 **MySQL 已知死锁**（10 并发死锁 3 次）
  2. INSERT IGNORE + UPDATE → 死于插入意向锁与 gap 锁互等（死锁 6 次）
  3. GET_LOCK + FOR UPDATE → 无死锁但**连接级锁在池化连接下不可靠**（锁随连接归还泄漏，重复单号）
- **最终方案（已生效）**：`UPDATE seq=seq+1`（行存在时单行锁串行化，无 gap 锁无死锁）+ `INSERT` 建行 + 捕获 DuplicateKey/DeadlockLoser 重试 8 次（MySQL 官方对"并发创建同一唯一键"的标准处理）

### 序列铁律（改代码必守）
1. **sequences 表永远不要回退**——单号被软删单据占用时，回退必然撞唯一键且事务回滚让序号卡死（盘点单 PD 实踩）
2. 清理脚本的序列重同步必须基于 `MAX(现存单号)`（**含软删行**），保证下一号 = MAX+1 永不复用
3. 历史软删单据（deleted=1 且 remark 含"验证"等）会永久占住单号唯一键——发现即清理

## 6.20 安全修复：全局鉴权拦截器（2026-08-01）

### 漏洞背景（实测确认）
- 安全扫描 172 个 /api 端点：**171 个无 token 可直接访问**（仅 1 个返回 401）
- 根因：OperationLogInterceptor 只记日志**不鉴权**（preHandle 直接 return true），鉴权散落在各 Controller 方法且漏覆盖绝大部分
- 后果：无登录可读全部业务数据（客户/财务/日志/备份列表）+ **POST /api/backup/restore 可触发数据库回滚**；配合花生壳外网映射（9087hzlk8738.vicp.fun→8080）= 外网任意人可读全厂数据

### 修复方案（已上线）
- 新增 `AuthInterceptor`：覆盖 `/api/**`，仅排除 `/api/auth/login`；从 Authorization 头提取 token（Bearer 或裸 token）→ `SessionStore.verify` → 无效返回 401 JSON `{"success":false,"error":"未登录或登录已过期"}`
- 注册顺序：AuthInterceptor **先于** OperationLogInterceptor（未登录请求不记日志）
- CORS 预检（OPTIONS）放行
- 前端 `request.js` 已处理 401（清会话跳登录），无需改前端

### 验证结果
- 无 token 扫描：172 端点全部拦截（117 直接 401 + 54 个路径参数端点真实格式 401 + login 放行）
- e2e 168/168 全过（带 token 正常流程零破坏）
- 压测 11/11 全过

### 遗留小瑕疵（非安全）
- 不存在的路径（如 GET /api/customers/{id}——CustomerController 无此映射）返回 500 而非 404：NoResourceFoundException 在静态资源 fallback 路径未走 @ExceptionHandler。无数据泄露，内网可接受，后续可修
- 越权测试（普通用户调 admin 接口）尚未做——后续补

