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

/** 财务模块数据访问（收款/付款单、应收应付汇总、核销、发票） */
@Mapper
public interface FinanceMapper {

    // ============ 收款单 ============
    @Insert("INSERT INTO receipt_vouchers (rv_no, customer_id, customer_name, amount, receipt_date, receipt_method, remark) " +
            "VALUES (#{rvNo}, #{customerId}, #{customerName}, #{amount}, #{date}, #{method}, #{remark})")
    int receiptInsert(@Param("rvNo") String rvNo, @Param("customerId") Long customerId,
                      @Param("customerName") String customerName, @Param("amount") BigDecimal amount,
                      @Param("date") String date, @Param("method") String method, @Param("remark") String remark);

    @Select("<script>SELECT rv.*, COALESCE(s.settled,0) AS settled_amount " +
            "FROM receipt_vouchers rv " +
            "LEFT JOIN (SELECT voucher_id, SUM(amount) AS settled FROM settlements " +
            "WHERE settle_type='receipt' AND deleted = 0 GROUP BY voucher_id) s ON s.voucher_id = rv.id " +
            "<where>rv.deleted = 0 <if test='kw != null and kw != \"\"'>" +
            "(rv.rv_no LIKE CONCAT('%',#{kw},'%') OR rv.customer_name LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY rv.id DESC</script>")
    List<Map<String, Object>> receiptList(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    @Select("SELECT COALESCE(SUM(amount),0) FROM settlements WHERE settle_type='receipt' AND voucher_id=#{id} AND deleted = 0")
    BigDecimal receiptSettled(@Param("id") Long id);

    @Select("SELECT id FROM receipt_vouchers WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> receiptExists(@Param("id") Long id);

    @Update("UPDATE receipt_vouchers SET deleted = 1 WHERE id=#{id}")
    int receiptDelete(@Param("id") Long id);

    // ============ 付款单 ============
    @Insert("INSERT INTO payment_vouchers (pv_no, supplier_id, supplier_name, amount, pay_date, pay_method, remark) " +
            "VALUES (#{pvNo}, #{supplierId}, #{supplierName}, #{amount}, #{date}, #{method}, #{remark})")
    int paymentInsert(@Param("pvNo") String pvNo, @Param("supplierId") Long supplierId,
                      @Param("supplierName") String supplierName, @Param("amount") BigDecimal amount,
                      @Param("date") String date, @Param("method") String method, @Param("remark") String remark);

    @Select("<script>SELECT pv.*, COALESCE(s.settled,0) AS settled_amount " +
            "FROM payment_vouchers pv " +
            "LEFT JOIN (SELECT voucher_id, SUM(amount) AS settled FROM settlements " +
            "WHERE settle_type='payment' AND deleted = 0 GROUP BY voucher_id) s ON s.voucher_id = pv.id " +
            "<where>pv.deleted = 0 <if test='kw != null and kw != \"\"'>" +
            "(pv.pv_no LIKE CONCAT('%',#{kw},'%') OR pv.supplier_name LIKE CONCAT('%',#{kw},'%'))" +
            "</if></where> ORDER BY pv.id DESC</script>")
    List<Map<String, Object>> paymentList(@Param("kw") String keyword, IPage<Map<String, Object>> page);

    @Select("SELECT COALESCE(SUM(amount),0) FROM settlements WHERE settle_type='payment' AND voucher_id=#{id} AND deleted = 0")
    BigDecimal paymentSettled(@Param("id") Long id);

    @Select("SELECT id FROM payment_vouchers WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> paymentExists(@Param("id") Long id);

    @Update("UPDATE payment_vouchers SET deleted = 1 WHERE id=#{id}")
    int paymentDelete(@Param("id") Long id);

    // ============ 应收/应付汇总 ============
    @Select("SELECT d.customer_id AS party_id, COALESCE(c.name,'') AS party_name, " +
            "SUM(d.total_amount) AS total_amount, COALESCE(s.settled,0) AS settled_amount, " +
            "COALESCE(r.ret,0) AS return_amount, " +
            "SUM(d.total_amount) - COALESCE(s.settled,0) - COALESCE(r.ret,0) AS balance " +
            "FROM delivery_notes d LEFT JOIN customers c ON c.id = d.customer_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='delivery' AND deleted = 0 GROUP BY ref_id) s ON s.ref_id = d.id " +
            "LEFT JOIN (SELECT customer_id, SUM(total_amount) AS ret FROM sales_returns WHERE deleted = 0 GROUP BY customer_id) r ON r.customer_id = d.customer_id " +
            "WHERE d.deleted = 0 GROUP BY d.customer_id, c.name, s.settled, r.ret ORDER BY balance DESC")
    List<Map<String, Object>> receivableSummary();

    @Select("SELECT po.supplier_id AS party_id, COALESCE(s.name,'') AS party_name, " +
            "SUM(po.total_amount) AS total_amount, COALESCE(p.settled,0) AS settled_amount, " +
            "SUM(po.total_amount) - COALESCE(p.settled,0) AS balance " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='purchase' AND deleted = 0 GROUP BY ref_id) p ON p.ref_id = po.id " +
            "WHERE po.deleted = 0 GROUP BY po.supplier_id, s.name, p.settled ORDER BY balance DESC")
    List<Map<String, Object>> payableSummary();

    // ============ 分月汇总 ============
    @Select("SELECT DATE_FORMAT(dn_date, '%Y-%m') AS month, " +
            "SUM(total_amount) AS sales, " +
            "(SELECT COALESCE(SUM(total_amount),0) FROM sales_returns WHERE DATE_FORMAT(return_date,'%Y-%m') = DATE_FORMAT(dn_date, '%Y-%m') AND deleted = 0) AS returns_amount, " +
            "(SELECT COALESCE(SUM(amount),0) FROM receipt_vouchers WHERE DATE_FORMAT(receipt_date,'%Y-%m') = DATE_FORMAT(dn_date, '%Y-%m') AND deleted = 0) AS received " +
            "FROM delivery_notes WHERE deleted = 0 GROUP BY month, returns_amount, received ORDER BY month")
    List<Map<String, Object>> receivableMonthly();

    @Select("SELECT DATE_FORMAT(po_date, '%Y-%m') AS month, " +
            "SUM(total_amount) AS purchases, " +
            "(SELECT COALESCE(SUM(amount),0) FROM payment_vouchers WHERE DATE_FORMAT(pay_date,'%Y-%m') = DATE_FORMAT(po_date, '%Y-%m') AND deleted = 0) AS paid " +
            "FROM purchase_orders WHERE deleted = 0 GROUP BY month, paid ORDER BY month")
    List<Map<String, Object>> payableMonthly();

    // ============ 单据列表（金蝶式） ============
    @Select("<script>SELECT d.id, d.dn_no AS doc_no, d.dn_date AS doc_date, d.customer_id AS party_id, " +
            "COALESCE(c.name,'') AS party_name, d.total_amount AS amount, " +
            "COALESCE(s.settled,0) AS settled_amount, d.total_amount - COALESCE(s.settled,0) AS balance, " +
            "COALESCE(i.invoiced,0) AS invoiced_amount, d.total_amount - COALESCE(i.invoiced,0) AS uninvoiced_amount " +
            "FROM delivery_notes d LEFT JOIN customers c ON c.id = d.customer_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='delivery' AND deleted = 0 GROUP BY ref_id) s ON s.ref_id = d.id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS invoiced FROM invoices WHERE ref_type='delivery' AND deleted = 0 GROUP BY ref_id) i ON i.ref_id = d.id " +
            "<where>d.deleted = 0 " +
            "<if test='start != null and start != \"\"'> AND d.dn_date &gt;= #{start}</if>" +
            "<if test='end != null and end != \"\"'> AND d.dn_date &lt;= #{end}</if>" +
            "<if test='kw != null and kw != \"\"'> AND (d.dn_no LIKE CONCAT('%',#{kw},'%') OR COALESCE(c.name,'') LIKE CONCAT('%',#{kw},'%'))</if>" +
            "</where> ORDER BY d.id DESC</script>")
    List<Map<String, Object>> deliveryDocList(@Param("kw") String keyword, @Param("start") String start,
                                              @Param("end") String end, IPage<Map<String, Object>> page);

    @Select("<script>SELECT po.id, po.po_no AS doc_no, po.po_date AS doc_date, po.supplier_id AS party_id, " +
            "COALESCE(s.name,'') AS party_name, po.total_amount AS amount, " +
            "COALESCE(p.settled,0) AS settled_amount, po.total_amount - COALESCE(p.settled,0) AS balance, " +
            "COALESCE(i.invoiced,0) AS invoiced_amount, po.total_amount - COALESCE(i.invoiced,0) AS uninvoiced_amount " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='purchase' AND deleted = 0 GROUP BY ref_id) p ON p.ref_id = po.id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS invoiced FROM invoices WHERE ref_type='purchase' AND deleted = 0 GROUP BY ref_id) i ON i.ref_id = po.id " +
            "<where>po.deleted = 0 " +
            "<if test='start != null and start != \"\"'> AND po.po_date &gt;= #{start}</if>" +
            "<if test='end != null and end != \"\"'> AND po.po_date &lt;= #{end}</if>" +
            "<if test='kw != null and kw != \"\"'> AND (po.po_no LIKE CONCAT('%',#{kw},'%') OR COALESCE(s.name,'') LIKE CONCAT('%',#{kw},'%'))</if>" +
            "</where> ORDER BY po.id DESC</script>")
    List<Map<String, Object>> purchaseDocList(@Param("kw") String keyword, @Param("start") String start,
                                              @Param("end") String end, IPage<Map<String, Object>> page);

