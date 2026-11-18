-- v1 테이블과 데이터를 유지한다. v2 분석 배치만 별도 버전으로 적재한다.
create table recommendation_dataset (
	dataset_id varchar(80) primary key,
	reference_month date not null check (extract(day from reference_month) = 1),
	population varchar(30) not null check (population = 'DOMESTIC_20_39'),
	policy_version varchar(80) not null,
	reason_version varchar(80) not null,
	profile_version varchar(80) not null,
	source_sha256 char(64) not null,
	computed_at timestamptz not null,
	published boolean not null default false
);

create table cell_condition (
	dataset_id varchar(80) not null references recommendation_dataset(dataset_id),
	cell_id bigint not null references cell(cell_id),
	weekday varchar(3) not null check (weekday in ('mon','tue','wed','thu','fri','sat','sun')),
	slot varchar(10) not null check (slot in ('lunch','afternoon','dinner','late_night')),
	quality_status varchar(30) not null check (quality_status in
		('AVAILABLE','NOT_OBSERVED','INCOMPLETE','NO_POSITIVE_VISITS')),
	visitor_percentile double precision check (visitor_percentile between 0 and 100),
	primary key (dataset_id,cell_id,weekday,slot),
	check ((quality_status = 'AVAILABLE') = (visitor_percentile is not null))
);
create index idx_cell_condition_candidates
	on cell_condition(dataset_id,weekday,slot,visitor_percentile desc)
	where quality_status = 'AVAILABLE';

create table cell_reason (
	dataset_id varchar(80) not null,
	cell_id bigint not null,
	weekday varchar(3) not null,
	slot varchar(10) not null,
	position smallint not null check (position between 1 and 2),
	code varchar(60) not null check (code in ('VISIT_RELATIVE_HIGH','DAY_DISTINCT',
		'STAY_DISTINCT','WALK_DISTINCT','AGE20_DISTINCT','AGE30_DISTINCT','TIME_PEAK')),
	primary key (dataset_id,cell_id,weekday,slot,position),
	unique (dataset_id,cell_id,weekday,slot,code),
	foreign key (dataset_id,cell_id,weekday,slot)
		references cell_condition(dataset_id,cell_id,weekday,slot)
);

-- 요일별 프로필은 선택된 추천 시간대와 독립적이므로 slot이 키에 들어가지 않는다.
create table cell_visit_profile (
	dataset_id varchar(80) not null references recommendation_dataset(dataset_id),
	cell_id bigint not null references cell(cell_id),
	weekday varchar(3) not null check (weekday in ('mon','tue','wed','thu','fri','sat','sun')),
	status varchar(30) not null check (status in
		('AVAILABLE','INCOMPLETE_TIME_SLOTS','NO_POSITIVE_VISITS','NO_DISTINCT_PEAK')),
	primary key (dataset_id,cell_id,weekday)
);

-- 시간대 라벨·고정 지표·척도·강조·설명은 API에서 조립한다.
-- AVAILABLE 프로필에만 네 시간대 행을 적재한다.
create table cell_visit_bar (
	dataset_id varchar(80) not null,
	cell_id bigint not null,
	weekday varchar(3) not null,
	slot varchar(10) not null check (slot in ('lunch','afternoon','dinner','late_night')),
	relative_height smallint not null check (relative_height between 0 and 100),
	primary key (dataset_id,cell_id,weekday,slot),
	foreign key (dataset_id,cell_id,weekday)
		references cell_visit_profile(dataset_id,cell_id,weekday)
);

-- FK/CHECK만으로 표현할 수 없는 행간 조건을 공개 전 검증한다.
-- 공개 배치의 프로필·막대는 적재 후 불변이다. 교정은 새 datasetId로 적재한다.
create function validate_split_dataset_publication() returns trigger language plpgsql as $$
begin
	if new.published and (tg_op = 'INSERT' or not old.published) then
		if exists (
			select 1 from cell_visit_profile p
			left join cell_visit_bar b using (dataset_id,cell_id,weekday)
			where p.dataset_id=new.dataset_id
			group by p.dataset_id,p.cell_id,p.weekday,p.status
			having (p.status='AVAILABLE' and
				(count(b.slot)<>4 or count(*) filter(where b.relative_height=100)<>1))
				or (p.status<>'AVAILABLE' and count(b.slot)<>0)
		) then
			raise exception 'Invalid visit profile bars for publication';
		end if;
	end if;
	return new;
end $$;
create trigger validate_split_dataset_publication
	before insert or update of published on recommendation_dataset
	for each row execute function validate_split_dataset_publication();

-- 서버 DB 역할로만 읽는다. 브라우저 anon/authenticated 직접 조회 정책은 만들지 않는다.
alter table recommendation_dataset enable row level security;
alter table cell_condition enable row level security;
alter table cell_reason enable row level security;
alter table cell_visit_profile enable row level security;
alter table cell_visit_bar enable row level security;
