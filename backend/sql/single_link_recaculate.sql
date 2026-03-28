-- 단건 재계산 SQL

update short_links s
   set total_clicks = coalesce((
       select count(*)
         from link_click_events e
        where e.short_link_id = s.id
   ), 0)
 where s.id = :short_link_id;