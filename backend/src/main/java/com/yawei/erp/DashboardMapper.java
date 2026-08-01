package com.yawei.erp;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 仪表盘数据访问 */
@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM customer_orders WHERE status IN ('pending','partial')")
    long pendingOrders();

    @Select("SELECT COUNT(*) FROM customer_orders WHERE status = 'pending'")
    long fullUnshipped();

    @Select("SELECT COUNT(*) FROM customer_orders WHERE status = 'partial'")
    long partialOrders();

    @Select("SELECT COALESCE(SUM(total_amount),0) FROM delivery_notes WHERE dn_date >= #{start} AND dn_date < #{end}")
    BigDecimal monthSales(@Param("start") String start, @Param("end") String end);

    @Select("SELECT COUNT(*) FROM delivery_notes WHERE dn_date >= #{start} AND dn_date < #{end}")
    long monthDeliveries(@Param("start") String start, @Param("end") String end);

    @Select("SELECT COALESCE(SUM(total_amount),0) FROM purchase_orders WHERE po_date >= #{start} AND po_date < #{end}")
    BigDecimal monthPurchases(@Param("start") String start, @Param("end") String end);

    @Select("SELECT COUNT(*) FROM customer_orders WHERE order_date >= #{start} AND order_date < #{end}")
    long monthNewOrders(@Param("start") String start, @Param("end") String end);

    @Select("SELECT COALESCE(SUM(amount),0) FROM payment_vouchers WHERE pay_date >= #{start} AND pay_date < #{end}")
    BigDecimal monthPayments(@Param("start") String start, @Param("end") String end);

    @Select("SELECT COALESCE(SUM(amount),0) FROM receipt_vouchers WHERE receipt_date >= #{start} AND receipt_date < #{end}")
    BigDecimal monthReceipts(@Param("start") String start, @Param("end") String end);

    /** 库存预警数（当前库存 <= 最低库存；与原版 v_stock_alerts 一致）——GROUP BY 单查询，避免 N+1 子查询 */
    @Select("SELECT COUNT(*) FROM (" +
            "SELECT m.id, m.min_stock, " +
            "COALESCE(SUM(CASE WHEN sm.move_type='" + Constants.MOVE_IN + "' THEN sm.quantity WHEN sm.move_type='" + Constants.MOVE_OUT + "' THEN -sm.quantity ELSE 0 END),0) AS stock " +
            "FROM materials m LEFT JOIN stock_movements sm ON sm.material_id = m.id " +
            "GROUP BY m.id, m.min_stock " +
            "HAVING stock <= m.min_stock) t")
    long stockAlerts();

    /** 库存预警列表（前8） */
    @Select("SELECT m.id, m.code, m.name, m.min_stock, " +
            "COALESCE(SUM(CASE WHEN sm.move_type='" + Constants.MOVE_IN + "' THEN sm.quantity WHEN sm.move_type='" + Constants.MOVE_OUT + "' THEN -sm.quantity ELSE 0 END),0) AS stock " +
            "FROM materials m LEFT JOIN stock_movements sm ON sm.material_id = m.id " +
            "GROUP BY m.id, m.code, m.name, m.min_stock " +
            "HAVING stock <= m.min_stock " +
            "ORDER BY stock ASC LIMIT 8")
    List<Map<String, Object>> alertList();

    /** 待出货订单（前5） */
    @Select("SELECT o.id, o.co_no, o.order_date, o.customer_id, COALESCE(c.name,'') AS customer_name, " +
            "o.total_amount, o.delivered_quantity, o.total_quantity " +
            "FROM customer_orders o LEFT JOIN customers c ON c.id = o.customer_id " +
            "WHERE o.status = 'pending' ORDER BY o.id DESC LIMIT 5")
    List<Map<String, Object>> pendingOrderList();

    /** 部分出货订单（前5） */
    @Select("SELECT o.id, o.co_no, o.order_date, COALESCE(c.name,'') AS customer_name, " +
            "o.delivered_quantity, o.total_quantity, o.total_amount " +
            "FROM customer_orders o LEFT JOIN customers c ON c.id = o.customer_id " +
            "WHERE o.status = 'partial' ORDER BY o.id DESC LIMIT 5")
    List<Map<String, Object>> partialOrderList();

    /** 最近订单（前8） */
    @Select("SELECT o.id, o.co_no, o.order_date, COALESCE(c.name,'') AS customer_name, o.total_amount, o.status " +
            "FROM customer_orders o LEFT JOIN customers c ON c.id = o.customer_id " +
            "ORDER BY o.id DESC LIMIT 8")
    List<Map<String, Object>> recentOrders();

    /** 最近送货（前8） */
    @Select("SELECT d.id, d.dn_no, d.dn_date, COALESCE(c.name,'') AS customer_name, d.total_amount " +
            "FROM delivery_notes d LEFT JOIN customers c ON c.id = d.customer_id " +
            "ORDER BY d.id DESC LIMIT 8")
    List<Map<String, Object>> recentDeliveries();

    /** 应收前5 */
    @Select("SELECT d.customer_id AS party_id, COALESCE(c.name,'') AS party_name, " +
            "SUM(d.total_amount) - COALESCE(s.settled,0) AS balance " +
            "FROM delivery_notes d LEFT JOIN customers c ON c.id = d.customer_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='delivery' GROUP BY ref_id) s ON s.ref_id = d.id " +
            "GROUP BY d.customer_id, c.name, s.settled ORDER BY balance DESC LIMIT 5")
    List<Map<String, Object>> topReceivables();

    /** 应付前5 */
    @Select("SELECT po.supplier_id AS party_id, COALESCE(s.name,'') AS party_name, " +
            "SUM(po.total_amount) - COALESCE(p.settled,0) AS balance " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='purchase' GROUP BY ref_id) p ON p.ref_id = po.id " +
            "GROUP BY po.supplier_id, s.name, p.settled ORDER BY balance DESC LIMIT 5")
    List<Map<String, Object>> topPayables();
}
