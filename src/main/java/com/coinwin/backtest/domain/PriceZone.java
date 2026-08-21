package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.PriceBand;
import com.coinwin.position.domain.Direction;
import java.util.Optional;

/**
 * 지지·저항 대 하나. 여러 번 반응한 가격 <b>구간</b>이지 선이 아니다.
 *
 * <p>구간이라는 것이 이 전략의 분할 진입을 공짜로 만든다 — 대 자체가 두 진입가(근단·원단)를
 * 주므로 배치에 별도 파라미터가 없다. 선으로 다루면 "2차를 얼마나 아래에 걸 것인가" 라는
 * 정당화할 근거 없는 숫자가 하나 더 생긴다.
 *
 * <p>{@link PriceBand} 를 재사용하는 이유는 두 경계와 비교하는 계산이 구름·볼린저와 완전히
 * 같기 때문이다. 경계 포함 규칙이 한쪽만 바뀌면 지표와 대가 다른 답을 낸다.
 *
 * <p><b>역할(지지/저항)은 필드가 아니다.</b> {@link #roleAt} 이 현재 종가로 파생한다 —
 * 근거는 {@link ZoneRole}.
 *
 * @param band 군집된 피벗들의 실제 최소~최대
 * @param touches 이 대를 만든 피벗 수. 곧 강도다
 */
public record PriceZone(PriceBand band, int touches) {

    public PriceZone {
        DomainValues.required(band, "대 구간");
        DomainValues.atLeast(touches, 2, "대의 터치 횟수");
    }

    /** 이 대가 지금 지지인가 저항인가. 대 안이면 비어 있다. */
    public Optional<ZoneRole> roleAt(Price close) {
        DomainValues.required(close, "종가");
        return ZoneRole.of(band.positionOf(close));
    }

    /**
     * 가격이 먼저 닿는 경계. 1차 진입가다.
     *
     * <p>롱은 위에서 내려오므로 상단, 숏은 아래에서 올라가므로 하단이다.
     */
    public Price nearEdgeFor(Direction direction) {
        DomainValues.required(direction, "진입 방향");
        return switch (direction) {
            case LONG -> band.upper();
            case SHORT -> band.lower();
        };
    }

    /** 대를 끝까지 통과했을 때 닿는 경계. 2차 진입가이자 손절 기준점이다. */
    public Price farEdgeFor(Direction direction) {
        DomainValues.required(direction, "진입 방향");
        return switch (direction) {
            case LONG -> band.lower();
            case SHORT -> band.upper();
        };
    }

    public Money width() {
        return band.width();
    }

    /**
     * 이 대를 사람이 읽을 문장으로. {@code MarketContext.rationale} 이 비어 있으면 거부되고
     * (ADR 017), 백테스트에는 손으로 쓸 사람이 없으므로 대가 자기를 설명한다.
     *
     * <p>구간과 터치 횟수를 싣는 이유는 <b>그 둘이 대의 정체 전부</b>이기 때문이다. 사후에
     * 기록을 볼 때 "왜 여기서 들어갔는가" 에 답하는 데 다른 값이 필요 없다.
     */
    public String describeAs(ZoneRole role) {
        DomainValues.required(role, "대의 역할");
        return "%s대 %s~%s (터치 %d회) 근단 반전 진입".formatted(
                role.label(),
                band.lower().value().toPlainString(),
                band.upper().value().toPlainString(),
                touches);
    }
}
