package com.coinwin.market.adapter.out.yahoo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;

/**
 * 야후 차트 응답에서 우리가 쓰는 자리만.
 *
 * <p>실제 응답은 {@code chart.result[0].meta} 아래에 서른 칸이 넘는다. 필요한 둘만 읽고
 * 모르는 칸은 무시한다. <b>문서화된 API 가 아니라 칸이 늘거나 줄 수 있고</b>, 그때 우리 쪽이
 * 깨지지 않는 것이 이 설정의 이유다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChart(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {

        /** 목록을 그대로 들고 있으면 밖에서 바꿀 수 있다. 응답 DTO 와 같은 처리다. */
        public Chart {
            result = result == null ? List.of() : List.copyOf(result);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(YahooMeta meta) {
    }

    /** 결과가 비어 있을 수 있다 — 모르는 티커를 물으면 그렇다. */
    Optional<YahooMeta> meta() {
        return Optional.ofNullable(chart)
                .map(Chart::result)
                .filter(results -> !results.isEmpty())
                .map(results -> results.get(0))
                .map(Result::meta);
    }
}
