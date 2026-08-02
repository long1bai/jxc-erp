package com.yawei.erp.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 生产（成品入库/生产退料）数据访问 */
@Mapper
public interface ProductionMapper {

    // ============ 成品入库 ============

    @Insert("INSERT INTO production_ins (pi_no, in_date, total_quantity, total_items, remark, status) " +
            "VALUES (#{piNo}, #{inDate}, #{totalQty}, #{totalItems}, #{remark}, 'done')")
    int piInsert(@Param("piNo") String piNo, @Param("inDate") String inDate,
                 @Param("totalQty") BigDecimal totalQty, @Param("totalItems") int totalItems,
                 @Param("remark") String remark);

    @Insert("INSERT INTO production_in_items (pi_id, product_id, product_name, spec, unit, quantity, unit_cost, amount, sort_order) " +
            "VALUES (#{piId}, #{productId}, #{productName}, #{spec}, #{unit}, #{quantity}, #{unitCost}, #{amount}, #{sortOrder})")
    int piItemInsert(@Param("piId") Long piId, @Param("productId") Long productId,
                     @Param("productName") String productName, @Param("spec") String spec,
                     @Param("unit") String unit, @Param("quantity") BigDecimal quantity,
                     @Param("unitCost") BigDecimal unitCost, @Param("amount") BigDecimal amount,
                     @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT pi.* FROM production_ins pi " +
            "<where>pi.deleted = 0 <if test='kw != null and kw != \"\"'>AND (pi.pi_no LIKE CONCAT('%',#{kw},'%') OR pi.remark LIKE CONCAT('%',#{kw},'%'))</if></where> " +
            "ORDER BY pi.id DESC</script>")
    List<Map<String, Object>> piList(@Param("kw") String kw, IPage<Map<String, Object>> page);

    @Select("SELECT * FROM production_ins WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> piExists(@Param("id") Long id);

    @Select("SELECT * FROM production_in_items WHERE pi_id = #{piId} AND deleted = 0 ORDER BY sort_order, id")
    List<Map<String, Object>> piItems(@Param("piId") Long piId);

    @Update("UPDATE production_in_items SET deleted = 1 WHERE pi_id = #{piId}")
    int piItemsDelete(@Param("piId") Long piId);

    @Update("UPDATE production_ins SET deleted = 1 WHERE id = #{id}")
    int piDelete(@Param("id") Long id);

    @Select("SELECT id FROM production_ins WHERE pi_no = #{piNo} AND deleted = 0 LIMIT 1")
    Long lastPiId(@Param("piNo") String piNo);

    @Update("UPDATE production_ins SET total_quantity = #{totalQty}, total_items = #{totalItems} WHERE id = #{id}")
    int piSummaryUpdate(@Param("id") Long id, @Param("totalQty") BigDecimal totalQty,
                        @Param("totalItems") int totalItems);

    // ============ 生产退料 ============

    @Insert("INSERT INTO production_returns (prt_no, return_date, total_quantity, remark, status) " +
            "VALUES (#{prtNo}, #{returnDate}, #{totalQty}, #{remark}, 'done')")
    int prtInsert(@Param("prtNo") String prtNo, @Param("returnDate") String returnDate,
                  @Param("totalQty") BigDecimal totalQty, @Param("remark") String remark);

    @Insert("INSERT INTO production_return_items (prt_id, material_id, material_name, spec, unit, quantity, sort_order) " +
            "VALUES (#{prtId}, #{materialId}, #{materialName}, #{spec}, #{unit}, #{quantity}, #{sortOrder})")
    int prtItemInsert(@Param("prtId") Long prtId, @Param("materialId") Long materialId,
                      @Param("materialName") String materialName, @Param("spec") String spec,
                      @Param("unit") String unit, @Param("quantity") BigDecimal quantity,
                      @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT prt.* FROM production_returns prt " +
            "<where>prt.deleted = 0 <if test='kw != null and kw != \"\"'>AND (prt.prt_no LIKE CONCAT('%',#{kw},'%') OR prt.remark LIKE CONCAT('%',#{kw},'%'))</if></where> " +
            "ORDER BY prt.id DESC</script>")
    List<Map<String, Object>> prtList(@Param("kw") String kw, IPage<Map<String, Object>> page);

    @Select("SELECT * FROM production_returns WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> prtExists(@Param("id") Long id);

    @Select("SELECT * FROM production_return_items WHERE prt_id = #{prtId} AND deleted = 0 ORDER BY sort_order, id")
    List<Map<String, Object>> prtItems(@Param("prtId") Long prtId);

    @Update("UPDATE production_return_items SET deleted = 1 WHERE prt_id = #{prtId}")
    int prtItemsDelete(@Param("prtId") Long prtId);

    @Update("UPDATE production_returns SET deleted = 1 WHERE id = #{id}")
    int prtDelete(@Param("id") Long id);

    @Select("SELECT id FROM production_returns WHERE prt_no = #{prtNo} AND deleted = 0 LIMIT 1")
    Long lastPrtId(@Param("prtNo") String prtNo);

    @Update("UPDATE production_returns SET total_quantity = #{totalQty} WHERE id = #{id}")
    int prtSummaryUpdate(@Param("id") Long id, @Param("totalQty") BigDecimal totalQty);

    // ============ 统计 ============

    /** 成品入库统计（按产品×月） */
    @Select("<script>SELECT pii.product_id, COALESCE(m.name,'') AS product_name, " +
            "DATE_FORMAT(pi.in_date, '%Y-%m') AS month, " +
            "SUM(pii.quantity) AS qty, SUM(pii.amount) AS amount " +
            "FROM production_in_items pii JOIN production_ins pi ON pi.id = pii.pi_id " +
            "LEFT JOIN materials m ON m.id = pii.product_id " +
            "<where>pi.deleted = 0 AND pii.deleted = 0 " +
            "<if test='from != null and from != \"\"'>AND pi.in_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'>AND pi.in_date &lt;= #{to}</if>" +
            "</where> GROUP BY pii.product_id, m.name, month ORDER BY m.name, month</script>")
    List<Map<String, Object>> productionInStats(@Param("from") String from, @Param("to") String to);

    /** 生产退料统计（按物料×月） */
    @Select("<script>SELECT pri.material_id, COALESCE(m.name,'') AS material_name, " +
            "DATE_FORMAT(prt.return_date, '%Y-%m') AS month, SUM(pri.quantity) AS qty " +
            "FROM production_return_items pri JOIN production_returns prt ON prt.id = pri.prt_id " +
            "LEFT JOIN materials m ON m.id = pri.material_id " +
            "<where>prt.deleted = 0 AND pri.deleted = 0 " +
            "<if test='from != null and from != \"\"'>AND prt.return_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'>AND prt.return_date &lt;= #{to}</if>" +
            "</where> GROUP BY pri.material_id, m.name, month ORDER BY m.name, month</script>")
    List<Map<String, Object>> productionReturnStats(@Param("from") String from, @Param("to") String to);
}
