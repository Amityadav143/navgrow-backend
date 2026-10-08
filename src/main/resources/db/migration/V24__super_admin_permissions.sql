-- ============================================================================
-- V24 — Super Admin role + custom per-user permissions
--
-- Adds a SUPER_ADMIN tier to the user_role enum and a free-text `permissions`
-- column on users holding a comma-separated list of granted admin capabilities
-- (see com.navgrow.enums.Permission). SUPER_ADMIN/ADMIN implicitly have all
-- permissions; other roles get only what a SUPER_ADMIN explicitly grants.
-- ============================================================================

-- Add SUPER_ADMIN to the enum if not already present (PostgreSQL has no
-- IF NOT EXISTS for ALTER TYPE, so we check pg_enum manually — same pattern as V3).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum
        WHERE enumlabel = 'SUPER_ADMIN'
        AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'user_role')
    ) THEN
        ALTER TYPE user_role ADD VALUE 'SUPER_ADMIN';
    END IF;
END $$;

-- Per-user granted permissions (comma-separated Permission names). NULL = none.
ALTER TABLE users ADD COLUMN IF NOT EXISTS permissions TEXT;
