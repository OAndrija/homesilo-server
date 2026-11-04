-- Accounts created before roles mattered are all USER, which would leave an
-- upgraded install with nobody able to manage users. If there is no admin,
-- promote the oldest account (the person who set the server up).
UPDATE users
SET role = 'ADMIN'
WHERE id = (SELECT id FROM users ORDER BY created_at, id LIMIT 1)
  AND NOT EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN');
