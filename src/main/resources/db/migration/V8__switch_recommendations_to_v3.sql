-- 배포 전에 기존 DB 백업과 v3 CSV 준비가 필요하다.
-- 구 점수는 월/요일 4분류로 복원할 수 없어서 별도 보관한다.
alter table cell_slot_score rename to cell_slot_score_v1_archive;
alter table cell_slot_metric rename to cell_slot_metric_v1_archive;
alter table cell_score rename to cell_score_v1_archive;
alter table cell_metric rename to cell_metric_v1_archive;

do $$
begin
	if exists (select legal_dong from cell group by legal_dong having count(distinct area) > 1) then
		raise exception 'Conflicting areas for legal_dong';
	end if;
end $$;
insert into legal_dong (name, area)
select distinct legal_dong, area from cell
on conflict (name) do update set area = excluded.area;
alter table cell add constraint fk_cell_legal_dong
	foreign key (legal_dong) references legal_dong (name);
alter table cell
	drop column area, drop column legal_dong_en, drop column uplus_cell_id,
	drop column center_lat, drop column center_lng;

update store set kind = case when category = 'CAFE' then 'CAFE'
	when food_type = 'KOREAN' then 'KOREAN'
	when food_type = 'BAR' then 'BAR' else 'OTHER' end;
-- v3 적격성과 개업일은 새 원본으로 확정한다. ID를 보존하고 재적재까지 추천하지 않는다.
update store set active = false;
alter table store alter column kind set not null;
-- 적재기는 place ID 이관 때 NULL을 쓴다. 기존 중복은 임의로 지우지 않고 실패시킨다.
alter table store add constraint uq_store_google_place_id unique (google_place_id);
alter table store
	drop column category, drop column food_type, drop column franchise,
	drop column name_romanized, drop column sub_type, drop column road_addr,
	drop column road_addr_en, drop column license_date;

create table cell_slot_score (
	cell_id bigint not null references cell(cell_id),
	month integer not null check (month between 1 and 12),
	day_type varchar(20) not null check (day_type in ('MON_THU', 'FRI', 'SAT', 'SUN')),
	time_slot varchar(20) not null,
	density_level varchar(20) not null,
	peak_hour boolean not null,
	score double precision not null check (score between 0 and 100),
	constraint pk_cell_slot_score_v3 primary key (cell_id, month, day_type, time_slot)
);
create index idx_cell_slot_score_month_score
	on cell_slot_score (month, day_type, time_slot, score desc);
create table cell_slot_metric (
	cell_id bigint not null references cell(cell_id),
	month integer not null check (month between 1 and 12),
	day_type varchar(20) not null check (day_type in ('MON_THU', 'FRI', 'SAT', 'SUN')),
	time_slot varchar(20) not null,
	metric_key varchar(50) not null,
	value double precision not null check (value between 0 and 1),
	constraint pk_cell_slot_metric_v3 primary key (cell_id, month, day_type, time_slot, metric_key)
);
alter table cell_slot_score enable row level security;
alter table cell_slot_metric enable row level security;
