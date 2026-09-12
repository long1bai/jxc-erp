package com.jxc.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import com.jxc.erp.entity.Supplier;

@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    @Select("<script>SELECT * FROM suppliers " +
            "<where>deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(name LIKE CONCAT('%',#{kw},'%') OR code LIKE CONCAT('%',#{kw},'%') " +
            "OR contact LIKE CONCAT('%',#{kw},'%') OR phone LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY id DESC</script>")
    List<Supplier> search(@Param("kw") String keyword, IPage<Supplier> page);

    @Select("SELECT (SELECT COUNT(*) FROM purchase_orders WHERE supplier_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM payment_vouchers WHERE supplier_id=#{id} AND deleted = 0) + " +
            "(SELECT COUNT(*) FROM purchase_returns WHERE supplier_id=#{id} AND deleted = 0)")
    Long countRefs(@Param("id") Long id);
}
