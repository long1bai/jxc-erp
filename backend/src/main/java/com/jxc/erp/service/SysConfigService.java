package com.jxc.erp.service;

import com.jxc.erp.controller.CatalogController.CatalogMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 系统参数服务：读 sys_config 表，带默认值兜底。
 * 2026-08-02 产品化改造：业务参数（单号前缀/默认仓库/物料分类/工号前缀/休息时段）
 * 全部可配置，买家改 sys_config 即生效，不用改代码。
 */
@Service
public class SysConfigService {

    private final CatalogMapper mapper;

    /** 默认值（与硬编码时代一致，兼容未配置的存量库） */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("seq_ie", "IE"),            // 收支单号前缀
            Map.entry("seq_zz", "ZZ"),            // 转账单号前缀
            Map.entry("seq_cgdd", "CGDD"),        // 采购入库单号前缀
            Map.entry("seq_th", "TH"),            // 采购退货单号前缀
            Map.entry("seq_rcv", "RCV"),          // 收款单号前缀
            Map.entry("seq_pay", "PAY"),          // 付款单号前缀
            Map.entry("seq_inv", "INV"),          // 发票单号前缀
            Map.entry("seq_po", "PO"),            // 采购订单号前缀
            Map.entry("default_warehouse", "1"),  // 默认仓库 id
            Map.entry("default_material_category", "原材料"),  // 自动建物料默认分类
            Map.entry("employee_no_prefix", "W"), // 员工工号前缀
            Map.entry("break_lunch_start", "12:00"),   // 午休开始
            Map.entry("break_lunch_end", "13:00"),     // 午休结束
            Map.entry("break_dinner_start", "17:30"),  // 晚餐开始
            Map.entry("break_dinner_end", "18:00"),    // 晚餐结束
            Map.entry("enable_projects", "0")          // 项目模块开关（1=开 0=关，默认关）
    );

    public SysConfigService(CatalogMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取配置值（sys_config 优先，缺省回退内置默认） */
    public String get(String key) {
        try {
            String v = mapper.configValue(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        } catch (Exception ignored) {
        }
        return DEFAULTS.getOrDefault(key, "");
    }

    /** 读取整数配置 */
    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 读取 Long 配置 */
    public Long getLong(String key, Long fallback) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 读取"HH:mm"格式配置为分钟偏移（用于休息时段计算） */
    public int getMinutes(String key, int fallbackMinutes) {
        String v = get(key);
        try {
            String[] parts = v.split(":");
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return fallbackMinutes;
        }
    }
}
