package com.yawei.erp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yawei.erp.mapper.SysMapper;
import com.yawei.erp.mapper.TradeMapper;
import com.yawei.erp.mapper.WorkMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 报工模块业务层：分组/员工/工序管理、报工登记（自动扣料）、统计。
 * 逻辑自 WorkController 抽取（2026-08-02 企业化分层重构），行为保持不变。
 */
@Service
public class WorkService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Value("${app.legacy-web-public}")
    private String legacyWebPublic;

    private final WorkMapper mapper;
    private final SysMapper sys;
    private final TradeMapper trade;

        private final SysConfigService cfg;

public WorkService(WorkMapper mapper, SysMapper sys, TradeMapper trade, SysConfigService cfg) {
        this.mapper = mapper;
        this.cfg = cfg;
        this.sys = sys;
        this.trade = trade;
    }

    // ============ 分组 ============

    public List<Map<String, Object>> groups() {
        return mapper.groups();
    }

    public Map<String, Object> createGroup(String name, String description, Long leaderId) {
        Long id = mapper.nextId();
        mapper.groupInsert(id, name.trim(), description, leaderId);
        return Map.of("id", id);
    }

    public void updateGroup(Long id, String name, String description, Long leaderId) {
        mapper.groupUpdate(id, name.trim(), description, leaderId);
    }

    public void deleteGroup(Long id) {
        mapper.groupDelete(id);
    }

    public boolean groupExists(Long id) {
        return !mapper.groupExists(id).isEmpty();
    }

    public Long countEmployeesByGroup(Long id) {
        return mapper.countEmployeesByGroup(id);
    }

    // ============ 员工 ============

    public List<Map<String, Object>> employees(String keyword) {
        return mapper.employees(keyword.trim());
    }

    /** 生成下一个 W 流水工号（W001 起，唯一不重用；与账号自动创建同规则） */
    private String nextEmployeeNo() {
        Long max = mapper.maxEmployeeNo();
        return cfg.get("employee_no_prefix") + String.format("%03d", (max == null ? 0 : max) + 1);
    }

    public Map<String, Object> createEmployee(String username, String password, String name,
                                              String phone, Long groupId, String role) {
        String gname = groupId == null ? "" : sys.groupName(groupId);
        Long id = mapper.nextId();
        // 工号：留空自动生成（W+3位流水 W001 起，唯一不重用）；也可手动填
        String uname = (username == null || username.isBlank())
                ? nextEmployeeNo()
                : username.trim();
        mapper.employeeInsert(id, uname,
                password == null ? "" : password, name.trim(),
                phone, groupId, gname == null ? "" : gname,
                role == null ? "operator" : role);
        // 反向联动：姓名与登录账号同名 → 自动绑定（仅未绑定账号时）
        mapper.linkEmployeeByName(name.trim(), id);
        return Map.of("id", id, "username", uname);
    }

    public void updateEmployee(Long id, String username, String name, String phone,
                               Long groupId, String role) {
        String gname = groupId == null ? "" : sys.groupName(groupId);
        // 工号：留空自动生成（W+3位流水）；也可手动改
        String uname = (username == null || username.isBlank())
                ? nextEmployeeNo()
                : username.trim();
        mapper.employeeUpdate(id, uname, name.trim(), phone, groupId,
                gname == null ? "" : gname, role == null ? "operator" : role);
    }

    public void deleteEmployee(Long id) {
        mapper.employeeDelete(id);
    }

    public boolean employeeExists(Long id) {
        return !mapper.employeeExists(id).isEmpty();
    }

    // ============ 工序 ============

    public List<Map<String, Object>> processes(String keyword) {
        return mapper.processes(keyword.trim());
    }

    public Map<String, Object> createProcess(String name, String description, Long groupId,
                                             Integer sortOrder, BigDecimal unitPrice) {
        Long id = mapper.nextId();
        mapper.processInsert(id, name.trim(), description, groupId,
                sortOrder == null ? 0 : sortOrder,
                unitPrice == null ? BigDecimal.ZERO : unitPrice);
        return Map.of("id", id);
    }

    public void updateProcess(Long id, String name, String description, Long groupId,
                              Integer sortOrder, BigDecimal unitPrice) {
        mapper.processUpdate(id, name.trim(), description, groupId,
                sortOrder == null ? 0 : sortOrder,
                unitPrice == null ? BigDecimal.ZERO : unitPrice);
    }

    public void deleteProcess(Long id) {
        mapper.processDelete(id);
    }

    public boolean processExists(Long id) {
        return !mapper.processExists(id).isEmpty();
    }

    // ============ 工序扣料配置 ============

    public List<Map<String, Object>> processMaterials(Long processId) {
        return mapper.processMaterials(processId);
    }

    public Map<String, Object> addProcessMaterial(Long processId, Long materialId, BigDecimal quantityPerUnit) {
        Long id = mapper.nextId();
        mapper.pmInsert(id, processId, materialId, quantityPerUnit);
        return Map.of("id", id);
    }

    public void deleteProcessMaterial(Long id) {
        mapper.pmDelete(id);
    }

    // ============ 报工 ============

    public PageResultData reports(String date, Long groupId, Long employeeId,
                                  String keyword, int page, int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.reports(
                date.isBlank() ? null : date, groupId, employeeId, keyword.trim(), p);
        return new PageResultData(items, p.getTotal(), p.getCurrent(), p.getSize());
    }

    public record PageResultData(List<Map<String, Object>> items, long total, long page, long size) {
        public Map<String, Object> toMap() {
            return Map.of("items", items, "total", total, "page", page, "size", size);
        }
    }

    /** 提交报工（补录：自动算时长、自动扣工序物料库存；支持拍照上传） */
    @Transactional
    public Map<String, Object> createReport(Long employeeId, Long processId, BigDecimal quantity,
                                            String startTime, String endTime, String reportDate,
                                            String remark, Long materialId, String materialName,
                                            List<MultipartFile> images) throws Exception {
        var emp = mapper.employeeExists(employeeId);
        if (emp.isEmpty()) {
            return Map.of("error", "员工不存在");
        }
        var proc = mapper.processExists(processId);
        if (proc.isEmpty()) {
            return Map.of("error", "工序不存在");
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
        return Map.of("id", reportId);
    }

    // ============ 打卡报工（开始/结束/取消） ============

    /** 开始报工：创建 in_progress 记录（数量 0），同一员工同一时刻只允许一条；支持拍照上传 */
    @Transactional
    public Map<String, Object> startReport(Long employeeId, Long processId, String startTime,
                                           String remark, Long materialId, String materialName,
                                           List<MultipartFile> images) throws Exception {
        var emp = mapper.employeeExists(employeeId);
        if (emp.isEmpty()) {
            return Map.of("error", "员工不存在");
        }
        var proc = mapper.processExists(processId);
        if (proc.isEmpty()) {
            return Map.of("error", "工序不存在");
        }
        if (!mapper.inProgress(employeeId).isEmpty()) {
            return Map.of("error", "该员工已有进行中的报工，请先结束");
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
            return Map.of("error", "该员工已有进行中的报工，请先结束");
        }

        saveImages(reportId, images);
        return Map.of("id", reportId);
    }

    /** 结束报工：填数量 → 自动算时长（扣除午休/晚餐）→ 自动扣工序物料库存 */
    @Transactional
    public Map<String, Object> finishReport(Long id, BigDecimal quantity, String endTime) {
        var rows = mapper.reportExists(id);
        if (rows.isEmpty()) {
            return Map.of("error", "报工记录不存在");
        }
        if (!"in_progress".equals(String.valueOf(rows.get(0).get("status")))) {
            return Map.of("error", "该报工已结束");
        }
        String end = endTime == null || endTime.isBlank() ? now() : endTime;
        String duration = calcWorkDuration(String.valueOf(rows.get(0).get("report_date")),
                String.valueOf(rows.get(0).get("start_time")), end);
        mapper.reportFinish(id, quantity, end, duration);

        // 自动扣料：工序配置的物料 × 每单位消耗 × 数量
        Long processId = ((Number) rows.get(0).get("process_id")).longValue();
        String empName = rows.get(0).get("employee_name") == null ? "" : String.valueOf(rows.get(0).get("employee_name"));
        String procName = rows.get(0).get("process_name") == null ? "" : String.valueOf(rows.get(0).get("process_name"));
        deductMaterials(id, processId, quantity, empName, procName);
        return Map.of("id", id, "duration", duration);
    }

    /** 取消进行中的报工（逻辑删除；图片文件保留——回收站模式） */
    @Transactional
    public boolean cancelReport(Long id) {
        return mapper.reportCancel(id) > 0;
    }

    /** 某员工进行中的报工（打卡页刷新恢复状态） */
    public List<Map<String, Object>> inProgress(Long employeeId) {
        return mapper.inProgress(employeeId);
    }

    /** 某员工某天已完成报工（打卡页"今日已完成"） */
    public List<Map<String, Object>> myReports(Long employeeId, String date) {
        String d = date.isBlank() ? today() : date;
        return mapper.myReports(employeeId, d);
    }

    /** 员工最近一次报工的工序 id（打卡页预选） */
    public Long lastProcessId(Long employeeId) {
        var rows = mapper.lastProcess(employeeId);
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("process_id")).longValue();
    }

    /** 删除报工（逻辑删除，库存自动回补；图片文件保留——回收站模式，可恢复） */
    @Transactional
    public void deleteReport(Long id) {
        mapper.reportDelete(id);
        trade.movementsDeleteByRef("work_report", id);
    }

    // ============ 统计 ============

    /** 日报表：按员工汇总数量/工资 */
    public List<Map<String, Object>> dailyStats(String date) {
        String d = date.isBlank() ? today() : date;
        return mapper.dailyStats(d);
    }

    /** 月报表：按员工汇总 */
    public List<Map<String, Object>> monthlyStats(String month) {
        String m = month.isBlank() ? LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) : month;
        return mapper.monthlyStats(m);
    }

    /** 统计报告（含日均/趋势：前后半段日均对比，≥4天有意义） */
    public List<Map<String, Object>> statsData(String from, String to, Long groupId,
                                               Long processId, Long employeeId) {
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
        return rows;
    }

    /** 每日产量趋势（按天，用于折线/柱状图） */
    public List<Map<String, Object>> trend(Long employeeId, String processName, String from, String to) {
        return mapper.dailyTrend(employeeId,
                processName.isBlank() ? null : processName,
                from.isBlank() ? null : from, to.isBlank() ? null : to);
    }

    /** 计件工资汇总（按员工×工序，月度） */
    public Map<String, Object> wages(String month, Long groupId, Long employeeId) {
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
        return Map.of("month", m, "employees", emps.values(),
                "summary", Map.of("total_wage", totalWage, "total_qty", totalQty,
                        "people", emps.size(), "max_wage", maxWage));
    }

    // ============ 工具 ============

    /** 保存报工照片（可选）：{app.upload-dir}/work/（WebConfig 静态映射 /uploads/** 供访问）；同步 image_count 列 */
    private void saveImages(Long reportId, List<MultipartFile> images) throws Exception {
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
            long[][] breaks = {{cfg.getMinutes("break_lunch_start", 12*60), cfg.getMinutes("break_lunch_end", 13*60)},
                          {cfg.getMinutes("break_dinner_start", 17*60+30), cfg.getMinutes("break_dinner_end", 18*60)}};
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
}
