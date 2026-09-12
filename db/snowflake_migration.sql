-- 雪花 ID 触发器修复（2026-07-31 v2）
-- 改为仅在 NEW.id 为空时生成 sfid()——Java 显式传入的雪花 id 保留
-- （Java 层 nextId() 与触发器 sfid() 同源同序列，不会重复）

DELIMITER $$
DROP FUNCTION IF EXISTS sfid$$
CREATE FUNCTION sfid() RETURNS BIGINT NO SQL
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

DROP TRIGGER IF EXISTS trg_bom_items_bi;
DROP TRIGGER IF EXISTS trg_customer_order_items_bi;
DROP TRIGGER IF EXISTS trg_customer_orders_bi;
DROP TRIGGER IF EXISTS trg_customers_bi;
DROP TRIGGER IF EXISTS trg_delivery_items_bi;
DROP TRIGGER IF EXISTS trg_delivery_notes_bi;
DROP TRIGGER IF EXISTS trg_invoices_bi;
DROP TRIGGER IF EXISTS trg_materials_bi;
DROP TRIGGER IF EXISTS trg_payment_vouchers_bi;
DROP TRIGGER IF EXISTS trg_process_materials_bi;
DROP TRIGGER IF EXISTS trg_processes_bi;
DROP TRIGGER IF EXISTS trg_purchase_items_bi;
DROP TRIGGER IF EXISTS trg_purchase_orders_bi;
DROP TRIGGER IF EXISTS trg_purchase_return_items_bi;
DROP TRIGGER IF EXISTS trg_purchase_returns_bi;
DROP TRIGGER IF EXISTS trg_receipt_vouchers_bi;
DROP TRIGGER IF EXISTS trg_sequences_bi;
DROP TRIGGER IF EXISTS trg_settlements_bi;
DROP TRIGGER IF EXISTS trg_stock_movements_bi;
DROP TRIGGER IF EXISTS trg_suppliers_bi;
DROP TRIGGER IF EXISTS trg_users_bi;
DROP TRIGGER IF EXISTS trg_warehouses_bi;
DROP TRIGGER IF EXISTS trg_work_employees_bi;
DROP TRIGGER IF EXISTS trg_work_group_processes_bi;
DROP TRIGGER IF EXISTS trg_work_groups_bi;
DROP TRIGGER IF EXISTS trg_work_report_images_bi;
DROP TRIGGER IF EXISTS trg_work_reports_bi;

CREATE TRIGGER trg_bom_items_bi BEFORE INSERT ON bom_items FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_customer_order_items_bi BEFORE INSERT ON customer_order_items FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_customer_orders_bi BEFORE INSERT ON customer_orders FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_customers_bi BEFORE INSERT ON customers FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_delivery_items_bi BEFORE INSERT ON delivery_items FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_delivery_notes_bi BEFORE INSERT ON delivery_notes FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_invoices_bi BEFORE INSERT ON invoices FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_materials_bi BEFORE INSERT ON materials FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_payment_vouchers_bi BEFORE INSERT ON payment_vouchers FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_process_materials_bi BEFORE INSERT ON process_materials FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_processes_bi BEFORE INSERT ON processes FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_purchase_items_bi BEFORE INSERT ON purchase_items FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_purchase_orders_bi BEFORE INSERT ON purchase_orders FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_purchase_return_items_bi BEFORE INSERT ON purchase_return_items FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_purchase_returns_bi BEFORE INSERT ON purchase_returns FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_receipt_vouchers_bi BEFORE INSERT ON receipt_vouchers FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_sequences_bi BEFORE INSERT ON sequences FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_settlements_bi BEFORE INSERT ON settlements FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_stock_movements_bi BEFORE INSERT ON stock_movements FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_suppliers_bi BEFORE INSERT ON suppliers FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_users_bi BEFORE INSERT ON users FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_warehouses_bi BEFORE INSERT ON warehouses FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_work_employees_bi BEFORE INSERT ON work_employees FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_work_group_processes_bi BEFORE INSERT ON work_group_processes FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_work_groups_bi BEFORE INSERT ON work_groups FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_work_report_images_bi BEFORE INSERT ON work_report_images FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());
CREATE TRIGGER trg_work_reports_bi BEFORE INSERT ON work_reports FOR EACH ROW SET NEW.id = IFNULL(NEW.id, sfid());

-- =============================================
-- 2026-08-01 独立采购订单（v3 IF 版：NEW.id 预分配自增值时兜底）
-- =============================================
DROP TRIGGER IF EXISTS trg_po_orders_bi;
CREATE TRIGGER trg_po_orders_bi BEFORE INSERT ON po_orders FOR EACH ROW
    SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
DROP TRIGGER IF EXISTS trg_po_order_items_bi;
CREATE TRIGGER trg_po_order_items_bi BEFORE INSERT ON po_order_items FOR EACH ROW
    SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
