package com.jxc.erp.controller;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.mapper.ReportMapper;

/** 报表：月度销售、对账单 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportMapper mapper;

    public ReportController(ReportMapper mapper) {
        this.mapper = mapper;
    }

    /** 月度销售汇总（年份） */
    @GetMapping("/sales-monthly")
    public Map<String, Object> salesMonthly(@RequestParam int year) {
        return ApiResponse.ok(Map.of("year", year, "items", mapper.salesMonthly(year)));
    }

    /** 某月送货单明细 */
    @GetMapping("/sales-detail")
    public Map<String, Object> salesDetail(@RequestParam String month) {
        return ApiResponse.ok(Map.of("month", month, "items", mapper.salesDetail(month)));
    }

    /** 对账单数据（销售/采购）；partyId 为空 = 全部往来单位汇总 */
    @GetMapping("/reconciliation")
    public Map<String, Object> reconciliation(
            @RequestParam(defaultValue = "sales") String type,
            @RequestParam(required = false) Long partyId,
            @RequestParam String start,
            @RequestParam String end) {
        boolean sales = !"purchase".equals(type);
        if (partyId == null) {
            var rows = sales
                    ? mapper.reconCustomerSummary(start, end)
                    : mapper.reconSupplierSummary(start, end);
            BigDecimal total = rows.stream()
                    .map(r -> (BigDecimal) r.get("total"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return ApiResponse.ok(Map.of(
                    "type", sales ? "sales" : "purchase",
                    "start", start, "end", end,
                    "items", rows, "total", total));
        }
        Map<String, Object> party = sales
                ? mapper.customerInfo(partyId)
                : mapper.supplierInfo(partyId);
        var items = sales
                ? mapper.salesRecon(partyId, start, end)
                : mapper.purchaseRecon(partyId, start, end);
        BigDecimal total = items.stream()
                .map(r -> (BigDecimal) r.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ApiResponse.ok(Map.of(
                "type", sales ? "sales" : "purchase",
                "party", party == null ? Map.of() : party,
                "start", start, "end", end,
                "items", items, "total", total));
    }

    /** 利润报表（销售额/成本/毛利，成本=物料采购价） */
    @GetMapping("/profit")
    public Map<String, Object> profit(
            @RequestParam String start,
            @RequestParam String end) {
        return ApiResponse.ok(Map.of(
                "start", start, "end", end,
                "summary", mapper.profitSummary(start, end),
                "items", mapper.profitByCustomer(start, end)));
    }

    /** 销售查询（按物料/客户/期间） */
    @GetMapping("/sales-query")
    public Map<String, Object> salesQuery(
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "") String start,
            @RequestParam(defaultValue = "") String end) {
        return ApiResponse.ok(Map.of("items", mapper.salesQuery(materialId, customerId,
                start.isBlank() ? null : start, end.isBlank() ? null : end)));
    }
}
