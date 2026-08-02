package com.yawei.erp.dto;

import java.math.BigDecimal;
import java.util.List;

/** 独立采购订单 DTO */
public final class PoOrderDtos {

    private PoOrderDtos() {}

    public record ItemReq(Long materialId, String materialName, String spec, String unit,
                          BigDecimal quantity, BigDecimal unitPrice) {}

    public record CreateReq(Long supplierId, String orderDate, String handler, String remark, List<ItemReq> items) {}

    public record ReceiveReq(Long materialId, BigDecimal quantity) {}
}
