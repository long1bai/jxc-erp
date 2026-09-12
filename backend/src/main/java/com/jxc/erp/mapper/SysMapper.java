package com.jxc.erp.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.jxc.erp.common.Constants;

/** 通用小查询（库存汇总/客户名/序列号） */
@Mapper
public interface SysMapper {

    /** 当前库存（流水汇总，已删流水不计——逻辑删除单据后库存自动回补） */
    @Select("SELECT COALESCE(SUM(CASE WHEN move_type='" + Constants.MOVE_IN + "' THEN quantity WHEN move_type='" + Constants.MOVE_OUT + "' THEN -quantity WHEN move_type='" + Constants.MOVE_ADJUST + "' THEN quantity ELSE 0 END),0) " +
            "FROM stock_movements WHERE material_id = #{materialId} AND deleted = 0")
    BigDecimal currentStock(@Param("materialId") Long materialId);

    @Select("SELECT name FROM customers WHERE id = #{id}")
    String customerName(@Param("id") Long id);

    @Select("SELECT name FROM suppliers WHERE id = #{id}")
    String supplierName(@Param("id") Long id);

    @Select("SELECT name FROM work_groups WHERE id = #{id}")
    String groupName(@Param("id") Long id);

    @Select("SELECT seq FROM sequences WHERE prefix = #{prefix} AND year = #{year} AND month = #{month}")
    Integer seqSelect(@Param("prefix") String prefix, @Param("year") String year, @Param("month") String month);

    @Update("UPDATE sequences SET seq = seq + 1 WHERE prefix = #{prefix} AND year = #{year} AND month = #{month}")
    int seqIncrement(@Param("prefix") String prefix, @Param("year") String year, @Param("month") String month);

    @Insert("INSERT INTO sequences (prefix, year, month, seq) VALUES (#{prefix}, #{year}, #{month}, 1)")
    int seqInsert(@Param("prefix") String prefix, @Param("year") String year, @Param("month") String month);

    @Select("SELECT COUNT(*) FROM delivery_notes WHERE deleted = 0")
    long countDeliveries();
}
