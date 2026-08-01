package com.yawei.erp;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 报表数据访问（月度销售、对账单） */
@Mapper
public interface ReportMapper {

    /** 月度销售汇总（某年按月） */
    @Select("SELECT DATE_FORMAT(dn_date, '%Y-%m') AS month, COUNT(*) AS doc_count, " +
            "SUM(total_quantity) AS total_quantity, SUM(total_amount) AS total_amount " +
            "FROM delivery_notes WHERE YEAR(dn_date) = #{year} " +
            "GROUP BY DATE_FORMAT(dn_date, '%Y-%m') ORDER BY month")
    List<Map<String, Object>> salesMonthly(@Param("year") int year);

    /** 某月送货单明细 */
    @Select("SELECT dn_no, dn_date, customer_name, total_quantity, total_amount " +
            "FROM delivery_notes WHERE DATE_FORMAT(dn_date, '%Y-%m') = #{month} " +
            "ORDER BY dn_date, id")
    List<Map<String, Object>> salesDetail(@Param("month") String month);

    /** 销售对账单明细（送货单 + 物料编码/规格/单位 + 订单号） */
    @Select("SELECT di.id, dn.dn_no, dn.dn_date, COALESCE(o.co_no,'') AS co_no, " +
            "COALESCE(m.code,'') AS material_code, COALESCE(m.name,'') AS material_name, " +
            "COALESCE(m.spec,'') AS spec, COALESCE(di.unit,'') AS unit, " +
            "di.quantity, di.unit_price, di.amount " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN customer_orders o ON o.id = dn.customer_order_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "WHERE dn.customer_id = #{partyId} AND dn.dn_date >= #{start} AND dn.dn_date <= #{end} " +
            "ORDER BY dn.dn_date, dn.id")
    List<Map<String, Object>> salesRecon(@Param("partyId") Long partyId,
                                         @Param("start") String start, @Param("end") String end);

    /** 采购对账单明细 */
    @Select("SELECT pi.id, po.po_no, po.po_date, '' AS co_no, " +
            "COALESCE(m.code,'') AS material_code, COALESCE(m.name,'') AS material_name, " +
            "COALESCE(m.spec,'') AS spec, COALESCE(pi.unit,'') AS unit, " +
            "pi.quantity, pi.unit_price, pi.amount " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "LEFT JOIN materials m ON m.id = pi.material_id " +
            "WHERE po.supplier_id = #{partyId} AND po.po_date >= #{start} AND po.po_date <= #{end} " +
            "ORDER BY po.po_date, po.id")
    List<Map<String, Object>> purchaseRecon(@Param("partyId") Long partyId,
                                            @Param("start") String start, @Param("end") String end);

    @Select("SELECT name, contact, phone, address FROM customers WHERE id = #{id}")
    Map<String, Object> customerInfo(@Param("id") Long id);

    @Select("SELECT name, contact, phone, address FROM suppliers WHERE id = #{id}")
    Map<String, Object> supplierInfo(@Param("id") Long id);

    /** 对账汇总（未选客户时）：按客户分组，单数+金额 */
    @Select("SELECT dn.customer_id AS party_id, COALESCE(dn.customer_name,'') AS party_name, " +
            "COUNT(DISTINCT dn.id) AS bill_count, ROUND(SUM(di.amount),2) AS total " +
            "FROM delivery_notes dn JOIN delivery_items di ON di.dn_id = dn.id " +
            "WHERE dn.dn_date >= #{start} AND dn.dn_date <= #{end} " +
            "GROUP BY dn.customer_id, dn.customer_name ORDER BY total DESC")
    List<Map<String, Object>> reconCustomerSummary(@Param("start") String start, @Param("end") String end);

