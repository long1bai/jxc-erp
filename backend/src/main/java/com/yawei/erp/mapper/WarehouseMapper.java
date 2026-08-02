package com.yawei.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.yawei.erp.entity.Warehouse;

/** 仓库数据访问 */
@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {

    /** 该仓库是否已有库存流水（删除保护） */
    @Select("SELECT COUNT(*) FROM stock_movements WHERE warehouse_id = #{id} AND deleted = 0")
    long countMovements(@Param("id") Long id);
}
