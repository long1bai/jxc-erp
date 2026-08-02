package com.yawei.erp.common;

/**
 * 全局常量（业务魔法值集中管理）
 *
 * 规范：业务字面量（单号前缀 / 角色 / 库存流水类型）禁止散落各处硬编码，
 * 统一引用本类；改一处全局生效。
 */
public final class Constants {

    private Constants() {
    }

    // ============ 单号前缀（seq.nextDaily 用） ============
    /** 客户订单 */
    public static final String SEQ_SALES_ORDER = "XSDD";
    /** 销售退货 */
    public static final String SEQ_SALES_RETURN = "XSTH";
    /** 成品入库 */
    public static final String SEQ_PRODUCT_IN = "CPRK";
    /** 生产退料 */
    public static final String SEQ_PRODUCT_RETURN = "PCTL";
    /** 库存盘点 */
    public static final String SEQ_STOCK_TAKE = "PD";
    /** 库存调拨 */
    public static final String SEQ_STOCK_TRANSFER = "ST";
    /** 采购订单 */
    public static final String SEQ_PO_ORDER = "PO";

    // ============ 角色（users.role） ============
    /** 管理员（全部权限） */
    public static final String ROLE_ADMIN = "admin";
    /** 老板（报表/财务查看） */
    public static final String ROLE_BOSS = "boss";
    /** 开发 */
    public static final String ROLE_DEV = "dev";
    /** 员工（报工/拍照） */
    public static final String ROLE_EMPLOYEE = "employee";

    // ============ 库存流水类型（stock_movements.move_type） ============
    /** 入库 */
    public static final String MOVE_IN = "in";
    /** 出库 */
    public static final String MOVE_OUT = "out";
    /** 盘点调整 */
    public static final String MOVE_ADJUST = "adjust";

    // ============ 其他 ============
    /** 雪花 ID 阈值：小于该值的自增 ID 在触发器中被覆盖为雪花（对应 db/snowflake_migration.sql 的 1e14） */
    public static final long SNOWFLAKE_THRESHOLD = 100000000000000L;
}
