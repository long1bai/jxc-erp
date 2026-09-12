-- jxc ERP：单据能力中心 —— 动态字段元数据表（幂等，可重复执行）
-- 对应 init.sql 全量脚本中的：doc_field_defs 表
-- 用途：单据动态字段（②列可变）的字段定义元数据；值存各单据表已有 ext_json 列（不参与统计/筛选）

USE jxc_erp;

-- 1) 建 doc_field_defs 表（元数据/配置类表，同 sys_config 定位，用 AUTO_INCREMENT，不进雪花触发器体系）
CREATE TABLE IF NOT EXISTS `doc_field_defs` (
  `id`         bigint NOT NULL AUTO_INCREMENT,
  `doc_type`   varchar(32)  NOT NULL COMMENT '单据类型标识，如 purchase_order',
  `field_key`  varchar(64)  NOT NULL COMMENT 'ext_json 键名',
  `field_name` varchar(64)  NOT NULL COMMENT '列显示名（中文）',
  `field_type` varchar(16)  NOT NULL DEFAULT 'text' COMMENT 'text|number|date|select',
  `options`    text         NULL COMMENT 'select 选项，JSON 数组字符串，如 ["内销","外销"]',
  `sort_order` int          NOT NULL DEFAULT 0,
  `enabled`    tinyint      NOT NULL DEFAULT 1,
  `created_at` datetime     DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_type_key` (`doc_type`, `field_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
