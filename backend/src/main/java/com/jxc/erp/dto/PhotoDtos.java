package com.jxc.erp.dto;

import java.math.BigDecimal;
import java.util.List;

/** 拍照入库 DTO */
public final class PhotoDtos {

    private PhotoDtos() {}

    /** 拍照识别请求 */
    public record PhotoReq(String imageBase64) {}

    /** 确认入库明细项 */
    public record ConfirmItem(Long materialId, String name, String spec, String unit,
                              BigDecimal quantity, BigDecimal unitPrice) {}

    /** 确认入库请求 */
    public record ConfirmReq(Long supplierId, String poDate, String remark, Long warehouseId,
                             List<ConfirmItem> items) {}
}
