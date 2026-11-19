package com.odyssey.recommendation.v2;

import com.odyssey.cellcondition.entity.Weekday;
import com.odyssey.cellslotscore.entity.TimeSlot;

/**
 * 응답이 어떤 분석 배치와 조건에서 나왔는지 알려 준다. 이유를 다른 목록 조건과 섞지 않도록 실제 조회
 * 조건만 담는다. weekday 와 slot 은 Jackson 설정으로 소문자(mon, late_night)가 된다.
 *
 * @param datasetId      분석 배치 ID
 * @param referenceMonth 분석 기준 월(YYYY-MM). 방문 예정 날짜가 아니다
 */
public record V2Context(String datasetId, String referenceMonth, Weekday weekday, TimeSlot slot) {
}
