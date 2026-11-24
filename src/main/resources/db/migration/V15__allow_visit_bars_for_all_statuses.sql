-- 방문 패턴 막대를 AVAILABLE 외 상태에도 적재한다. 2025년 배치는 같은 원본으로 다시 계산한 막대를 공개 뒤에 더했다.
-- 기존 AVAILABLE 막대는 바뀌지 않는다. 품질 검사를 통과하지 못한 시간대는 0 대신 null 로 둔다.
alter table cell_visit_bar alter column relative_height drop not null;

-- 막대는 없거나 시간대마다 하나씩 네 개다. 상태별 조건은 VisitProfile 과 같다.
create or replace function validate_split_dataset_publication() returns trigger language plpgsql as $$
begin
	if new.published and (tg_op = 'INSERT' or not old.published) then
		if exists (
			select 1 from cell_visit_profile p
			left join cell_visit_bar b using (dataset_id,cell_id,weekday)
			where p.dataset_id=new.dataset_id
			group by p.dataset_id,p.cell_id,p.weekday,p.status
			having not (
				count(b.slot) = 0 and p.status <> 'AVAILABLE'
				or count(b.slot) = 4 and case p.status
					when 'AVAILABLE' then count(b.relative_height) = 4
						and count(*) filter (where b.relative_height = 100) = 1
					when 'NO_DISTINCT_PEAK' then count(b.relative_height) = 4
						and count(*) filter (where b.relative_height = 100) >= 1
					when 'INCOMPLETE_TIME_SLOTS' then count(b.relative_height) < 4
						and count(*) filter (where b.relative_height = 100) >= 1
					else false end)
		) then
			raise exception 'Invalid visit profile bars for publication';
		end if;
	end if;
	return new;
end $$;
