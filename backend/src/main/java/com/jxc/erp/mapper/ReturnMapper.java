package com.jxc.erp.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 采购退货数据访问 */
@Mapper
public interface ReturnMapper {

    @Insert("INSERT INTO purchase_returns (pr_no, supplier_id, supplier_name, return_date, po_id, total_quantity, total_amount, remark) " +
            "VALUES (#{prNo}, #{supplierId}, #{supplierName}, #{returnDate}, #{poId}, #{totalQty}, #{totalAmt}, #{remark})")
    int returnInsert(@Param("prNo") String prNo, @Param("supplierId") Long supplierId,
                     @Param("supplierName") String supplierName, @Param("returnDate") String returnDate,
                     @Param("poId") Long poId, @Param("totalQty") BigDecimal totalQty,
                     @Param("totalAmt") BigDecimal totalAmt, @Param("remark") String remark);

    @Insert("INSERT INTO purchase_return_items (pr_id, material_id, material_name, spec, unit, quantity, unit_price, amount, sort_order) " +
            "VALUES (#{prId}, #{materialId}, #{materialName}, #{spec}, #{unit}, #{quantity}, #{unitPrice}, #{amount}, #{sortOrder})")
    int returnItemInsert(@Param("prId") Long prId, @Param("materialId") Long materialId,
                         @Param("materialName") String materialName, @Param("spec") String spec,
                         @Param("unit") String unit, @Param("quantity") BigDecimal quantity,
                         @Param("unitPrice") BigDecimal unitPrice, @Param("amount") BigDecimal amount,
                         @Param("sortOrder") int sortOrder);

    @Select("<script>SELECT pr.* FROM purchase_returns pr " +
            "<where>pr.deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(pr.pr_no LIKE CONCAT('%',#{kw},'%') OR pr.supplier_name LIKE CONCAT('%',#{kw},'%'))</if></where> " +
            "ORDER BY pr.id DESC</script>")
    List<Map<String, Object>> returnList(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    @Select("SELECT * FROM purchase_returns WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> returnExists(@Param("id") Long id);

    @Select("SELECT * FROM purchase_return_items WHERE pr_id = #{prId} AND deleted = 0 ORDER BY sort_order, id")
    List<Map<String, Object>> returnItems(@Param("prId") Long prId);

    @Update("UPDATE purchase_return_items SET deleted = 1 WHERE pr_id = #{prId}")
    int returnItemsDelete(@Param("prId") Long prId);

    @Update("UPDATE purchase_returns SET deleted = 1 WHERE id = #{id}")
    int returnDelete(@Param("id") Long id);

    @Select("SELECT id FROM purchase_returns WHERE pr_no = #{prNo} AND deleted = 0 LIMIT 1")
    Long lastReturnId(@Param("prNo") String prNo);
}
