# 打卡报工设计（2026-07-31）

## 背景

Java 版最初把"报工登记"做成了管理式表单：点"新建报工"→弹窗填 员工/工序/数量/日期/开始/结束/备注 共 8 个字段 → 保存。
工人每天最高频的动作变成"打开弹窗、填一堆字段"，体验倒退。旧 Python 版（`I:\erp-server\server\templates\work_reports\submit.html`）
本来就有"开始→计时→结束"的打卡流程，Java 版搬迁时丢了。

**结论：报工登记恢复打卡式，管理功能降级为管理员 tab。**

## 设计原则

- 工人路径零弹窗：选姓名 → 选工序 → 点【开始】；完工填数量点【结束】。全程 1 个页面、2 次点击
- 开工后表单锁定为只读摘要 + 大计时器，避免误改
- 计时自动扣除午休 12:00-13:00、晚餐 17:30-18:00（与老版一致）
- 结束报工自动扣工序物料库存（与"补录报工"同一套扣料逻辑）
- 同一员工同一时刻只允许一条进行中报工（防重复打卡）
- 刷新/重进页面自动恢复进行中状态和上次员工、上次工序
- **账号按姓名自动关联员工**：用户管理建员工账号时填的"姓名"与报工员工名单（work_employees）同名即自动关联（users.work_employee_id）；**名单无同名 → 自动创建同名报工员工并关联**（管理员/老板账号不创建）。工人登录打卡页自动带出本人，无需手动选姓名、无需手动关联
- **开始/结束时间可改**（默认当前时间，补录早上忘打卡等场景用）
- **开工可拍照**（最多 3 张，存 web/public/uploads/work/，写 work_report_images 表，删报工级联删除）

## 状态机

```
in_progress ──finish──▶ completed（填数量/结束时间/时长，扣料）
     │
     └──cancel──▶ 直接删行（不留脏数据，统计只认 completed）
```

`work_reports.status` 字段：`'in_progress'` / `'completed'`（补录直接 completed）。
注意：Java 统计 SQL（daily/monthly/workStats/wages）未过滤 status，靠"取消即删行"保证 in_progress 不污染统计。

## 后端 API（WorkController，前缀 /api/work）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /reports/start | 开始：**multipart/form-data**（employeeId, processId, startTime?, remark?, materialId?, materialName?, images? 多文件）；startTime 空=当前时间；重复开始被拒；images 存 web/public/uploads/work/ + 写 work_report_images |
| POST | /reports/{id}/finish | 结束：body {quantity, endTime?}；endTime 空=当前时间；自动算时长+扣料；仅 in_progress 可结束 |
| POST | /reports/{id}/cancel | 取消（删除 in_progress 行） |
| GET | /reports/in-progress?employeeId= | 进行中的报工（打卡页恢复状态） |
| GET | /reports/my?employeeId=&date= | 某员工某天已完成（"今日已完成"列表，date 空=今天，取最近50条） |
| GET | /reports/last-process?employeeId= | 最近一次报工的 process_id（打卡页预选工序） |

列表查询（GET /reports）额外返回 `image_count`（图片数）、`first_image`（首张图路径）和 `image_paths`（全部路径，逗号分隔），前端"报工记录"表格的"图片"列显示缩略图 + 数量角标，点击预览全部图。

图片生命周期：
- 打卡开工、补录都可拍照（≤3 张），存 `web/public/uploads/work/`，写 work_report_images 表
- 删除/取消报工时自动删除对应照片文件（防孤儿文件累积；旧路径 `/uploads/2026-xx/` 不删防误伤）
- `work_reports.image_count` 冗余列随图片保存同步更新（历史数据已回填）
- 旧 Python 版的历史图片已迁移到 `web/public/uploads/`（按原路径结构），历史报工图可正常显示

实现位置：
- `WorkMapper.java`：reportStart / reportFinish / reportCancel / inProgress / myReports / lastProcess
- `WorkController.java`：startReport / finishReport / cancelReport / inProgress / myReports / lastProcess

### 时长计算（calcWorkDuration）

扣除休息段（分钟偏移）：午休 `[12*60, 13*60]`、晚餐 `[17*60+30, 18*60]`。
按天循环扣除（支持跨天场景），返回"X小时Y分钟"或"Y分钟"。与前端计时器（WorkReports.vue `workSeconds`）同口径。
原 calcDuration（补录用）不扣休息段，保留不变。

### 扣料（deductMaterials，抽成私有方法，补录+打卡共用）

```
工序配置的物料(process_materials) × quantity_per_unit × 报工数量
→ 写 stock_movements：move_type='out', ref_type='work_report', ref_id=报工id
→ 备注："报工扣料#员工名-工序名"
```

## 前端（WorkReports.vue）

- **tab 结构**：`📝 报工`（打卡，默认，所有人）｜ `📋 报工记录`（管理员）｜ `📊 日报`（管理员）｜ `🗓 月报`（管理员）
  - 工人（role != admin）只渲染"报工"tab，看不到管理功能
  - `?tab=records|daily|monthly` 支持菜单直达（管理员）
- **打卡页布局**：白底卡片（宽380px，手机 100%）+ 右侧"今日已完成"表格（手机自动换行堆叠）
  - 开工前：姓名（可搜索下拉）→ 工序（按员工分组过滤，预选上次）→ 物料搜索（选填，/api/materials?keyword=）→ 备注 → 【开始】整宽大按钮
  - 开工后：只读摘要（姓名/分组/工序/开始时间）+ 计时器 + 完成数量(el-input-number) + 【结束】+ 取消报工链接
  - 计时器每秒 tick，前端也扣午休/晚餐（workSeconds），显示 `时:分:秒` 或 `分:秒`
- **记忆**：账号关联的员工优先（登录自动带出本人）；其次 `localStorage['wr_last_emp']` 上次员工；切员工自动 loadInProgress + loadMyDone + loadLastProcess
- **物料搜索**：输入防抖300ms → GET /api/materials?keyword=&size=8 → 下拉选择 → 存 materialId/materialName
- 原"新建报工"弹窗改为管理员 tab 的"补录报工"（补历史/漏记用）

## 验证记录（curl 实测，2026-07-31）

- 开始→进行中→结束 350 件 → 今日已完成出现 ✅
- 11:00 开始 13:30 结束 → 时长 = 1小时30分钟（扣午休1h）✅
- 同员工重复开始 → 被拦（"该员工已有进行中的报工"）✅
- 取消 → in_progress 清除 ✅
- 结束自动扣料：300件 × 0.01 = 3，流水 ref_type=work_report 备注"报工扣料#胡云艳-裁线" ✅
- 测试痕迹：报工记录 id 1178/1181、流水 id 3888（物料4757 黑色夹子线8#，-3）→ 待清理

## 与旧版对照（口径沿用）

| 项 | 老 Python 版 | Java 版 |
|---|---|---|
| 计时规则 | JS calcWorkSeconds 扣 [12-13],[17:30-18:00] | calcWorkDuration 同口径 |
| 开始/结束 | POST /api/work-report/start · finish/{rid} | POST /api/work/reports/start · /{id}/finish |
| 进行中恢复 | /api/work-report/in-progress | /api/work/reports/in-progress |
| 物料搜索 | /api/materials/search | /api/materials?keyword= |
| 今日已完成 | /api/work-report/my-reports | /api/work/reports/my |
| 工序按分组过滤 | /api/work-report/group-processes/{gid} | 前端本地过滤（processes 自带 group_id） |
