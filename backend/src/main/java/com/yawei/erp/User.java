package com.yawei.erp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户表（登录账号）
 * 密码兼容迁移：支持 Python pbkdf2 格式 + BCrypt 新格式
 */
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 逻辑删除标记（0=正常 1=已删）；MP @com.baomidou.mybatisplus.annotation.TableLogic 自动过滤/软删除 */
    @com.baomidou.mybatisplus.annotation.TableLogic
    @TableField(value = "deleted", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Integer deleted;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    private String role;

    /** 关联的报工员工 id（work_employees），工人登录打卡页自动选中；ALWAYS 允许清空为 null */
    @TableField(value = "work_employee_id",
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long workEmployeeId;

    @TableField("is_active")
    private Boolean active;

    @TableField(value = "created_at", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getWorkEmployeeId() { return workEmployeeId; }
    public void setWorkEmployeeId(Long workEmployeeId) { this.workEmployeeId = workEmployeeId; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
