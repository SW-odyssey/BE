package com.odyssey.cell;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;


/**
 * 50m 격자 한 칸
 * BE 는 읽기만 한다. 적재는 analysis 저장소가 한다
 */
@Entity
@Immutable
/** 읽기전용이기 때문에 값을 바꿔도 UPDATE가 안 나간다*/
@Table(name = "cell")
@Getter
/**getCellId()같은 메서드를 자동으로 만들어준다*/
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** 인자가 없는 생성자를 만들어준다 -> JPA가 읽은 값을 담을 빈 객체를 만들때 사용하며 PROTECTED라서 다른코다 빈 객체를 만드는걸 막아준다*/
public class Cell {
    @Id
    private Long cellId;

    /**
     * U+데이터칸 번호, U+ 데이터가 들어오기 전에는 비어있음
     */
    @Column(unique = true, length = 50)
    private String uplusCellId;

    /**
     * 한칸이 속한 구역, 한칸은 동으로 정함
     * area DB에 JONGNO 같은 글자가 그대로 저장 되도록 하게 함
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Area area;

    /**
     * 동 이름
     */
    @Column(nullable = false, length = 30)
    private String legalDong;

    /**
     * 동 영문 표기
     */
    @Column(nullable = false, length = 30)
    private String legalDongEn;

    /**
     * 칸 중심 위도
     */
    private double centerLat;

    /**
     * 칸 중심 경도
     */
    private double centerLng;

    /**
     * 칸 남서쪽(죄측 하단) 모서리의 위도
     */
    private double swLat;

    /**
     * 칸 북동쪽(좌측 하단) 모서리의 경도
     */
    private double swLng;

    /**
     * 칸 북동쪽(우측 상단) 모서리의 위도
     */
    private double neLat;

    /**
     * 칸 북동쪽(우측 상단) 모서리의 경도
     */
    private double neLng;
}