    // ============ 核销 ============
    @Insert("INSERT INTO settlements (id, settle_type, voucher_id, voucher_no, ref_type, ref_id, amount, remark) " +
            "VALUES (#{id}, #{settleType}, #{voucherId}, #{voucherNo}, #{refType}, #{refId}, #{amount}, #{remark})")
    int settleInsert(@Param("id") Long id, @Param("settleType") String settleType, @Param("voucherId") Long voucherId,
                     @Param("voucherNo") String voucherNo, @Param("refType") String refType,
                     @Param("refId") Long refId, @Param("amount") BigDecimal amount, @Param("remark") String remark);

    @Select("SELECT s.* FROM settlements s WHERE s.ref_type=#{refType} AND s.ref_id=#{refId} AND s.deleted = 0 ORDER BY s.id")
    List<Map<String, Object>> settleList(@Param("refType") String refType, @Param("refId") Long refId);

    @Select("SELECT id FROM settlements WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> settleExists(@Param("id") Long id);

    @Update("UPDATE settlements SET deleted = 1 WHERE id=#{id}")
    int settleDelete(@Param("id") Long id);

    /** 单据未结余额 */
    @Select("<script>SELECT total_amount - COALESCE((SELECT SUM(amount) FROM settlements " +
            "WHERE ref_type=#{refType} AND ref_id=#{refId} AND deleted = 0),0) AS balance FROM " +
            "<choose><when test='refType == \"delivery\"'>delivery_notes</when>" +
            "<otherwise>purchase_orders</otherwise></choose> WHERE id=#{refId} AND deleted = 0</script>")
    BigDecimal docBalance(@Param("refType") String refType, @Param("refId") Long refId);

    /** 收款/付款单未核销余额 */
    @Select("<script>SELECT amount - COALESCE((SELECT SUM(amount) FROM settlements " +
            "WHERE settle_type=#{settleType} AND voucher_id=#{voucherId} AND deleted = 0),0) AS balance FROM " +
            "<choose><when test='settleType == \"receipt\"'>receipt_vouchers</when>" +
            "<otherwise>payment_vouchers</otherwise></choose> WHERE id=#{voucherId} AND deleted = 0</script>")
    BigDecimal voucherBalance(@Param("settleType") String settleType, @Param("voucherId") Long voucherId);

    @Select("<script>SELECT rv_no AS vno, customer_id AS party_id, customer_name AS party_name FROM receipt_vouchers WHERE id=#{id} AND deleted = 0 " +
            "UNION ALL SELECT pv_no, supplier_id, supplier_name FROM payment_vouchers WHERE id=#{id} AND deleted = 0</script>")
    List<Map<String, Object>> voucherInfo(@Param("id") Long id);

    // ============ 账龄分析 ============
    @Select("SELECT d.customer_id AS party_id, COALESCE(c.name,'') AS party_name, " +
            "SUM(d.total_amount - COALESCE(s.settled,0)) - COALESCE(r.ret,0) AS total_balance, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), d.dn_date) <= 30 THEN d.total_amount - COALESCE(s.settled,0) ELSE 0 END) AS age_30, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), d.dn_date) > 30 AND DATEDIFF(CURDATE(), d.dn_date) <= 60 THEN d.total_amount - COALESCE(s.settled,0) ELSE 0 END) AS age_60, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), d.dn_date) > 60 AND DATEDIFF(CURDATE(), d.dn_date) <= 90 THEN d.total_amount - COALESCE(s.settled,0) ELSE 0 END) AS age_90, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), d.dn_date) > 90 THEN d.total_amount - COALESCE(s.settled,0) ELSE 0 END) AS age_120 " +
            "FROM delivery_notes d LEFT JOIN customers c ON c.id = d.customer_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='delivery' AND deleted = 0 GROUP BY ref_id) s ON s.ref_id = d.id " +
            "LEFT JOIN (SELECT customer_id, SUM(total_amount) AS ret FROM sales_returns WHERE deleted = 0 GROUP BY customer_id) r ON r.customer_id = d.customer_id " +
            "WHERE d.deleted = 0 GROUP BY d.customer_id, c.name, r.ret HAVING total_balance > 0 ORDER BY total_balance DESC")
    List<Map<String, Object>> receivableAging();

    @Select("SELECT po.supplier_id AS party_id, COALESCE(s.name,'') AS party_name, " +
            "SUM(po.total_amount - COALESCE(p.settled,0)) AS total_balance, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), po.po_date) <= 30 THEN po.total_amount - COALESCE(p.settled,0) ELSE 0 END) AS age_30, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), po.po_date) > 30 AND DATEDIFF(CURDATE(), po.po_date) <= 60 THEN po.total_amount - COALESCE(p.settled,0) ELSE 0 END) AS age_60, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), po.po_date) > 60 AND DATEDIFF(CURDATE(), po.po_date) <= 90 THEN po.total_amount - COALESCE(p.settled,0) ELSE 0 END) AS age_90, " +
            "SUM(CASE WHEN DATEDIFF(CURDATE(), po.po_date) > 90 THEN po.total_amount - COALESCE(p.settled,0) ELSE 0 END) AS age_120 " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS settled FROM settlements WHERE ref_type='purchase' AND deleted = 0 GROUP BY ref_id) p ON p.ref_id = po.id " +
            "WHERE po.deleted = 0 GROUP BY po.supplier_id, s.name HAVING total_balance > 0 ORDER BY total_balance DESC")
    List<Map<String, Object>> payableAging();

