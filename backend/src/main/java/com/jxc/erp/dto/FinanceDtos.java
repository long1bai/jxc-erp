package com.jxc.erp.dto;

import java.math.BigDecimal;

/** 财务模块 DTO */
public final class FinanceDtos {

    private FinanceDtos() {}

    /** 核销请求 */
    public record SettleReq(String refType, Long refId, Long voucherId, BigDecimal amount, String remark) {}

    /** 发票请求 */
    public record InvoiceReq(String invoiceType, Long refId, Long customerId, String customerName,
                             Long supplierId, String supplierName, BigDecimal amount,
                             String invoiceDate, String remark) {}
}
