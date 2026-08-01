# 运维文档（jxc电子 ERP v2）

> 面向唯一开发者（小亚）的日常运维手册。覆盖：启动/停止、备份/恢复、日志、故障速查、升级流程。
> 深度排障案例与实踩坑：见 `08-dev-notes.md`；技能配方 `yawei-erp-system`（references/startup-and-run.md）。

## 1. 系统拓扑与端口地图

```
┌─────────────┐  局域网    ┌──────────────┐
│ 手机/PC 浏览器 ├──────────►│ 8080 后端一体  │
│ http://192.168.1.88:8080 │  (jar: 页面+API+图片) │
└─────────────┘            └──────┬───────┘
                                  │
                      ┌───────────┼───────────┐
                      ▼           ▼           ▼
                 MySQL 3306     vite 5173    I:\yawei-uploads\
                 (yawei_erp)    (开发用)      (上传图片)
```

| 端口 | 服务 | 说明 |
|---|---|---|
| 3306 | MySQL 8 | 生产库 `yawei_erp`，root 无密码（内网小厂） |
| 8080 | 后端 jar | 一体部署：页面+API+图片同一端口 |
| 5173 | vite dev | 仅开发调试用（proxy /api、/uploads → 8080） |
| 8000 | Python v1 | 旧 ERP（I:\yawei-erp，仍在用，数据口径需对照核验） |