    // ============ 发票 ============
    @Insert("INSERT INTO invoices (invoice_no, invoice_type, ref_type, ref_id, customer_id, customer_name, supplier_id, supplier_name, amount, invoice_date, remark) " +
            "VALUES (#{invoiceNo}, #{invoiceType}, #{refType}, #{refId}, #{customerId}, #{customerName}, #{supplierId}, #{supplierName}, #{amount}, #{date}, #{remark})")
    int invoiceInsert(@Param("invoiceNo") String invoiceNo, @Param("invoiceType") String invoiceType,
                      @Param("refType") String refType, @Param("refId") Long refId,
                      @Param("customerId") Long customerId, @Param("customerName") String customerName,
                      @Param("supplierId") Long supplierId, @Param("supplierName") String supplierName,
                      @Param("amount") BigDecimal amount, @Param("date") String date, @Param("remark") String remark);

    @Select("<script>SELECT inv.* FROM invoices inv " +
            "<where>inv.deleted = 0 " +
            "<if test='type != null and type != \"\"'> AND inv.invoice_type = #{type}</if>" +
            "<if test='kw != null and kw != \"\"'> AND (inv.invoice_no LIKE CONCAT('%',#{kw},'%') " +
            "OR inv.customer_name LIKE CONCAT('%',#{kw},'%') OR inv.supplier_name LIKE CONCAT('%',#{kw},'%'))</if>" +
            "</where> ORDER BY inv.id DESC LIMIT 500</script>")
    List<Map<String, Object>> invoiceList(@Param("type") String type, @Param("kw") String keyword);

