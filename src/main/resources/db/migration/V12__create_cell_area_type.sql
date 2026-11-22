-- 칸의 동네 시간 유형. 코스 추천 이유(AREA_TYPE)에만 쓴다.
-- 분석 배치(dataset)와 무관하다. 2025년 1~12월 150m 창 NMF로 한 번 정한 값이라 배치마다 다시 적재하지 않는다.
-- 적재 SQL은 analysis 저장소가 만든다. 표가 비어 있으면 이유가 안 붙을 뿐 코스는 그대로다.
-- 직장·외국인 이름표는 기각했다(논문 4.4절). 시간 유형만 둔다.
create table cell_area_type (
	cell_id bigint primary key,
	area_type varchar(20) not null
		check (area_type in ('late_night','weekend_day','weekday_day','mixed')),
	source varchar(80) not null
);

-- 서버 DB 역할로만 읽는다. 브라우저 anon/authenticated 직접 조회 정책은 만들지 않는다.
alter table cell_area_type enable row level security;
