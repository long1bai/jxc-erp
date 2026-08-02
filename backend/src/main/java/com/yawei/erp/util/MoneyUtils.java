package com.yawei.erp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 金额计算工具 */
public final class MoneyUtils {

    private MoneyUtils() {}

    /** 数量 × 单价, 保留 2 位 */
    public static BigDecimal amount(BigDecimal qty, BigDecimal price) {
        if (qty == null || price == null) {
            return BigDecimal.ZERO;
        }
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
}