    @Select("SELECT id FROM invoices WHERE id=#{id} AND deleted = 0")
    List<Map<String, Object>> invoiceExists(@Param("id") Long id);

    @Update("UPDATE invoices SET deleted = 1 WHERE id=#{id}")
    int invoiceDelete(@Param("id") Long id);

    /** 待开票单据（未开票余额>0） */
    @Select("<script>SELECT * FROM (" +
            "SELECT d.id, d.dn_no AS doc_no, d.dn_date AS doc_date, d.customer_id AS party_id, " +
            "COALESCE(c.name,'') AS party_name, d.total_amount AS amount, " +
            "COALESCE(i.invoiced,0) AS invoiced_amount, d.total_amount - COALESCE(i.invoiced,0) AS balance " +
            "FROM delivery_notes d LEFT JOIN customers c ON c.id = d.customer_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS invoiced FROM invoices WHERE ref_type='delivery' AND deleted = 0 GROUP BY ref_id) i ON i.ref_id = d.id " +
            "<where>d.deleted = 0 <if test='partyId != null'> AND d.customer_id = #{partyId}</if></where>" +
            ") t WHERE t.balance &gt; 0 ORDER BY t.id DESC LIMIT 500</script>")
    List<Map<String, Object>> salesInvoiceRefs(@Param("partyId") Long partyId);

