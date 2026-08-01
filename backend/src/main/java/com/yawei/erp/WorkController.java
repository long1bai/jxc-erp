package com.yawei.erp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 报工模块：分组/员工/工序管理、报工登记（自动扣料）、统计 */
@RestController
@RequestMapping("/api/work")
public class WorkController {

    @org.springframework.beans.factory.annotation.Value("${app.upload-dir}")
    private String uploadDir;

    @org.springframework.beans.factory.annotation.Value("${app.legacy-web-public}")
    private String legacyWebPublic;

    private final WorkMapper mapper;
    private final SysMapper sys;
    private final TradeMapper trade;

    public WorkController(WorkMapper mapper, SysMapper sys, TradeMapper trade) {
        this.mapper = mapper;
        this.sys = sys;
        this.trade = trade;
    }

    // ============ 分组 ============

    @GetMapping("/groups")
    public Map<String, Object> groups() {
        return ApiResponse.ok(Map.of("items", mapper.groups()));
    }

    @PostMapping("/groups")
    public Map<String, Object> createGroup(@RequestBody GroupReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("分组名称不能为空");
        }
        Long id = mapper.nextId();
        mapper.groupInsert(id, req.name().trim(), req.description(), req.leaderId());
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/groups/{id}")
    public Map<String, Object> updateGroup(@PathVariable Long id, @RequestBody GroupReq req) {
        if (mapper.groupExists(id).isEmpty()) {
            return ApiResponse.fail("分组不存在");
        }
        mapper.groupUpdate(id, req.name().trim(), req.description(), req.leaderId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/groups/{id}")
    public Map<String, Object> deleteGroup(@PathVariable Long id) {
        if (mapper.groupExists(id).isEmpty()) {
            return ApiResponse.fail("分组不存在");
        }
        Long cnt = mapper.countEmployeesByGroup(id);
        if (cnt != null && cnt > 0) {
            return ApiResponse.fail("该分组下还有员工，不能删除；请先调整员工分组");
        }
        mapper.groupDelete(id);
        return ApiResponse.ok();
    }

    // ============ 员工 ============

    @GetMapping("/employees")
    public Map<String, Object> employees(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(Map.of("items", mapper.employees(keyword.trim())));
    }

    /** 生成下一个 W 流水工号（W001 起，唯一不重用；与账号自动创建同规则） */
    private String nextEmployeeNo() {
        Long max = mapper.maxEmployeeNo();
        return "W" + String.format("%03d", (max == null ? 0 : max) + 1);
    }

    @PostMapping("/employees")
    public Map<String, Object> createEmployee(@RequestBody EmployeeReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("员工姓名不能为空");
        }
        String gname = req.groupId() == null ? "" : sys.groupName(req.groupId());
        Long id = mapper.nextId();
        // 工号：留空自动生成（W+3位流水 W001 起，唯一不重用）；也可手动填
        String uname = (req.username() == null || req.username().isBlank())
                ? nextEmployeeNo()
                : req.username().trim();
        mapper.employeeInsert(id, uname,
                req.password() == null ? "" : req.password(), req.name().trim(),
                req.phone(), req.groupId(), gname == null ? "" : gname,
                req.role() == null ? "operator" : req.role());
        // 反向联动：姓名与登录账号同名 → 自动绑定（仅未绑定账号时）
        mapper.linkEmployeeByName(req.name().trim(), id);
        return ApiResponse.ok(Map.of("id", id, "username", uname));
    }

    @PutMapping("/employees/{id}")
    public Map<String, Object> updateEmployee(@PathVariable Long id, @RequestBody EmployeeReq req) {
        if (mapper.employeeExists(id).isEmpty()) {
            return ApiResponse.fail("员工不存在");
        }
        String gname = req.groupId() == null ? "" : sys.groupName(req.groupId());
        // 工号：留空自动生成（W+3位流水）；也可手动改
        String uname = (req.username() == null || req.username().isBlank())
                ? nextEmployeeNo()
                : req.username().trim();
        mapper.employeeUpdate(id, uname, req.name().trim(), req.phone(), req.groupId(),
                gname == null ? "" : gname, req.role() == null ? "operator" : req.role());
        return ApiResponse.ok();
    }

    @DeleteMapping("/employees/{id}")
    public Map<String, Object> deleteEmployee(@PathVariable Long id) {
        if (mapper.employeeExists(id).isEmpty()) {
            return ApiResponse.fail("员工不存在");
        }
        mapper.employeeDelete(id);
        return ApiResponse.ok();
    }

    // ============ 工序 ============

    @GetMapping("/processes")
    public Map<String, Object> processes(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(Map.of("items", mapper.processes(keyword.trim())));
    }

    @PostMapping("/processes")
    public Map<String, Object> createProcess(@RequestBody ProcessReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("工序名称不能为空");
        }
        Long id = mapper.nextId();
        mapper.processInsert(id, req.name().trim(), req.description(), req.groupId(),
                req.sortOrder() == null ? 0 : req.sortOrder(),
                req.unitPrice() == null ? BigDecimal.ZERO : req.unitPrice());
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/processes/{id}")
    public Map<String, Object> updateProcess(@PathVariable Long id, @RequestBody ProcessReq req) {
        if (mapper.processExists(id).isEmpty()) {
            return ApiResponse.fail("工序不存在");
        }
        mapper.processUpdate(id, req.name().trim(), req.description(), req.groupId(),
                req.sortOrder() == null ? 0 : req.sortOrder(),
                req.unitPrice() == null ? BigDecimal.ZERO : req.unitPrice());
        return ApiResponse.ok();
    }

    @DeleteMapping("/processes/{id}")
    public Map<String, Object> deleteProcess(@PathVariable Long id) {
        if (mapper.processExists(id).isEmpty()) {
            return ApiResponse.fail("工序不存在");
        }
        mapper.processDelete(id);
        return ApiResponse.ok();
    }

    // ============ 工序扣料配置 ============

    @GetMapping("/process-materials")
    public Map<String, Object> processMaterials(@RequestParam Long processId) {
        return ApiResponse.ok(Map.of("items", mapper.processMaterials(processId)));
    }

    @PostMapping("/process-materials")
    public Map<String, Object> addProcessMaterial(@RequestBody PMReq req) {
        if (req.processId() == null || req.materialId() == null || req.quantityPerUnit() == null) {
            return ApiResponse.fail("参数不完整");
        }
        Long id = mapper.nextId();
        mapper.pmInsert(id, req.processId(), req.materialId(), req.quantityPerUnit());
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/process-materials/{id}")
    public Map<String, Object> deleteProcessMaterial(@PathVariable Long id) {
        mapper.pmDelete(id);
        return ApiResponse.ok();
    }

    // ============ 报工 ============

    @GetMapping("/reports")
    public Map<String, Object> reports(
            @RequestParam(defaultValue = "") String date,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.reports(
                date.isBlank() ? null : date, groupId, employeeId, keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    /** 提交报工（补录：自动算时长、自动扣工序物料库存；支持拍照上传） */
    @PostMapping(value = "/reports", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> createReport(
            @RequestParam Long employeeId,
            @RequestParam Long processId,
            @RequestParam java.math.BigDecimal quantity,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String reportDate,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String materialName,
            @RequestParam(value = "images", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> images) throws Exception {
        if (employeeId == null || processId == null
                || quantity == null || quantity.signum() <= 0) {
            return ApiResponse.fail("请选择员工、工序并填写数量");
        }
        var emp = mapper.employeeExists(employeeId);
        if (emp.isEmpty()) {
            return ApiResponse.fail("员工不存在");
        }
        var proc = mapper.processExists(processId);
        if (proc.isEmpty()) {
            return ApiResponse.fail("工序不存在");
        }
        // 冗余字段（员工/工序的名称与分组）；group_id=0 视为未分组（存 NULL 避免外键冲突）
        Long groupId = null;
        Object gidObj = emp.get(0).get("group_id");
        if (gidObj != null && ((Number) gidObj).longValue() > 0) {
            groupId = ((Number) gidObj).longValue();
        }
        String groupName = emp.get(0).get("group_name") == null ? "" : (String) emp.get(0).get("group_name");
        String empName = emp.get(0).get("name") == null ? "" : (String) emp.get(0).get("name");
        String procName = proc.get(0).get("name") == null ? "" : (String) proc.get(0).get("name");

        String rd = reportDate == null || reportDate.isBlank() ? today() : reportDate;
        String duration = calcDuration(rd, startTime, endTime);

        Long reportId = mapper.nextId();
        mapper.reportInsert(reportId, employeeId, empName, groupId, groupName == null ? "" : groupName,
                processId, procName, quantity, startTime, endTime,
                duration, remark, rd, materialId, materialName);

        saveImages(reportId, images);
        deductMaterials(reportId, processId, quantity, empName, procName);
        return ApiResponse.ok(Map.of("id", reportId));
    }

    // ============ 打卡报工（开始/结束/取消） ============

    /** 开始报工：创建 in_progress 记录（数量 0），同一员工同一时刻只允许一条；支持拍照上传 */
    @PostMapping(value = "/reports/start", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> startReport(
            @RequestParam Long employeeId,
            @RequestParam Long processId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) String materialName,
            @RequestParam(value = "images", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> images) throws Exception {
        if (employeeId == null || processId == null) {
            return ApiResponse.fail("请选择员工和工序");
        }
        var emp = mapper.employeeExists(employeeId);
        if (emp.isEmpty()) {
            return ApiResponse.fail("员工不存在");
        }
        var proc = mapper.processExists(processId);
        if (proc.isEmpty()) {
            return ApiResponse.fail("工序不存在");
        }
        if (!mapper.inProgress(employeeId).isEmpty()) {
            return ApiResponse.fail("该员工已有进行中的报工，请先结束");
        }
        Long groupId = null;
        Object gidObj = emp.get(0).get("group_id");
        if (gidObj != null && ((Number) gidObj).longValue() > 0) {
            groupId = ((Number) gidObj).longValue();
        }
        String groupName = emp.get(0).get("group_name") == null ? "" : (String) emp.get(0).get("group_name");
        String empName = emp.get(0).get("name") == null ? "" : (String) emp.get(0).get("name");
        String procName = proc.get(0).get("name") == null ? "" : (String) proc.get(0).get("name");

        String st = startTime == null || startTime.isBlank() ? now() : startTime;
        String reportDate = st.contains(" ") ? st.substring(0, 10) : today();

        Long reportId = mapper.nextId();
        int n = mapper.reportStart(reportId, employeeId, empName, groupId, groupName == null ? "" : groupName,
                processId, procName, st, remark, reportDate,
                materialId, materialName);
        if (n == 0) {
            // 原子插入未命中（并发下已有进行中报工），与上面预检结果一致时也走友好提示
            return ApiResponse.fail("该员工已有进行中的报工，请先结束");
        }

        saveImages(reportId, images);
        return ApiResponse.ok(Map.of("id", reportId));
    }

    /** 结束报工：填数量 → 自动算时长（扣除午休/晚餐）→ 自动扣工序物料库存 */
    @PostMapping("/reports/{id}/finish")
    @Transactional
    public Map<String, Object> finishReport(@PathVariable Long id, @RequestBody FinishReq req) {
        if (req.quantity() == null || req.quantity().signum() <= 0) {
            return ApiResponse.fail("请填写完成数量");
        }
        var rows = mapper.reportExists(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("报工记录不存在");
        }
        if (!"in_progress".equals(String.valueOf(rows.get(0).get("status")))) {
            return ApiResponse.fail("该报工已结束");
        }
        String endTime = req.endTime() == null || req.endTime().isBlank() ? now() : req.endTime();
        String duration = calcWorkDuration(String.valueOf(rows.get(0).get("report_date")),
                String.valueOf(rows.get(0).get("start_time")), endTime);
        mapper.reportFinish(id, req.quantity(), endTime, duration);

        // 自动扣料：工序配置的物料 × 每单位消耗 × 数量
        Long processId = ((Number) rows.get(0).get("process_id")).longValue();
        String empName = rows.get(0).get("employee_name") == null ? "" : String.valueOf(rows.get(0).get("employee_name"));
        String procName = rows.get(0).get("process_name") == null ? "" : String.valueOf(rows.get(0).get("process_name"));
        deductMaterials(id, processId, req.quantity(), empName, procName);
        return ApiResponse.ok(Map.of("id", id, "duration", duration));
    }

    /** 取消进行中的报工（逻辑删除；图片文件保留——回收站模式） */
    @PostMapping("/reports/{id}/cancel")
    @Transactional
    public Map<String, Object> cancelReport(@PathVariable Long id) {
        int n = mapper.reportCancel(id);
        if (n == 0) {
            return ApiResponse.fail("没有进行中的报工可取消");
        }
        return ApiResponse.ok();
    }

    /** 某员工进行中的报工（打卡页刷新恢复状态） */
    @GetMapping("/reports/in-progress")
    public Map<String, Object> inProgress(@RequestParam Long employeeId) {
        var items = mapper.inProgress(employeeId);
        if (items.isEmpty()) {
            return ApiResponse.ok(Map.of("items", java.util.Collections.emptyList()));
        }
        return ApiResponse.ok(Map.of("items", items));
    }

    /** 某员工某天已完成报工（打卡页"今日已完成"） */
    @GetMapping("/reports/my")
    public Map<String, Object> myReports(@RequestParam Long employeeId,
                                         @RequestParam(defaultValue = "") String date) {
        String d = date.isBlank() ? today() : date;
        return ApiResponse.ok(Map.of("date", d, "items", mapper.myReports(employeeId, d)));
    }

    /** 员工最近一次报工的工序 id（打卡页预选） */
    @GetMapping("/reports/last-process")
    public Map<String, Object> lastProcess(@RequestParam Long employeeId) {
        var rows = mapper.lastProcess(employeeId);
        return ApiResponse.ok(Map.of("processId", rows.isEmpty() ? null : rows.get(0).get("process_id")));
    }

    /** 保存报工照片（可选）：{app.upload-dir}/work/（WebConfig 静态映射 /uploads/** 供访问）；同步 image_count 列 */
    private void saveImages(Long reportId, java.util.List<org.springframework.web.multipart.MultipartFile> images) throws Exception {
        if (images == null || images.isEmpty()) {
            return;
        }
        java.nio.file.Path dir = java.nio.file.Path.of(uploadDir, "work");
        java.nio.file.Files.createDirectories(dir);
        int sort = 0;
        for (var f : images) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            String original = f.getOriginalFilename() == null ? "photo.jpg" : f.getOriginalFilename();
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
            if (ext.length() > 6 || ext.contains("/") || ext.contains("\\")) {
                ext = ".jpg";
            }
            String filename = "wr_" + System.currentTimeMillis() + "_" + (sort) + ext;
            f.transferTo(dir.resolve(filename).toFile());
            mapper.imageInsert(reportId, "/uploads/work/" + filename, sort);
            sort++;
        }
        mapper.updateImageCount(reportId, sort);
    }

    /** 删除报工的照片文件（删除/取消报工时清理，防孤儿文件累积） */
    private void deleteImageFiles(Long reportId) {
        try {
            var rows = mapper.imagesByReport(reportId);
            for (var row : rows) {
                Object p = row.get("image_path");
                if (p == null) {
                    continue;
                }
                String path = String.valueOf(p);
                if (!path.startsWith("/uploads/work/")) {
                    continue;  // 旧路径（/uploads/2026-xx/）不删，防误伤历史数据
                }
                java.nio.file.Path file = java.nio.file.Path.of(legacyWebPublic + path);
                java.nio.file.Files.deleteIfExists(file);
            }
        } catch (Exception ignored) {
            // 文件删除失败不影响主流程（记录删除照常进行）
        }
    }

    /** 工序物料扣库存（报工完成时调用；记录出入库流水） */
    private void deductMaterials(Long reportId, Long processId, BigDecimal quantity,
                                 String empName, String procName) {
        var pms = mapper.processMaterials(processId);
        for (var pm : pms) {
            Long materialId = ((Number) pm.get("material_id")).longValue();
            BigDecimal perUnit = (BigDecimal) pm.get("quantity_per_unit");
            BigDecimal consume = perUnit.multiply(quantity).setScale(3, RoundingMode.HALF_UP);
            BigDecimal before = stock(materialId);
            BigDecimal after = before.subtract(consume);
            trade.movementInsert(materialId, "out", "work_report", reportId,
                    consume, before, after, BigDecimal.ZERO, BigDecimal.ZERO,
                    today(), "报工扣料#" + empName + "-" + procName);
        }
    }

    /** 删除报工（逻辑删除，库存自动回补；图片文件保留——回收站模式，可恢复） */
    @DeleteMapping("/reports/{id}")
    @Transactional
    public Map<String, Object> deleteReport(@PathVariable Long id) {
        var rows = mapper.reportExists(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("报工记录不存在");
        }
        mapper.reportDelete(id);
        trade.movementsDeleteByRef("work_report", id);
        return ApiResponse.ok();
    }

    // ============ 统计 ============

    /** 日报表：按员工汇总数量/工资 */
    @GetMapping("/stats/daily")
    public Map<String, Object> dailyStats(@RequestParam(defaultValue = "") String date) {
        String d = date.isBlank() ? today() : date;
        return ApiResponse.ok(Map.of("date", d, "items", mapper.dailyStats(d)));
    }

    /** 月报表：按员工汇总 */
    @GetMapping("/stats/monthly")
    public Map<String, Object> monthlyStats(@RequestParam(defaultValue = "") String month) {
        String m = month.isBlank() ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) : month;
        return ApiResponse.ok(Map.of("month", m, "items", mapper.monthlyStats(m)));
    }

    /** 统计报告（含日均/趋势：前后半段日均对比，≥4天有意义） */
    @GetMapping("/stats-data")
    public Map<String, Object> statsData(
            @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long processId,
            @RequestParam(required = false) Long employeeId) {
        var rows = mapper.workStats(from.isBlank() ? null : from, to.isBlank() ? null : to,
                groupId, processId, employeeId);
        for (var row : rows) {
            row.put("employee_name", row.get("employee_name") == null ? "" : row.get("employee_name"));
            row.put("group_name", row.get("group_name") == null ? "" : row.get("group_name"));
            row.put("process_name", row.get("process_name") == null ? "" : row.get("process_name"));
            BigDecimal qty = (BigDecimal) row.get("total_qty");
            int days = ((Number) row.get("work_days")).intValue();
            // 日均 = 总数量 ÷ 实际报工天数
            row.put("daily_avg", days > 0 ? qty.divide(BigDecimal.valueOf(days), 1, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
            // 趋势：每日产量前后半段对比
            Long empId = ((Number) row.get("employee_id")).longValue();
            String pname = (String) row.get("process_name");
            var daily = mapper.dailyTrend(empId, pname,
                    from.isBlank() ? null : from, to.isBlank() ? null : to);
            int n = daily.size();
            if (n >= 4) {
                int half = n / 2;
                double sum1 = daily.subList(0, half).stream().mapToDouble(d -> ((BigDecimal) d.get("daily_qty")).doubleValue()).sum();
                double sum2 = daily.subList(half, n).stream().mapToDouble(d -> ((BigDecimal) d.get("daily_qty")).doubleValue()).sum();
                double avg1 = sum1 / half;
                double avg2 = sum2 / (n - half);
                if (avg1 > 0) {
                    double change = (avg2 - avg1) / avg1;
                    row.put("trend_change", Math.round(change * 1000) / 10.0);
                    row.put("trend_dir", change > 0.15 ? "up" : change < -0.15 ? "down" : "flat");
                } else {
                    row.put("trend_change", 0);
                    row.put("trend_dir", "flat");
                }
            } else {
                row.put("trend_change", 0);
                row.put("trend_dir", "none");
            }
        }
        return ApiResponse.ok(Map.of("items", rows));
    }

    /** 每日产量趋势（按天，用于折线/柱状图） */
    @GetMapping("/trend")
    public Map<String, Object> trend(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "") String processName,
            @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to) {
        return ApiResponse.ok(Map.of("items", mapper.dailyTrend(employeeId,
                processName.isBlank() ? null : processName,
                from.isBlank() ? null : from, to.isBlank() ? null : to)));
    }

    /** 计件工资汇总（按员工×工序，月度） */
    @GetMapping("/stats/wages")
    public Map<String, Object> wages(@RequestParam(defaultValue = "") String month,
                                     @RequestParam(required = false) Long groupId,
                                     @RequestParam(required = false) Long employeeId) {
        String m = month.isBlank() ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) : month;
        var rows = mapper.wageSummary(m, groupId, employeeId);
        // 汇总：按员工聚合（员工→工序明细）
        java.util.LinkedHashMap<Long, Map<String, Object>> emps = new java.util.LinkedHashMap<>();
        BigDecimal totalWage = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal maxWage = BigDecimal.ZERO;
        for (var row : rows) {
            row.put("employee_name", row.get("employee_name") == null ? "" : row.get("employee_name"));
            row.put("group_name", row.get("group_name") == null ? "" : row.get("group_name"));
            row.put("process_name", row.get("process_name") == null ? "" : row.get("process_name"));
            Long empId = ((Number) row.get("employee_id")).longValue();
            BigDecimal amount = (BigDecimal) row.get("amount");
            BigDecimal qty = (BigDecimal) row.get("qty");
            totalWage = totalWage.add(amount);
            totalQty = totalQty.add(qty);
            maxWage = maxWage.max(amount);
            var emp = emps.computeIfAbsent(empId, k -> {
                var e = new java.util.HashMap<String, Object>();
                e.put("employee_id", empId);
                e.put("employee_name", row.get("employee_name"));
                e.put("group_name", row.get("group_name"));
                e.put("total_amount", BigDecimal.ZERO);
                e.put("total_qty", BigDecimal.ZERO);
                e.put("details", new java.util.ArrayList<Object>());
                return e;
            });
            emp.put("total_amount", ((BigDecimal) emp.get("total_amount")).add(amount));
            emp.put("total_qty", ((BigDecimal) emp.get("total_qty")).add(qty));
            @SuppressWarnings("unchecked")
            var details = (java.util.List<Object>) emp.get("details");
            details.add(row);
        }
        return ApiResponse.ok(Map.of("month", m, "employees", emps.values(),
                "summary", Map.of("total_wage", totalWage, "total_qty", totalQty,
                        "people", emps.size(), "max_wage", maxWage)));
    }

    // ============ 工具 ============

    private String calcDuration(String reportDate, String start, String end) {
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            return "";
        }
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime s = LocalDateTime.parse(full(reportDate, start), fmt);
            LocalDateTime e = LocalDateTime.parse(full(reportDate, end), fmt);
            long mins = java.time.Duration.between(s, e).toMinutes();
            if (mins < 0) {
                return "";
            }
            return mins >= 60 ? (mins / 60) + "小时" + (mins % 60) + "分钟" : mins + "分钟";
        } catch (Exception ex) {
            return "";
        }
    }

    /** 打卡计时：扣除午休 12:00-13:00、晚餐 17:30-18:00（与老版报工口径一致） */
    private String calcWorkDuration(String reportDate, String start, String end) {
        if (start == null || end == null || start.isBlank() || end.isBlank()) {
            return "";
        }
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime s = LocalDateTime.parse(full(reportDate, start), fmt);
            LocalDateTime e = LocalDateTime.parse(full(reportDate, end), fmt);
            long totalMin = java.time.Duration.between(s, e).toMinutes();
            if (totalMin < 0) {
                return "";
            }
            // 休息段（分钟偏移）：午休 12:00-13:00，晚餐 17:30-18:00
            long[][] breaks = {{12 * 60, 13 * 60}, {17 * 60 + 30, 18 * 60}};
            long workMin = totalMin;
            LocalDateTime cursor = s;
            LocalDateTime stop = e;
            while (cursor.isBefore(stop)) {
                int sm = cursor.getHour() * 60 + cursor.getMinute();
                LocalDateTime nextDay = cursor.toLocalDate().plusDays(1).atStartOfDay();
                long dayEndMin = cursor.toLocalDate().equals(stop.toLocalDate())
                        ? stop.getHour() * 60 + stop.getMinute() : 24 * 60;
                long daySpan = stop.isAfter(nextDay) ? 24 * 60 : dayEndMin;
                for (long[] b : breaks) {
                    long bs = Math.max(sm, b[0]);
                    long be = Math.min(daySpan, b[1]);
                    if (be > bs) {
                        workMin -= (be - bs);
                    }
                }
                cursor = nextDay;
            }
            if (workMin < 0) {
                workMin = 0;
            }
            return workMin >= 60 ? (workMin / 60) + "小时" + (workMin % 60) + "分钟" : workMin + "分钟";
        } catch (Exception ex) {
            return "";
        }
    }

    private String full(String reportDate, String t) {
        return t.contains(" ") ? t : reportDate + " " + t;
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private BigDecimal stock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private String today() {
        return LocalDate.now().toString();
    }

    public record GroupReq(String name, String description, Long leaderId) {}
    public record EmployeeReq(String username, String password, String name, String phone,
                              Long groupId, String role) {}
    public record ProcessReq(String name, String description, Long groupId, Integer sortOrder,
                             BigDecimal unitPrice) {}
    public record PMReq(Long processId, Long materialId, BigDecimal quantityPerUnit) {}
    public record FinishReq(BigDecimal quantity, String endTime) {}
}
