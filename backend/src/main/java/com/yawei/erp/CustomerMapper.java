package com.yawei.erp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {

    @Select("<script>SELECT * FROM customers " +
            "<where>deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(name LIKE CONCAT('%',#{kw},'%') OR code LIKE CONCAT('%',#{kw},'%') " +
            "OR contact LIKE CONCAT('%',#{kw},'%') OR phone LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY id DESC</script>")
    List<Customer> search(@Param("kw") String keyword, IPage<Customer> page);

    @Select("SELECT (SELECT COUNT(*) FROM customer_orders WHERE customer_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM delivery_notes WHERE customer_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM receipt_vouchers WHERE customer_id=#{id} AND deleted = 0)")
    Long countRefs(@Param("id") Long id);
}
