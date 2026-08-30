package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.indicator.domain.BandRatio;
import com.coinwin.indicator.domain.BollingerValue;
import java.util.Optional;

/**
 * 볼린저가 지금 말하는 것.
 *
 * <p><b>「밴드 안」 은 너무 많은 것을 같은 사실로 만든다.</b> 하단에 붙어 있는 것과 상단 바로
 * 아래인 것이 같은 딱지를 받는데, 그 둘은 되돌림을 보는 사람에게 정반대 자리다.
 * {@link #ratio} 가 그 안쪽을 말한다 — 하단이 0, 상단이 1 이고, 밖으로 나가면 그 범위를
 * 벗어난다. 근거는 {@code docs/spec/indicator-usage.md} § 4.2.
 *
 * <p><b>비율이 비어 있을 수 있다.</b> 폭이 0 이면 "어디쯤" 이라는 물음이 성립하지 않는다.
 * 나머지 값은 그대로 성립하므로 이것 하나만 빠진다.
 *
 * <p><b>폭이 좁다는 것은 최근 움직임이 작았다는 뜻이지 다음이 무엇이라는 뜻이 아니다.</b>
 * 이 저장소는 밴드폭으로 어떤 거래도 내 본 적이 없다.
 *
 * @param position 밴드 대비 위치
 * @param upper 밴드 상단
 * @param middle 밴드 중심 (20봉 이동평균)
 * @param lower 밴드 하단
 * @param bandWidthPercent 중심선 대비 폭. 가격대가 달라도 비교되도록 비율이다
 * @param ratio 밴드 안에서 어디쯤인가. 폭이 0 이면 비어 있다
 */
public record BollingerReadout(
        BandPosition position,
        Price upper,
        Price middle,
        Price lower,
        Percentage bandWidthPercent,
        Optional<BandRatio> ratio) {

    public BollingerReadout {
        DomainValues.required(position, "밴드 위치");
        DomainValues.required(upper, "밴드 상단");
        DomainValues.required(middle, "밴드 중심");
        DomainValues.required(lower, "밴드 하단");
        DomainValues.required(bandWidthPercent, "밴드 폭");
        DomainValues.required(ratio, "밴드 안 위치");
    }

    /**
     * 볼린저 값을 지금 가격 기준으로 읽는다.
     *
     * <p><b>위치와 비율은 지표가 판정한다.</b> 같은 밴드에 대해 두 곳이 각자 계산하면 경계
     * 처리가 갈라진다.
     */
    public static BollingerReadout of(BollingerValue value, Price close) {
        DomainValues.required(value, "볼린저 값");
        DomainValues.required(close, "현재가");
        return new BollingerReadout(
                value.positionOf(close),
                value.upper(),
                value.middle(),
                value.lower(),
                value.bandWidth(),
                value.band().ratioOf(close));
    }
}
