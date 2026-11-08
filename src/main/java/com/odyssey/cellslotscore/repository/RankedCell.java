package com.odyssey.cellslotscore.repository;

/**
 * 추천 후보 칸의 정렬용 프로젝션.
 *
 * @param cellId 50m 칸 번호. 가게 목록의 cellId와 연결한다
 * @param score  analysis가 같은 달·요일·시간대에서 계산한 방문 인원 순위 백분위(0~100).
 *               위치 추천에서는 지역·붐빔 필터를 거친 후보 칸 안에서 다시 순위를 매긴다
 */
public record RankedCell(Long cellId, double score) {
}
