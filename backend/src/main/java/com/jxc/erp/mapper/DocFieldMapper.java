package com.jxc.erp.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 动态字段元数据（doc_field_defs）数据访问 */
@Mapper
public interface DocFieldMapper {

    /** 某单据类型的启用字段（渲染列/表单用） */
    @Select("SELECT id, field_key, field_name, field_type, options, sort_order FROM doc_field_defs " +
            "WHERE doc_type = #{docType} AND enabled = 1 AND deleted = 0 ORDER BY sort_order, id")
    List<Map<String, Object>> listFields(@Param("docType") String docType);

    /** 某单据类型全部字段（保存时清理用） */
    @Select("SELECT id, field_key FROM doc_field_defs WHERE doc_type = #{docType} AND deleted = 0")
    List<Map<String, Object>> listAll(@Param("docType") String docType);

    /** 按单据类型+键查启用行 id（本表为配置表，save=全量物理替换，不保留软删残行） */
    @Select("SELECT id FROM doc_field_defs WHERE doc_type = #{docType} AND field_key = #{fieldKey} AND deleted = 0 LIMIT 1")
    Long findIdByKey(@Param("docType") String docType, @Param("fieldKey") String fieldKey);

    @Insert("INSERT INTO doc_field_defs (doc_type, field_key, field_name, field_type, options, sort_order) " +
            "VALUES (#{docType}, #{fieldKey}, #{fieldName}, #{fieldType}, #{options}, #{sortOrder})")
    int insertField(@Param("docType") String docType, @Param("fieldKey") String fieldKey,
                    @Param("fieldName") String fieldName, @Param("fieldType") String fieldType,
                    @Param("options") String options, @Param("sortOrder") int sortOrder);

    @Update("UPDATE doc_field_defs SET enabled = 1, field_name = #{fieldName}, field_type = #{fieldType}, " +
            "options = #{options}, sort_order = #{sortOrder} WHERE id = #{id} AND deleted = 0")
    int updateField(@Param("id") Long id, @Param("fieldName") String fieldName,
                    @Param("fieldType") String fieldType, @Param("options") String options,
                    @Param("sortOrder") int sortOrder);

    /** 物理删除某单据类型全部定义（save=全量替换，配置表不做软删，避免软删残行撞 uk(doc_type,field_key)） */
    @Delete("DELETE FROM doc_field_defs WHERE doc_type = #{docType}")
    int deleteByDocType(@Param("docType") String docType);

    @Delete("DELETE FROM doc_field_defs WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
