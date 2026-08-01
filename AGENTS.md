# AGENTS.md — 给 AI 协作者的项目指南

> 本文件是 AI 助手（Hermes / 其他编码 AI）在本仓库工作的总入口。**动手前必读**，按下面顺序消化：项目定位 → 技术栈 → 必读文档 → 业务口径 → 开发流程 → 测试纪律 → 文档规范 → 关键决策 → 用户偏好。

## 项目定位

示例公司有限公司内部 ERP（进销存+生产报工），**小厂内部使用，局域网部署，实用优先**。唯一开发者=老板本人，通过 AI（Hermes）协作开发。所有功能**实测通过才交付**。

- v2（本仓库）：Java 21 + Spring Boot 4.1 + MyBatis-Plus + MySQL 8 + Vite/Vue3/Element Plus，8080 一体部署
- v1（仍在用）：`I:\yawei-erp`，FastAPI + SQLite，8000 端口——**数据口径需对照核验**（预警数/待处理订单等）

## 技术栈与目录

```
backend/   Spring Boot 4.1 + MyBatis-Plus（注解 SQL 为主），Controller 用 record 定义请求体
web/       Vue 3 + Vite + Element Plus + Pinia（Element Plus 中文文档为准）
docs/      01产品 02架构 03后端 04前端 05API 06数据库 07功能 08开发备忘 09运维 10AI
scripts/   e2e_test.py（168项回归）+ stress_test.py（11项压测）—— 交付前必跑
db/        init.sql 建表；生产库 yawei_erp（root 无密码）
backup/    数据库备份（gitignore）
```

## 必读文档（按优先级）

1. **docs/08-dev-notes.md** —— 开发备忘：关键约定（扩展时必守）/ UI规范 / 雪花主键 / 已知坑。**改代码前先读，改后同步**
2. **docs/01-product.md** —— 业务口径（改代码前先读）
3. **docs/09-ops.md** —— 运维手册（启动/停止/备份/故障速查）
4. **docs/10-ai.md** —— AI 功能（报价助手/拍照识别）配置与排障
5. **docs/test-reports/README.md** —— 测试资产用法
6. 技能 `yawei-erp-system`（Hermes 侧）—— 完整配方（启动/迁移/UI/报工/财务等 references/）

## 关键业务口径（改逻辑前必懂）

- **生产模式**：材料入库 → 报工（5 工序：裁线/端子/组装/后焊/太阳能线材）→ 成品组装 → 送货出库
- **报工自动扣料**：报工按「工序物料绑定(process_materials)」自动扣组件库存；**成品入库不重复扣**（避免重复扣）——这是设计不是 bug
- **库存模型**：`currentStock = SUM(stock_movements)` 流水聚合，**不是余额字段**。逻辑删除流水=自动回滚，删除单据无需额外回滚逻辑
- **单号生成**：sequences 表按天自增（前缀-YYYYMMDD-0001），`SequenceUtil.nextDaily` 用 UPDATE+INSERT+重试 8 次（并发安全）
- **逻辑删除**：所有业务表 `deleted` 字段软删；**软删行仍占唯一键**（单号/组件组合），发现撞键先查软删数据
- **雪花主键**：id 用雪花算法（sfid），触发器兜底（39 表，自增值<1e14 覆盖）；详情见 docs/08-dev-notes.md + 技能 trigger-v3 配方
- **资金/财务**：应收=送货金额-收款；应付=入库金额-付款-退货；核销/结清状态流转

## 开发流程（每个任务的标准路径）

1. **读上下文**：docs/08-dev-notes.md 关键约定 → 相关模块代码 → 相关技能配方
2. **写代码**：后端改 Controller/Mapper（注解 SQL），前端改 views/（Element Plus 组件）
3. **本地验证**：vite 5173 改前端（刷新即生效）；后端 mvn package + java -jar + curl 冒烟
4. **测试**：`cd /i/yawei-erp-java/scripts && python e2e_test.py`（168 项）+ `python stress_test.py`（11 项）——**交付前必跑，数据自动清理**
5. **文档同步**：改了什么、踩了什么坑 → docs/08-dev-notes.md（新功能加 6.x 节）
6. **提交**：git 按逻辑拆 commit（fix/feat/test/docs）；dev 分支开发 → 验证 → merge main（或 main 直接提交后 dev 快进对齐）

## 测试纪律（红线）

- **测试数据必须带「【测试】」/「【压测】」前缀**，结束后 SQL 全量清理，生产库 0 残留（已验证基线）
- **sequences 表永远不要手动回退/删除**——软删单据占号会锁死发号（盘点 PD 实踩）。清理脚本内置重同步（MAX 含软删）
- 报工打卡并发（同员工）已做原子防重；序列并发方案是 UPDATE+INSERT+重试（踩坑记录：ON DUP/INSERT IGNORE/GET_LOCK 均失败，见 docs/08-dev-notes.md 6.19）
- 断言要严格（并发场景要求全量成功），别写宽松断言掩盖失败
- 测试脚本可重复执行（数据唯一化带序号）

## 文档规范

- **新知识优先写文档，记忆只留索引**（用户明确要求）
- 改功能必须同步 docs/08-dev-notes.md；新模块加 6.x 节；README 文档导航同步
- 文档均为中文；使用指南图形结合（SVG 流程图），用户不喜欢纯文字
- 测试/压测结果落盘 docs/test-reports/<日期>-*.md

## 关键设计决策（改之前先想清楚，别推翻）

| 决策 | 原因 |
|---|---|
| 库存=流水聚合 | 并发安全、删单自动回滚，before/after 仅展示 |
| 雪花主键+触发器兜底 | 避免自增撞号；v2 IF 版适配 MySQL8 NEW.id 预分配 |
| 单号序列 UPDATE+INSERT+重试 | MySQL 并发唯一键的标准处理（其他方案均实测失败） |
| 软删为主 | 可恢复；但 BOM 唯一键冲突 → 硬删例外（先清后插） |
| 报工打卡原子 INSERT...SELECT | 防同员工并发重复开启 |
| 模块栏(56px 竖排)已删除 | 用户否决（象过河布局迭代后定稿） |

## 用户偏好（合作方式）

- **中文交流**；回答用对比表格；交付前功能必须实测通过
- 实用主义、小厂内部用不考虑安全；补丁>3 次就整页重写；移动端友好（手机卡片化）
- 用户给选项时倾向"更详细"的方案，并要求上网查业界主流做法佐证；重大决策要求多角色分析
- 会对照行业软件（象过河）截图逐模块核对功能，要求"全做+参考业内标准"
- 用户会开 F12 贴 Console/Network 证据协助定位——让用户贴证据比自己猜快
- 交互方案被否两次以上：停手给最简方案（如时间选择器「现在」按钮）

## 启动速查（详见 docs/09-ops.md）

```bash
# MySQL（Windows 风格路径！）
/c/mysql/8.0.28/bin/mysqld.exe --defaults-file='C:/mysql/my.ini'
# 后端
cd /i/yawei-erp-java/backend && java -jar target/yawei-erp-0.0.1-SNAPSHOT.jar   # curl 轮询 200 就绪
# 前端（开发）
cd /i/yawei-erp-java/web && npm run dev   # 5173
# 停后端：netstat 拿 8080 真实 PID → taskkill /PID <pid> /F（bash 包装 PID 无效）
```
