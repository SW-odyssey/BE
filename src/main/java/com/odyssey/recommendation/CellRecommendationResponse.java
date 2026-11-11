package com.odyssey.recommendation;

import java.util.List;

import com.odyssey.cell.entity.Cell;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/** 필요할 때 조회하는 지도 경계. 추천 응답의 items[].cellId와 연결한다. */
public record CellRecommendationResponse(
	@ArraySchema(arraySchema = @Schema(description = "셀 ID 오름차순 목록. 중복·없는 ID는 제외"),
		maxItems = 30)
	List<CellItem> cells) {

	public record CellItem(
		@Schema(description = "추천 장소가 속한 50m 칸 번호", example = "81383966") Long cellId,
		@Schema(description = "지도에 표시할 셀의 사각형 경계") Bounds bounds) {

		/** 칸의 남서·북동 좌표를 지도 사각형의 네 경계로 바꾼다. */
		static CellItem from(Cell cell) {
			return new CellItem(cell.getCellId(), new Bounds(
				cell.getSwLat(), cell.getSwLng(), cell.getNeLat(), cell.getNeLng()));
		}
	}

	/** 모든 경계는 WGS84 위경도이며 단위는 도다. */
	public record Bounds(
		@Schema(description = "남쪽 경계 위도", example = "37.5698") double south,
		@Schema(description = "서쪽 경계 경도", example = "126.9797") double west,
		@Schema(description = "북쪽 경계 위도", example = "37.5702") double north,
		@Schema(description = "동쪽 경계 경도", example = "126.9803") double east) {
	}
}
