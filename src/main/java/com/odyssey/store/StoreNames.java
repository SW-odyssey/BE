package com.odyssey.store;

/** 상호의 영어·간체 중국어·일본어 표기. 없는 표기는 원본 상호를 사용한다. */
public record StoreNames(String english, String chinese, String japanese) {

	public static final StoreNames EMPTY = new StoreNames(null, null, null);

	public enum Language {
		KO, EN, ZH, JA
	}

	public String display(String original, Language language) {
		String translated = switch (language) {
			case KO -> null;
			case EN -> english;
			case ZH -> chinese;
			case JA -> japanese;
		};
		return translated == null || translated.isBlank() ? original : translated;
	}
}
