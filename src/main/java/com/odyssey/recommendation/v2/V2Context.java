package com.odyssey.recommendation.v2;

/** 이유를 다른 목록 조건과 섞지 않도록 분석 배치와 실제 조회 조건만 돌려준다. */
public record V2Context(String datasetId, String referenceMonth, String weekday, String slot) {}
