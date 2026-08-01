package com.yawei.erp;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 逻辑删除 + 用户名加后缀（防重录同用户名冲突） */
    @Update("UPDATE users SET username = CONCAT(username, '#del#', id), deleted = 1 WHERE id = #{id}")
    int softDeleteWithUsernameSuffix(@Param("id") Long id);
}
