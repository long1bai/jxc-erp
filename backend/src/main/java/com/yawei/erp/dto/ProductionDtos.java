package com.yawei.erp.dto;

import java.math.BigDecimal;
import java.util.List;

/** 生产管理 DTO */
public final class ProductionDtos {

    private ProductionDtos() {}

    public record PiReq(String inDate, String remark, List<PiItemReq> items) {}
    public record PiItemReq(Long productId, BigDecimal quantity) {}
    public record PrtReq(String returnDate, String remark, List<PrtItemReq> items) {}
    public record PrtItemReq(Long materialId, BigDecimal quantity) {}
}