    @Select("<script>SELECT * FROM (" +
            "SELECT po.id, po.po_no AS doc_no, po.po_date AS doc_date, po.supplier_id AS party_id, " +
            "COALESCE(s.name,'') AS party_name, po.total_amount AS amount, " +
            "COALESCE(i.invoiced,0) AS invoiced_amount, po.total_amount - COALESCE(i.invoiced,0) AS balance " +
            "FROM purchase_orders po LEFT JOIN suppliers s ON s.id = po.supplier_id " +
            "LEFT JOIN (SELECT ref_id, SUM(amount) AS invoiced FROM invoices WHERE ref_type='purchase' AND deleted = 0 GROUP BY ref_id) i ON i.ref_id = po.id " +
            "<where>po.deleted = 0 <if test='partyId != null'> AND po.supplier_id = #{partyId}</if></where>" +
            ") t WHERE t.balance &gt; 0 ORDER BY t.id DESC LIMIT 500</script>")
    List<Map<String, Object>> purchaseInvoiceRefs(@Param("partyId") Long partyId);

    /** 生成雪花主键（触发器同款 sfid()） */
    @Select("SELECT sfid()")
    Long nextId();

    /** 按单据号回查 id（触发器生成雪花主键后 LAST_INSERT_ID 不可用） */
    @Select("SELECT id FROM invoices WHERE invoice_no = #{invoiceNo} AND deleted = 0 LIMIT 1")
    Long lastInvoiceId(@Param("invoiceNo") String invoiceNo);

    @Select("SELECT id FROM receipt_vouchers WHERE rv_no = #{rvNo} AND deleted = 0 LIMIT 1")
    Long lastReceiptId(@Param("rvNo") String rvNo);

    @Select("SELECT id FROM payment_vouchers WHERE pv_no = #{pvNo} AND deleted = 0 LIMIT 1")
    Long lastPaymentId(@Param("pvNo") String pvNo);
}
