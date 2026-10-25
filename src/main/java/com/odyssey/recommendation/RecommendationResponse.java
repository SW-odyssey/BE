package com.odyssey.recommendation;

import com.odyssey.cell.entity.Area;
import com.odyssey.cell.entity.Cell;
import com.odyssey.cellslotscore.entity.DayType;
import com.odyssey.cellslotscore.entity.TimeSlot;
import com.odyssey.store.entity.Category;
import com.odyssey.store.entity.FoodType;
import com.odyssey.store.repository.StoreCardRow;

import java.time.LocalDate;
import java.util.List;

/**
 * 추천 응답 (#30). 한 번의 응답으로 목록 · 개수 미리보기 · 지도 세 화면을 채운다.
 *
 * <p>enum 필드(category, areaId, dayType, slot)는 Jackson 설정으로 소문자가 된다 ("insadong").
 *
 * @param applied 서버가 채우거나 판단한 값. FE 가 보낸 그대로인 값은 돌려주지 않는다
 * @param total   "장소 N곳 보기" (S03). items 길이와 같다
 * @param items   추천 가게. 이 순서가 추천 순서다
 * @param cells   지도에 칠할 칸. items 의 모든 cellId 가 여기 있다
 */
public record RecommendationResponse(
	Applied applied,
	int total,
	List<Item> items,
	List<CellShape> cells) {

	/**
	 * @param visitDate 생략하면 서버가 오늘로 채운다
	 * @param dayType   방문일로 서버가 판단한 평일 / 주말 (예: "주말 저녁 기준" 표시)
	 * @param slot      생략하면 서버가 오후로 채운다
	 */
	public record Applied(LocalDate visitDate, DayType dayType, TimeSlot slot) {
	}

	/**
	 * 목록 카드 한 장. 상세(#31) 응답의 앞부분과 같은 모양이라 FE 가 카드 타입을 같이 쓴다.
	 *
	 * @param since         개업 연도 (인허가일 기준). 예) 1968
	 * @param googlePlaceId 사진을 구글에서 가져올 때 쓴다. 없으면 null
	 */
	public record Item(
		Long id,
		String name,
		Category category,
		FoodType foodType,
		Area areaId,
		Point point,
		Long cellId,
		int since,
		String googlePlaceId) {

		static Item from(StoreCardRow row) {
			return new Item(
				row.id(),
				row.name(),
				row.category(),
				row.foodType(),
				row.area(),
				new Point(row.lat(), row.lng()),
				row.cellId(),
				row.licenseDate().getYear(),
				row.googlePlaceId());
		}
	}

	/** 지도 표시와 기기에서 거리순 정렬에 쓴다 */
	public record Point(double lat, double lng) {
	}

	/** GeoJSON Polygon. coordinates = [ 고리 [ 점 [lng, lat] ] ] */
	public record Polygon(String type, double[][][] coordinates) {
	}

	public record CellShape(Long id, Polygon geometry) {

		/**
		 * 칸의 남서(sw) · 북동(ne) 모서리로 사각형을 만든다.
		 *
		 * <p>GeoJSON 은 [위도, 경도] 가 아니라 <b>[경도, 위도]</b> 순서다.
		 * 고리는 반시계 방향으로 돌고 첫 점으로 다시 닫는다 (점 5개).
		 * <pre>
		 *   (swLng, neLat) ── (neLng, neLat)
		 *        │                  │
		 *   (swLng, swLat) ── (neLng, swLat)
		 * </pre>
		 */
		static CellShape from(Cell cell) {
			double[][] ring = {
				{cell.getSwLng(), cell.getSwLat()},   // 남서
				{cell.getNeLng(), cell.getSwLat()},   // 남동
				{cell.getNeLng(), cell.getNeLat()},   // 북동
				{cell.getSwLng(), cell.getNeLat()},   // 북서
				{cell.getSwLng(), cell.getSwLat()}};  // 남서 (닫기)
			return new CellShape(cell.getCellId(), new Polygon("Polygon", new double[][][] {ring}));
		}
	}

	/** 조건에 맞는 칸이나 가게가 없을 때. 오류가 아니라 200 과 빈 목록 (#30) */
	public static RecommendationResponse empty(LocalDate visitDate, DayType dayType, TimeSlot slot) {
		return of(visitDate, dayType, slot, List.of(), List.of());
	}

	/**
	 * @param stores 이미 추천 순서로 정렬된 가게
	 * @param cells  stores 가 들어 있는 칸
	 */
	public static RecommendationResponse of(LocalDate visitDate, DayType dayType, TimeSlot slot,
		List<StoreCardRow> stores, List<Cell> cells) {
		return new RecommendationResponse(
			new Applied(visitDate, dayType, slot),
			stores.size(),
			stores.stream().map(Item::from).toList(),
			cells.stream().map(CellShape::from).toList());
	}
}
