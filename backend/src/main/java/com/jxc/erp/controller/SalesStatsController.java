package com.jxc.erp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.mapper.ReportMapper;

/** 销售报表（象过河/金蝶标准维度：按客户/商品/仓库统计、毛利、明细查询、月度分析、退货统计） */
@RestController
@RequestMapping("/api/sales-stats")
public class SalesStatsController {

    private final ReportMapper report;

    public SalesStatsController(ReportMapper report) {
        this.report = report;
    }

    @GetMapping("/by-customer")
    public Map<String, Object> byCustomer(@RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesStatsByCustomer(from, to)));
    }

    @GetMapping("/by-material")
    public Map<String, Object> byMaterial(@RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesStatsByMaterial(from, to)));
    }

    @GetMapping("/by-warehouse")
    public Map<String, Object> byWarehouse(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesStatsByWarehouse(from, to)));
    }

    /** 销售统计（按经手人） */
    @GetMapping("/by-handler")
    public Map<String, Object> byHandler(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesStatsByHandler(from, to)));
    }

    @GetMapping("/gross-profit")
    public Map<String, Object> grossProfit(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.grossProfitByCustomer(from, to)));
    }

    @GetMapping("/detail")
    public Map<String, Object> detail(@RequestParam(required = false) String kw,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesDetailQuery(kw, from, to)));
    }

    @GetMapping("/monthly")
    public Map<String, Object> monthly(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesMonthlyStats(from, to)));
    }

    @GetMapping("/customer-monthly")
    public Map<String, Object> customerMonthly(@RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesCustomerMonthly(from, to)));
    }

    @GetMapping("/material-monthly")
    public Map<String, Object> materialMonthly(@RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesMaterialMonthly(from, to)));
    }

    @GetMapping("/returns/by-customer")
    public Map<String, Object> returnsByCustomer(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesReturnsByCustomer(from, to)));
    }

    @GetMapping("/returns/by-material")
    public Map<String, Object> returnsByMaterial(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", report.salesReturnsByMaterial(from, to)));
    }
}
