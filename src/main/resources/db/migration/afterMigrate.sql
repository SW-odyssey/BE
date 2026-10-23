-- Flyway 콜백. 마이그레이션이 끝난 뒤 기동할 때마다 실행된다 (버전 번호가 없어 이력에 남지 않는다)
-- Flyway 이력 테이블(flyway_schema_history)도 public 스키마에 생겨서 Supabase 공개 API 로 노출된다
-- 정책 없이 RLS 만 켜서 공개 API 로는 접근할 수 없게 한다. 테이블 소유자인 Flyway 는 RLS 를 적용받지 않는다
-- 마이그레이션(V 파일)으로 하지 않는 이유: 마이그레이션 중에는 Flyway 가 이 테이블을 잡고 있어 alter 가 끝없이 기다린다
-- ${flyway:table} 은 Flyway 가 이력 테이블 이름으로 바꿔 넣는다. 여러 번 실행해도 결과가 같다
alter table ${flyway:table} enable row level security;
