package com.yawei.erp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 仪表盘综合数据（与原 Python 版样式对齐） */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardMapper mapper;

    /** 短期内存缓存：仪表盘数据 60 秒内复用，避免每次进页面重算 16 条 SQL */
    private static final long CACHE_TTL_MS = 60_000L;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private volatile long cacheTime = 0L;

    public DashboardController(DashboardMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/data")
    public Map<String, Object> data() {
        long now = System.currentTimeMillis();
        Map<String, Object> hit = cache;
        if (now - cacheTime < CACHE_TTL_MS && !hit.isEmpty()) {
            return hit;
        }
        Map<String, Object> fresh = build();
        cache.clear();
        cache.putAll(fresh);
        cacheTime = now;
        return fresh;
    }

    private Map<String, Object> build() {
        LocalDate today = LocalDate.now();
        String start = today.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String end = today.plusMonths(1).withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return ApiResponse.ok(Map.of(
                "stats", Map.of(
                        "pending_orders", mapper.pendingOrders(),
                        "full_unshipped", mapper.fullUnshipped(),
                        "partial_orders", mapper.partialOrders(),
                        "month_sales", mapper.monthSales(start, end),
                        "month_deliveries", mapper.monthDeliveries(start, end),
                        "month_purchases", mapper.monthPurchases(start, end),
                        "month_new_orders", mapper.monthNewOrders(start, end),
                        "stock_alerts", mapper.stockAlerts(),
                        "month_payments", mapper.monthPayments(start, end),
                        "month_receipts", mapper.monthReceipts(start, end)),
                "payables", mapper.topPayables(),
                "receivables", mapper.topReceivables(),
                "pending_list", mapper.pendingOrderList(),
                "partial_list", mapper.partialOrderList(),
                "alerts", mapper.alertList(),
                "recent_orders", mapper.recentOrders(),
                "recent_deliveries", mapper.recentDeliveries()));
    }
}
