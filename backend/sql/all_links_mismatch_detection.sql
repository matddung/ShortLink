-- 전체 링크 대상 불일치 탐지 SQL

select s.id as short_link_id,
       s.short_code,
       s.total_clicks as stored_total_clicks,
       coalesce(e.event_count, 0) as event_total_clicks,
       coalesce(e.event_count, 0) - s.total_clicks as delta
  from short_links s
  left join (
       select short_link_id, count(*) as event_count
         from link_click_events
        group by short_link_id
  ) e on e.short_link_id = s.id
 where s.total_clicks <> coalesce(e.event_count, 0)
 order by s.id;