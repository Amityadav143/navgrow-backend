-- ============================================================================
-- V25 — Promote the bootstrap admin to SUPER_ADMIN
--
-- Must be a SEPARATE migration from V24: PostgreSQL will not allow a newly added
-- enum value to be used in the same transaction that added it. V24 adds the
-- SUPER_ADMIN label; here (a later transaction) we can safely assign it.
--
-- This gives the deployment one working Super Admin out of the box, who can then
-- grant custom access to other users from the admin panel. Change this email (or
-- the account's password) for your own super-admin as needed.
-- ============================================================================

UPDATE users
   SET role = 'SUPER_ADMIN'
 WHERE email = 'admin@navgrow.org';
