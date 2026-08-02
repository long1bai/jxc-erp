# 技术架构文档（jxc电子 ERP）

## 1. 技术栈

| 层 | 技术 | 版本 | 说明 |
|---|---|---|---|
| 前端 | Vue 3 + Vite + Element Plus + Pinia | Vue 3.5 / Vite 7 | `web/` 目录 |
| 前端 | Mermaid | 11.x | 使用指南流程图（改文字即改图） |
| 后端 | Java + Spring Boot | JDK 21 / Spring Boot 4.1 | `backend/` 目录 |
| 持久层 | MyBatis-Plus + MyBatis | 3.5.17 | 注解 SQL，无 XML mapper |
| 数据库 | MySQL | 8.0 | 库名 `yawei_erp`，root 密码 `REDACTED_PASSWORD`（2026-08-02 加固，存 db_secret.env） |
| AI | DashScope qwen-plus / qwen3-vl-plus | - | 报价聊天 / 拍照识别；模型/提示词可配置（ai_config.json） |

## 2. 架构总览

```
浏览器 (PC/手机)
    │  http://<host>:5173  (vite dev，局域网直接用；生产同端口)
    ▼
Vue3 SPA (web/src)          ── axios (web/src/utils/request.js) ──►  /api/*  ──►  后端 8080
    │ 路由守卫按角色(employee/admin/boss/dev)过滤
    ▼
Spring Boot (backend/src/main/java/com/yawei/erp)   ← 2026-08-02 分层重构
    ├── controller/    REST 接口（ApiResponse 统一包装 {success,data,error}；只做参数绑定）
    ├── service/      业务逻辑（Work/Photo/Finance/PoOrder/Production 已抽，CRUD 类后续）
    ├── mapper/       MyBatis 注解 SQL（含 <script> 动态 SQL）
    ├── entity/       实体（Customer/Material/Supplier/User/Warehouse/OperationLog）
    ├── dto/          record 请求/响应对象（XxxDtos 聚合类）
    ├── common/       ApiResponse/PageResult/Constants/GlobalExceptionHandler/BaseController
    ├── config/       MybatisPlusConfig/WebConfig（环境变量占位符 ${ERP_XXX:默认}）
    ├── security/     AuthInterceptor/SessionStore/PasswordUtils/TokenUtils
    ├── client/       DashScopeClient（模型/提示词从配置读）
    ├── interceptor/  OperationLogInterceptor
    ├── util/         SequenceUtil/MoneyUtils
    └── YaweiErpApplication   启动类
    ▼
MySQL 8 (yawei_erp, 27 张表; sys_config 存品牌/业务参数)
```

- 前后端分离部署：`vite dev` 时 5173 出页面（`web/vite.config.js` 代理 /api → 8080）；8080 只出 API
- 报工照片存 `web/public/uploads/work/`（vite public 目录，`/uploads/...` 直接可访问）；历史旧图在 `web/public/uploads/`（已从旧 Python 版迁移）

## 3. 目录结构

```
I:\yawei-erp-java\
├── backend/                     # Spring Boot 后端
│   ├── src/main/java/com/yawei/erp/   # 全部类平铺（Controller/Mapper/实体/配置）
│   ├── src/main/resources/application.yml
│   └── target/yawei-erp-0.0.1-SNAPSHOT.jar   # 构建产物
├── web/                         # Vue3 前端
│   ├── src/views/               # 28 个页面组件
│   ├── src/router/index.js      # 路由（meta.roles 角色控制）
│   ├── src/api/                 # axios 封装（base.js/trade.js/stock.js/...）
│   ├── src/components/          # 通用组件（MermaidFlow.vue 等）
│   └── public/uploads/          # 上传图片（报工照片 work/ + 历史图）
├── db/init.sql                  # 初始 schema（建表语句）
├── docs/                        # 文档（本目录）
└── README.md                    # 总览入口
```

## 4. 构建与启动

```bash
# 后端（注意：mvnw wrapper 本机不可用，必须用本地 Maven）
cd backend
M2_HOME='C:\maven\apache-maven-3.9.9' cmd /c "C:\\maven\\apache-maven-3.9.9\\bin\\mvn.cmd -o -DskipTests package"
# 启动（jar 被占用时先 netstat 找 8080 真实 PID 再 taskkill /PID xx /F）
"E:\Program Files\Java\jdk-21\bin\java.exe" -jar target/yawei-erp-0.0.1-SNAPSHOT.jar > server.log 2>&1

# 前端
cd web
npm run dev       # 开发（5173，局域网直接访问）
npm run build     # 生产构建（dist/）
```

生产/局域网访问用 5173（vite dev 即可）；8080 只出 API。

## 5. 关键机制

### 5.1 MyBatis-Plus 手动装配
Spring Boot 4 + MyBatis-Plus 3.5.17 自动配置失效，`MybatisPlusConfig` 手动创建 `MybatisSqlSessionFactoryBean` + 分页拦截器 + `StdOutImpl` SQL 日志（排查用，保留）。

### 5.2 统一响应
所有接口返回 `ApiResponse`：`{"success": true, "data": {...}}` 或 `{"success": false, "error": "..."}`。前端 `request.js` 拦截器解包，`error` 自动 ElMessage。

### 5.3 库存流水（核心）
- 所有库存变动写 `stock_movements`（in/out、ref_type/ref_id 关联单据、before_stock/after_stock 快照）
- 单据删除时 `movementsDeleteByRef(ref_type, ref_id)` 删流水 = 库存自动回补
- 单表操作无事务补偿问题（删除单据即回滚）

### 5.4 分页
`Page<T>` + 分页插件；`PageResult` 返回 `{items, total, page, size}`。列表 SQL 用 `<script>` 动态 SQL。

### 5.5 上传
- multipart 限制已调大：单文件 15MB / 请求 30MB（application.yml）
- 报工照片：`POST /work/reports/start`、`POST /work/reports`（补录）multipart 携带 `images`，存 `web/public/uploads/work/wr_<ts>_<n>.jpg`，写 `work_report_images` 表；删除/取消报工自动删文件（防孤儿）

### 5.6 序列号
`sequences` 表 + 事务内取号，生成单据编号（CGDD-/XSDD-/SHDD- 等前缀 + 日期 + 流水）。

## 6. 部署拓扑（现状）

单机部署（内网服务器）：
- MySQL 8 本地 3306
- Java 后端 8080（后台进程）
- vite dev 5173（长期运行提供页面）

## 7. 常见故障速查

| 现象 | 原因 | 处理 |
|---|---|---|
| 5173 打不开 | vite dev 进程挂了 | `cd web && npm run dev` 重启 |
| 8080 拒绝连接 | 后端进程没了 | 按第 4 节重启 jar |
| 构建 BUILD FAILURE（jar 锁） | 后端在跑 | `netstat -ano \| grep ":8080.*LISTEN"` 找 PID → `MSYS_NO_PATHCONV=1 taskkill /PID <pid> /F` |
| 图片上传 500 | multipart 超限（旧 jar） | 重新构建（application.yml 已调大限制） |
| 中文乱码 | 终端编码 | 代码全 UTF-8；curl 发中文 JSON 用文件方式 |
| mvnw 下载被墙 | wrapper 不可用 | 用 C:\maven\apache-maven-3.9.9 |
