-- Run against a backed-up existing MySQL database before deploying this release.
-- Preserves the meaning of existing statuses; old ACCEPTED rows remain completed.
ALTER TABLE claims MODIFY COLUMN status ENUM('ACCEPTED', 'APPROVED', 'DECLINED', 'OPEN', 'WITHDRAWN') NOT NULL;
