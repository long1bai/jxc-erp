CREATE TABLE IF NOT EXISTS stock_transfers (
  id BIGINT PRIMARY KEY,
  transfer_no VARCHAR(32) NOT NULL COMMENT '调拨单号 ST+日期+序号',
  from_warehouse_id BIGINT DEFAULT 0 COMMENT '调出仓库',
  from_warehouse_name VARCHAR(50) DEFAULT '',
  to_warehouse_id BIGINT DEFAULT 0 COMMENT '调入仓库',
  to_warehouse_name VARCHAR(50) DEFAULT '',
  transfer_date DATE COMMENT '调拨日期',
  total_items INT DEFAULT 0,
  total_quantity DECIMAL(14,3) DEFAULT 0,
  remark VARCHAR(500) DEFAULT '',
  created_by VARCHAR(50) DEFAULT '',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  INDEX idx_tf_no (transfer_no),
  INDEX idx_tf_date (transfer_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存调拨单';

CREATE TABLE IF NOT EXISTS stock_transfer_items (
  id BIGINT PRIMARY KEY,
  transfer_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  material_name VARCHAR(100) DEFAULT '',
  spec VARCHAR(100) DEFAULT '',
  unit VARCHAR(20) DEFAULT '',
  quantity DECIMAL(14,3) NOT NULL,
  INDEX idx_tf_item (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨明细';

DROP TRIGGER IF EXISTS trg_stock_transfers_bi;
CREATE TRIGGER trg_stock_transfers_bi BEFORE INSERT ON stock_transfers
FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);

DROP TRIGGER IF EXISTS trg_stock_transfer_items_bi;
CREATE TRIGGER trg_stock_transfer_items_bi BEFORE INSERT ON stock_transfer_items
FOR EACH ROW SET NEW.id = IF(NEW.id IS NULL OR NEW.id < 100000000000000, sfid(), NEW.id);
