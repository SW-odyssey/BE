alter table store drop constraint ck_store_kind;
alter table store add constraint ck_store_kind
	check (kind in ('CAFE', 'KOREAN', 'BAR', 'OTHER', 'SHOP', 'PLAY'));
