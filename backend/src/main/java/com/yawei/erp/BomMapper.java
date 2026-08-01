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

/** BOM 数据访问（成品→组件配方） */
@Mapper
public interface BomMapper {

    /** 有 BOM 配置的成品列表（分页搜索） */
    @Select("<script>SELECT DISTINCT m.id AS product_id, m.code AS product_code, m.name AS product_name, " +
            "m.spec AS product_spec, m.unit AS product_unit, " +
            "(SELECT COUNT(*) FROM bom_items b WHERE b.product_id = m.id) AS component_count " +
            "FROM bom_items b JOIN materials m ON m.id = b.product_id " +
            "<where>b.deleted = 0 AND m.deleted = 0 <if test='kw != null and kw != \"\"'>" +
            "(m.code LIKE CONCAT('%',#{kw},'%') OR m.name LIKE CONCAT('%',#{kw},'%'))</if></where> " +
            "ORDER BY m.id DESC</script>")
    List<Map<String, Object>> bomProducts(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    /** 成品组件明细 */
    @Select("SELECT b.id, b.component_id, b.quantity, COALESCE(m.code,'') AS component_code, " +
            "COALESCE(m.name,'') AS component_name, COALESCE(m.spec,'') AS component_spec, " +
            "COALESCE(m.unit,'') AS component_unit, " +
            "COALESCE((SELECT sm.after_stock FROM stock_movements sm " +
            "WHERE sm.material_id = b.component_id AND sm.move_type='" + Constants.MOVE_IN + "' AND sm.deleted = 0 ORDER BY sm.id DESC LIMIT 1),0) AS stock " +
            "FROM bom_items b LEFT JOIN materials m ON m.id = b.component_id " +
            "WHERE b.product_id = #{productId} AND b.deleted = 0 ORDER BY b.id")
    List<Map<String, Object>> bomItems(@Param("productId") Long productId);

    /** 检查物料是否已有 BOM（作为成品） */
    @Select("SELECT COUNT(*) FROM bom_items WHERE product_id = #{productId} AND deleted = 0")
    int bomExists(@Param("productId") Long productId);

    @Insert("INSERT INTO bom_items (product_id, component_id, quantity) VALUES (#{productId}, #{componentId}, #{quantity})")
    int bomInsert(@Param("productId") Long productId, @Param("componentId") Long componentId,
                  @Param("quantity") BigDecimal quantity);

    @Update("UPDATE bom_items SET deleted = 1 WHERE product_id = #{productId}")
    int bomDeleteByProduct(@Param("productId") Long productId);

    /** 生成雪花主键（触发器同款 sfid()） */
    @Select("SELECT sfid()")
    Long nextId();
}
