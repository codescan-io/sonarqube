-- Test-only. Column the fork's OrganizationMemberMapper reads; production gets it from codescanng's schema.
ALTER TABLE organization_members ADD COLUMN IF NOT EXISTS type VARCHAR(40) DEFAULT 'STANDARD';
