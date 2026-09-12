CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_name VARCHAR(50) DEFAULT '' COMMENT '操作用户',
  role VARCHAR(20) DEFAULT '' COMMENT '用户角色',
  method VARCHAR(10) DEFAULT '' COMMENT 'HTTP方法',
  path VARCHAR(200) DEFAULT '' COMMENT '接口路径',
  module VARCHAR(50) DEFAULT '' COMMENT '模块',
  detail VARCHAR(500) DEFAULT '' COMMENT '操作详情',
  ip VARCHAR(50) DEFAULT '' COMMENT 'IP',
  status INT DEFAULT 200 COMMENT '状态码',
  cost_ms INT DEFAULT 0 COMMENT '耗时ms',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
  deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
  INDEX idx_logs_time (created_at),
  INDEX idx_logs_user (user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';
