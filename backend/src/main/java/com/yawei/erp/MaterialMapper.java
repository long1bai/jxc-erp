package com.yawei.erp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    @Select("<script>SELECT * FROM materials " +
            "<where>deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(name LIKE CONCAT('%',#{kw},'%') OR code LIKE CONCAT('%',#{kw},'%') " +
            "OR spec LIKE CONCAT('%',#{kw},'%') OR category LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY id DESC</script>")
    List<Material> search(@Param("kw") String keyword, IPage<Material> page);

    @Select("SELECT (SELECT COUNT(*) FROM customer_order_items WHERE material_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM purchase_items WHERE material_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM delivery_items WHERE material_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM stock_movements WHERE material_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM bom_items WHERE (product_id=#{id} OR component_id=#{id}) AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM process_materials WHERE material_id=#{id} AND deleted = 0)")
    Long countRefs(@Param("id") Long id);

    /** 逻辑删除 + code 加后缀（防重录同编码冲突） */
    @Update("UPDATE materials SET code = CONCAT(COALESCE(code,''), '#del#', id), deleted = 1 WHERE id = #{id}")
    int softDeleteWithCodeSuffix(@Param("id") Long id);
}
