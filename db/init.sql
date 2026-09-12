-- ============================================================
-- 进销存系统 数据库初始化脚本（干净库）
-- 无业务数据；字典: 仓库/工序/快递公司/审批配置
-- 初始账号: admin / admin123（首次登录后请修改）
-- 生成日期: 2026-08-04
-- ============================================================
CREATE DATABASE IF NOT EXISTS `jxc_erp` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `jxc_erp`;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `approval_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_config` (
  `id` bigint NOT NULL,
  `doc_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint DEFAULT '0',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `doc_type` (`doc_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单据审批配置（0=创建即生效,1=需审批）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bom_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bom_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `component_id` bigint NOT NULL,
  `quantity` decimal(14,4) NOT NULL DEFAULT '1.0000',
  `unit` varchar(32) DEFAULT NULL,
  `remark` text,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bom` (`product_id`,`component_id`),
  KEY `fk_bom_component` (`component_id`),
  CONSTRAINT `fk_bom_component` FOREIGN KEY (`component_id`) REFERENCES `materials` (`id`),
  CONSTRAINT `fk_bom_product` FOREIGN KEY (`product_id`) REFERENCES `materials` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568127323301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `cash_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cash_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `account_type` varchar(16) DEFAULT 'cash',
  `initial_balance` decimal(14,2) DEFAULT '0.00',
  `remark` varchar(255) DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ca_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128338301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `customer_order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `co_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `unit_price` decimal(12,4) DEFAULT NULL,
  `amount` decimal(14,2) DEFAULT NULL,
  `delivered_quantity` decimal(14,3) DEFAULT '0.000',
  `remark` text,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_coi_co` (`co_id`),
  KEY `idx_coi_material` (`material_id`),
  CONSTRAINT `fk_coi_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`),
  CONSTRAINT `fk_coi_order` FOREIGN KEY (`co_id`) REFERENCES `customer_orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568127428601 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `customer_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `co_no` varchar(64) DEFAULT NULL,
  `customer_id` bigint NOT NULL,
  `customer_name` varchar(500) NOT NULL,
  `order_date` date NOT NULL,
  `expected_date` date DEFAULT NULL,
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `total_amount` decimal(14,2) DEFAULT '0.00',
  `remark` text,
  `status` varchar(16) DEFAULT 'pending',
  `source` varchar(16) DEFAULT 'manual',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delivered_quantity` decimal(14,3) NOT NULL DEFAULT '0.000',
  `delivered_amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `co_no` (`co_no`),
  KEY `idx_co_date` (`order_date`),
  KEY `idx_co_customer` (`customer_id`),
  KEY `idx_co_status` (`status`),
  CONSTRAINT `fk_customer_orders_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127428401 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `contact` varchar(64) DEFAULT NULL,
  `phone` varchar(64) DEFAULT NULL,
  `region` varchar(100) DEFAULT '' COMMENT '客户区域',
  `address` text,
  `bank_account` varchar(128) DEFAULT NULL,
  `tax_id` varchar(64) DEFAULT NULL,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_customers_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=178580903499401 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `delivery_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dn_id` bigint NOT NULL,
  `co_item_id` bigint DEFAULT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `unit_price` decimal(12,4) DEFAULT NULL,
  `amount` decimal(14,2) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `idx_dn_items_dn` (`dn_id`),
  KEY `idx_dn_items_material` (`material_id`),
  KEY `idx_dn_items_coi` (`co_item_id`),
  CONSTRAINT `fk_dn_items_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`),
  CONSTRAINT `fk_dn_items_note` FOREIGN KEY (`dn_id`) REFERENCES `delivery_notes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568127435901 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `delivery_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_notes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dn_no` varchar(64) DEFAULT NULL,
  `customer_order_id` bigint DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `customer_name` varchar(500) DEFAULT NULL,
  `dn_date` date NOT NULL,
  `handler` varchar(100) DEFAULT NULL COMMENT '经手人',
  `warehouse_id` bigint DEFAULT '1',
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `total_amount` decimal(14,2) DEFAULT '0.00',
  `print_count` int DEFAULT '0',
  `remark` text,
  `approve_status` varchar(16) NOT NULL DEFAULT 'approved' COMMENT 'approved/pending/rejected',
  `approve_by` varchar(100) DEFAULT NULL,
  `approve_at` datetime DEFAULT NULL,
  `status` varchar(16) DEFAULT 'done',
  `source` varchar(16) DEFAULT 'manual',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `dn_no` (`dn_no`),
  KEY `idx_dn_date` (`dn_date`),
  KEY `idx_dn_customer` (`customer_id`),
  KEY `idx_dn_co` (`customer_order_id`),
  CONSTRAINT `fk_delivery_notes_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `fk_delivery_notes_order` FOREIGN KEY (`customer_order_id`) REFERENCES `customer_orders` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127435701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `express_companies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `express_companies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `contact` varchar(100) DEFAULT '',
  `phone` varchar(50) DEFAULT '',
  `remark` varchar(255) DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ec_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127277301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `income_expenses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `income_expenses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ie_no` varchar(64) DEFAULT NULL,
  `ie_type` varchar(8) NOT NULL,
  `category` varchar(64) NOT NULL,
  `account_id` bigint DEFAULT NULL,
  `amount` decimal(14,2) NOT NULL,
  `ie_date` date NOT NULL,
  `remark` varchar(500) DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ie_no` (`ie_no`),
  KEY `idx_ie_date` (`ie_date`),
  KEY `idx_ie_account` (`account_id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128349401 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_no` varchar(64) DEFAULT NULL,
  `invoice_type` varchar(8) NOT NULL,
  `ref_type` varchar(32) NOT NULL,
  `ref_id` bigint NOT NULL,
  `customer_id` bigint DEFAULT '0',
  `customer_name` varchar(500) DEFAULT '',
  `supplier_id` bigint DEFAULT '0',
  `supplier_name` varchar(500) DEFAULT '',
  `amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `invoice_date` date NOT NULL,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_no` (`invoice_no`),
  KEY `idx_invoices_ref` (`ref_type`,`ref_id`),
  KEY `idx_invoices_type` (`invoice_type`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128388101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `materials`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `materials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `category` varchar(64) DEFAULT NULL,
  `purchase_price` decimal(12,4) DEFAULT NULL,
  `sale_price` decimal(12,4) DEFAULT NULL,
  `min_stock` decimal(12,3) DEFAULT NULL,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_materials_category` (`category`),
  KEY `idx_materials_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127395601 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `operation_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_name` varchar(50) DEFAULT '' COMMENT '鎿嶄綔鐢ㄦ埛',
  `role` varchar(20) DEFAULT '' COMMENT '鐢ㄦ埛瑙掕壊',
  `method` varchar(10) DEFAULT '' COMMENT 'HTTP鏂规硶',
  `path` varchar(200) DEFAULT '' COMMENT '鎺ュ彛璺?緞',
  `module` varchar(50) DEFAULT '' COMMENT '妯″潡',
  `detail` varchar(500) DEFAULT '' COMMENT '鎿嶄綔璇︽儏',
  `ip` varchar(50) DEFAULT '' COMMENT 'IP',
  `status` int DEFAULT '200' COMMENT '鐘舵?鐮',
  `cost_ms` int DEFAULT '0' COMMENT '鑰楁椂ms',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鏃堕棿',
  `deleted` tinyint DEFAULT '0' COMMENT '閫昏緫鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_logs_time` (`created_at`),
  KEY `idx_logs_user` (`user_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2715 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎿嶄綔鏃ュ織';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment_vouchers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_vouchers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pv_no` varchar(64) DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `supplier_name` varchar(500) NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `pay_date` date NOT NULL,
  `pay_method` varchar(16) DEFAULT '转账',
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pv_no` (`pv_no`),
  KEY `idx_pv_date` (`pay_date`),
  KEY `idx_pv_supplier` (`supplier_id`),
  CONSTRAINT `fk_payment_vouchers_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128369701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `po_order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `po_order_items` (
  `id` bigint NOT NULL,
  `po_order_id` bigint DEFAULT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `spec` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unit` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL DEFAULT '0.000',
  `unit_price` decimal(14,4) NOT NULL DEFAULT '0.0000',
  `amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `received_quantity` decimal(14,3) NOT NULL DEFAULT '0.000' COMMENT '已入库数量（分批累计）',
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_poi_order` (`po_order_id`),
  KEY `idx_poi_material` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `po_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `po_orders` (
  `id` bigint NOT NULL,
  `po_order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `supplier_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `order_date` date DEFAULT NULL,
  `handler` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/partial/done',
  `total_quantity` decimal(14,3) NOT NULL DEFAULT '0.000',
  `total_amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `po_order_no` (`po_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `process_materials`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `process_materials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `process_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `quantity_per_unit` decimal(12,4) NOT NULL DEFAULT '1.0000',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pm_material` (`material_id`),
  KEY `idx_process_materials_process` (`process_id`),
  CONSTRAINT `fk_pm_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`),
  CONSTRAINT `fk_pm_process` FOREIGN KEY (`process_id`) REFERENCES `processes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568128270501 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `processes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `processes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `description` text,
  `group_id` bigint DEFAULT '0',
  `sort_order` int DEFAULT '0',
  `unit_price` decimal(12,4) DEFAULT '0.0000',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127299901 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `production_in_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_in_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pi_id` bigint NOT NULL,
  `product_id` bigint DEFAULT NULL,
  `product_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `unit_cost` decimal(12,4) DEFAULT '0.0000',
  `amount` decimal(14,2) DEFAULT '0.00',
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pii_pi` (`pi_id`),
  CONSTRAINT `fk_pii_pi` FOREIGN KEY (`pi_id`) REFERENCES `production_ins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568128224101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `production_ins`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_ins` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pi_no` varchar(64) DEFAULT NULL,
  `in_date` date NOT NULL,
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `total_items` int DEFAULT '0',
  `remark` varchar(500) DEFAULT '',
  `status` varchar(16) DEFAULT 'done',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pi_no` (`pi_no`),
  KEY `idx_pi_date` (`in_date`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128223901 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `production_return_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_return_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prt_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pri_prt` (`prt_id`),
  CONSTRAINT `fk_pri_prt` FOREIGN KEY (`prt_id`) REFERENCES `production_returns` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568128236201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `production_returns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `production_returns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prt_no` varchar(64) DEFAULT NULL,
  `return_date` date NOT NULL,
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `remark` varchar(500) DEFAULT '',
  `status` varchar(16) DEFAULT 'done',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prt_no` (`prt_no`),
  KEY `idx_prt_date` (`return_date`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128236001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `projects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projects` (
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
) ENGINE=InnoDB AUTO_INCREMENT=178568127412701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `po_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `assembly_system` varchar(100) DEFAULT NULL COMMENT '装配系统（自由填写）',
  `quantity` decimal(14,3) NOT NULL,
  `unit_price` decimal(12,4) DEFAULT NULL,
  `amount` decimal(14,2) DEFAULT NULL,
  `remark` text,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_po_items_po` (`po_id`),
  KEY `idx_po_items_material` (`material_id`),
  CONSTRAINT `fk_po_items_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`),
  CONSTRAINT `fk_po_items_order` FOREIGN KEY (`po_id`) REFERENCES `purchase_orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568127412901 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `po_no` varchar(64) DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `project_id` bigint DEFAULT NULL COMMENT '关联项目',
  `project_name` varchar(255) DEFAULT NULL COMMENT '项目名称冗余（显示用）',
  `po_date` date NOT NULL,
  `handler` varchar(100) DEFAULT NULL COMMENT '经手人',
  `po_order_id` bigint DEFAULT NULL COMMENT '关联采购订单',
  `warehouse_id` bigint DEFAULT '1',
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `total_amount` decimal(14,2) DEFAULT '0.00',
  `image_path` text,
  `remark` text,
  `approve_status` varchar(16) NOT NULL DEFAULT 'approved' COMMENT 'approved/pending/rejected',
  `approve_by` varchar(100) DEFAULT NULL,
  `approve_at` datetime DEFAULT NULL,
  `status` varchar(16) DEFAULT 'done',
  `source` varchar(16) DEFAULT 'manual',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `po_no` (`po_no`),
  KEY `idx_po_date` (`po_date`),
  KEY `idx_po_supplier` (`supplier_id`),
  KEY `idx_po_project` (`project_id`),
  CONSTRAINT `fk_purchase_orders_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `fk_purchase_orders_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127412701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_return_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_return_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pr_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `unit_price` decimal(12,4) DEFAULT NULL,
  `amount` decimal(14,2) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `idx_pr_items_pr` (`pr_id`),
  CONSTRAINT `fk_pr_items_return` FOREIGN KEY (`pr_id`) REFERENCES `purchase_returns` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568127344201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_returns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_returns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pr_no` varchar(64) DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `supplier_name` varchar(500) NOT NULL,
  `return_date` date NOT NULL,
  `po_id` bigint DEFAULT NULL,
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `total_amount` decimal(14,2) DEFAULT '0.00',
  `remark` text,
  `status` varchar(16) DEFAULT 'done',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `pr_no` (`pr_no`),
  KEY `idx_pr_date` (`return_date`),
  KEY `idx_purchase_returns_supplier` (`supplier_id`),
  CONSTRAINT `fk_purchase_returns_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127344101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `receipt_vouchers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receipt_vouchers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rv_no` varchar(64) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `customer_name` varchar(500) NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `receipt_date` date NOT NULL,
  `receipt_method` varchar(16) DEFAULT '转账',
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `rv_no` (`rv_no`),
  KEY `idx_rv_date` (`receipt_date`),
  KEY `idx_rv_customer` (`customer_id`),
  CONSTRAINT `fk_receipt_vouchers_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128365201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sales_return_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_return_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sr_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `unit_price` decimal(12,4) DEFAULT NULL,
  `amount` decimal(14,2) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `remark` varchar(500) DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sri_sr` (`sr_id`),
  CONSTRAINT `fk_sri_sr` FOREIGN KEY (`sr_id`) REFERENCES `sales_returns` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568127451701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sales_returns`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sales_returns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sr_no` varchar(64) DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `customer_name` varchar(500) NOT NULL,
  `return_date` date NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `total_amount` decimal(14,2) DEFAULT '0.00',
  `remark` text,
  `status` varchar(16) DEFAULT 'done',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sr_no` (`sr_no`),
  KEY `idx_sr_customer` (`customer_id`),
  KEY `idx_sr_date` (`return_date`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127451601 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sequences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sequences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prefix` varchar(16) NOT NULL,
  `year` varchar(4) NOT NULL,
  `month` varchar(2) NOT NULL,
  `seq` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seq` (`prefix`,`year`,`month`)
) ENGINE=InnoDB AUTO_INCREMENT=178567915623801 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settlements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `settle_type` varchar(8) NOT NULL,
  `voucher_id` bigint NOT NULL,
  `voucher_no` varchar(64) DEFAULT '',
  `ref_type` varchar(32) NOT NULL,
  `ref_id` bigint NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_settle_ref` (`ref_type`,`ref_id`),
  KEY `idx_settle_voucher` (`voucher_id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128383801 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stock_movements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_movements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `material_id` bigint NOT NULL,
  `warehouse_id` bigint DEFAULT '1',
  `move_type` varchar(8) NOT NULL,
  `ref_type` varchar(32) NOT NULL,
  `ref_id` bigint NOT NULL,
  `ref_item_id` bigint DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL,
  `before_stock` decimal(14,3) DEFAULT '0.000',
  `after_stock` decimal(14,3) DEFAULT '0.000',
  `unit_price` decimal(12,4) DEFAULT NULL,
  `amount` decimal(14,2) DEFAULT NULL,
  `move_date` date NOT NULL,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_material` (`material_id`),
  KEY `idx_stock_date` (`move_date`),
  CONSTRAINT `fk_stock_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128291101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stock_take_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_take_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `st_id` bigint NOT NULL,
  `material_id` bigint DEFAULT NULL,
  `material_name` varchar(500) NOT NULL,
  `spec` text,
  `unit` varchar(32) DEFAULT NULL,
  `book_qty` decimal(14,3) DEFAULT '0.000',
  `actual_qty` decimal(14,3) DEFAULT '0.000',
  `diff_qty` decimal(14,3) DEFAULT '0.000',
  `unit_cost` decimal(12,4) DEFAULT '0.0000',
  `diff_amount` decimal(14,2) DEFAULT '0.00',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sti_st` (`st_id`),
  CONSTRAINT `fk_sti_st` FOREIGN KEY (`st_id`) REFERENCES `stock_takes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568128250001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stock_takes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_takes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `st_no` varchar(64) DEFAULT NULL,
  `take_date` date NOT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  `status` varchar(16) DEFAULT 'draft',
  `total_items` int DEFAULT '0',
  `total_diff_qty` decimal(14,3) DEFAULT '0.000',
  `total_diff_amount` decimal(14,2) DEFAULT '0.00',
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_st_no` (`st_no`),
  KEY `idx_st_date` (`take_date`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128249601 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stock_transfer_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_transfer_items` (
  `id` bigint NOT NULL,
  `transfer_id` bigint NOT NULL,
  `material_id` bigint NOT NULL,
  `material_name` varchar(100) DEFAULT '',
  `spec` varchar(100) DEFAULT '',
  `unit` varchar(20) DEFAULT '',
  `quantity` decimal(14,3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tf_item` (`transfer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='璋冩嫧鏄庣粏';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stock_transfers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_transfers` (
  `id` bigint NOT NULL,
  `transfer_no` varchar(32) NOT NULL COMMENT '璋冩嫧鍗曞彿 ST+鏃ユ湡+搴忓彿',
  `from_warehouse_id` bigint DEFAULT '0' COMMENT '璋冨嚭浠撳簱',
  `from_warehouse_name` varchar(50) DEFAULT '',
  `to_warehouse_id` bigint DEFAULT '0' COMMENT '璋冨叆浠撳簱',
  `to_warehouse_name` varchar(50) DEFAULT '',
  `transfer_date` date DEFAULT NULL COMMENT '璋冩嫧鏃ユ湡',
  `total_items` int DEFAULT '0',
  `total_quantity` decimal(14,3) DEFAULT '0.000',
  `remark` varchar(500) DEFAULT '',
  `created_by` varchar(50) DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_tf_no` (`transfer_no`),
  KEY `idx_tf_date` (`transfer_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搴撳瓨璋冩嫧鍗';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `suppliers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `suppliers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `contact` varchar(64) DEFAULT NULL,
  `phone` varchar(64) DEFAULT NULL,
  `address` text,
  `bank_account` varchar(128) DEFAULT NULL,
  `tax_id` varchar(64) DEFAULT NULL,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_suppliers_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127251701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(1000) DEFAULT '',
  `remark` varchar(255) DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ck` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=178568138136901 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `transfers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transfers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tf_no` varchar(64) DEFAULT NULL,
  `from_account_id` bigint NOT NULL,
  `to_account_id` bigint NOT NULL,
  `amount` decimal(14,2) NOT NULL,
  `tf_date` date NOT NULL,
  `remark` varchar(500) DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tf_no` (`tf_no`),
  KEY `idx_tf_date` (`tf_date`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128353301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `display_name` varchar(64) DEFAULT NULL,
  `role` varchar(16) DEFAULT 'operator',
  `work_employee_id` bigint DEFAULT NULL,
  `is_active` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=178568128439201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `warehouses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `remark` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=178580903492801 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `work_employees`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_employees` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(128) DEFAULT '123456',
  `name` varchar(64) NOT NULL,
  `phone` varchar(32) DEFAULT '',
  `group_id` bigint DEFAULT '0',
  `group_name` varchar(64) DEFAULT '',
  `role` varchar(16) DEFAULT 'employee',
  `status` varchar(16) DEFAULT 'active',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=178568127290701 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `work_group_processes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_group_processes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `process_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gp` (`group_id`,`process_id`),
  KEY `fk_wgp_process` (`process_id`),
  CONSTRAINT `fk_wgp_group` FOREIGN KEY (`group_id`) REFERENCES `work_groups` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_wgp_process` FOREIGN KEY (`process_id`) REFERENCES `processes` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `work_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `description` text,
  `leader_id` bigint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=888888888888889 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `work_report_images`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_report_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL,
  `image_path` text NOT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wri_report` (`report_id`),
  CONSTRAINT `fk_wri_report` FOREIGN KEY (`report_id`) REFERENCES `work_reports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=178568128275801 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `work_reports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `work_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(64) NOT NULL,
  `group_id` bigint DEFAULT '0',
  `group_name` varchar(64) DEFAULT '',
  `process_id` bigint DEFAULT '0',
  `process_name` varchar(64) DEFAULT '',
  `quantity` decimal(14,3) DEFAULT '0.000',
  `start_time` varchar(32) DEFAULT NULL,
  `end_time` varchar(32) DEFAULT NULL,
  `duration` varchar(32) DEFAULT '',
  `remark` text,
  `image_count` int DEFAULT '0',
  `status` varchar(16) DEFAULT 'completed',
  `report_date` varchar(16) DEFAULT NULL,
  `material_id` bigint DEFAULT '0',
  `material_name` varchar(500) DEFAULT '',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT '0',
  `ext_json` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wr_employee` (`employee_id`),
  KEY `idx_wr_date` (`report_date`),
  KEY `idx_work_reports_group` (`group_id`),
  KEY `idx_work_reports_employee` (`employee_id`),
  KEY `idx_work_reports_process` (`process_id`),
  KEY `idx_work_reports_date` (`report_date`),
  CONSTRAINT `fk_wr_employee` FOREIGN KEY (`employee_id`) REFERENCES `work_employees` (`id`),
  CONSTRAINT `fk_wr_group` FOREIGN KEY (`group_id`) REFERENCES `work_groups` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_wr_process` FOREIGN KEY (`process_id`) REFERENCES `processes` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=178568128297301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ===== 雪花ID函数 =====
DELIMITER $$
CREATE FUNCTION sfid() RETURNS BIGINT DETERMINISTIC
BEGIN
  DECLARE ts BIGINT;
  DECLARE s INT;
  SET @sf_lock = GET_LOCK('jxc_sfid', 1);
  SET ts = FLOOR(UNIX_TIMESTAMP(NOW(3)) * 1000);
  IF @sfid_ts = ts THEN
    SET s = IFNULL(@sfid_seq, 0) + 1;
  ELSE
    SET s = 0;
  END IF;
  SET @sfid_ts = ts;
  SET @sfid_seq = s;
  DO RELEASE_LOCK('jxc_sfid');
  RETURN ts * 100 + s;
END$$
DELIMITER ;

-- ===== 雪花 ID 兜底触发器（44 表） =====
DROP TRIGGER IF EXISTS trg_bom_items_bi;
CREATE TRIGGER trg_bom_items_bi BEFORE INSERT ON `bom_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_cash_accounts_bi;
CREATE TRIGGER trg_cash_accounts_bi BEFORE INSERT ON `cash_accounts` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_customer_order_items_bi;
CREATE TRIGGER trg_customer_order_items_bi BEFORE INSERT ON `customer_order_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_customer_orders_bi;
CREATE TRIGGER trg_customer_orders_bi BEFORE INSERT ON `customer_orders` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_customers_bi;
CREATE TRIGGER trg_customers_bi BEFORE INSERT ON `customers` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_delivery_items_bi;
CREATE TRIGGER trg_delivery_items_bi BEFORE INSERT ON `delivery_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_delivery_notes_bi;
CREATE TRIGGER trg_delivery_notes_bi BEFORE INSERT ON `delivery_notes` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_express_companies_bi;
CREATE TRIGGER trg_express_companies_bi BEFORE INSERT ON `express_companies` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_income_expenses_bi;
CREATE TRIGGER trg_income_expenses_bi BEFORE INSERT ON `income_expenses` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_invoices_bi;
CREATE TRIGGER trg_invoices_bi BEFORE INSERT ON `invoices` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_materials_bi;
CREATE TRIGGER trg_materials_bi BEFORE INSERT ON `materials` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_payment_vouchers_bi;
CREATE TRIGGER trg_payment_vouchers_bi BEFORE INSERT ON `payment_vouchers` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_po_order_items_bi;
CREATE TRIGGER trg_po_order_items_bi BEFORE INSERT ON `po_order_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_po_orders_bi;
CREATE TRIGGER trg_po_orders_bi BEFORE INSERT ON `po_orders` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_process_materials_bi;
CREATE TRIGGER trg_process_materials_bi BEFORE INSERT ON `process_materials` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_processes_bi;
CREATE TRIGGER trg_processes_bi BEFORE INSERT ON `processes` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_production_in_items_bi;
CREATE TRIGGER trg_production_in_items_bi BEFORE INSERT ON `production_in_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_production_ins_bi;
CREATE TRIGGER trg_production_ins_bi BEFORE INSERT ON `production_ins` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_production_return_items_bi;
CREATE TRIGGER trg_production_return_items_bi BEFORE INSERT ON `production_return_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_production_returns_bi;
CREATE TRIGGER trg_production_returns_bi BEFORE INSERT ON `production_returns` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_projects_bi;
CREATE TRIGGER trg_projects_bi BEFORE INSERT ON `projects` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_purchase_items_bi;
CREATE TRIGGER trg_purchase_items_bi BEFORE INSERT ON `purchase_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_purchase_orders_bi;
CREATE TRIGGER trg_purchase_orders_bi BEFORE INSERT ON `purchase_orders` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_purchase_return_items_bi;
CREATE TRIGGER trg_purchase_return_items_bi BEFORE INSERT ON `purchase_return_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_purchase_returns_bi;
CREATE TRIGGER trg_purchase_returns_bi BEFORE INSERT ON `purchase_returns` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_receipt_vouchers_bi;
CREATE TRIGGER trg_receipt_vouchers_bi BEFORE INSERT ON `receipt_vouchers` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_sales_return_items_bi;
CREATE TRIGGER trg_sales_return_items_bi BEFORE INSERT ON `sales_return_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_sales_returns_bi;
CREATE TRIGGER trg_sales_returns_bi BEFORE INSERT ON `sales_returns` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_sequences_bi;
CREATE TRIGGER trg_sequences_bi BEFORE INSERT ON `sequences` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_settlements_bi;
CREATE TRIGGER trg_settlements_bi BEFORE INSERT ON `settlements` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_stock_movements_bi;
CREATE TRIGGER trg_stock_movements_bi BEFORE INSERT ON `stock_movements` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_stock_take_items_bi;
CREATE TRIGGER trg_stock_take_items_bi BEFORE INSERT ON `stock_take_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_stock_takes_bi;
CREATE TRIGGER trg_stock_takes_bi BEFORE INSERT ON `stock_takes` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_stock_transfer_items_bi;
CREATE TRIGGER trg_stock_transfer_items_bi BEFORE INSERT ON `stock_transfer_items` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_stock_transfers_bi;
CREATE TRIGGER trg_stock_transfers_bi BEFORE INSERT ON `stock_transfers` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_suppliers_bi;
CREATE TRIGGER trg_suppliers_bi BEFORE INSERT ON `suppliers` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_sys_config_bi;
CREATE TRIGGER trg_sys_config_bi BEFORE INSERT ON `sys_config` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_transfers_bi;
CREATE TRIGGER trg_transfers_bi BEFORE INSERT ON `transfers` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_users_bi;
CREATE TRIGGER trg_users_bi BEFORE INSERT ON `users` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_warehouses_bi;
CREATE TRIGGER trg_warehouses_bi BEFORE INSERT ON `warehouses` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_work_employees_bi;
CREATE TRIGGER trg_work_employees_bi BEFORE INSERT ON `work_employees` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_work_group_processes_bi;
CREATE TRIGGER trg_work_group_processes_bi BEFORE INSERT ON `work_group_processes` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_work_groups_bi;
CREATE TRIGGER trg_work_groups_bi BEFORE INSERT ON `work_groups` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_work_report_images_bi;
CREATE TRIGGER trg_work_report_images_bi BEFORE INSERT ON `work_report_images` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_work_reports_bi;
CREATE TRIGGER trg_work_reports_bi BEFORE INSERT ON `work_reports` FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);

-- ===== 字典数据 =====

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `warehouses` WRITE;
/*!40000 ALTER TABLE `warehouses` DISABLE KEYS */;
INSERT INTO `warehouses` (id, name, location, remark, created_at, updated_at, deleted)
VALUES (1, '主仓库', '公司仓库', '默认仓库', NOW(), NOW(), 0);
/*!40000 ALTER TABLE `warehouses` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `processes` WRITE;
/*!40000 ALTER TABLE `processes` DISABLE KEYS */;
/*!40000 ALTER TABLE `processes` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `express_companies` WRITE;
/*!40000 ALTER TABLE `express_companies` DISABLE KEYS */;
/*!40000 ALTER TABLE `express_companies` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `approval_config` WRITE;
/*!40000 ALTER TABLE `approval_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `approval_config` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ===== 初始管理员 =====
INSERT INTO `users` (id, username, password_hash, display_name, role, work_employee_id, is_active, created_at, updated_at, deleted)
VALUES (1, 'admin', 'pbkdf2:sha256:600000$3f236411eadf0d52ff9976f442d32459$07fecc1f851e85b166893f85b901beecc4ec62dcf0683a8845e8944e1cae44b8', '管理员', 'admin', NULL, 1, NOW(), NOW(), 0);

-- ===== 序列 =====
INSERT INTO `sequences` (prefix, year, month, seq) VALUES
('IE','2026','01',0),('ZZ','2026','01',0),('CGDD','2026','01',0),('TH','2026','01',0),
('RCV','2026','01',0),('PAY','2026','01',0),('INV','2026','01',0),('PO','2026','01',0);
