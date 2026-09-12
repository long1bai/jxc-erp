package com.jxc.erp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 仓库 */
@TableName("warehouses")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 逻辑删除标记（0=正常 1=已删）；MP @com.baomidou.mybatisplus.annotation.TableLogic 自动过滤/软删除 */
    @com.baomidou.mybatisplus.annotation.TableLogic
    @TableField(value = "deleted", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private Integer deleted;

    private String name;

    private String location;

    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
