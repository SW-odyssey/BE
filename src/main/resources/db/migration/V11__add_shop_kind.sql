-- 코스 오후 자리의 '구경'(공예·갤러리·한복 등 소매점)을 같은 store 표에 둔다.
-- 소매점은 인허가 관리번호가 없어 mgmt_no 에 SG-상가업소번호를 넣는다. analysis 의 store_shops.sql 이 적재한다.
alter table store drop constraint ck_store_kind;
alter table store add constraint ck_store_kind
	check (kind in ('CAFE', 'KOREAN', 'BAR', 'OTHER', 'SHOP'));
