package com.yawei.erp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 报工模块数据访问（分组/员工/工序/报工/工序扣料/统计） */
@Mapper
public interface WorkMapper {

    // ============ 分组 ============
    @Select("SELECT * FROM work_groups WHERE deleted = 0 ORDER BY id")
    List<Map<String, Object>> groups();

    /** 生成雪花主键（触发器同款 sfid()，Java 显式插入用） */
    @Select("SELECT sfid()")
    Long nextId();

    @Insert("INSERT INTO work_groups (id, name, description, leader_id) VALUES (#{id}, #{name}, #{description}, #{leaderId})")
    int groupInsert(@Param("id") Long id, @Param("name") String name, @Param("description") String description,
                    @Param("leaderId") Long leaderId);

    @Update("UPDATE work_groups SET name=#{name}, description=#{description}, leader_id=#{leaderId} WHERE id=#{id}")
    int groupUpdate(@Param("id") Long id, @Param("name") String name, @Param("description") String description,
                    @Param("leaderId") Long leaderId);

    @Update("UPDATE work_groups SET deleted = 1 WHERE id=#{id}")
    int groupDelete(@Param("id") Long id);

    @Select("SELECT id FROM work_groups WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> groupExists(@Param("id") Long id);

    // ============ 员工 ============
    @Select("<script>SELECT id, username, name, phone, COALESCE(group_id,0) AS group_id, " +
            "COALESCE(group_name,'') AS group_name, role, status FROM work_employees " +
            "<where>deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(name LIKE CONCAT('%',#{kw},'%') OR username LIKE CONCAT('%',#{kw},'%'))</if></where> " +
            "ORDER BY group_id, id</script>")
    List<Map<String, Object>> employees(@Param("kw") String keyword);

    @Insert("INSERT INTO work_employees (id, username, password, name, phone, group_id, group_name, role, status) " +
            "VALUES (#{id}, #{username}, #{password}, #{name}, #{phone}, #{groupId}, #{groupName}, #{role}, 'active')")
    int employeeInsert(@Param("id") Long id, @Param("username") String username, @Param("password") String password,
                       @Param("name") String name, @Param("phone") String phone,
                       @Param("groupId") Long groupId, @Param("groupName") String groupName,
                       @Param("role") String role);

    @Update("UPDATE work_employees SET username=#{username}, name=#{name}, phone=#{phone}, group_id=#{groupId}, " +
            "group_name=#{groupName}, role=#{role} WHERE id=#{id}")
    int employeeUpdate(@Param("id") Long id, @Param("username") String username, @Param("name") String name, @Param("phone") String phone,
                       @Param("groupId") Long groupId, @Param("groupName") String groupName,
                       @Param("role") String role);

    @Update("UPDATE work_employees SET deleted = 1 WHERE id=#{id}")
    int employeeDelete(@Param("id") Long id);

    @Select("SELECT id, name, group_id, group_name FROM work_employees WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> employeeExists(@Param("id") Long id);

    // ============ 工序 ============
    @Select("<script>SELECT p.*, COALESCE(g.name,'') AS group_name FROM processes p " +
            "LEFT JOIN work_groups g ON g.id = p.group_id " +
            "<where>p.deleted = 0 <if test='kw != null and kw != \"\"'>AND " +
            "(p.name LIKE CONCAT('%',#{kw},'%'))</if></where> ORDER BY p.group_id, p.sort_order, p.id</script>")
    List<Map<String, Object>> processes(@Param("kw") String keyword);

    @Insert("INSERT INTO processes (id, name, description, group_id, sort_order, unit_price) " +
            "VALUES (#{id}, #{name}, #{description}, #{groupId}, #{sortOrder}, #{unitPrice})")
    int processInsert(@Param("id") Long id, @Param("name") String name, @Param("description") String description,
                      @Param("groupId") Long groupId, @Param("sortOrder") Integer sortOrder,
                      @Param("unitPrice") BigDecimal unitPrice);

    @Update("UPDATE processes SET name=#{name}, description=#{description}, group_id=#{groupId}, " +
            "sort_order=#{sortOrder}, unit_price=#{unitPrice} WHERE id=#{id}")
    int processUpdate(@Param("id") Long id, @Param("name") String name, @Param("description") String description,
                      @Param("groupId") Long groupId, @Param("sortOrder") Integer sortOrder,
                      @Param("unitPrice") BigDecimal unitPrice);

    @Update("UPDATE processes SET deleted = 1 WHERE id=#{id}")
    int processDelete(@Param("id") Long id);

    @Select("SELECT id, name FROM processes WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> processExists(@Param("id") Long id);

    // ============ 报工 ============
    @Insert("INSERT INTO work_reports (id, employee_id, employee_name, group_id, group_name, process_id, process_name, " +
            "quantity, start_time, end_time, duration, remark, image_count, status, report_date, material_id, material_name) " +
            "VALUES (#{id}, #{employeeId}, #{employeeName}, #{groupId}, #{groupName}, #{processId}, #{processName}, " +
            "#{quantity}, #{startTime}, #{endTime}, #{duration}, #{remark}, 0, 'completed', #{reportDate}, #{materialId}, #{materialName})")
    int reportInsert(@Param("id") Long id, @Param("employeeId") Long employeeId, @Param("employeeName") String employeeName,
                     @Param("groupId") Long groupId, @Param("groupName") String groupName,
                     @Param("processId") Long processId, @Param("processName") String processName,
                     @Param("quantity") BigDecimal quantity, @Param("startTime") String startTime,
                     @Param("endTime") String endTime, @Param("duration") String duration,
                     @Param("remark") String remark, @Param("reportDate") String reportDate,
                     @Param("materialId") Long materialId, @Param("materialName") String materialName);

    @Select("<script>SELECT wr.id, wr.employee_id, wr.employee_name, wr.group_id, wr.group_name, " +
            "wr.process_id, wr.process_name, wr.quantity, wr.start_time, wr.end_time, " +
            "wr.duration, wr.remark, wr.status, wr.report_date, wr.material_id, wr.material_name, " +
            "wr.created_at, wr.updated_at, " +
            "COALESCE(p.unit_price,0) AS unit_price, " +
            "(wr.quantity * COALESCE(p.unit_price,0)) AS wage, " +
            "(SELECT COUNT(*) FROM work_report_images wri WHERE wri.report_id = wr.id) AS image_count, " +
            "(SELECT wri.image_path FROM work_report_images wri WHERE wri.report_id = wr.id " +
            "ORDER BY wri.sort_order LIMIT 1) AS first_image, " +
            "(SELECT GROUP_CONCAT(wri.image_path ORDER BY wri.sort_order SEPARATOR ',') " +
            "FROM work_report_images wri WHERE wri.report_id = wr.id) AS image_paths " +
            "FROM work_reports wr LEFT JOIN processes p ON p.id = wr.process_id " +
            "<where>wr.deleted = 0 " +
            "<if test='date != null and date != \"\"'> AND wr.report_date = #{date}</if>" +
            "<if test='groupId != null'> AND wr.group_id = #{groupId}</if>" +
            "<if test='employeeId != null'> AND wr.employee_id = #{employeeId}</if>" +
            "<if test='kw != null and kw != \"\"'> AND (wr.employee_name LIKE CONCAT('%',#{kw},'%') " +
            "OR wr.process_name LIKE CONCAT('%',#{kw},'%'))</if>" +
            "</where> ORDER BY wr.id DESC</script>")
    List<Map<String, Object>> reports(@Param("date") String date, @Param("groupId") Long groupId,
                                      @Param("employeeId") Long employeeId, @Param("kw") String keyword,
                                      IPage<Map<String, Object>> page);

    @Update("UPDATE work_reports SET deleted = 1 WHERE id=#{id}")
    int reportDelete(@Param("id") Long id);

    @Select("SELECT * FROM work_reports WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> reportExists(@Param("id") Long id);

    // ============ 打卡报工（开始/结束/取消） ============

    /** 开始报工：插入 status='in_progress' 记录，数量先为 0
     *  原子防重：INSERT...SELECT...WHERE NOT EXISTS（并发下同员工仅 1 条成功，避免 TOCTOU） */
    @Insert("INSERT INTO work_reports (id, employee_id, employee_name, group_id, group_name, process_id, process_name, " +
            "quantity, start_time, end_time, duration, remark, image_count, status, report_date, material_id, material_name) " +
            "SELECT #{id}, #{employeeId}, #{employeeName}, #{groupId}, #{groupName}, #{processId}, #{processName}, " +
            "0, #{startTime}, NULL, '', #{remark}, 0, 'in_progress', #{reportDate}, #{materialId}, #{materialName} " +
            "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM work_reports " +
            "WHERE employee_id = #{employeeId} AND status = 'in_progress' AND deleted = 0)")
    int reportStart(@Param("id") Long id, @Param("employeeId") Long employeeId, @Param("employeeName") String employeeName,
                    @Param("groupId") Long groupId, @Param("groupName") String groupName,
                    @Param("processId") Long processId, @Param("processName") String processName,
                    @Param("startTime") String startTime, @Param("remark") String remark,
                    @Param("reportDate") String reportDate,
                    @Param("materialId") Long materialId, @Param("materialName") String materialName);

    /** 结束报工：填数量/结束时间/时长，置为 completed */
    @Update("UPDATE work_reports SET quantity=#{quantity}, end_time=#{endTime}, duration=#{duration}, " +
            "status='completed' WHERE id=#{id} AND status='in_progress'")
    int reportFinish(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
                     @Param("endTime") String endTime, @Param("duration") String duration);

    /** 取消进行中的报工（直接删除，不留脏数据） */
    @Update("UPDATE work_reports SET deleted = 1 WHERE id=#{id} AND status='in_progress'")
    int reportCancel(@Param("id") Long id);

    /** 某员工当前进行中的报工（打卡页恢复状态用） */
    @Select("SELECT * FROM work_reports WHERE employee_id=#{employeeId} AND status='in_progress' AND deleted = 0 " +
            "ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> inProgress(@Param("employeeId") Long employeeId);

    /** 某员工某天已完成报工（打卡页"今日已完成"） */
    @Select("SELECT id, process_name, quantity, start_time, end_time, duration FROM work_reports " +
            "WHERE employee_id=#{employeeId} AND report_date=#{date} AND status='completed' AND deleted = 0 " +
            "ORDER BY id DESC LIMIT 50")
    List<Map<String, Object>> myReports(@Param("employeeId") Long employeeId, @Param("date") String date);

    /** 员工最近一次报工的工序（打卡页预选） */
    @Select("SELECT process_id FROM work_reports WHERE employee_id=#{employeeId} AND status='completed' AND deleted = 0 " +
            "ORDER BY id DESC LIMIT 1")
    List<Map<String, Object>> lastProcess(@Param("employeeId") Long employeeId);

    /** 按姓名精确查员工（账号自动关联用） */
    @Select("SELECT id, name FROM work_employees WHERE name = #{name} AND deleted = 0 LIMIT 1")
    List<Map<String, Object>> employeeByName(@Param("name") String name);

    /** 员工报工记录数（删除账号保护用） */
    @Select("SELECT COUNT(*) FROM work_reports WHERE employee_id = #{employeeId} AND deleted = 0")
    Long countReportsByEmployee(@Param("employeeId") Long employeeId);

    /** 员工改名（账号改名时同步，保持同一记录） */
    @Update("UPDATE work_employees SET name = #{name} WHERE id = #{id}")
    int employeeRename(@Param("id") Long id, @Param("name") String name);

    /** 反向联动：新增员工时，同名账号自动绑定（仅未绑定时） */
    @Update("UPDATE users SET work_employee_id = #{empId} " +
            "WHERE display_name = #{name} AND (work_employee_id IS NULL OR work_employee_id = 0)")
    int linkEmployeeByName(@Param("name") String name, @Param("empId") Long empId);

    /** 当前最大 W 流水工号（含已删记录——删号不重用，号不能回填） */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(username, 2) AS UNSIGNED)), 0) " +
            "FROM work_employees WHERE username REGEXP '^W[0-9]+$'")
    Long maxEmployeeNo();

    /** 分组下员工数（删除分组保护用） */
    @Select("SELECT COUNT(*) FROM work_employees WHERE group_id = #{groupId} AND deleted = 0")
    Long countEmployeesByGroup(@Param("groupId") Long groupId);

    /** 自动创建报工员工（新增员工账号时名单无同名则创建） */
    @Insert("INSERT INTO work_employees (id, username, name, group_id, group_name, role, status) " +
            "VALUES (#{id}, #{username}, #{name}, 0, '', 'employee', 'active')")
    int insertEmployeeAuto(@Param("id") Long id, @Param("username") String username, @Param("name") String name);

    /** 报工图片 */
    @Insert("INSERT INTO work_report_images (report_id, image_path, sort_order) " +
            "VALUES (#{reportId}, #{imagePath}, #{sortOrder})")
    int imageInsert(@Param("reportId") Long reportId, @Param("imagePath") String imagePath,
                    @Param("sortOrder") int sortOrder);

    /** 某报工的图片路径列表（删除报工时清理文件用） */
    @Select("SELECT image_path FROM work_report_images WHERE report_id=#{reportId} ORDER BY sort_order")
    List<Map<String, Object>> imagesByReport(@Param("reportId") Long reportId);

    /** 同步 image_count 列（冗余列，保持与 work_report_images 一致） */
    @Update("UPDATE work_reports SET image_count=#{count} WHERE id=#{reportId}")
    int updateImageCount(@Param("reportId") Long reportId, @Param("count") int count);

    // ============ 工序扣料 ============
    @Select("SELECT pm.*, COALESCE(m.name,'') AS material_name, COALESCE(m.unit,'') AS unit " +
            "FROM process_materials pm LEFT JOIN materials m ON m.id = pm.material_id " +
            "WHERE pm.process_id=#{processId} AND pm.deleted = 0 ORDER BY pm.id")
    List<Map<String, Object>> processMaterials(@Param("processId") Long processId);

    @Insert("INSERT INTO process_materials (id, process_id, material_id, quantity_per_unit) " +
            "VALUES (#{id}, #{processId}, #{materialId}, #{qtyPerUnit})")
    int pmInsert(@Param("id") Long id, @Param("processId") Long processId, @Param("materialId") Long materialId,
                 @Param("qtyPerUnit") BigDecimal qtyPerUnit);

    @Update("UPDATE process_materials SET deleted = 1 WHERE id=#{id}")
    int pmDelete(@Param("id") Long id);

    // ============ 统计 ============
    /** 日报：按组/员工汇总（数量、工资） */
    @Select("SELECT wr.group_id, COALESCE(wr.group_name,'') AS group_name, " +
            "wr.employee_id, COALESCE(wr.employee_name,'') AS employee_name, " +
            "SUM(wr.quantity) AS total_quantity, " +
            "SUM(wr.quantity * COALESCE(p.unit_price,0)) AS total_wage, COUNT(*) AS report_count, " +
            "SUM(wr.image_count) AS image_count " +
            "FROM work_reports wr LEFT JOIN processes p ON p.id = wr.process_id " +
            "WHERE wr.report_date = #{date} AND wr.deleted = 0 " +
            "GROUP BY wr.group_id, wr.group_name, wr.employee_id, wr.employee_name " +
            "ORDER BY wr.group_id, wr.employee_id")
    List<Map<String, Object>> dailyStats(@Param("date") String date);
    /** 月度汇总（按员工） */
    @Select("SELECT wr.employee_id, COALESCE(wr.employee_name,'') AS employee_name, " +
            "COALESCE(wr.group_name,'') AS group_name, " +
            "SUM(wr.quantity) AS total_quantity, " +
            "SUM(wr.quantity * COALESCE(p.unit_price,0)) AS total_wage, " +
            "COUNT(DISTINCT wr.report_date) AS work_days, SUM(wr.image_count) AS image_count " +
            "FROM work_reports wr LEFT JOIN processes p ON p.id = wr.process_id " +
            "WHERE DATE_FORMAT(wr.report_date, '%Y-%m') = #{month} AND wr.deleted = 0 " +
            "GROUP BY wr.employee_id, wr.employee_name, wr.group_name ORDER BY total_wage DESC")
    List<Map<String, Object>> monthlyStats(@Param("month") String month);

    // ============ 统计增强（日均/趋势/工资） ============

    /** 统计报告：按员工×工序分组基础数据 */
    @Select("<script>SELECT wr.employee_id, wr.employee_name, wr.group_name, wr.process_id, wr.process_name, " +
            "SUM(wr.quantity) AS total_qty, COUNT(*) AS report_count, " +
            "COUNT(DISTINCT wr.report_date) AS work_days " +
            "FROM work_reports wr " +
            "<where>wr.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND wr.report_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND wr.report_date &lt;= #{to}</if>" +
            "<if test='groupId != null'> AND wr.group_id = #{groupId}</if>" +
            "<if test='processId != null'> AND wr.process_id = #{processId}</if>" +
            "<if test='employeeId != null'> AND wr.employee_id = #{employeeId}</if>" +
            "</where> GROUP BY wr.employee_id, wr.employee_name, wr.group_name, wr.process_id, wr.process_name " +
            "ORDER BY total_qty DESC</script>")
    List<Map<String, Object>> workStats(@Param("from") String from, @Param("to") String to,
                                        @Param("groupId") Long groupId, @Param("processId") Long processId,
                                        @Param("employeeId") Long employeeId);

    /** 某员工（+工序名）的每日产量（趋势图/前后半段对比） */
    @Select("<script>SELECT wr.report_date AS report_date, SUM(wr.quantity) AS daily_qty " +
            "FROM work_reports wr " +
            "<where>wr.deleted = 0 " +
            "<if test='from != null and from != \"\"'> AND wr.report_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'> AND wr.report_date &lt;= #{to}</if>" +
            "AND wr.employee_id = #{employeeId}" +
            "<if test='processName != null and processName != \"\"'> AND wr.process_name = #{processName}</if>" +
            "</where> GROUP BY wr.report_date ORDER BY wr.report_date</script>")
    List<Map<String, Object>> dailyTrend(@Param("employeeId") Long employeeId,
                                         @Param("processName") String processName,
                                         @Param("from") String from, @Param("to") String to);

    /** 工资汇总：按员工×工序（数量×单价） */
    @Select("<script>SELECT wr.employee_id, wr.employee_name, wr.group_name, wr.process_id, wr.process_name, " +
            "MAX(p.unit_price) AS unit_price, SUM(wr.quantity) AS qty, " +
            "ROUND(SUM(wr.quantity * COALESCE(p.unit_price,0)),2) AS amount " +
            "FROM work_reports wr LEFT JOIN processes p ON p.id = wr.process_id " +
            "<where>wr.deleted = 0 AND DATE_FORMAT(wr.report_date, '%Y-%m') = #{month} " +
            "<if test='groupId != null'> AND wr.group_id = #{groupId}</if>" +
            "<if test='employeeId != null'> AND wr.employee_id = #{employeeId}</if>" +
            "</where> GROUP BY wr.employee_id, wr.employee_name, wr.group_name, wr.process_id, wr.process_name " +
            "ORDER BY wr.employee_name, wr.process_name</script>")
    List<Map<String, Object>> wageSummary(@Param("month") String month, @Param("groupId") Long groupId,
                                          @Param("employeeId") Long employeeId);
}