- 外网访问：花生壳 `9087hzlk8738.vicp.fun` → 映射到本机 8080
- 上传目录：`I:\yawei-uploads\`（报工图片 `work\`），代码通过 `WebConfig.addResourceHandlers` 映射 `/uploads/**`

## 2. 启动 / 停止 / 重启

**顺序：MySQL → 后端 jar → （开发时）vite。每步验证端口 + 真实请求，不要盲目 sleep。**

### 2.1 MySQL（3306）

```bash
# 启动（Windows 风格路径！/c/ 前缀会被 git-bash 转义成 \c\ 导致秒退）
/c/mysql/8.0.28/bin/mysqld.exe --defaults-file='C:/mysql/my.ini'
# 验证
netstat -ano | grep ':3306'
```

### 2.2 后端（8080）

```bash
cd /i/yawei-erp-java/backend && java -jar target/yawei-erp-0.0.1-SNAPSHOT.jar
# 就绪验证（curl 轮询 200，约 1-4 秒）
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/
```

**停后端（重要，别用任务管理器猜 PID）**：

```bash
PID=$(netstat -ano | grep ':8080.*LISTENING' | head -1 | awk '{print $NF}')
MSYS_NO_PATHCONV=1 taskkill /PID $PID /F
# 确认端口已释放再重新构建（否则 mvn package 报 Unable to rename ...jar.original）
```

**构建**（改动后端代码后）：

```bash
powershell -NoProfile -Command "& 'C:\maven\apache-maven-3.9.9\bin\mvn.cmd' -f 'I:\yawei-erp-java\backend\pom.xml' -DskipTests package -q"
```

**Hikari 连接池懒加载**：后端可先于 MySQL 启动；MySQL 起来后第一次 DB 请求自动恢复，**不用重启后端**。

### 2.3 前端 vite（仅开发）

```bash
cd /i/yawei-erp-java/web && npm run dev   # 5173
```

- 改前端源码：5173 普通刷新生效；**8080 部署需重新 build + 后端 jar 内置静态资源**
- 504 Outdated Optimize Dep = 旧 vite 僵尸进程占端口 → taskkill

### 2.4 完整重启

```bash
# 停 jar → 确认 8080 空 → mvn package → java -jar（后台）→ curl 200 就绪
```

## 3. 备份与恢复

### 3.1 数据库备份（最重要）

```bash
# 全量逻辑备份
/c/mysql/8.0.28/bin/mysqldump.exe -uroot yawei_erp > backup/yawei_erp_$(date +%Y%m%d).sql
# 或仅数据（不含建表）
/c/mysql/8.0.28/bin/mysqldump.exe -uroot yawei_erp --no-create-info > backup/yawei_erp_data_$(date +%Y%m%d).sql
```

- 备份目录：`I:\yawei-erp-java\backup\`（已被 .gitignore 排除）
- 上传图片单独备份：`I:\yawei-uploads\` 整个目录拷走

### 3.2 恢复（⚠️ 必读）

**恢复操作必须在临时库演练验证，真实库恢复会回滚到备份时点，丢掉备份后的所有数据。**

```bash
# 建临时库演练
/c/mysql/8.0.28/bin/mysql.exe -uroot -e "CREATE DATABASE yawei_erp_restore_test CHARACTER SET utf8mb4;"
/c/mysql/8.0.28/bin/mysql.exe -uroot yawei_erp_restore_test < backup/yawei_erp_YYYYMMDD.sql
# 对比验证后再决定是否覆盖真实库
```

完整演练流程与事故复盘：`08-dev-notes.md` 6.13 + 技能 references/restore-test-and-deployment.md。

### 3.3 旧版 H 盘智能备份（v1 时代遗留）

Python v1 时代 `smart_backup.py` 每 30 分钟检查 + 17:00 强制备份到 `H:\jxc系统备份\YYYYMMDD\`。v2 上线后建议迁移到 mysqldump 计划任务（见技能 references/legacy-python-v1.md 备份章节）。

## 4. 日志体系

| 日志 | 位置 | 内容 |
|---|---|---|
| 后端运行 | 后端进程 stdout（nohup/后台会话） | INFO 级 SQL、请求、启动信息 |
| 应用错误 | `~/.hermes/logs/errors.log`（Hermes 侧） | 后端抛错（需接入日志文件时才可见） |
| 操作审计 | `operation_logs` 表（系统菜单「操作日志」页） | 所有写操作 + 登录，自动记录 |
| v1 日志 | `I:\yawei-erp\logs\` | app/access/error/api.log |

后端日志开关：`backend/src/main/resources/application.yml` 的 MyBatis stdout 日志（StdOutImpl）。

## 5. 故障速查

| 症状 | 原因 | 处理 |
|---|---|---|
| 8080 起不来/旧代码生效 | 多轮旧 java 进程占端口 | netstat 8080 → taskkill 全部 java.exe → 确认端口空 → 再启动 |
| 后端起来但 500 | 见日志第一条异常 | 从进程输出拿**完整**堆栈（API 错误消息被截断，别只看返回体） |
| 列表搜索报 SQL 语法错误 | `<where>` 块条件间缺 AND | 检查 Mapper，第二个 `<if>` 必须自带 `AND ` 前缀（13 处同款已修） |
| 盘点/单据创建 500 且单号卡住 | sequences 与单据表失同步 / 软删占号 | 查 sequences 表 seq 与 MAX(现存单号含软删)，重同步（见 6.3） |
| vite 504 Outdated Optimize | 旧 vite 僵尸进程 | taskkill 5173 占用进程重启 |
| 上传图片 404 | 上传目录映射断 | 确认 `I:\yawei-uploads\` 存在 + WebConfig 映射 |
| 数据库磁盘满/连接满 | 连接池默认 10 | Hikari 懒加载：MySQL 恢复后第一次请求自动恢复 |
| 中文乱码 | 控制台/文件编码 | 保证 UTF-8；MySQL 连接串 charset=utf8mb4 |

## 6. 数据维护（运维常用 SQL）

### 6.1 查看残留测试/压测数据

```sql
SELECT 'customers' t, COUNT(*) c FROM customers WHERE name LIKE '%【测试】%' OR name LIKE '%【压测】%'
UNION ALL SELECT 'materials', COUNT(*) FROM materials WHERE name LIKE '%【测试】%' OR name LIKE '%【压测】%';
-- 各单据表同理（remark 前缀匹配）
```

### 6.2 清理历史软删单据（占单号唯一键的死数据）

```sql
-- 示例：盘点单。任何 deleted=1 且无业务价值的单据都可清理（先删子表再删父表）
DELETE FROM stock_take_items WHERE st_id IN (SELECT id FROM stock_takes WHERE deleted=1 AND remark LIKE '%验证%');
DELETE FROM stock_takes WHERE deleted=1 AND remark LIKE '%验证%';
```

### 6.3 单据序列重同步（软删占号卡死时）

```sql
-- 把序列重置为 MAX(现存单号，含软删)，下一号=MAX+1 永不复用
UPDATE sequences s SET seq = (
  SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(st_no,'-',-1) AS UNSIGNED)),0)
  FROM stock_takes WHERE st_no LIKE 'PD-20260801-%'
) WHERE s.prefix='PD:20260801';
```

**铁律：sequences 表永远不要手动回退**（回退必撞软删占号 → 事务回滚 → 序号卡死）。测试脚本清理逻辑已内置重同步。

## 7. 升级发布流程（改代码 → 上线）

```bash
# 1. 改代码（后端 backend/，前端 web/）
# 2. 本地验证（vite 5173 快速调试；后端重建）
# 3. 测试：python scripts/e2e_test.py（168 项）+ python scripts/stress_test.py（11 项），数据自动清理
# 4. 构建发布
cd /i/yawei-erp-java/backend && mvn -DskipTests package -q
# 5. 停旧 jar → 起新 jar → curl 200 就绪 → 登录冒烟（列表/搜索各点一下）
# 6. 更新 docs/08-dev-notes.md（改了什么、踩了什么坑）
# 7. git 提交（dev 开发 → 验证 → merge main；或 main 直接提交后 dev 快进对齐）
```

## 8. 安全边界（内网小厂，实用优先）

- 无 HTTPS、root 无密码、无防火墙白名单——**设计如此**（局域网内部使用）
- 外网仅通过花生壳映射暴露 8080，无鉴权的敏感接口注意别新增
- 数据库备份是唯一保险，**定期验证备份可恢复**（临时库演练，别等灾难发生）
