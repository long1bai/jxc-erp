package com.yawei.erp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 进销存（采购/订单/送货）数据访问 */
@Mapper
public interface TradeMapper {

    // ============ 采购单 ============

    @Select("<script>SELECT po.id, po.po_no, po.po_date, po.supplier_id, COALESCE(s.name,'') AS supplier_name, " +
            "po.total_quantity, po.total_amount, po.remark, po.created_at " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "<where>po.deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(po.po_no LIKE CONCAT('%',#{kw},'%') OR COALESCE(s.name,'') LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY po.id DESC</script>")
    List<Map<String, Object>> purchaseList(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    @Select("SELECT po.*, COALESCE(s.name,'') AS supplier_name FROM purchase_orders po " +
            "LEFT JOIN suppliers s ON s.id = po.supplier_id WHERE po.id = #{id} AND po.deleted = 0")
    List<Map<String, Object>> purchaseDetail(@Param("id") Long id);

    @Select("SELECT * FROM purchase_items WHERE po_id = #{id} AND deleted = 0 ORDER BY sort_order, id")
    List<Map<String, Object>> purchaseItems(@Param("id") Long id);

    @Insert("INSERT INTO purchase_orders (po_no, supplier_id, po_date, handler, total_quantity, total_amount, remark) " +
            "VALUES (#{poNo}, #{supplierId}, #{poDate}, #{handler}, #{totalQty}, #{totalAmt}, #{remark})")
    int purchaseInsert(@Param("poNo") String poNo, @Param("supplierId") Long supplierId,
                       @Param("poDate") String poDate, @Param("handler") String handler,
                       @Param("totalQty") java.math.BigDecimal totalQty,
                       @Param("totalAmt") java.math.BigDecimal totalAmt, @Param("remark") String remark);

    @Insert("INSERT INTO purchase_items (po_id, material_id, material_name, spec, unit, quantity, unit_price, amount, sort_order) " +
            "VALUES (#{poId}, #{materialId}, #{materialName}, #{spec}, #{unit}, #{quantity}, #{unitPrice}, #{amount}, #{sort})")
    int purchaseItemInsert(@Param("poId") Long poId, @Param("materialId") Long materialId,
                           @Param("materialName") String materialName, @Param("spec") String spec,
                           @Param("unit") String unit, @Param("quantity") java.math.BigDecimal quantity,
                           @Param("unitPrice") java.math.BigDecimal unitPrice,
                           @Param("amount") java.math.BigDecimal amount, @Param("sort") int sort);

    @Update("UPDATE purchase_orders SET deleted = 1 WHERE id = #{id}")
    int purchaseDelete(@Param("id") Long id);

    @Update("UPDATE purchase_items SET deleted = 1 WHERE po_id = #{id}")
    int purchaseItemsDelete(@Param("id") Long id);

    // ============ 客户订单 ============

    @Select("<script>SELECT o.id, o.co_no, o.order_date, o.customer_id, COALESCE(c.name,'') AS customer_name, " +
            "o.total_quantity, o.total_amount, o.delivered_quantity, o.delivered_amount, " +
            "o.status, o.remark, o.created_at " +
            "FROM customer_orders o LEFT JOIN customers c ON c.id = o.customer_id " +
            "<where>o.deleted = 0 " +
            "<if test='kw != null and kw != \"\"'>AND " +
            "(o.co_no LIKE CONCAT('%',#{kw},'%') OR COALESCE(c.name,'') LIKE CONCAT('%',#{kw},'%'))</if>" +
            "<if test='status != null and status != \"\"'> AND o.status = #{status}</if>" +
            "</where> ORDER BY o.id DESC</script>")
    List<Map<String, Object>> orderList(@Param("kw") String keyword, @Param("status") String status,
                                        IPage<Map<String, Object>> page);

    @Select("SELECT o.*, COALESCE(c.name,'') AS customer_name FROM customer_orders o " +
            "LEFT JOIN customers c ON c.id = o.customer_id WHERE o.id = #{id} AND o.deleted = 0")
    List<Map<String, Object>> orderDetail(@Param("id") Long id);

    @Select("SELECT * FROM customer_order_items WHERE co_id = #{id} AND deleted = 0 ORDER BY sort_order, id")
    List<Map<String, Object>> orderItems(@Param("id") Long id);

    @Insert("INSERT INTO customer_orders (co_no, customer_id, customer_name, order_date, total_quantity, total_amount, " +
            "delivered_quantity, delivered_amount, status, remark) " +
            "VALUES (#{coNo}, #{customerId}, #{customerName}, #{orderDate}, #{totalQty}, #{totalAmt}, 0, 0, 'pending', #{remark})")
    int orderInsert(@Param("coNo") String coNo, @Param("customerId") Long customerId,
                    @Param("customerName") String customerName, @Param("orderDate") String orderDate,
                    @Param("totalQty") java.math.BigDecimal totalQty,
                    @Param("totalAmt") java.math.BigDecimal totalAmt, @Param("remark") String remark);

    @Insert("INSERT INTO customer_order_items (co_id, material_id, material_name, spec, unit, " +
            "quantity, unit_price, amount, delivered_quantity, sort_order) " +
            "VALUES (#{coId}, #{materialId}, #{materialName}, #{spec}, #{unit}, " +
            "#{quantity}, #{unitPrice}, #{amount}, 0, #{sort})")
    int orderItemInsert(@Param("coId") Long coId, @Param("materialId") Long materialId,
                        @Param("materialName") String materialName, @Param("spec") String spec,
                        @Param("unit") String unit, @Param("quantity") java.math.BigDecimal quantity,
                        @Param("unitPrice") java.math.BigDecimal unitPrice,
                        @Param("amount") java.math.BigDecimal amount, @Param("sort") int sort);

    @Update("UPDATE customer_orders SET deleted = 1 WHERE id = #{id}")
    int orderDelete(@Param("id") Long id);

    @Update("UPDATE customer_order_items SET deleted = 1 WHERE co_id = #{id}")
    int orderItemsDelete(@Param("id") Long id);

    @Select("SELECT status, delivered_quantity FROM customer_orders WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> orderStatus(@Param("id") Long id);

    // ============ 送货单 ============

    @Select("<script>SELECT d.id, d.dn_no, d.dn_date, d.customer_id, COALESCE(c.name,'') AS customer_name, " +
            "d.customer_order_id, COALESCE(o.co_no,'') AS co_no, " +
            "d.total_quantity, d.total_amount, d.remark, d.created_at " +
            "FROM delivery_notes d " +
            "LEFT JOIN customers c ON c.id = d.customer_id " +
            "LEFT JOIN customer_orders o ON o.id = d.customer_order_id " +
            "<where>d.deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(d.dn_no LIKE CONCAT('%',#{kw},'%') OR COALESCE(c.name,'') LIKE CONCAT('%',#{kw},'%') " +
            "OR COALESCE(o.co_no,'') LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY d.id DESC</script>")
    List<Map<String, Object>> deliveryList(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    @Select("SELECT d.*, COALESCE(c.name,'') AS customer_name, COALESCE(o.co_no,'') AS co_no, " +
            "COALESCE(c.address,'') AS customer_address, COALESCE(c.contact,'') AS customer_contact, " +
            "COALESCE(c.phone,'') AS customer_phone " +
            "FROM delivery_notes d " +
            "LEFT JOIN customers c ON c.id = d.customer_id " +
            "LEFT JOIN customer_orders o ON o.id = d.customer_order_id WHERE d.id = #{id} AND d.deleted = 0")
    List<Map<String, Object>> deliveryDetail(@Param("id") Long id);

    @Select("SELECT di.*, COALESCE(m.code,'') AS material_code, COALESCE(o.co_no,'') AS order_no " +
            "FROM delivery_items di " +
            "LEFT JOIN materials m ON m.id = di.material_id " +
            "LEFT JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "LEFT JOIN customer_orders o ON o.id = dn.customer_order_id " +
            "WHERE di.dn_id = #{id} AND di.deleted = 0 ORDER BY di.sort_order, di.id")
    List<Map<String, Object>> deliveryItems(@Param("id") Long id);

    @Insert("INSERT INTO delivery_notes (dn_no, customer_id, customer_name, customer_order_id, dn_date, " +
            "handler, total_quantity, total_amount, remark) VALUES (#{dnNo}, #{customerId}, #{customerName}, #{orderId}, #{dnDate}, #{handler}, #{totalQty}, #{totalAmt}, #{remark})")
    int deliveryInsert(@Param("dnNo") String dnNo, @Param("customerId") Long customerId,
                       @Param("customerName") String customerName, @Param("orderId") Long orderId,
                       @Param("dnDate") String dnDate, @Param("handler") String handler,
                       @Param("totalQty") java.math.BigDecimal totalQty,
                       @Param("totalAmt") java.math.BigDecimal totalAmt, @Param("remark") String remark);

    @Insert("INSERT INTO delivery_items (dn_id, material_id, material_name, spec, unit, " +
            "quantity, unit_price, amount, sort_order) " +
            "VALUES (#{dnId}, #{materialId}, #{materialName}, #{spec}, #{unit}, #{quantity}, #{unitPrice}, #{amount}, #{sort})")
    int deliveryItemInsert(@Param("dnId") Long dnId, @Param("materialId") Long materialId,
                           @Param("materialName") String materialName, @Param("spec") String spec,
                           @Param("unit") String unit, @Param("quantity") java.math.BigDecimal quantity,
                           @Param("unitPrice") java.math.BigDecimal unitPrice,
                           @Param("amount") java.math.BigDecimal amount, @Param("sort") int sort);

    @Update("UPDATE delivery_notes SET deleted = 1 WHERE id = #{id}")
    int deliveryDelete(@Param("id") Long id);

    @Update("UPDATE delivery_items SET deleted = 1 WHERE dn_id = #{id}")
    int deliveryItemsDelete(@Param("id") Long id);

    @Update("UPDATE stock_movements SET deleted = 1 WHERE ref_type = #{refType} AND ref_id = #{id}")
    int movementsDeleteByRef(@Param("refType") String refType, @Param("id") Long id);

    @Insert("INSERT INTO stock_movements (material_id, warehouse_id, move_type, ref_type, ref_id, " +
            "quantity, before_stock, after_stock, unit_price, amount, move_date, remark) " +
            "VALUES (#{materialId}, 1, #{moveType}, #{refType}, #{refId}, #{quantity}, #{before}, #{after}, #{unitPrice}, #{amount}, #{moveDate}, #{remark})")
    int movementInsert(@Param("materialId") Long materialId, @Param("moveType") String moveType,
                       @Param("refType") String refType, @Param("refId") Long refId,
                       @Param("quantity") java.math.BigDecimal quantity,
                       @Param("before") java.math.BigDecimal before,
                       @Param("after") java.math.BigDecimal after,
                       @Param("unitPrice") java.math.BigDecimal unitPrice,
                       @Param("amount") java.math.BigDecimal amount,
                       @Param("moveDate") String moveDate, @Param("remark") String remark);

    /** 带仓库的入库流水（采购入库选仓用；warehouseId 为空默认 1 号仓） */
    @Insert("INSERT INTO stock_movements (material_id, warehouse_id, move_type, ref_type, ref_id, " +
            "quantity, before_stock, after_stock, unit_price, amount, move_date, remark) " +
            "VALUES (#{materialId}, COALESCE(#{warehouseId},1), #{moveType}, #{refType}, #{refId}, " +
            "#{quantity}, #{before}, #{after}, #{unitPrice}, #{amount}, #{moveDate}, #{remark})")
    int movementInsertWh(@Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId,
                          @Param("moveType") String moveType,
                          @Param("refType") String refType, @Param("refId") Long refId,
                          @Param("quantity") java.math.BigDecimal quantity,
                          @Param("before") java.math.BigDecimal before,
                          @Param("after") java.math.BigDecimal after,
                          @Param("unitPrice") java.math.BigDecimal unitPrice,
                          @Param("amount") java.math.BigDecimal amount,
                          @Param("moveDate") String moveDate, @Param("remark") String remark);

    @Select("SELECT customer_order_id FROM delivery_notes WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> deliveryOrderRef(@Param("id") Long id);

    @Select("SELECT COALESCE(SUM(quantity),0) AS qty, COALESCE(SUM(amount),0) AS amt " +
            "FROM delivery_items di JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "WHERE dn.customer_order_id = #{orderId} AND dn.deleted = 0")
    Map<String, Object> deliveryAgg(@Param("orderId") Long orderId);

    @Update("UPDATE customer_orders SET delivered_quantity = #{qty}, delivered_amount = #{amt}, status = #{status} WHERE id = #{id}")
    int orderUpdateDelivered(@Param("id") Long id, @Param("qty") java.math.BigDecimal qty,
                             @Param("amt") java.math.BigDecimal amt, @Param("status") String status);

    @Update("UPDATE customer_order_items i SET i.delivered_quantity = COALESCE((" +
            "SELECT SUM(di.quantity) FROM delivery_items di " +
            "JOIN delivery_notes dn ON dn.id = di.dn_id " +
            "WHERE dn.customer_order_id = #{orderId} AND di.material_id = i.material_id AND dn.deleted = 0), 0) " +
            "WHERE i.co_id = #{orderId}")
    int orderItemsSyncDelivered(@Param("orderId") Long orderId);

    @Select("SELECT total_quantity FROM customer_orders WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> orderTotal(@Param("id") Long id);

    @Select("SELECT quantity, delivered_quantity FROM customer_order_items WHERE co_id = #{orderId} AND material_id = #{materialId} AND deleted = 0")
    List<Map<String, Object>> orderItemDelivered(@Param("orderId") Long orderId, @Param("materialId") Long materialId);

    @Select("SELECT COUNT(*) FROM settlements WHERE ref_type = 'delivery' AND ref_id = #{id} AND deleted = 0")
    long deliverySettledCount(@Param("id") Long id);

    @Select("<script>SELECT o.id, o.co_no, o.order_date, o.total_quantity, o.delivered_quantity, " +
            "o.total_amount, o.delivered_amount, COALESCE(c.name,'') AS customer_name " +
            "FROM customer_orders o LEFT JOIN customers c ON c.id = o.customer_id " +
            "WHERE o.deleted = 0 AND o.status IN ('pending','partial')" +
            "<if test='customerId != null'> AND o.customer_id = #{customerId}</if>" +
            " ORDER BY o.id DESC LIMIT 200</script>")
    List<Map<String, Object>> openOrders(@Param("customerId") Long customerId);

    /** 按单据号回查 id（触发器生成雪花主键后 LAST_INSERT_ID 不可用） */
    @Select("SELECT id FROM customer_orders WHERE co_no = #{coNo} AND deleted = 0 LIMIT 1")
    Long lastOrderId(@Param("coNo") String coNo);

    @Select("SELECT id FROM delivery_notes WHERE dn_no = #{dnNo} AND deleted = 0 LIMIT 1")
    Long lastDeliveryId(@Param("dnNo") String dnNo);

    @Select("SELECT id FROM purchase_orders WHERE po_no = #{poNo} AND deleted = 0 LIMIT 1")
    Long lastPurchaseId(@Param("poNo") String poNo);

    // ============ 采购报表（象过河/金蝶标准维度） ============

    /** 采购统计（按供应商）：单数/数量/金额 */
    @Select("<script>SELECT po.supplier_id, COALESCE(s.name,'') AS supplier_name, " +
            "COUNT(*) AS order_count, SUM(po.total_quantity) AS total_quantity, SUM(po.total_amount) AS total_amount " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "<where>po.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY po.supplier_id, s.name ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> purchaseStatsBySupplier(@Param("from") String from, @Param("to") String to);

    /** 采购统计（按经手人） */
    @Select("<script>SELECT COALESCE(NULLIF(po.handler,''),'未填写') AS handler, " +
            "COUNT(*) AS order_count, SUM(po.total_quantity) AS total_quantity, SUM(po.total_amount) AS total_amount " +
            "FROM purchase_orders po " +
            "<where>po.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY po.handler ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> purchaseStatsByHandler(@Param("from") String from, @Param("to") String to);

    /** 采购统计（按商品）：数量/金额/均价 */
    @Select("<script>SELECT pi.material_id, COALESCE(m.name,'') AS material_name, COALESCE(m.spec,'') AS spec, " +
            "SUM(pi.quantity) AS total_quantity, SUM(pi.amount) AS total_amount, " +
            "CASE WHEN SUM(pi.quantity) = 0 THEN 0 ELSE ROUND(SUM(pi.amount) / SUM(pi.quantity), 4) END AS avg_price " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "LEFT JOIN materials m ON m.id = pi.material_id " +
            "<where>po.deleted = 0 AND pi.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY pi.material_id, m.name, m.spec ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> purchaseStatsByMaterial(@Param("from") String from, @Param("to") String to);

    /** 采购统计（按仓库）：单数/数量/金额 */
    @Select("<script>SELECT po.warehouse_id, COALESCE(w.name,'') AS warehouse_name, COUNT(*) AS order_count, " +
            "SUM(po.total_quantity) AS total_quantity, SUM(po.total_amount) AS total_amount " +
            "FROM purchase_orders po LEFT JOIN warehouses w ON w.id = po.warehouse_id " +
            "<where>po.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY po.warehouse_id, w.name ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> purchaseStatsByWarehouse(@Param("from") String from, @Param("to") String to);

    /** 采购明细查询（按单号/供应商/商品，分页） */
    @Select("<script>SELECT po.po_no, po.po_date, COALESCE(s.name,'') AS supplier_name, " +
            "pi.material_id, COALESCE(m.name,'') AS material_name, COALESCE(m.spec,'') AS spec, " +
            "COALESCE(pi.unit,'') AS unit, pi.quantity, pi.unit_price, pi.amount " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "LEFT JOIN materials m ON m.id = pi.material_id " +
            "<where>po.deleted = 0 AND pi.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "<if test='kw != null and kw != \"\"'> AND (po.po_no LIKE CONCAT('%',#{kw},'%') " +
            "OR COALESCE(s.name,'') LIKE CONCAT('%',#{kw},'%') OR COALESCE(m.name,'') LIKE CONCAT('%',#{kw},'%'))</if>" +
            "</where> ORDER BY po.po_date DESC, po.id DESC LIMIT 5000</script>")
    List<Map<String, Object>> purchaseDetailQuery(@Param("kw") String kw, @Param("from") String from,
                                                  @Param("to") String to);

    /** 采购月度汇总（按月：单数/数量/金额） */
    @Select("<script>SELECT DATE_FORMAT(po.po_date, '%Y-%m') AS month, COUNT(*) AS order_count, " +
            "SUM(po.total_quantity) AS total_quantity, SUM(po.total_amount) AS total_amount " +
            "FROM purchase_orders po " +
            "<where>po.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY month ORDER BY month</script>")
    List<Map<String, Object>> purchaseMonthly(@Param("from") String from, @Param("to") String to);

    /** 采购商品月度分析（商品×月） */
    @Select("<script>SELECT pi.material_id, COALESCE(m.name,'') AS material_name, " +
            "DATE_FORMAT(po.po_date, '%Y-%m') AS month, " +
            "SUM(pi.quantity) AS qty, SUM(pi.amount) AS amount " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "LEFT JOIN materials m ON m.id = pi.material_id " +
            "<where>po.deleted = 0 AND pi.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY pi.material_id, m.name, month ORDER BY m.name, month</script>")
    List<Map<String, Object>> purchaseMaterialMonthly(@Param("from") String from, @Param("to") String to);

    /** 供应商供货月度分析（供应商×月） */
    @Select("<script>SELECT po.supplier_id, COALESCE(s.name,'') AS supplier_name, " +
            "DATE_FORMAT(po.po_date, '%Y-%m') AS month, COUNT(*) AS order_count, SUM(po.total_amount) AS amount " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "<where>po.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY po.supplier_id, s.name, month ORDER BY s.name, month</script>")
    List<Map<String, Object>> supplierMonthly(@Param("from") String from, @Param("to") String to);

    /** 采购价格趋势（商品×月 均价） */
    @Select("<script>SELECT pi.material_id, COALESCE(m.name,'') AS material_name, " +
            "DATE_FORMAT(po.po_date, '%Y-%m') AS month, " +
            "ROUND(AVG(pi.unit_price), 4) AS avg_price, SUM(pi.quantity) AS qty " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "LEFT JOIN materials m ON m.id = pi.material_id " +
            "<where>po.deleted = 0 AND pi.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND po.po_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND po.po_date &lt;= #{to}</if>" +
            "</where> GROUP BY pi.material_id, m.name, month ORDER BY m.name, month</script>")
    List<Map<String, Object>> purchasePriceTrend(@Param("from") String from, @Param("to") String to);

    /** 采购退货统计（按供应商） */
    @Select("<script>SELECT pr.supplier_id, COALESCE(s.name,'') AS supplier_name, COUNT(*) AS return_count, " +
            "SUM(pr.total_quantity) AS total_quantity, SUM(pr.total_amount) AS total_amount " +
            "FROM purchase_returns pr LEFT JOIN suppliers s ON s.id = pr.supplier_id " +
            "<where>pr.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND pr.return_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND pr.return_date &lt;= #{to}</if>" +
            "</where> GROUP BY pr.supplier_id, s.name ORDER BY total_amount DESC</script>")
    List<Map<String, Object>> returnStatsBySupplier(@Param("from") String from, @Param("to") String to);

    /** 采购退货统计（按商品） */
    @Select("<script>SELECT pri.material_id, COALESCE(m.name,'') AS material_name, COALESCE(m.spec,'') AS spec, " +
            "SUM(pri.quantity) AS qty, SUM(pri.amount) AS amount " +
            "FROM purchase_return_items pri LEFT JOIN purchase_returns pr ON pr.id = pri.pr_id " +
            "LEFT JOIN materials m ON m.id = pri.material_id " +
            "<where>pr.deleted = 0 AND pri.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND pr.return_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND pr.return_date &lt;= #{to}</if>" +
            "</where> GROUP BY pri.material_id, m.name, m.spec ORDER BY amount DESC</script>")
    List<Map<String, Object>> returnStatsByMaterial(@Param("from") String from, @Param("to") String to);
}
