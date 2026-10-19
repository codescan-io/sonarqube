-- Test-only. In production codescanng's Flyway migration (V81__create_ip_allowlist.sql) owns these objects,
-- and the fork has no migration for them. Keep this script identical in effect to that migration.
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS ip_allowlist_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS ip_allowlist_applies_to_admins BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS cs_ip_allowlist_range (
  uuid VARCHAR(40) NOT NULL,
  organization_uuid VARCHAR(40) NOT NULL,
  cidr VARCHAR(43) NOT NULL,
  description VARCHAR(255) NOT NULL,
  created_by VARCHAR(255),
  created_at BIGINT NOT NULL,
  CONSTRAINT pk_cs_ip_allowlist_range PRIMARY KEY (uuid),
  CONSTRAINT uq_cs_ip_allowlist_range UNIQUE (organization_uuid, cidr));
CREATE INDEX IF NOT EXISTS ix_cs_ip_allowlist_range_org ON cs_ip_allowlist_range (organization_uuid);

-- Column the fork's OrganizationMemberMapper reads; production gets it from codescanng's schema.
ALTER TABLE organization_members ADD COLUMN IF NOT EXISTS type VARCHAR(40) DEFAULT 'STANDARD';
