package com.jxc.erp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import com.jxc.erp.entity.Project;

/** 项目（工地）数据访问：CRUD + 项目材料成本台账 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /** 项目列表：join 客户出 customer_name，子查询出材料成本总额 total_amount + 采购单数 po_count */
    @Select("<script>SELECT p.*, COALESCE(c.name,'') AS customer_name, " +
            "COALESCE((SELECT SUM(pi.amount) FROM purchase_items pi " +
            "JOIN purchase_orders po ON po.id = pi.po_id " +
            "WHERE po.project_id = p.id AND po.deleted = 0 AND pi.deleted = 0),0) AS total_amount, " +
            "(SELECT COUNT(*) FROM purchase_orders po WHERE po.project_id = p.id AND po.deleted = 0) AS po_count " +
            "FROM projects p LEFT JOIN customers c ON c.id = p.customer_id " +
            "<where>p.deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(p.name LIKE CONCAT('%',#{kw},'%') OR COALESCE(p.code,'') LIKE CONCAT('%',#{kw},'%') " +
            "OR COALESCE(c.name,'') LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY p.id DESC</script>")
    List<Map<String, Object>> search(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    /** 下拉 options（轻量全量） */
    @Select("SELECT id, code, name FROM projects WHERE deleted = 0 ORDER BY id DESC LIMIT 1000")
    List<Map<String, Object>> options();

    /** 删除前校验：是否被采购单引用 */
    @Select("SELECT COUNT(*) FROM purchase_orders WHERE project_id = #{id} AND deleted = 0")
    Long countPurchases(@Param("id") Long id);

    /** 台账头部（带客户名） */
    @Select("SELECT p.*, COALESCE(c.name,'') AS customer_name FROM projects p " +
            "LEFT JOIN customers c ON c.id = p.customer_id WHERE p.id = #{id} AND p.deleted = 0")
    List<Map<String, Object>> costProject(@Param("id") Long id);

    /** 台账明细：join 采购单 + 供应商，按日期排序（对应供货明细表） */
    @Select("SELECT pi.id, po.po_no, po.po_date, COALESCE(s.name,'') AS supplier_name, " +
            "COALESCE(pi.assembly_system,'') AS assembly_system, COALESCE(pi.spec,'') AS spec, " +
            "pi.material_name, COALESCE(pi.unit,'') AS unit, " +
            "pi.quantity, pi.unit_price, pi.amount, COALESCE(pi.remark,'') AS remark " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "WHERE po.project_id = #{id} AND po.deleted = 0 AND pi.deleted = 0 " +
            "ORDER BY po.po_date, po.id, pi.sort_order, pi.id")
    List<Map<String, Object>> costItems(@Param("id") Long id);

    /** 按装配系统分组小计（空值归"未分类"） */
    @Select("SELECT COALESCE(NULLIF(pi.assembly_system,''),'未分类') AS assembly_system, " +
            "SUM(pi.quantity) AS total_quantity, SUM(pi.amount) AS total_amount " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "WHERE po.project_id = #{id} AND po.deleted = 0 AND pi.deleted = 0 " +
            "GROUP BY pi.assembly_system ORDER BY total_amount DESC")
    List<Map<String, Object>> costSummary(@Param("id") Long id);

    /** 整表合计 */
    @Select("SELECT COALESCE(SUM(pi.quantity),0) AS total_quantity, COALESCE(SUM(pi.amount),0) AS total_amount " +
            "FROM purchase_items pi JOIN purchase_orders po ON po.id = pi.po_id " +
            "WHERE po.project_id = #{id} AND po.deleted = 0 AND pi.deleted = 0")
    List<Map<String, Object>> costTotals(@Param("id") Long id);
}
