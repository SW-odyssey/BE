package com.odyssey.metadata;

/**
 * 화면의 선택지 하나.
 *
 * @param id        요청에 쓰는 값. 서버 enum 이름을 소문자로 쓴다 (GitHub 이슈 #28)
 * @param label     화면에 보여 줄 이름
 * @param isDefault 처음 열었을 때 선택돼 있는지
 */
public record Option(String id, String label, boolean isDefault) {
}
