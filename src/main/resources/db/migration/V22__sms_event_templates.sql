-- Per-event MSG91 Flow template IDs (DLT-approved) for transactional SMS.
--
-- MSG91's Flow API needs a distinct template/flow id per message type. These
-- hold the approved DLT template ids for each event so the app can send the right
-- one. All nullable — a blank id means that event has no MSG91 Flow template yet.
-- The OTP template id already lives in msg91_template_id (added in V20).

ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_welcome          VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_order_cod        VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_order_online     VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_order_shipped    VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_order_delivered  VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_order_cancelled  VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_order_processing VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_order_refunded   VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_password_changed VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_rfq_received     VARCHAR(100);
ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS tpl_rfq_ready        VARCHAR(100);
