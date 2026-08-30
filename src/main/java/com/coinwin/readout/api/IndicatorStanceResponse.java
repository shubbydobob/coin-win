package com.coinwin.readout.api;

import com.coinwin.readout.domain.IndicatorStance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 지표 하나가 지금 선 자리.
 *
 * <p><b>유리한 쪽이 아니다.</b> 전부 정의로 정해지는 사실이고 — 종가가 구름 위인가, MACD 가
 * 시그널 위인가 — 그것이 계속된다는 뜻은 어디에도 없다. 이 저장소는 그 부류의 전제를 7년
 * 15,110봉에서 반증했다({@code docs/adr/021}).
 *
 * <p><b>무리를 함께 낸다.</b> 무리 없이 내면 세는 쪽이 다섯을 한 번에 세게 되고, 그것은
 * 산술적으로 성립하지 않는다 — {@code docs/spec/indicator-usage.md} § 2.
 */
@Schema(description = "지표 하나가 지금 선 자리. 유리한 쪽이 아니라 관측이다")
public record IndicatorStanceResponse(
        @Schema(description = "지표 이름", example = "MACD")
        String indicator,

        @Schema(description = """
                무엇을 재는 부류인가. **TREND 와 REVERSION 을 함께 세면 안 된다** —
                종가가 밴드 상단 위인 것은 되돌림에게 과열이고 추세에게 돌파인데
                둘 다 LONG 으로 적히기 때문이다.""",
                example = "TREND",
                allowableValues = {"TREND", "REVERSION"})
        String family,

        @Schema(description = """
                어느 쪽에 서 있는가. **UNKNOWN 은 NEUTRAL 과 다른 사실이다** —
                앞은 봉이 모자라 말할 수 없는 것이고 뒤는 어느 쪽도 아닌 것이다.""",
                example = "SHORT",
                allowableValues = {"LONG", "SHORT", "NEUTRAL", "UNKNOWN"})
        String stance,

        @Schema(description = "그렇게 본 근거. **일어난 일까지만 적는다**",
                example = "시그널 아래에 있다")
        String statement) {

    static List<IndicatorStanceResponse> from(List<IndicatorStance> stances) {
        return stances.stream()
                .map(stance -> new IndicatorStanceResponse(
                        stance.indicator().label(),
                        stance.family().name(),
                        stance.stance().name(),
                        stance.statement()))
                .toList();
    }
}
