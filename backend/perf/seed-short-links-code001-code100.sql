INSERT INTO short_links (
  original_url,
  short_code,
  created_at,
  total_clicks,
  owner_key,
  owner_user_id,
  anonymous_expires_at
)
SELECT
  format('https://example.com/%s', lpad(gs::text, 3, '0')) AS original_url,
  format('code%s', lpad(gs::text, 3, '0')) AS short_code,
  now() AS created_at,
  0 AS total_clicks,
  format('perf-seed-%s', lpad(gs::text, 3, '0')) AS owner_key,
  NULL AS owner_user_id,
  NULL AS anonymous_expires_at
FROM generate_series(1, 100) AS gs
ON CONFLICT (short_code) DO UPDATE
SET
  original_url = EXCLUDED.original_url,
  total_clicks = 0,
  owner_key = EXCLUDED.owner_key,
  owner_user_id = NULL,
  anonymous_expires_at = NULL;