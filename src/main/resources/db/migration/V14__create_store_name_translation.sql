-- 원본 상호가 달라지면 조회 쿼리에서 이전 번역을 사용하지 않는다.
create table store_name_translation (
    store_id bigint primary key references store (id) on delete cascade,
    source_name varchar(100) not null,
    name_en text not null check (btrim(name_en) <> ''),
    name_zh text not null check (btrim(name_zh) <> ''),
    name_ja text not null check (btrim(name_ja) <> '')
);

alter table store_name_translation enable row level security;