    /** 对账汇总（未选供应商时）：按供应商分组，单数+金额 */
    @Select("SELECT po.supplier_id AS party_id, COALESCE(s.name,'') AS party_name, " +
            "COUNT(DISTINCT po.id) AS bill_count, ROUND(SUM(pi.amount),2) AS total " +
            "FROM purchase_orders po JOIN purchase_items pi ON pi.po_id = po.id " +
            "LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "WHERE po.po_date >= #{start} AND po.po_date <= #{end} " +
            "GROUP BY po.supplier_id, s.name ORDER BY total DESC")
    List<Map<String, Object>> reconSupplierSummary(@Param("start") String start, @Param("end") String end);

    /** 利润报表：按客户分组（销售额/成本/毛利） */
    @Select("SELECT dn.customer_id, COALESCE(c.name,'') AS customer_name, " +
            "SUM(di.amount) AS sales, " +
            "SUM(di.quantity * COALESCE(m.purchase_price,0)) AS cost, " +
            "SUM(di.amount - di.quantity * COALESCE(m.purchase_price,0)) AS profit " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN customers c ON c.id = dn.customer_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "WHERE dn.dn_date >= #{start} AND dn.dn_date <= #{end} " +
            "GROUP BY dn.customer_id, c.name ORDER BY profit DESC")
    List<Map<String, Object>> profitByCustomer(@Param("start") String start, @Param("end") String end);

    /** 利润报表：期间汇总 */
    @Select("SELECT COALESCE(SUM(di.amount),0) AS sales, " +
            "COALESCE(SUM(di.quantity * COALESCE(m.purchase_price,0)),0) AS cost, " +
            "COALESCE(SUM(di.amount - di.quantity * COALESCE(m.purchase_price,0)),0) AS profit " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "WHERE dn.dn_date >= #{start} AND dn.dn_date <= #{end}")
    Map<String, Object> profitSummary(@Param("start") String start, @Param("end") String end);

    /** 销售查询：按物料/客户/期间查送货明细 */
    @Select("<script>SELECT dn.dn_no, dn.dn_date, COALESCE(c.name,'') AS customer_name, " +
            "COALESCE(m.code,'') AS material_code, COALESCE(m.name,'') AS material_name, " +
            "COALESCE(m.spec,'') AS spec, di.quantity, di.unit_price, di.amount " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN customers c ON c.id = dn.customer_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "<where>" +
            "<if test='materialId != null'> AND di.material_id = #{materialId}</if>" +
            "<if test='customerId != null'> AND dn.customer_id = #{customerId}</if>" +
            "<if test='start != null and start != \"\"'> AND dn.dn_date &gt;= #{start}</if>" +
            "<if test='end != null and end != \"\"'> AND dn.dn_date &lt;= #{end}</if>" +
            "</where> ORDER BY dn.dn_date DESC, dn.id DESC LIMIT 500</script>")
    List<Map<String, Object>> salesQuery(@Param("materialId") Long materialId,
                                         @Param("customerId") Long customerId,
                                         @Param("start") String start, @Param("end") String end);

    // ============ 销售报表（象过河/金蝶标准维度，2026-07 新增） ============

