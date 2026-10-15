create table cell_score (
    cell_id         bigint              primary key references cell (cell_id),
    score           double precision,
    rank            integer,
    eligible        boolean             not null,
    version         varchar(50)         not null,
    computed_at     timestamp           not null
);

-- 랭킹 API 가 rank 순으로 읽는다
create index idx_cell_score_rank on cell_score (rank);

-- Supabase 는 public 스키마 테이블을 공개 API 로 노출한다. 정책 없이 켜 두면 공개 API 로는 접근할 수 없다
alter table cell_score enable row level security;
