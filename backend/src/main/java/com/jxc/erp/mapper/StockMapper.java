package com.jxc.erp.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import com.jxc.erp.common.Constants;

/** 库存查询 */
@Mapper
public interface StockMapper {

    /** 当前库存列表（搜索/分页/按仓库筛选，低库存标记字段 min_stock） */
    @Select("<script>SELECT m.id, m.code, m.name, m.spec, m.unit, m.category, " +
            "m.purchase_price, m.sale_price, m.min_stock, " +
            "COALESCE(SUM(CASE WHEN sm.move_type='" + Constants.MOVE_IN + "' THEN sm.quantity " +
            "WHEN sm.move_type='" + Constants.MOVE_OUT + "' THEN -sm.quantity WHEN sm.move_type='" + Constants.MOVE_ADJUST + "' THEN sm.quantity ELSE 0 END),0) AS stock " +
            "FROM materials m LEFT JOIN stock_movements sm ON m.id = sm.material_id AND sm.deleted = 0 " +
            "<where>m.deleted = 0 " +
            "<if test='whId != null'> AND sm.warehouse_id = #{whId}</if> " +
            "<if test='kw != null and kw != \"\"'> AND " +
            "(m.name LIKE CONCAT('%',#{kw},'%') OR m.code LIKE CONCAT('%',#{kw},'%') " +
            "OR m.spec LIKE CONCAT('%',#{kw},'%') OR m.category LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> GROUP BY m.id ORDER BY m.category, m.name</script>")
    List<Map<String, Object>> inventory(@Param("kw") String keyword, @Param("whId") Long warehouseId,
                                        IPage<Map<String, Object>> page);

    /** 库存流水（按物料或全部） */
    @Select("<script>SELECT sm.*, m.name AS material_name FROM stock_movements sm " +
            "LEFT JOIN materials m ON m.id = sm.material_id " +
            "<where>sm.deleted = 0 <if test='materialId != null and materialId > 0'> AND sm.material_id = #{materialId}</if></where> " +
            "ORDER BY sm.id DESC LIMIT #{limit}</script>")
    List<Map<String, Object>> movements(@Param("materialId") Long materialId, @Param("limit") int limit);
}
