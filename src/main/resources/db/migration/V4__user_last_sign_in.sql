-- When each person last signed in, for the admin's user list. NULL until their
-- next sign-in (accounts created by an admin start out as "never").
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP(6);
