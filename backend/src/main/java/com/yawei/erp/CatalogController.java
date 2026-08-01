package com.yawei.erp;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统目录接口（业内规范：菜单/字典/配置由后端下发，前端不硬编码业务数据）
 *  - /api/menus：按当前用户角色过滤的菜单树
 *  - /api/dicts：数据字典（下拉数据源）
 *  - /api/config/company：公司信息（打印抬头等，存 sys_config 可改）
 */
@RestController
public class CatalogController {

    private final CatalogMapper mapper;
    private final SessionStore sessionStore;

    public CatalogController(CatalogMapper mapper, SessionStore sessionStore) {
        this.mapper = mapper;
        this.sessionStore = sessionStore;
    }

    // ============ 菜单树 ============

    public record MenuNode(String path, String title, String icon, List<String> roles, List<MenuNode> children) {}

    /** 完整菜单树（roles = 可见角色；空 = 全部角色） */
    private static final List<MenuNode> MENU_TREE = List.of(
            new MenuNode("/dashboard", "仪表盘", "Odometer", List.of(), null),

            // 报工
            new MenuNode("work", "报工", "Timer",
                    List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS),
                    List.of(
                            new MenuNode("/work/reports", "报工登记", "Timer", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/work/stats", "报工统计", "DataAnalysis", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS), null),
                            new MenuNode("/work/settings", "报工设置", "Setting", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null))),

            // 进销存（admin）
            new MenuNode("trade", "进销存", "ShoppingCart",
                    List.of(Constants.ROLE_ADMIN),
                    List.of(
                            new MenuNode("/orders", "客户订单", "Document", List.of(Constants.ROLE_ADMIN), null),
                            new MenuNode("/deliveries", "送货单", "Van", List.of(Constants.ROLE_ADMIN), null),
                            new MenuNode("/sales-returns", "销售退货", "RefreshLeft", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/purchases", "采购入库", "Box", List.of(Constants.ROLE_ADMIN), null),
                            new MenuNode("/purchase-returns", "采购退货", "RefreshLeft", List.of(Constants.ROLE_ADMIN), null))),

            // 库存报表
            new MenuNode("stock-report", "报表中心", "DataAnalysis",
                    List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS),
                    List.of(
                            new MenuNode("/stock/inventory", "库存查询", "Box", List.of(), null),
                            new MenuNode("/stock-takes", "库存盘点", "Clipboard", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/purchase-reports", "采购报表", "DataLine", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS), null),
                            new MenuNode("/sales-reports", "销售报表", "DataLine", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS), null),
                            new MenuNode("/reports", "报表中心", "DataAnalysis", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS), null),
                            new MenuNode("/bom", "BOM 配方", "Files", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null))),

