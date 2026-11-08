-- 확장 단계: 구 조회/적재가 사용하는 열과 키는 유지한다.
-- 같은 법정동이 여러 지역이면 임의로 고르지 않고 데이터 정정을 요구한다.
do $$
begin
	if exists (
		select legal_dong from cell group by legal_dong having count(distinct area) > 1
	) then
		raise exception 'Conflicting areas for legal_dong; resolve before V7';
	end if;
end $$;

create table legal_dong (
	name varchar(30) primary key,
	area varchar(20) not null
);
insert into legal_dong (name, area)
select distinct legal_dong, area from cell;

-- 기존 적재기도 계속 동작해야 하므로 전환 완료 전까지 nullable 이다.
alter table store add column kind varchar(10);
alter table store add column since_date date;
update store set kind = case
	when category = 'CAFE' then 'CAFE'
	when food_type = 'KOREAN' then 'KOREAN'
	when food_type = 'BAR' then 'BAR'
	else 'OTHER' end;
-- license_date 는 이력 기준 개업일이 아니다. since_date 는 analysis 재적재로 채운다.
alter table store add constraint ck_store_kind
	check (kind in ('CAFE', 'KOREAN', 'BAR', 'OTHER'));
create index idx_store_cell_kind on store (cell_id, kind);

create table store_badge (
	store_id bigint not null references store (id),
	badge varchar(20) not null,
	primary key (store_id, badge),
	constraint ck_store_badge check (badge in ('CENTURY_STORE', 'ORAE_GAGE', 'FUTURE_HERITAGE'))
);

create table cell_month_metric (
	cell_id bigint not null references cell (cell_id),
	month integer not null check (month between 1 and 12),
	metric_key varchar(50) not null,
	value double precision not null check (value between 0 and 1),
	primary key (cell_id, month, metric_key)
);

create table score_batch (
	name varchar(50) primary key,
	version varchar(50) not null,
	computed_at timestamp not null
);

alter table legal_dong enable row level security;
alter table store_badge enable row level security;
alter table cell_month_metric enable row level security;
alter table score_batch enable row level security;
