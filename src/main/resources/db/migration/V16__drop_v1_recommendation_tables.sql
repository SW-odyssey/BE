-- 매장 적재와 점검에서 v1 참조를 제거한 뒤 적용한다.
-- 분석에서 사용하는 매장 배지와 V8의 이력 보관 테이블은 유지한다.
drop table
	public.cell_slot_score,
	public.cell_slot_metric,
	public.cell_month_metric,
	public.score_batch
restrict;
