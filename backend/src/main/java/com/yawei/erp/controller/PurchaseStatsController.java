package com.yawei.erp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.mapper.TradeMapper;

/** 采购报表（象过河/金蝶标准维度：按供应商/商品/仓库统计、明细查询、月度分析、价格趋势、退货统计） */
@RestController
@RequestMapping("/api/purchase-stats")
public class PurchaseStatsController {

    private final TradeMapper trade;

    public PurchaseStatsController(TradeMapper trade) {
        this.trade = trade;
    }

    /** 采购统计（按供应商） */
    @GetMapping("/by-supplier")
    public Map<String, Object> bySupplier(@RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchaseStatsBySupplier(from, to)));
    }

    /** 采购统计（按商品） */
    @GetMapping("/by-material")
    public Map<String, Object> byMaterial(@RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchaseStatsByMaterial(from, to)));
    }

    /** 采购统计（按仓库） */
    @GetMapping("/by-warehouse")
    public Map<String, Object> byWarehouse(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchaseStatsByWarehouse(from, to)));
    }

    /** 采购统计（按经手人） */
    @GetMapping("/by-handler")
    public Map<String, Object> byHandler(@RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchaseStatsByHandler(from, to)));
    }

    /** 采购明细查询（全量 LIMIT 5000，前端分页） */
    @GetMapping("/detail")
    public Map<String, Object> detail(@RequestParam(required = false) String kw,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchaseDetailQuery(kw, from, to)));
    }

    /** 采购月度汇总 */
    @GetMapping("/monthly")
    public Map<String, Object> monthly(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchaseMonthly(from, to)));
    }

    /** 采购商品月度分析（商品×月） */
    @GetMapping("/material-monthly")
    public Map<String, Object> materialMonthly(@RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchaseMaterialMonthly(from, to)));
    }

    /** 供应商供货月度分析（供应商×月） */
    @GetMapping("/supplier-monthly")
    public Map<String, Object> supplierMonthly(@RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.supplierMonthly(from, to)));
    }

    /** 采购价格趋势（商品×月 均价） */
    @GetMapping("/price-trend")
    public Map<String, Object> priceTrend(@RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.purchasePriceTrend(from, to)));
    }

    /** 采购退货统计（按供应商） */
    @GetMapping("/returns/by-supplier")
    public Map<String, Object> returnsBySupplier(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.returnStatsBySupplier(from, to)));
    }

    /** 采购退货统计（按商品） */
    @GetMapping("/returns/by-material")
    public Map<String, Object> returnsByMaterial(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", trade.returnStatsByMaterial(from, to)));
    }
}
