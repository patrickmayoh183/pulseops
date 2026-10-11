CREATE TABLE monitors (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NOT NULL,
  url VARCHAR(2048) NOT NULL,
  enabled BOOLEAN NOT NULL,
  interval_seconds INT NOT NULL,
  timeout_ms INT NOT NULL,
  expected_status INT NOT NULL,
  current_status VARCHAR(16) NOT NULL,
  failure_streak INT NOT NULL DEFAULT 0,
  success_streak INT NOT NULL DEFAULT 0,
  last_checked_at TIMESTAMP(6) NULL,
  next_check_at TIMESTAMP(6) NOT NULL,
  lease_token VARCHAR(36) NULL,
  lease_until TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_monitors_due ON monitors(enabled, next_check_at);
CREATE TABLE monitor_checks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  monitor_id BIGINT NOT NULL,
  checked_at TIMESTAMP(6) NOT NULL,
  status VARCHAR(16) NOT NULL,
  latency_ms BIGINT NOT NULL,
  status_code INT NULL,
  error VARCHAR(300) NULL,
  CONSTRAINT fk_check_monitor FOREIGN KEY(monitor_id) REFERENCES monitors(id)
);
CREATE INDEX idx_checks_monitor_time ON monitor_checks(monitor_id, checked_at);
CREATE INDEX idx_checks_time ON monitor_checks(checked_at);
CREATE TABLE incidents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  monitor_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(16) NOT NULL,
  started_at TIMESTAMP(6) NOT NULL,
  resolved_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_incident_monitor FOREIGN KEY(monitor_id) REFERENCES monitors(id)
);
CREATE INDEX idx_incidents_status ON incidents(monitor_id, status);
CREATE TABLE incident_updates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  message VARCHAR(2000) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_update_incident FOREIGN KEY(incident_id) REFERENCES incidents(id)
);
CREATE INDEX idx_updates_incident ON incident_updates(incident_id, created_at);
CREATE TABLE audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  action VARCHAR(60) NOT NULL,
  target VARCHAR(100) NOT NULL,
  username VARCHAR(100) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  detail VARCHAR(1000) NOT NULL
);
CREATE TABLE application_locks (name VARCHAR(50) PRIMARY KEY);
INSERT INTO application_locks(name) VALUES ('monitor-create');
