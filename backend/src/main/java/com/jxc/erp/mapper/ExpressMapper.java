package com.jxc.erp.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 快递物流公司 */
@Mapper
public interface ExpressMapper {

    @Select("SELECT * FROM express_companies WHERE deleted = 0 ORDER BY id")
    List<Map<String, Object>> list();

    @Select("SELECT * FROM express_companies WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> exists(@Param("id") Long id);

    @Insert("INSERT INTO express_companies (name, contact, phone, remark) VALUES (#{name}, #{contact}, #{phone}, #{remark})")
    int insert(@Param("name") String name, @Param("contact") String contact,
               @Param("phone") String phone, @Param("remark") String remark);

    @Update("UPDATE express_companies SET name=#{name}, contact=#{contact}, phone=#{phone}, remark=#{remark} WHERE id=#{id}")
    int update(@Param("id") Long id, @Param("name") String name, @Param("contact") String contact,
               @Param("phone") String phone, @Param("remark") String remark);

    @Update("UPDATE express_companies SET deleted = 1 WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