    /** 销售统计（按客户） */
    @Select("<script>SELECT dn.customer_id, COALESCE(c.name,'') AS customer_name, COUNT(*) AS order_count, " +
            "SUM(dn.total_quantity) AS total_quantity, SUM(dn.total_amount) AS total_amount " +
            "FROM delivery_notes dn LEFT JOIN customers c ON c.id = dn.customer_id " +
            "<where>dn.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY dn.customer_id, c.name ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> salesStatsByCustomer(@Param("from") String from, @Param("to") String to);

    /** 销售统计（按经手人） */
    @Select("<script>SELECT COALESCE(NULLIF(dn.handler,''),'未填写') AS handler, COUNT(*) AS order_count, " +
            "SUM(dn.total_quantity) AS total_quantity, SUM(dn.total_amount) AS total_amount " +
            "FROM delivery_notes dn " +
            "<where>dn.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY dn.handler ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> salesStatsByHandler(@Param("from") String from, @Param("to") String to);

    /** 销售统计（按商品，含毛利） */
    @Select("<script>SELECT di.material_id, COALESCE(m.name,'') AS material_name, COALESCE(m.spec,'') AS spec, " +
            "SUM(di.quantity) AS total_quantity, SUM(di.amount) AS total_amount, " +
            "SUM(di.amount - di.quantity * COALESCE(m.purchase_price,0)) AS profit " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "<where>dn.deleted = 0 AND di.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY di.material_id, m.name, m.spec ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> salesStatsByMaterial(@Param("from") String from, @Param("to") String to);

    /** 销售统计（按仓库） */
    @Select("<script>SELECT dn.warehouse_id, COALESCE(w.name,'') AS warehouse_name, COUNT(*) AS order_count, " +
            "SUM(dn.total_quantity) AS total_quantity, SUM(dn.total_amount) AS total_amount " +
            "FROM delivery_notes dn LEFT JOIN warehouses w ON w.id = dn.warehouse_id " +
            "<where>dn.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY dn.warehouse_id, w.name ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> salesStatsByWarehouse(@Param("from") String from, @Param("to") String to);

    /** 毛利汇总（按客户：销售额/物料成本/人工成本/毛利/毛利率）
     *  人工成本 = 售出数量 × 该成品单件人工成本（报工工资合计 ÷ 报工数量，按 material_id 归集） */
    @Select("<script>SELECT dn.customer_id, COALESCE(c.name,'') AS customer_name, " +
            "SUM(di.amount) AS sales, SUM(di.quantity * COALESCE(m.purchase_price,0)) AS cost, " +
            "SUM(di.quantity * COALESCE((SELECT SUM(wr.quantity * COALESCE(p.unit_price,0)) / NULLIF(SUM(wr.quantity),0) " +
            "FROM work_reports wr JOIN processes p ON p.id = wr.process_id " +
            "WHERE wr.material_id = di.material_id AND wr.deleted = 0 AND wr.status = 'completed'),0)) AS labor_cost, " +
            "SUM(di.amount - di.quantity * COALESCE(m.purchase_price,0) " +
            "  - di.quantity * COALESCE((SELECT SUM(wr.quantity * COALESCE(p.unit_price,0)) / NULLIF(SUM(wr.quantity),0) " +
            "FROM work_reports wr JOIN processes p ON p.id = wr.process_id " +
            "WHERE wr.material_id = di.material_id AND wr.deleted = 0 AND wr.status = 'completed'),0)) AS profit, " +
            "CASE WHEN SUM(di.amount) = 0 THEN 0 ELSE ROUND(SUM(di.amount - di.quantity * COALESCE(m.purchase_price,0) " +
            "  - di.quantity * COALESCE((SELECT SUM(wr.quantity * COALESCE(p.unit_price,0)) / NULLIF(SUM(wr.quantity),0) " +
            "FROM work_reports wr JOIN processes p ON p.id = wr.process_id " +
            "WHERE wr.material_id = di.material_id AND wr.deleted = 0 AND wr.status = 'completed'),0)) / SUM(di.amount) * 100, 1) END AS margin_rate " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN customers c ON c.id = dn.customer_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "<where>dn.deleted = 0 AND di.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY dn.customer_id, c.name ORDER BY profit DESC</script>")
    List<Map<String, Object>> grossProfitByCustomer(@Param("from") String from, @Param("to") String to);

    /** 销售明细查询（按单号/客户/商品，全量 LIMIT 5000 前端分页） */
    @Select("<script>SELECT dn.dn_no, dn.dn_date, COALESCE(c.name,'') AS customer_name, " +
            "di.material_id, COALESCE(m.name,'') AS material_name, COALESCE(m.spec,'') AS spec, " +
            "COALESCE(di.unit,'') AS unit, di.quantity, di.unit_price, di.amount " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN customers c ON c.id = dn.customer_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "<where>dn.deleted = 0 AND di.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "<if test='kw != null and kw != \"\"'> AND (dn.dn_no LIKE CONCAT('%',#{kw},'%') " +
            "OR COALESCE(c.name,'') LIKE CONCAT('%',#{kw},'%') OR COALESCE(m.name,'') LIKE CONCAT('%',#{kw},'%'))</if>" +
            "</where> ORDER BY dn.dn_date DESC, dn.id DESC LIMIT 5000</script>")
    List<Map<String, Object>> salesDetailQuery(@Param("kw") String kw, @Param("from") String from,
                                               @Param("to") String to);

    /** 销售月度汇总（按月：单数/数量/销售/退货） */
    @Select("<script>SELECT DATE_FORMAT(dn.dn_date, '%Y-%m') AS month, COUNT(*) AS order_count, " +
            "SUM(dn.total_quantity) AS total_quantity, SUM(dn.total_amount) AS sales, " +
            "(SELECT COALESCE(SUM(total_amount),0) FROM sales_returns WHERE DATE_FORMAT(return_date,'%Y-%m') = DATE_FORMAT(dn.dn_date, '%Y-%m') AND deleted = 0) AS returns_amount " +
            "FROM delivery_notes dn " +
            "<where>dn.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY month, returns_amount ORDER BY month</script>")
    List<Map<String, Object>> salesMonthlyStats(@Param("from") String from, @Param("to") String to);

    /** 销售月度分析（按客户×月） */
    @Select("<script>SELECT dn.customer_id, COALESCE(c.name,'') AS customer_name, " +
            "DATE_FORMAT(dn.dn_date, '%Y-%m') AS month, " +
            "COUNT(*) AS order_count, SUM(dn.total_amount) AS amount " +
            "FROM delivery_notes dn LEFT JOIN customers c ON c.id = dn.customer_id " +
            "<where>dn.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY dn.customer_id, c.name, month ORDER BY c.name, month</script>")
    List<Map<String, Object>> salesCustomerMonthly(@Param("from") String from, @Param("to") String to);

    /** 销售月度分析（按商品×月） */
    @Select("<script>SELECT di.material_id, COALESCE(m.name,'') AS material_name, " +
            "DATE_FORMAT(dn.dn_date, '%Y-%m') AS month, " +
            "SUM(di.quantity) AS qty, SUM(di.amount) AS amount " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "<where>dn.deleted = 0 AND di.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND dn.dn_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND dn.dn_date &lt;= #{to}</if>" +
            "</where> GROUP BY di.material_id, m.name, month ORDER BY m.name, month</script>")
    List<Map<String, Object>> salesMaterialMonthly(@Param("from") String from, @Param("to") String to);

    /** 销售退货统计（按客户） */
    @Select("<script>SELECT sr.customer_id, COALESCE(c.name,'') AS customer_name, COUNT(*) AS return_count, " +
            "SUM(sr.total_quantity) AS total_quantity, SUM(sr.total_amount) AS total_amount " +
            "FROM sales_returns sr LEFT JOIN customers c ON c.id = sr.customer_id " +
            "<where>sr.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND sr.return_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND sr.return_date &lt;= #{to}</if>" +
            "</where> GROUP BY sr.customer_id, c.name ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> salesReturnsByCustomer(@Param("from") String from, @Param("to") String to);

    /** 销售退货统计（按商品） */
    @Select("<script>SELECT sri.material_id, COALESCE(m.name,'') AS material_name, COALESCE(m.spec,'') AS spec, " +
            "SUM(sri.quantity) AS qty, SUM(sri.amount) AS amount " +
            "FROM sales_return_items sri LEFT JOIN sales_returns sr ON sr.id = sri.sr_id " +
            "LEFT JOIN materials m ON m.id = sri.material_id " +
            "<where>sr.deleted = 0 AND sri.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND sr.return_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND sr.return_date &lt;= #{to}</if>" +
            "</where> GROUP BY sri.material_id, m.name, m.spec ORDER BY amount DESC</script>")
    List<Map<String, Object>> salesReturnsByMaterial(@Param("from") String from, @Param("to") String to);
}