            // 财务
            new MenuNode("finance", "财务", "Wallet",
                    List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS),
                    List.of(
                            new MenuNode("/finance/receivables", "应收款", "ArrowDown", List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS), null),
                            new MenuNode("/finance/payables", "应付款", "ArrowUp", List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS), null),
                            new MenuNode("/finance/vouchers?tab=receipts", "收款单", "Money", List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS), null),
                            new MenuNode("/finance/vouchers?tab=payments", "付款单", "Money", List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS), null),
                            new MenuNode("/finance/accounts", "资金账户", "Wallet", List.of(Constants.ROLE_ADMIN), null),
                            new MenuNode("/finance/funds", "收支转账", "Money", List.of(Constants.ROLE_ADMIN), null),
                            new MenuNode("/finance/account-reports", "收支报表", "DataAnalysis", List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS), null))),

            // 生产
            new MenuNode("production", "生产", "Box",
                    List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS),
                    List.of(
                            new MenuNode("/production/ins", "成品入库", "Box", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/production/returns", "生产退料", "RefreshLeft", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/production/stats", "生产统计", "DataAnalysis", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV, Constants.ROLE_BOSS), null))),

            // 员工专用顶层项
            new MenuNode("/work/reports", "报工登记", "Timer", List.of(Constants.ROLE_EMPLOYEE), null),
            new MenuNode("/stock/inventory", "库存查询", "Box", List.of(Constants.ROLE_EMPLOYEE), null),

            // AI
            new MenuNode("/ai/chat", "AI 报价", "MagicStick", List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS, Constants.ROLE_DEV), null),
            new MenuNode("/ai/photo", "拍照入库", "Camera", List.of(Constants.ROLE_ADMIN, Constants.ROLE_BOSS, Constants.ROLE_EMPLOYEE), null),

            // 基础资料
            new MenuNode("base", "基础资料", "Collection",
                    List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV),
                    List.of(
                            new MenuNode("/base/customers", "客户", "User", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/base/suppliers", "供应商", "Van", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/base/materials", "物料", "Goods", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/base/warehouses", "仓库", "House", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/base/express", "快递物流", "Van", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null))),

            // 系统
            new MenuNode("system", "系统", "Setting",
                    List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV),
                    List.of(
                            new MenuNode("/system/users", "用户管理", "User", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/system/backup", "数据备份", "FolderOpened", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null),
                            new MenuNode("/system/logs", "操作日志", "Document", List.of(Constants.ROLE_ADMIN, Constants.ROLE_DEV), null))),

            // 帮助
            new MenuNode("/help", "使用指南", "QuestionFilled", List.of(), null));

    @GetMapping("/api/menus")
    public org.springframework.http.ResponseEntity<Map<String, Object>> menus(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String auth) {
        String role = resolveRole(auth);
        // token 失效/未登录：返回 401 让前端跳登录（不能静默返回空菜单——用户会误以为菜单没了）
        if (auth == null || !auth.startsWith("Bearer ")) {
            return org.springframework.http.ResponseEntity.status(401)
                    .body(Map.of("success", false, "error", "登录已失效，请重新登录"));
        }
        var user = sessionStore.verify(auth.substring(7));
        if (user == null) {
            return org.springframework.http.ResponseEntity.status(401)
                    .body(Map.of("success", false, "error", "登录已失效，请重新登录"));
        }
        List<MenuNode> tree = new ArrayList<>();
        for (MenuNode n : MENU_TREE) {
            MenuNode filtered = filterNode(n, role);
            if (filtered != null) {
                tree.add(filtered);
            }
        }
        return org.springframework.http.ResponseEntity.ok(ApiResponse.ok(Map.of("menus", tree)));
    }

    private MenuNode filterNode(MenuNode node, String role) {
        if (node.roles() != null && !node.roles().isEmpty() && !node.roles().contains(role)) {
            return null;
        }
        List<MenuNode> kids = null;
        if (node.children() != null) {
            kids = new ArrayList<>();
            for (MenuNode c : node.children()) {
                MenuNode fc = filterNode(c, role);
                if (fc != null) {
                    kids.add(fc);
                }
            }
            if (kids.isEmpty()) {
                kids = null;
            }
        }
        return new MenuNode(node.path(), node.title(), node.icon(), node.roles(), kids);
    }

    private String resolveRole(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return "";
        }
        var user = sessionStore.verify(auth.substring(7));
        return user == null ? "" : user.role();
    }

    // ============ 数据字典 ============

    private static final Map<String, List<Map<String, String>>> DICTS = buildDicts();

    private static Map<String, List<Map<String, String>>> buildDicts() {
        Map<String, List<Map<String, String>>> d = new LinkedHashMap<>();
        d.put("income_categories", entries("废料收入", "利息", "其他收入"));
        d.put("expense_categories", entries("水电费", "房租", "运费", "人工工资", "杂费", "其他支出"));
        d.put("customer_regions", entries("珠三角", "长三角", "华东", "华北", "华南", "西南", "其他"));
        d.put("account_types", List.of(
                Map.of("value", "cash", "label", "现金"),
                Map.of("value", "bank", "label", "银行"),
                Map.of("value", "online", "label", "微信/支付宝")));
        return d;
    }

    private static List<Map<String, String>> entries(String... values) {
        List<Map<String, String>> list = new ArrayList<>();
        for (String v : values) {
            list.add(Map.of("value", v, "label", v));
        }
        return list;
    }

    @GetMapping("/api/dicts")
    public Map<String, Object> dicts() {
        return ApiResponse.ok(Map.of("dicts", DICTS));
    }

    // ============ 公司配置 ============

    @GetMapping("/api/config/company")
    public Map<String, Object> company() {
        return ApiResponse.ok(Map.of(
                "companyName", mapper.configValue("company_name"),
                "address", mapper.configValue("company_address"),
                "phone", mapper.configValue("company_phone")));
    }

    @Mapper
    public interface CatalogMapper {
        @Select("SELECT config_value FROM sys_config WHERE config_key = #{key} LIMIT 1")
        String configValue(@Param("key") String key);
    }
}
