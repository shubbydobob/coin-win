package com.coinwin.market.adapter.out.snapshot;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import com.coinwin.market.domain.LeverageBracket;
import com.coinwin.market.domain.LeverageBrackets;
import com.coinwin.market.domain.Symbol;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 스냅샷 파일의 모양. 도메인 타입이 아니라 <b>파일의 모양</b>이라 어댑터 안에 있다.
 *
 * <p>{@code recordedAt} 과 {@code source} 는 코드가 쓰지 않는다. 사람이 "이 숫자가 언제 것인지"
 * 를 파일 안에서 확인할 수 있어야 하기 때문에 남긴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LeverageBracketSnapshot(String symbol, String recordedAt, List<Tier> brackets) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tier(
            int tier,
            String notionalCap,
            String maintenanceMarginRate,
            String maintenanceAmount) {

        LeverageBracket toDomain() {
            return new LeverageBracket(tier, Money.of(notionalCap),
                    Percentage.of(maintenanceMarginRate), Money.of(maintenanceAmount));
        }
    }

    /** 여기서 도메인으로 넘어가는 순간 구간표의 정합성이 검사된다. */
    LeverageBrackets toDomain() {
        return new LeverageBrackets(Symbol.of(symbol), brackets.stream()
                .map(Tier::toDomain)
                .toList());
    }
}
