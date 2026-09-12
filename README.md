# 工厂生产经营一体化管理系统（Java 版）

小型工厂生产经营一体化管理系统（进销存 + 生产报工 + 财务，轻量 ERP）。部署环境：（公司地址），电话 （联系电话）（（联系人））。

## 文档导航（docs/）

| 文档 | 内容 |
|---|---|
| [docs/01-product.md](docs/01-product.md) | 产品文档：定位/角色/业务流程/功能清单/**业务口径**（改代码前先读） |
| [docs/02-architecture.md](docs/02-architecture.md) | 技术架构：技术栈/架构图/构建启动/关键机制/故障速查 |
| [docs/03-backend.md](docs/03-backend.md) | 后端文档：类清单/核心逻辑(库存流水/报工/单据状态机)/**已知坑** |
| [docs/04-frontend.md](docs/04-frontend.md) | 前端文档：路由表/页面说明/组件/样式约定 |
| [docs/05-api.md](docs/05-api.md) | API 文档：全部接口（80+，按模块分组+调用示例） |
| [docs/06-database.md](docs/06-database.md) | 数据库文档：27 张表/核心结构/关系/备份恢复 |
| [docs/07-features.md](docs/07-features.md) | 功能文档：各功能操作步骤（界面级指南在系统内 /help） |
| [docs/08-dev-notes.md](docs/08-dev-notes.md) | **开发备忘**：规则速查/UI规范/雪花主键/已知坑（开发前先读） |
| [docs/09-ops.md](docs/09-ops.md) | **运维手册**：启动/停止/备份恢复/日志/故障速查/升级流程 |
| [docs/10-ai.md](docs/10-ai.md) | **AI 文档**：报价助手/拍照识别/批量BOM 配置与排障 |
| [docs/11-production-readiness.md](docs/11-production-readiness.md) | **上线就绪评估**：验证基线/能力边界/遗留项/切换建议 |
| [docs/test-reports/README.md](docs/test-reports/README.md) | **测试资产**：e2e 168项 + 压测 11项，脚本用法/报告索引/清理机制 |
| [AGENTS.md](AGENTS.md) | **AI 协作指南**：给 AI 助手的工作总入口（业务口径/流程/纪律/偏好） |
| [docs/work-punch-design.md](docs/work-punch-design.md) | 报工打卡模块详细设计（状态机/API/扣料/图片） |

- 前端：Vue 3 + Vite + Element Plus + Pinia（`web/`）
- 后端：Java 21 + Spring Boot 4.1 + MyBatis-Plus（`backend/`）
- 数据库：MySQL（库名 `jxc_erp`，root 无密码，`db/init.sql` 建表）
- AI：阿里云 DashScope（qwen-plus 报价助手、qwen3-vl-plus 拍照识别），Key 配置沿用旧 Python 版 `I:\erp-server\photo_config.json` / `ai_config.json`

旧 Python 版（FastAPI + SQLite）在 `I:\erp-server`，端口 8000，**已于 2026-08-01 切换后停用**（数据已完整迁入 v2 并核验一致；如需回查历史数据可重启，勿再录单）。

## 启动与构建

```bash
# 后端（8080）：先编译再启动
cd /i/erp-server/backend
M2_HOME='C:\maven\apache-maven-3.9.9' cmd /c "C:\maven\apache-maven-3.9.9\bin\mvn.cmd -o -DskipTests package"   # 构建（-o 离线；mvnw 会卡在下载 wrapper，用本机 maven）
"E:\Program Files\Java\jdk-21\bin\java.exe" -jar target/erp-server-0.0.1-SNAPSHOT.jar   # 运行

# 前端（5173，代理 /api → 8080）
cd /i/erp-server/web && npm run dev
# 生产静态包（后端不托管 web/dist，当前以 vite dev 为准）
npm run build
```

- 管理员账号：admin / admin123
- 端口占用处理：`MSYS_NO_PATHCONV=1 taskkill /PID <pid> /F`（杀掉旧进程后 jar 才能重新打包，Windows 文件锁）
- 改 Java 后端必须重启后端进程才生效；改 vue 源码 vite 热更新即生效

## 目录结构

```
erp-server/
├── backend/src/main/java/com/jxc/erp/   # 单包结构：Controller + Mapper(MyBatis注解SQL) + 实体
│   ├── WorkController.java / WorkMapper.java      # 报工模块（分组/员工/工序/打卡/统计/扣料）
│   ├── MaterialController.java / TradeController... # 物料/进销存/库存
│   ├── AuthController.java / SessionStore.java     # 登录（users 表）
│   ├── DashScopeClient.java                        # 阿里云 AI 调用
│   └── ApiResponse.java                            # 统一返回 {success, data|error}
├── web/src/
│   ├── views/        # 每个功能一个 vue（见功能地图）
│   ├── router/index.js
│   ├── api/          # stock.js / trade.js / auth.js / base.js
│   └── utils/request.js   # axios 封装：baseURL=/api，业务失败统一 reject
├── db/init.sql       # 建表脚本（含 work_reports.status、work_report_images）
└── build_backend.bat # 官方构建脚本（M2_HOME=C:\maven\apache-maven-3.9.9）
```

## 功能地图

| 菜单 | 路由 | 页面 | 说明 |
|---|---|---|---|
| 仪表盘 | /dashboard | Dashboard.vue | 6统计卡(2×3)+应收应付+待出货+库存预警等，全部动态计算 |
| 报工-报工 | /work/reports | WorkReports.vue | **打卡报工**（工人默认页）+ 记录/日报/月报（管理员） |
| 报工-统计 | /work/stats | WorkStats.vue | 统计报告(日均/趋势)+工资汇总 |
| 报工-设置 | /work/settings | WorkSettings.vue | 分组/员工/工序/工序扣料配置 |
| 进销存 | /orders /deliveries /purchases /purchase-returns | | 客户订单/送货单/采购入库/采购退货 |
| 库存报表 | /stock/inventory /reports /bom | | 库存查询(可按仓库筛选)/报表中心/BOM配方 |
| 财务 | /finance/receivables /finance/payables /finance/vouchers | | 应收/应付/收付款单（金蝶K3式） |
| AI报价 | /ai/chat | AiChat.vue | 聊天式报价（qwen-plus），不落库 |
| 拍照入库 | /ai/photo | PhotoIn.vue | **闭环**：拍照→AI识别(供应商+明细)→核对编辑→确认入库(自动建物料+采购单+库存)，参照旧版 photo_receive |
| 基础资料 | /base/customers /base/suppliers /base/materials /base/warehouses | | 客户/供应商/物料/**仓库** |
| 系统 | /system/users /system/backup | | |
| 打印 | /deliveries/print/:id /reports/reconciliation-print | | 送货单一式两联/对账单（象过河样式） |

工人（非 admin）菜单只显示：报工登记/拍照入库/库存查询/使用指南；手机端有底部导航。

## 角色与权限（4 角色，菜单级控制）

| 角色 | 代码 | 可见菜单 | 定位 |
|---|---|---|---|
| 员工 | employee | 报工登记、拍照入库、库存查询、使用指南 | 车间打卡 |
| 管理员 | admin | 全功能 | 日常运营 |
| 老板 | boss | 库存查询、报表中心、财务(应收/应付/收付款)、AI报价、拍照入库 | 看数为主，不碰单据管理和基础资料 |
| 开发 | dev | 库存查询、报工(登记/统计/设置)、BOM、基础资料、系统(用户/备份)、AI报价 | 维护配置 |

- 实现：路由 `meta.roles` 数组 + 守卫（无权限跳仪表盘）；Layout 按角色渲染菜单；Users.vue 可选 4 角色
- 仅前端菜单/路由级控制，后端接口未鉴权（小厂内用，如要收紧再补）

## 打卡报工（2026-07-31 改造）

工人报工 = 打卡：选姓名→选工序→【开始】→ 完工填数量→【结束】，开工后自动计时（扣除午休/晚餐），下方"今日已完成"。
替代了原来"新建报工弹窗填8个字段"的管理式表单。设计细节见 `docs/work-punch-design.md`。

## 关键业务口径（易忘，勿写死）

- **月度销售报表基于送货单**（v_monthly_sales ← delivery_notes+delivery_items），不是客户订单；历史订单要生成送货单才出报表
- 仪表盘"待处理订单数" = pending + partial 合并口径
- 出货规则：订单关联的不能超量（可出=订购-已出）；临时出货不限制；超量≤5个备品备注"含备品"，>5需核实
- 报工计时排除午休 12:00-13:00、晚餐 17:30-18:00
- 报工统计：日均产量=总数量÷实际有报工天数；趋势=前后半段日均对比，≥4天才有意义
- 物料规则：参数不同=不同物料；顺序不同=同一物料加别名；10号=10AWG=10#；红/黑线是不同物料；特软硅胶线=锡铜线；红黑各半进货拆成红黑各半；UL标识线=特制线分开
- 数据整理：全部放一个 Sheet 加别名列（方便 Ctrl+F），不分多 Sheet

## 已知坑（开发必读）

1. **MyBatis @Select 动态 SQL 的 `<if>` 相邻必须加空格**，否则 `?AND` 拼接直接 500
2. **ONLY_FULL_GROUP_BY**：GROUP BY 必须包含全部非聚合列
3. **work_employees.group_id 为 NULL 时** MyBatis Map 不含该键，取值用 `COALESCE(group_id,0)` 或判空
4. 前端 `request` 拦截器已返回 `res.data`（body），页面里 `res.data.xxx` 才是业务 data
5. 同一员工同时只允许一条 in_progress 报工（后端强校验）
6. 删除报工（/work/reports/{id} DELETE）会回补库存流水；取消进行中报工是直接删行不留脏数据
7. `mvnw` 在本机不可用（wrapper 下载被墙），一律用 `C:\maven\apache-maven-3.9.9` 构建
8. 生产/局域网访问用 5173（vite dev）即可；8080 只出 API
9. MyBatis-Plus 3.5.17 在 Spring Boot 4 下自动配置失效，需手动建 SqlSessionFactory（见 JxcErpApplication/配置类）
10. **`SELECT wr.*` 与子查询别名同名列冲突**：work_reports 表本身有 image_count 列，列表查询再加 `(SELECT COUNT(*) ...) AS image_count` 会重复列名，MyBatis 取到表列值（恒 0）。带别名的列表查询必须显式列出列名，不要 `wr.*`
11. 图片上传默认限制单文件 1MB，已在 application.yml 调大到 15MB/30MB；报工照片存 `web/public/uploads/work/`（vite public 目录可直接访问），清理时只删自己的测试文件，勿按前缀全删（会误删用户照片）

## 待办（产品审视结论，按优先级）

1. ~~**拍照入库闭环**~~ ✅ 2026-07-31 完成：识别(供应商+明细) → 前端核对编辑 → 确认入库(自动补齐规格/自动新建物料 + 采购单 + 库存流水)。接口：POST /api/photo/recognize、/api/photo/confirm；实测通过（回归脚本 test_photo_flow.py）
2. ~~**仓库管理**~~ ✅ 2026-07-31 完成：基础资料-仓库 CRUD（有流水保护不可删）+ 采购入库选仓 + 库存查询按仓筛选。注意：送货出库/报工扣料目前仍从 1 号仓扣（未按仓出），如需分仓出库再扩展
3. **AI报价转订单**：报价结果一键生成客户订单草稿，接报表/利润分析
4. ~~**Help 页**~~ ✅ 2026-07-31 使用指南流程图已从手写 SVG 迁移到 **Mermaid**（npm 依赖，`web/src/components/MermaidFlow.vue` 封装；改文字即改图，6 张流程图定义在 Help.vue script 顶部）
5. ~~**报工记录支持"修改"**~~ ⏸ 保持"删除+补录"（补录已支持拍照上传，删了重录即可，不额外开发修改功能）
6. ~~**报工模块完善**~~ ✅ 2026-07-31：补录支持拍照(≤3张)；删除/取消报工自动清理照片文件(防孤儿)；boss 角色可看报工统计；记录表格多图预览(角标+全部图)；历史旧版图片已迁移到 web/public/uploads/(197MB/1848文件，历史报工图恢复显示)；image_count 列同步更新+历史回填(1144条)
