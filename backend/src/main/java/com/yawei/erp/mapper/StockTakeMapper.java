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

/** 库存盘点数据访问 */
@Mapper
public interface StockTakeMapper {

    @Insert("INSERT INTO stock_takes (st_no, take_date, warehouse_id, status, total_items, total_diff_qty, total_diff_amount, remark) " +
            "VALUES (#{stNo}, #{takeDate}, #{warehouseId}, #{status}, #{totalItems}, #{totalDiffQty}, #{totalDiffAmount}, #{remark})")
    int takeInsert(@Param("stNo") String stNo, @Param("takeDate") String takeDate,
                   @Param("warehouseId") Long warehouseId, @Param("status") String status,
                   @Param("totalItems") int totalItems, @Param("totalDiffQty") BigDecimal totalDiffQty,
                   @Param("totalDiffAmount") BigDecimal totalDiffAmount, @Param("remark") String remark);

    @Insert("INSERT INTO stock_take_items (st_id, material_id, material_name, spec, unit, book_qty, actual_qty, diff_qty, unit_cost, diff_amount) " +
            "VALUES (#{stId}, #{materialId}, #{materialName}, #{spec}, #{unit}, #{bookQty}, #{actualQty}, #{diffQty}, #{unitCost}, #{diffAmount})")
    int takeItemInsert(@Param("stId") Long stId, @Param("materialId") Long materialId,
                       @Param("materialName") String materialName, @Param("spec") String spec,
                       @Param("unit") String unit, @Param("bookQty") BigDecimal bookQty,
                       @Param("actualQty") BigDecimal actualQty, @Param("diffQty") BigDecimal diffQty,
                       @Param("unitCost") BigDecimal unitCost, @Param("diffAmount") BigDecimal diffAmount);

    @Select("<script>SELECT st.* FROM stock_takes st " +
            "<where>st.deleted = 0 " +
            "<if test='kw != null and kw != \"\"'>AND (st.st_no LIKE CONCAT('%',#{kw},'%') OR st.status LIKE CONCAT('%',#{kw},'%'))</if>" +
            "</where> ORDER BY st.id DESC</script>")
    List<Map<String, Object>> takeList(@Param("kw") String kw, IPage<Map<String, Object>> page);

    @Select("SELECT * FROM stock_takes WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> takeExists(@Param("id") Long id);

    @Select("SELECT * FROM stock_take_items WHERE st_id = #{stId} AND deleted = 0 ORDER BY id")
    List<Map<String, Object>> takeItems(@Param("stId") Long stId);

    @Update("UPDATE stock_takes SET status = #{status} WHERE id = #{id}")
    int takeUpdateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE stock_take_items SET actual_qty = #{actualQty}, diff_qty = #{diffQty}, diff_amount = #{diffAmount} WHERE id = #{itemId}")
    int takeItemUpdate(@Param("itemId") Long itemId, @Param("actualQty") BigDecimal actualQty,
                       @Param("diffQty") BigDecimal diffQty, @Param("diffAmount") BigDecimal diffAmount);

    @Update("UPDATE stock_take_items SET deleted = 1 WHERE st_id = #{stId}")
    int takeItemsDelete(@Param("stId") Long stId);

    @Update("UPDATE stock_takes SET deleted = 1 WHERE id = #{id}")
    int takeDelete(@Param("id") Long id);

    @Select("SELECT id FROM stock_takes WHERE st_no = #{stNo} AND deleted = 0 LIMIT 1")
    Long lastTakeId(@Param("stNo") String stNo);

    @Update("UPDATE stock_takes SET total_items=#{items}, total_diff_qty=#{diffQty}, total_diff_amount=#{diffAmt} WHERE id=#{id}")
    int takeUpdateTotals(@Param("id") Long id, @Param("items") int items,
                         @Param("diffQty") BigDecimal diffQty, @Param("diffAmt") BigDecimal diffAmt);
}
