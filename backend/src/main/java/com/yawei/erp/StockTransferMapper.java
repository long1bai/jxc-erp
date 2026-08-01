package com.yawei.erp;

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

/** 库存调拨单 */
@Mapper
public interface StockTransferMapper {

    @Insert("INSERT INTO stock_transfers (transfer_no, from_warehouse_id, from_warehouse_name, " +
            "to_warehouse_id, to_warehouse_name, transfer_date, total_items, total_quantity, remark, created_by) " +
            "VALUES (#{no}, #{fromWh}, #{fromName}, #{toWh}, #{toName}, #{date}, 0, 0, #{remark}, #{createdBy})")
    int insert(@Param("no") String no, @Param("fromWh") Long fromWh, @Param("fromName") String fromName,
               @Param("toWh") Long toWh, @Param("toName") String toName, @Param("date") String date,
               @Param("remark") String remark, @Param("createdBy") String createdBy);

    @Select("SELECT id FROM stock_transfers WHERE transfer_no = #{no} ORDER BY id DESC LIMIT 1")
    Long lastId(@Param("no") String no);

    @Insert("INSERT INTO stock_transfer_items (transfer_id, material_id, material_name, spec, unit, quantity) " +
            "VALUES (#{transferId}, #{materialId}, #{materialName}, #{spec}, #{unit}, #{quantity})")
    int itemInsert(@Param("transferId") Long transferId, @Param("materialId") Long materialId,
                   @Param("materialName") String materialName, @Param("spec") String spec,
                   @Param("unit") String unit, @Param("quantity") BigDecimal quantity);

    @Update("UPDATE stock_transfers SET total_items = #{n}, total_quantity = #{qty} WHERE id = #{id}")
    int updateTotals(@Param("id") Long id, @Param("n") int n, @Param("qty") BigDecimal qty);

    @Select("<script>SELECT * FROM stock_transfers WHERE deleted = 0 " +
            "<if test='kw != null and kw != \"\"'>AND (transfer_no LIKE CONCAT('%',#{kw},'%') " +
            "OR from_warehouse_name LIKE CONCAT('%',#{kw},'%') OR to_warehouse_name LIKE CONCAT('%',#{kw},'%') " +
            "OR remark LIKE CONCAT('%',#{kw},'%'))</if> " +
            "<if test='start != null and start != \"\"'>AND transfer_date &gt;= #{start}</if> " +
            "<if test='end != null and end != \"\"'>AND transfer_date &lt;= #{end}</if> " +
            "ORDER BY id DESC</script>")
    List<Map<String, Object>> query(@Param("kw") String kw, @Param("start") String start,
                                    @Param("end") String end, IPage<Map<String, Object>> page);

    @Select("SELECT * FROM stock_transfers WHERE id = #{id} AND deleted = 0")
    Map<String, Object> byId(@Param("id") Long id);

    @Select("SELECT * FROM stock_transfer_items WHERE transfer_id = #{id} ORDER BY id")
    List<Map<String, Object>> items(@Param("id") Long id);

    @Update("UPDATE stock_transfers SET deleted = 1 WHERE id = #{id}")
    int delete(@Param("id") Long id);

    @Delete("DELETE FROM stock_transfer_items WHERE transfer_id = #{id}")
    int itemsDelete(@Param("id") Long id);

    /** 某仓库某物料当前库存（流水汇总，调拨前后计算用） */
    @Select("SELECT COALESCE(SUM(CASE WHEN move_type='" + Constants.MOVE_IN + "' THEN quantity " +
            "WHEN move_type='" + Constants.MOVE_OUT + "' THEN -quantity " +
            "WHEN move_type='" + Constants.MOVE_ADJUST + "' THEN quantity ELSE 0 END), 0) " +
            "FROM stock_movements WHERE material_id = #{mid} AND warehouse_id = #{wid} AND deleted = 0")
    BigDecimal stockByWarehouse(@Param("mid") Long mid, @Param("wid") Long wid);
}
