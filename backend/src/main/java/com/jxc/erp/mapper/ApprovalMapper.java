package com.jxc.erp.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/** 单据审批（可插拔：approval_config 表控制哪些单据类型需审批，默认全关） */
@Mapper
public interface ApprovalMapper {

    /** 查询单据类型是否启用审批（0=创建即生效，1=需审批） */
    @Select("SELECT COALESCE(MAX(enabled),0) FROM approval_config WHERE doc_type = #{type}")
    int approvalEnabled(@Param("type") String type);

    /** 保存审批开关（幂等 UPSERT） */
    @Update("INSERT INTO approval_config (id, doc_type, enabled) VALUES (#{id}, #{type}, #{enabled}) " +
            "ON DUPLICATE KEY UPDATE enabled = #{enabled}")
    int upsertConfig(@Param("id") Long id, @Param("type") String type, @Param("enabled") int enabled);

    /** 待审批采购单列表 */
    @Select("SELECT po.id, po.po_no, po.po_date, po.handler, po.total_quantity, po.total_amount, po.remark, po.created_at, COALESCE(s.name,'') AS supplier_name " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "WHERE po.deleted = 0 AND po.approve_status = 'pending' ORDER BY po.created_at DESC")
    List<Map<String, Object>> pendingPurchases();

    /** 待审批送货单列表 */
    @Select("SELECT dn.id, dn.dn_no, dn.dn_date, dn.handler, dn.total_quantity, dn.total_amount, dn.remark, dn.created_at, COALESCE(c.name,'') AS customer_name " +
            "FROM delivery_notes dn LEFT JOIN customers c ON c.id = dn.customer_id " +
            "WHERE dn.deleted = 0 AND dn.approve_status = 'pending' ORDER BY dn.created_at DESC")
    List<Map<String, Object>> pendingDeliveries();

    /** 采购单明细（审批通过后执行库存动作用） */
    @Select("SELECT * FROM purchase_items WHERE po_id = #{id} AND deleted = 0")
    List<Map<String, Object>> purchaseItems(@Param("id") Long id);

    /** 送货单明细 */
    @Select("SELECT * FROM delivery_items WHERE dn_id = #{id} AND deleted = 0")
    List<Map<String, Object>> deliveryItems(@Param("id") Long id);

    @Select("SELECT warehouse_id FROM purchase_orders WHERE id = #{id} AND deleted = 0")
    Long purchaseWarehouse(@Param("id") Long id);

    @Select("SELECT customer_order_id FROM delivery_notes WHERE id = #{id} AND deleted = 0")
    Long deliveryOrderRef(@Param("id") Long id);

    @Update("UPDATE purchase_orders SET approve_status = #{status}, approve_by = #{by}, approve_at = NOW() WHERE id = #{id} AND deleted = 0")
    int updatePurchaseApprove(@Param("id") Long id, @Param("status") String status, @Param("by") String by);

    @Update("UPDATE delivery_notes SET approve_status = #{status}, approve_by = #{by}, approve_at = NOW() WHERE id = #{id} AND deleted = 0")
    int updateDeliveryApprove(@Param("id") Long id, @Param("status") String status, @Param("by") String by);

    @Select("SELECT po_no FROM purchase_orders WHERE id = #{id}")
    String purchaseNo(@Param("id") Long id);

    @Select("SELECT dn_no FROM delivery_notes WHERE id = #{id}")
    String deliveryNo(@Param("id") Long id);

    /** 写库存流水（采购入库） */
    @Update("INSERT INTO stock_movements (id, material_id, warehouse_id, move_type, ref_type, ref_id, quantity, before_stock, after_stock, unit_price, amount, move_date, remark) " +
            "VALUES (#{id}, #{materialId}, #{warehouseId}, 'in', 'purchase', #{refId}, #{qty}, #{before}, #{after}, #{price}, #{amt}, #{date}, #{remark})")
    int movementIn(@Param("id") Long id, @Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId,
                   @Param("refId") Long refId, @Param("qty") java.math.BigDecimal qty,
                   @Param("before") java.math.BigDecimal before, @Param("after") java.math.BigDecimal after,
                   @Param("price") java.math.BigDecimal price, @Param("amt") java.math.BigDecimal amt,
                   @Param("date") String date, @Param("remark") String remark);

    /** 写库存流水（销售出货） */
    @Update("INSERT INTO stock_movements (id, material_id, warehouse_id, move_type, ref_type, ref_id, quantity, before_stock, after_stock, unit_price, amount, move_date, remark) " +
            "VALUES (#{id}, #{materialId}, #{warehouseId}, 'out', 'delivery', #{refId}, #{qty}, #{before}, #{after}, #{price}, #{amt}, #{date}, #{remark})")
    int movementOut(@Param("id") Long id, @Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId,
                    @Param("refId") Long refId, @Param("qty") java.math.BigDecimal qty,
                    @Param("before") java.math.BigDecimal before, @Param("after") java.math.BigDecimal after,
                    @Param("price") java.math.BigDecimal price, @Param("amt") java.math.BigDecimal amt,
                    @Param("date") String date, @Param("remark") String remark);

    @Select("SELECT COALESCE(SUM(CASE WHEN move_type='in' THEN quantity ELSE -quantity END),0) FROM stock_movements WHERE material_id = #{materialId} AND deleted = 0")
    java.math.BigDecimal currentStock(@Param("materialId") Long materialId);

    @Select("SELECT COALESCE(MAX(id),0) + 1 FROM stock_movements")
    Long nextMovementId();
}
