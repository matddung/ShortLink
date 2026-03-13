WITH normalized AS (
  SELECT
    lower(regexp_replace(query, '\\s+', ' ', 'g')) AS q,
    calls
  FROM pg_stat_statements
)
SELECT 'short_links_select_calls' AS metric, COALESCE(SUM(calls), 0)::bigint AS value
FROM normalized
WHERE q LIKE 'select%from short_links%'
  AND q LIKE '%where%short_code%'
UNION ALL
SELECT 'link_click_events_insert_calls' AS metric, COALESCE(SUM(calls), 0)::bigint AS value
FROM normalized
WHERE q LIKE 'insert%into link_click_events%'
UNION ALL
SELECT 'short_links_total_clicks_update_calls' AS metric, COALESCE(SUM(calls), 0)::bigint AS value
FROM normalized
WHERE q LIKE 'update short_links%'
  AND q LIKE '%total_clicks%';