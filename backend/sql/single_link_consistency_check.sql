-- 단건 정합성 검증 쿼리

select s.id as short_link_id,
       s.total_clicks as stored_total_clicks,
       coalesce(e.event_count, 0) as event_total_clicks,
       coalesce(e.event_count, 0) - s.total_clicks as delta
  from short_links s
  left join (
       select short_link_id, count(*) as event_count
         from link_click_events
        group by short_link_id
  ) e on e.short_link_id = s.id
 where s.id = :short_link_id;