# 前端文档（Vue 3 + Vite + Element Plus）

## 1. 技术栈与约定

- Vue 3 `<script setup>` 组合式 API + Element Plus + Pinia（用户信息存 userStore）
- 请求封装：`web/src/utils/request.js`（axios 实例，带 token 拦截器、解包 `{success,data,error}`、错误自动 ElMessage）
- API 分层：`web/src/api/*.js`（base.js 基础资料 / trade.js 进销存 / stock.js 库存 / work.js 报工等）
- 路由守卫：`router/index.js` 全局守卫按 `meta.roles` 过滤（未登录跳 /login，无权限跳 /dashboard）
- 菜单按角色渲染：`Layout.vue` 4 套侧栏（employee/admin/boss/dev）
- 手机端：底部导航 + 响应式（`@media (max-width: 767px)` 表格转卡片）

## 2. 路由表（meta.roles 控制权限）

| 路径 | 页面 | roles |
|---|---|---|
| /login | Login.vue | - |
| /dashboard | Dashboard.vue | 全部 |
| /work/reports | WorkReports.vue（打卡+记录+日报+月报） | 全部（非 admin 只见打卡） |
| /work/stats | WorkStats.vue（统计/趋势/工资） | admin, dev, boss |
| /work/settings | WorkSettings.vue（分组/员工/工序/扣料） | admin, dev |
| /orders | Orders.vue | admin |
| /deliveries | Deliveries.vue + DeliveryPrint.vue(打印) | admin |
| /purchases | Purchases.vue | admin |
| /purchase-returns | PurchaseReturns.vue | admin |
| /bom | Bom.vue | admin, dev |
| /stock/inventory | Stock.vue（库存+流水+仓库筛选） | 全部 |
| /reports | Reports.vue（5 tab：月度/对账×2/利润/销售查询） | admin, boss |
| /reports/reconciliation-print | ReconciliationPrint.vue | admin, boss |
| /finance/receivables, /finance/payables | Receivables.vue, Payables.vue | admin, boss |
| /finance/vouchers | Vouchers.vue（收/付款单） | admin, boss |
| /ai/chat | AiChat.vue | admin, boss, dev |
| /ai/photo | PhotoIn.vue（拍照入库） | 全部 |
| /base/customers, /base/suppliers, /base/materials, /base/warehouses | Customers/Suppliers/Materials/Warehouse.vue | admin, dev |
| /system/users | Users.vue（4 角色管理） | admin, dev |
| /system/backup | Backup.vue | admin, dev |
| /help | Help.vue（使用指南） | 全部 |

## 3. 关键页面说明

### WorkReports.vue（最复杂，729 行）
- 4 tab：打卡报工（默认）/ 报工记录 / 日报 / 月报
- 打卡流：姓名→工序→(物料搜索下拉/备注/开始时间/拍照≤3张)→开始→计时器→完成数量/结束时间→结束
- localStorage 记住上次员工（`wr_last_emp`）；自动恢复进行中报工、预选上次工序
- 计时器前端实时算（workSeconds 扣午休/晚餐），结束后端权威计算
- 报工记录：查询(日期/分组/关键词)+分页+图片缩略图(角标×N,点击预览全部)+删除
- 补录弹窗：multipart FormData 提交（支持拍照）

### PhotoIn.vue（拍照入库）
- 两步卡：拍照→识别→核对（供应商/明细可编辑）→确认入库
- 识别结果：supplierId 可能为 null（未匹配），前端提示手动搜索选择

### Reports.vue / Finance / Vouchers
- 报表中心 5 tab、应收应付金蝶式（汇总卡+分月+单据列表+核销+发票）

### Help.vue
- 使用指南：Mermaid 流程图（`components/MermaidFlow.vue` 封装），6 张流程图定义在 Help.vue script 顶部常量（BIZ_FLOW/WORK_FLOW/PHOTO_FLOW/DELIVERY_FLOW/ORDER_FLOW/STOCK_FLOW），改文字即改图

## 4. 通用组件

| 组件 | 说明 |
|---|---|
| components/MermaidFlow.vue | Mermaid 渲染封装（props: id + code；主题 base + 蓝系变量） |
| components/Placeholder.vue | 占位页 |

## 5. 样式约定（用户偏好，改 UI 必读）

- 白底卡片（el-card shadow="never"）+ 标题栏
- 按钮统一 `size="small"`，不撑满（除打卡开始/结束按钮）
- 报工打卡：竖排表单对称、手机紧凑；开工后只读摘要+大计时器
- 统计卡：两个独立 row（每行 3 个 col-md-4）
- 侧栏：二级菜单可折叠（不常用默认收起，localStorage 记忆）；一级标题 >12px 加粗、opacity>.55
- 表格手机端：`@media(max-width:767px)` 包卡片样式
- 下拉框：保持 select 外观 + 搜索输入框（mat-search 模式）
- 图片缩略图：36px 圆角 + 红色数量角标
