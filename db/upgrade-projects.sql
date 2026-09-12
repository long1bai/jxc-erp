-- jxc ERP：项目材料成本台账 —— 存量库升级脚本（幂等，可重复执行）
-- 对应 init.sql 全量脚本中的：projects 表 + 触发器 + purchase_orders/purchase_items 加列

USE jxc_erp;

-- 1) 建 projects 表（幂等）
CREATE TABLE IF NOT EXISTS `projects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) DEFAULT NULL COMMENT '项目编号（可空，如 P2026-001）',
  `name` varchar(255) NOT NULL COMMENT '项目名称',
  `customer_id` bigint NOT NULL COMMENT '客户（甲方）',
  `remark` text,
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT 'active/done',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_projects_name` (`name`),
  KEY `idx_projects_customer` (`customer_id`),
  CONSTRAINT `fk_projects_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 雪花 ID 触发器（幂等：先删再建，与全量脚本一致）
DROP TRIGGER IF EXISTS trg_projects_bi;
CREATE TRIGGER trg_projects_bi BEFORE INSERT ON `projects` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);

-- 3) purchase_orders 加 project_id / project_name + 索引 + 外键（幂等：不存在才加）
SET @po_project = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='jxc_erp' AND TABLE_NAME='purchase_orders' AND COLUMN_NAME='project_id');
SET @po_project_name = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='jxc_erp' AND TABLE_NAME='purchase_orders' AND COLUMN_NAME='project_name');
SET @po_project_key = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='jxc_erp' AND TABLE_NAME='purchase_orders' AND INDEX_NAME='idx_po_project');
SET @po_project_fk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='jxc_erp' AND TABLE_NAME='purchase_orders' AND CONSTRAINT_NAME='fk_purchase_orders_project' AND CONSTRAINT_TYPE='FOREIGN KEY');

SET @sql = IF(@po_project=0, "ALTER TABLE purchase_orders ADD COLUMN project_id bigint DEFAULT NULL COMMENT '关联项目'", "SELECT 'skip: project_id exists'");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF(@po_project_name=0, "ALTER TABLE purchase_orders ADD COLUMN project_name varchar(255) DEFAULT NULL COMMENT '项目名称冗余（显示用）'", "SELECT 'skip: project_name exists'");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF(@po_project_key=0, "ALTER TABLE purchase_orders ADD KEY idx_po_project (project_id)", "SELECT 'skip: idx_po_project exists'");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF(@po_project_fk=0, "ALTER TABLE purchase_orders ADD CONSTRAINT fk_purchase_orders_project FOREIGN KEY (project_id) REFERENCES projects (id)", "SELECT 'skip: fk_purchase_orders_project exists'");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 4) purchase_items 加 assembly_system 列（幂等）
SET @pi_assembly = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='jxc_erp' AND TABLE_NAME='purchase_items' AND COLUMN_NAME='assembly_system');
SET @sql = IF(@pi_assembly=0, "ALTER TABLE purchase_items ADD COLUMN assembly_system varchar(100) DEFAULT NULL COMMENT '装配系统（自由填写）'", "SELECT 'skip: assembly_system exists'");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
