package com.yawei.erp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 销售退货数据访问 */
@Mapper
public interface SalesReturnMapper {

    @Insert("INSERT INTO sales_returns (sr_no, customer_id, customer_name, return_date, order_id, total_quantity, total_amount, remark) " +
            "VALUES (#{srNo}, #{customerId}, #{customerName}, #{returnDate}, #{orderId}, #{totalQty}, #{totalAmt}, #{remark})")
    int returnInsert(@Param("srNo") String srNo, @Param("customerId") Long customerId,
                     @Param("customerName") String customerName, @Param("returnDate") String returnDate,
                     @Param("orderId") Long orderId, @Param("totalQty") BigDecimal totalQty,
                     @Param("totalAmt") BigDecimal totalAmt, @Param("remark") String remark);

    @Insert("INSERT INTO sales_return_items (sr_id, material_id, material_name, spec, unit, quantity, unit_price, amount, sort_order) " +
            "VALUES (#{srId}, #{materialId}, #{materialName}, #{spec}, #{unit}, #{quantity}, #{unitPrice}, #{amount}, #{sortOrder})")
    int returnItemInsert(@Param("srId") Long srId, @Param("materialId") Long materialId,
                         @Param("materialName") String materialName, @Param("spec") String spec,
                         @Param("unit") String unit, @Param("quantity") BigDecimal quantity,
                         @Param("unitPrice") BigDecimal unitPrice, @Param("amount") BigDecimal amount,
                         @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT sr.* FROM sales_returns sr " +
            "<where>sr.deleted = 0 <if test='kw != null and kw != \"\"'>" +
            "(sr.sr_no LIKE CONCAT('%',#{kw},'%') OR sr.customer_name LIKE CONCAT('%',#{kw},'%'))</if></where> " +
            "ORDER BY sr.id DESC</script>")
    List<Map<String, Object>> returnList(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    @Select("SELECT * FROM sales_returns WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> returnExists(@Param("id") Long id);

    @Select("SELECT * FROM sales_return_items WHERE sr_id = #{srId} AND deleted = 0 ORDER BY sort_order, id")
    List<Map<String, Object>> returnItems(@Param("srId") Long srId);

    @Update("UPDATE sales_return_items SET deleted = 1 WHERE sr_id = #{srId}")
    int returnItemsDelete(@Param("srId") Long srId);

    @Update("UPDATE sales_returns SET deleted = 1 WHERE id = #{id}")
    int returnDelete(@Param("id") Long id);

    @Select("SELECT id FROM sales_returns WHERE sr_no = #{srNo} AND deleted = 0 LIMIT 1")
    Long lastReturnId(@Param("srNo") String srNo);
}
