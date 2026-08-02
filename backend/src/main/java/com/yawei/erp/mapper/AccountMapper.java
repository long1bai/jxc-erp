package com.yawei.erp.mapper;

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

/** 资金账户/收支/转账 + 余额聚合 */
@Mapper
public interface AccountMapper {

    // ============ 资金账户 ============
    @Select("SELECT * FROM cash_accounts WHERE deleted = 0 ORDER BY id")
    List<Map<String, Object>> accountList();

    @Select("SELECT * FROM cash_accounts WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> accountExists(@Param("id") Long id);

    @Insert("INSERT INTO cash_accounts (name, account_type, initial_balance, remark) VALUES (#{name}, #{type}, #{balance}, #{remark})")
    int accountInsert(@Param("name") String name, @Param("type") String type,
                      @Param("balance") BigDecimal balance, @Param("remark") String remark);

    @Update("UPDATE cash_accounts SET name=#{name}, account_type=#{type}, initial_balance=#{balance}, remark=#{remark} WHERE id=#{id}")
    int accountUpdate(@Param("id") Long id, @Param("name") String name, @Param("type") String type,
                      @Param("balance") BigDecimal balance, @Param("remark") String remark);

    @Update("UPDATE cash_accounts SET deleted = 1 WHERE id = #{id}")
    int accountDelete(@Param("id") Long id);

    @Select("<script>SELECT id FROM cash_accounts WHERE deleted = 0 AND id IN " +
            "(SELECT account_id FROM income_expenses WHERE deleted = 0 AND account_id = #{id} " +
            "UNION SELECT from_account_id FROM transfers WHERE deleted = 0 AND from_account_id = #{id} " +
            "UNION SELECT to_account_id FROM transfers WHERE deleted = 0 AND to_account_id = #{id}) LIMIT 1</script>")
    List<Map<String, Object>> accountUsage(@Param("id") Long id);

    // ============ 收支单 ============
    @Select("<script>SELECT ie.* FROM income_expenses ie " +
            "<where>ie.deleted = 0 " +
            "<if test='kw != null and kw != \"\"'>AND (ie.ie_no LIKE CONCAT('%',#{kw},'%') OR ie.category LIKE CONCAT('%',#{kw},'%') OR ie.remark LIKE CONCAT('%',#{kw},'%'))</if>" +
            "<if test='type != null and type != \"\"'>AND ie.ie_type = #{type}</if>" +
            "<if test='from != null and from != \"\"'>AND ie.ie_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'>AND ie.ie_date &lt;= #{to}</if>" +
            "</where> ORDER BY ie.id DESC</script>")
    List<Map<String, Object>> ieList(@Param("kw") String kw, @Param("type") String type,
                                     @Param("from") String from, @Param("to") String to,
                                     IPage<Map<String, Object>> page);

    @Select("SELECT * FROM income_expenses WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> ieExists(@Param("id") Long id);

    @Insert("INSERT INTO income_expenses (ie_no, ie_type, category, account_id, amount, ie_date, remark) " +
            "VALUES (#{ieNo}, #{ieType}, #{category}, #{accountId}, #{amount}, #{ieDate}, #{remark})")
    int ieInsert(@Param("ieNo") String ieNo, @Param("ieType") String ieType, @Param("category") String category,
                 @Param("accountId") Long accountId, @Param("amount") BigDecimal amount,
                 @Param("ieDate") String ieDate, @Param("remark") String remark);

    @Update("UPDATE income_expenses SET deleted = 1 WHERE id = #{id}")
    int ieDelete(@Param("id") Long id);

    @Select("SELECT id FROM income_expenses WHERE ie_no = #{ieNo} AND deleted = 0 LIMIT 1")
    Long lastIeId(@Param("ieNo") String ieNo);

    // ============ 转账 ============
    @Select("<script>SELECT tf.*, fa.name AS from_name, ta.name AS to_name FROM transfers tf " +
            "LEFT JOIN cash_accounts fa ON fa.id = tf.from_account_id " +
            "LEFT JOIN cash_accounts ta ON ta.id = tf.to_account_id " +
            "<where>tf.deleted = 0 " +
            "<if test='kw != null and kw != \"\"'>AND (tf.tf_no LIKE CONCAT('%',#{kw},'%') OR tf.remark LIKE CONCAT('%',#{kw},'%'))</if>" +
            "<if test='from != null and from != \"\"'>AND tf.tf_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'>AND tf.tf_date &lt;= #{to}</if>" +
            "</where> ORDER BY tf.id DESC</script>")
    List<Map<String, Object>> tfList(@Param("kw") String kw, @Param("from") String from,
                                     @Param("to") String to, IPage<Map<String, Object>> page);

    @Select("SELECT * FROM transfers WHERE id = #{id} AND deleted = 0")
    List<Map<String, Object>> tfExists(@Param("id") Long id);

    @Insert("INSERT INTO transfers (tf_no, from_account_id, to_account_id, amount, tf_date, remark) " +
            "VALUES (#{tfNo}, #{fromId}, #{toId}, #{amount}, #{tfDate}, #{remark})")
    int tfInsert(@Param("tfNo") String tfNo, @Param("fromId") Long fromId, @Param("toId") Long toId,
                 @Param("amount") BigDecimal amount, @Param("tfDate") String tfDate, @Param("remark") String remark);

    @Update("UPDATE transfers SET deleted = 1 WHERE id = #{id}")
    int tfDelete(@Param("id") Long id);

    @Select("SELECT id FROM transfers WHERE tf_no = #{tfNo} AND deleted = 0 LIMIT 1")
    Long lastTfId(@Param("tfNo") String tfNo);

    // ============ 报表 ============

    /** 账户余额表：期初 + 收入 - 支出 + 转入 - 转出 = 余额 */
    @Select("SELECT ca.id, ca.name, ca.account_type, ca.initial_balance, " +
            "COALESCE(inc.income,0) AS income, COALESCE(exp.expense,0) AS expense, " +
            "COALESCE(tin.amount,0) AS transfer_in, COALESCE(tout.amount,0) AS transfer_out, " +
            "ca.initial_balance + COALESCE(inc.income,0) - COALESCE(exp.expense,0) " +
            "+ COALESCE(tin.amount,0) - COALESCE(tout.amount,0) AS balance " +
            "FROM cash_accounts ca " +
            "LEFT JOIN (SELECT account_id, SUM(amount) AS income FROM income_expenses WHERE ie_type='income' AND deleted = 0 GROUP BY account_id) inc ON inc.account_id = ca.id " +
            "LEFT JOIN (SELECT account_id, SUM(amount) AS expense FROM income_expenses WHERE ie_type='expense' AND deleted = 0 GROUP BY account_id) exp ON exp.account_id = ca.id " +
            "LEFT JOIN (SELECT to_account_id, SUM(amount) AS amount FROM transfers WHERE deleted = 0 GROUP BY to_account_id) tin ON tin.to_account_id = ca.id " +
            "LEFT JOIN (SELECT from_account_id, SUM(amount) AS amount FROM transfers WHERE deleted = 0 GROUP BY from_account_id) tout ON tout.from_account_id = ca.id " +
            "WHERE ca.deleted = 0 ORDER BY ca.id")
    List<Map<String, Object>> accountBalances();

    /** 收支统计（按分类×月，可选类型/日期范围） */
    @Select("<script>SELECT ie.ie_type, ie.category, DATE_FORMAT(ie.ie_date, '%Y-%m') AS month, " +
            "SUM(ie.amount) AS amount, COUNT(*) AS cnt " +
            "FROM income_expenses ie " +
            "<where>ie.deleted = 0 " +
            "<if test='type != null and type != \"\"'>AND ie.ie_type = #{type}</if>" +
            "<if test='from != null and from != \"\"'>AND ie.ie_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'>AND ie.ie_date &lt;= #{to}</if>" +
            "</where> GROUP BY ie.ie_type, ie.category, month ORDER BY month, ie.ie_type, amount DESC</script>")
    List<Map<String, Object>> ieStats(@Param("type") String type, @Param("from") String from, @Param("to") String to);

    /** 经营状况月报（按月：收入/支出/净收支） */
    @Select("<script>SELECT m.month, " +
            "COALESCE(SUM(CASE WHEN ie.ie_type='income' THEN ie.amount ELSE 0 END),0) AS income, " +
            "COALESCE(SUM(CASE WHEN ie.ie_type='expense' THEN ie.amount ELSE 0 END),0) AS expense " +
            "FROM (SELECT DISTINCT DATE_FORMAT(ie_date,'%Y-%m') AS month FROM income_expenses WHERE deleted = 0 " +
            "<if test='from != null and from != \"\"'>AND ie_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'>AND ie_date &lt;= #{to}</if>" +
            ") m LEFT JOIN income_expenses ie ON DATE_FORMAT(ie.ie_date,'%Y-%m') = m.month AND ie.deleted = 0 " +
            "<if test='from != null and from != \"\"'>AND ie.ie_date &gt;= #{from}</if>" +
            "<if test='to != null and to != \"\"'>AND ie.ie_date &lt;= #{to}</if>" +
            " GROUP BY m.month ORDER BY m.month</script>")
    List<Map<String, Object>> businessMonthly(@Param("from") String from, @Param("to") String to);
}
