package com.coinwin.backtest.api;

import com.coinwin.ai.domain.Narrative;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 백테스트 결과를 문장으로 옮긴 것과, 그 문장이 쓸 수 있었던 수치 전부.
 *
 * <p>{@code facts} 를 함께 내려 주는 이유는 <b>대조할 수 있어야 하기 때문</b>이다. 요약만
 * 돌려주면 사용자는 그 문장을 믿는 수밖에 없다. 원본을 나란히 두면 문장이 무엇을 근거로
 * 하는지 눈으로 확인된다 — 그 대조 가능성이 ADR 005 가 요약을 허용한 근거 그 자체다.
 */
@Schema(description = "백테스트 결과 요약. 문장에 나오는 수는 전부 facts 안의 값이다")
public record BacktestNarrativeResponse(

        @Schema(description = "한국어 요약. 원본에 없는 수가 들어가면 응답 자체가 만들어지지 않는다")
        String narrative,

        @Schema(description = "요약이 쓸 수 있었던 수치")
        Map<String, BigDecimal> facts,

        @Schema(description = "수가 아닌 조건. 종목·주기·구간처럼 대조할 것이 없는 사실")
        Map<String, String> conditions) {

    public BacktestNarrativeResponse {
        // 순서를 잃지 않으면서 밖에서 못 바꾸게 한다. Map.copyOf 는 순서를 잃는다.
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        conditions = Collections.unmodifiableMap(new LinkedHashMap<>(conditions));
    }

    static BacktestNarrativeResponse from(Narrative narrative) {
        return new BacktestNarrativeResponse(narrative.text(),
                narrative.facts().numbers(), narrative.facts().context());
    }
}
