package com.yawei.erp.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import com.yawei.erp.entity.OperationLog;

/** 操作日志（审计）：记录所有写操作 + 前端错误上报 */
@Mapper
public interface OperationLogMapper {

    @Insert("INSERT INTO operation_logs (user_name, role, method, path, module, detail, ip, status, cost_ms) " +
            "VALUES (#{userName}, #{role}, #{method}, #{path}, #{module}, #{detail}, #{ip}, #{status}, #{costMs})")
    int insert(OperationLog log);

    /** 查询（关键词/日期区间，按时间倒序，LIMIT 最多 1000 条） */
    @Select("<script>SELECT * FROM operation_logs WHERE deleted = 0 " +
            "<if test='kw != null and kw != \"\"'>AND (user_name LIKE CONCAT('%',#{kw},'%') OR path LIKE CONCAT('%',#{kw},'%') " +
            "OR detail LIKE CONCAT('%',#{kw},'%') OR module LIKE CONCAT('%',#{kw},'%'))</if> " +
            "<if test='start != null and start != \"\"'>AND created_at &gt;= #{start}</if> " +
            "<if test='end != null and end != \"\"'>AND created_at &lt;= CONCAT(#{end}, ' 23:59:59')</if> " +
            "ORDER BY id DESC LIMIT #{size}</script>")
    List<Map<String, Object>> query(@Param("kw") String kw, @Param("start") String start,
                                    @Param("end") String end, @Param("size") int size);
}
