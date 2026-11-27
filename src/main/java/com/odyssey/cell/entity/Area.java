package com.odyssey.cell.entity;

public enum Area {
	JONGNO("종로"),
	IKSEON("익선동"),
	INSADONG("인사동"),
	EULJIRO("을지로"),
	MYEONGDONG("명동");

	private final String koreanName;

	Area(String koreanName) {
		this.koreanName = koreanName;
	}

	public String koreanName() {
		return koreanName;
	}
}
