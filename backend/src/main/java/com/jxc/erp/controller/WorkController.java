package com.jxc.erp.controller;

import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.service.WorkService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 报工模块：分组/员工/工序管理、报工登记（自动扣料）、统计。
 * 业务逻辑已抽取至 WorkService（企业化分层 2026-08-02）。
 */
@RestController
@RequestMapping("/api/work")
public class WorkController {

    private final WorkService service;

    public WorkController(WorkService service) {
        this.service = service;
    }

    // ============ 分组 ============

    @GetMapping("/groups")
    public Map<String, Object> groups() {
        return ApiResponse.ok(Map.of("items", service.groups()));
    }

    @PostMapping("/groups")
    public Map<String, Object> createGroup(@RequestBody GroupReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("分组名称不能为空");
        }
        return ApiResponse.ok(service.createGroup(req.name(), req.description(), req.leaderId()));
    }

    @PutMapping("/groups/{id}")
    public Map<String, Object> updateGroup(@PathVariable Long id, @RequestBody GroupReq req) {
        if (!service.groupExists(id)) {
            return ApiResponse.fail("分组不存在");
        }
        service.updateGroup(id, req.name(), req.description(), req.leaderId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/groups/{id}")
    public Map<String, Object> deleteGroup(@PathVariable Long id) {
        if (!service.groupExists(id)) {
            return ApiResponse.fail("分组不存在");
        }
        Long cnt = service.countEmployeesByGroup(id);
        if (cnt != null && cnt > 0) {
            return ApiResponse.fail("该分组下还有员工，不能删除；请先调整员工分组");
        }
        service.deleteGroup(id);
        return ApiResponse.ok();
    }

    // ============ 员工 ============

    @GetMapping("/employees")
    public Map<String, Object> employees(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(Map.of("items", service.employees(keyword)));
    }

    @PostMapping("/employees")
    public Map<String, Object> createEmployee(@RequestBody EmployeeReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("员工姓名不能为空");
        }
        return ApiResponse.ok(service.createEmployee(req.username(), req.password(), req.name(),
                req.phone(), req.groupId(), req.role()));
    }

    @PutMapping("/employees/{id}")
    public Map<String, Object> updateEmployee(@PathVariable Long id, @RequestBody EmployeeReq req) {
        if (!service.employeeExists(id)) {
            return ApiResponse.fail("员工不存在");
        }
        service.updateEmployee(id, req.username(), req.name(), req.phone(), req.groupId(), req.role());
        return ApiResponse.ok();
    }

    @DeleteMapping("/employees/{id}")
    public Map<String, Object> deleteEmployee(@PathVariable Long id) {
        if (!service.employeeExists(id)) {
            return ApiResponse.fail("员工不存在");
        }
        service.deleteEmployee(id);
        return ApiResponse.ok();
    }

    // ============ 工序 ============

    @GetMapping("/processes")
    public Map<String, Object> processes(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(Map.of("items", service.processes(keyword)));
    }

    @PostMapping("/processes")
    public Map<String, Object> createProcess(@RequestBody ProcessReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("工序名称不能为空");
        }
        return ApiResponse.ok(service.createProcess(req.name(), req.description(), req.groupId(),
                req.sortOrder(), req.unitPrice()));
    }

    @PutMapping("/processes/{id}")
    public Map<String, Object> updateProcess(@PathVariable Long id, @RequestBody ProcessReq req) {
        if (!service.processExists(id)) {
            return ApiResponse.fail("工序不存在");
        }
        service.updateProcess(id, req.name(), req.description(), req.groupId(),
                req.sortOrder(), req.unitPrice());
        return ApiResponse.ok();
    }

    @DeleteMapping("/processes/{id}")
    public Map<String, Object> deleteProcess(@PathVariable Long id) {
        if (!service.processExists(id)) {
            return ApiResponse.fail("工序不存在");
        }
        service.deleteProcess(id);
        return ApiResponse.ok();
    }

    // ============ 工序扣料配置 ============

    @GetMapping("/process-materials")
    public Map<String, Object> processMaterials(@RequestParam Long processId) {
        return ApiResponse.ok(Map.of("items", service.processMaterials(processId)));
    }

    @PostMapping("/process-materials")
    public Map<String, Object> addProcessMaterial(@RequestBody PMReq req) {
        if (req.processId() == null || req.materialId() == null || req.quantityPerUnit() == null) {
            return ApiResponse.fail("参数不完整");
        }
        return ApiResponse.ok(service.addProcessMaterial(req.processId(), req.materialId(), req.quantityPerUnit()));
    }

    @DeleteMapping("/process-materials/{id}")
    public Map<String, Object> deleteProcessMaterial(@PathVariable Long id) {
        service.deleteProcessMaterial(id);
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
        var data = service.reports(date, groupId, employeeId, keyword, page, size);
        return ApiResponse.ok(new PageResult(data.items(), data.total(), data.page(), data.size()).toMap());
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
        var result = service.createReport(employeeId, processId, quantity,
                startTime, endTime, reportDate, remark, materialId, materialName, images);
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
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
        var result = service.startReport(employeeId, processId, startTime,
                remark, materialId, materialName, images);
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
    }

    /** 结束报工：填数量 → 自动算时长（扣除午休/晚餐）→ 自动扣工序物料库存 */
    @PostMapping("/reports/{id}/finish")
    @Transactional
    public Map<String, Object> finishReport(@PathVariable Long id, @RequestBody FinishReq req) {
        if (req.quantity() == null || req.quantity().signum() <= 0) {
            return ApiResponse.fail("请填写完成数量");
        }
        var result = service.finishReport(id, req.quantity(), req.endTime());
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
    }

    /** 取消进行中的报工（逻辑删除；图片文件保留——回收站模式） */
    @PostMapping("/reports/{id}/cancel")
    @Transactional
    public Map<String, Object> cancelReport(@PathVariable Long id) {
        if (!service.cancelReport(id)) {
            return ApiResponse.fail("没有进行中的报工可取消");
        }
        return ApiResponse.ok();
    }

    /** 某员工进行中的报工（打卡页刷新恢复状态） */
    @GetMapping("/reports/in-progress")
    public Map<String, Object> inProgress(@RequestParam Long employeeId) {
        var items = service.inProgress(employeeId);
        if (items.isEmpty()) {
            return ApiResponse.ok(Map.of("items", java.util.Collections.emptyList()));
        }
        return ApiResponse.ok(Map.of("items", items));
    }

    /** 某员工某天已完成报工（打卡页"今日已完成"） */
    @GetMapping("/reports/my")
    public Map<String, Object> myReports(@RequestParam Long employeeId,
                                         @RequestParam(defaultValue = "") String date) {
        String d = date.isBlank() ? java.time.LocalDate.now().toString() : date;
        return ApiResponse.ok(Map.of("date", d, "items", service.myReports(employeeId, d)));
    }

    /** 员工最近一次报工的工序 id（打卡页预选） */
    @GetMapping("/reports/last-process")
    public Map<String, Object> lastProcess(@RequestParam Long employeeId) {
        return ApiResponse.ok(Map.of("processId", service.lastProcessId(employeeId)));
    }

    /** 删除报工（逻辑删除，库存自动回补；图片文件保留——回收站模式，可恢复） */
    @DeleteMapping("/reports/{id}")
    @Transactional
    public Map<String, Object> deleteReport(@PathVariable Long id) {
        service.deleteReport(id);
        return ApiResponse.ok();
    }

    // ============ 统计 ============

    /** 日报表：按员工汇总数量/工资 */
    @GetMapping("/stats/daily")
    public Map<String, Object> dailyStats(@RequestParam(defaultValue = "") String date) {
        String d = date.isBlank() ? java.time.LocalDate.now().toString() : date;
        return ApiResponse.ok(Map.of("date", d, "items", service.dailyStats(d)));
    }

    /** 月报表：按员工汇总 */
    @GetMapping("/stats/monthly")
    public Map<String, Object> monthlyStats(@RequestParam(defaultValue = "") String month) {
        String m = month.isBlank() ? java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")) : month;
        return ApiResponse.ok(Map.of("month", m, "items", service.monthlyStats(m)));
    }

    /** 统计报告（含日均/趋势：前后半段日均对比，≥4天有意义） */
    @GetMapping("/stats-data")
    public Map<String, Object> statsData(
            @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long processId,
            @RequestParam(required = false) Long employeeId) {
        return ApiResponse.ok(Map.of("items", service.statsData(from, to, groupId, processId, employeeId)));
    }

    /** 每日产量趋势（按天，用于折线/柱状图） */
    @GetMapping("/trend")
    public Map<String, Object> trend(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "") String processName,
            @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to) {
        return ApiResponse.ok(Map.of("items", service.trend(employeeId, processName, from, to)));
    }

    /** 计件工资汇总（按员工×工序，月度） */
    @GetMapping("/stats/wages")
    public Map<String, Object> wages(@RequestParam(defaultValue = "") String month,
                                     @RequestParam(required = false) Long groupId,
                                     @RequestParam(required = false) Long employeeId) {
        return ApiResponse.ok(service.wages(month, groupId, employeeId));
    }

    public record GroupReq(String name, String description, Long leaderId) {}
    public record EmployeeReq(String username, String password, String name, String phone,
                              Long groupId, String role) {}
    public record ProcessReq(String name, String description, Long groupId, Integer sortOrder,
                             BigDecimal unitPrice) {}
    public record PMReq(Long processId, Long materialId, BigDecimal quantityPerUnit) {}
    public record FinishReq(BigDecimal quantity, String endTime) {}
}
