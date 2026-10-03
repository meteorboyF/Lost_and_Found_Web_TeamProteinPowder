-- Run against a backed-up existing H2 preview after stopping the application.
-- Hibernate's schema update does not extend existing H2 native ENUM values.
ALTER TABLE claims ALTER COLUMN status ENUM('ACCEPTED', 'APPROVED', 'DECLINED', 'OPEN', 'WITHDRAWN') NOT NULL;
