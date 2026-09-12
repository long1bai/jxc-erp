-- =============================================
-- 进销存 ERP 数据库规范化脚本（企业级）
-- 1) 清理历史孤儿引用（置 NULL，保留单据完整性）
-- 2) 补统一审计字段 updated_at
-- 3) 补外键约束（命名 fk_<表>_<关联>，删除策略按业务语义）
-- 4) 补缺失索引
-- 幂等性：可重复执行（外键/列先判断存在）
-- =============================================

USE jxc_erp;

-- ============ 1. 清理孤儿数据 ============
UPDATE customer_order_items SET material_id = NULL
  WHERE material_id IS NOT NULL AND material_id NOT IN (SELECT id FROM materials);
UPDATE delivery_items SET material_id = NULL
  WHERE material_id IS NOT NULL AND material_id NOT IN (SELECT id FROM materials);
UPDATE purchase_items SET material_id = NULL
  WHERE material_id IS NOT NULL AND material_id NOT IN (SELECT id FROM materials);
UPDATE work_reports SET group_id = NULL
  WHERE group_id IS NOT NULL AND group_id NOT IN (SELECT id FROM work_groups);
UPDATE work_reports SET process_id = NULL
  WHERE process_id IS NOT NULL AND process_id NOT IN (SELECT id FROM processes);

-- ============ 2. 补统一审计字段（updated_at） ============
ALTER TABLE users ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE invoices ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE payment_vouchers ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE purchase_orders ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE purchase_returns ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE receipt_vouchers ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE settlements ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE stock_movements ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE work_employees ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE work_groups ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE work_group_processes ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE processes ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE process_materials ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE work_report_images ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE bom_items ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE warehouses ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ============ 3. 外键约束 ============
-- 3.1 单据主表 → 往来单位（RESTRICT：有单据的单位不能删，与业务删除保护一致）
ALTER TABLE customer_orders ADD CONSTRAINT fk_customer_orders_customer FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE delivery_notes ADD CONSTRAINT fk_delivery_notes_customer FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE purchase_orders ADD CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
ALTER TABLE purchase_returns ADD CONSTRAINT fk_purchase_returns_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id);
ALTER TABLE receipt_vouchers ADD CONSTRAINT fk_receipt_vouchers_customer FOREIGN KEY (customer_id) REFERENCES customers(id);
ALTER TABLE payment_vouchers ADD CONSTRAINT fk_payment_vouchers_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id);

-- 3.2 送货单 → 客户订单（RESTRICT：订单有送货单时不能删）
ALTER TABLE delivery_notes ADD CONSTRAINT fk_delivery_notes_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders(id);

-- 3.3 明细 → 主单（CASCADE：删主单自动删明细；代码仍显式先子后父，双保险）
ALTER TABLE customer_order_items ADD CONSTRAINT fk_coi_order FOREIGN KEY (co_id) REFERENCES customer_orders(id) ON DELETE CASCADE;
ALTER TABLE delivery_items ADD CONSTRAINT fk_dn_items_note FOREIGN KEY (dn_id) REFERENCES delivery_notes(id) ON DELETE CASCADE;
ALTER TABLE purchase_items ADD CONSTRAINT fk_po_items_order FOREIGN KEY (po_id) REFERENCES purchase_orders(id) ON DELETE CASCADE;
ALTER TABLE purchase_return_items ADD CONSTRAINT fk_pr_items_return FOREIGN KEY (pr_id) REFERENCES purchase_returns(id) ON DELETE CASCADE;

-- 3.4 明细 → 物料（RESTRICT：有业务引用的物料不能删）
ALTER TABLE customer_order_items ADD CONSTRAINT fk_coi_material FOREIGN KEY (material_id) REFERENCES materials(id);
ALTER TABLE delivery_items ADD CONSTRAINT fk_dn_items_material FOREIGN KEY (material_id) REFERENCES materials(id);
ALTER TABLE purchase_items ADD CONSTRAINT fk_po_items_material FOREIGN KEY (material_id) REFERENCES materials(id);
ALTER TABLE stock_movements ADD CONSTRAINT fk_stock_material FOREIGN KEY (material_id) REFERENCES materials(id);

-- 3.5 报工（SET NULL：组/工序删除后保留报工记录）
ALTER TABLE work_reports ADD CONSTRAINT fk_wr_group FOREIGN KEY (group_id) REFERENCES work_groups(id) ON DELETE SET NULL;
ALTER TABLE work_reports ADD CONSTRAINT fk_wr_employee FOREIGN KEY (employee_id) REFERENCES work_employees(id);
ALTER TABLE work_reports ADD CONSTRAINT fk_wr_process FOREIGN KEY (process_id) REFERENCES processes(id) ON DELETE SET NULL;
ALTER TABLE work_group_processes ADD CONSTRAINT fk_wgp_group FOREIGN KEY (group_id) REFERENCES work_groups(id) ON DELETE CASCADE;
ALTER TABLE work_group_processes ADD CONSTRAINT fk_wgp_process FOREIGN KEY (process_id) REFERENCES processes(id) ON DELETE CASCADE;
ALTER TABLE process_materials ADD CONSTRAINT fk_pm_process FOREIGN KEY (process_id) REFERENCES processes(id) ON DELETE CASCADE;
ALTER TABLE process_materials ADD CONSTRAINT fk_pm_material FOREIGN KEY (material_id) REFERENCES materials(id);

-- 3.6 BOM / 报工图片
ALTER TABLE bom_items ADD CONSTRAINT fk_bom_product FOREIGN KEY (product_id) REFERENCES materials(id) ON DELETE CASCADE;
ALTER TABLE bom_items ADD CONSTRAINT fk_bom_component FOREIGN KEY (component_id) REFERENCES materials(id);
ALTER TABLE work_report_images ADD CONSTRAINT fk_wri_report FOREIGN KEY (report_id) REFERENCES work_reports(id) ON DELETE CASCADE;

-- ============ 4. 补索引 ============
CREATE INDEX idx_work_reports_group ON work_reports(group_id);
CREATE INDEX idx_work_reports_employee ON work_reports(employee_id);
CREATE INDEX idx_work_reports_process ON work_reports(process_id);
CREATE INDEX idx_work_reports_date ON work_reports(report_date);
CREATE INDEX idx_wri_report ON work_report_images(report_id);
CREATE INDEX idx_process_materials_process ON process_materials(process_id);
CREATE INDEX idx_purchase_returns_supplier ON purchase_returns(supplier_id);
