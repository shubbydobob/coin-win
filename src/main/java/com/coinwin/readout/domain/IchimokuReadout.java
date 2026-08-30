package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.AtrMultiple;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.indicator.domain.IchimokuValue;
import java.util.Optional;

/**
 * 일목이 지금 말하는 것.
 *
 * <p><b>다섯 선을 계산해 두고 구름 위치 하나만 쓰고 있었다.</b> 「구름 위」 는 아슬아슬하게
 * 위인지 한참 위인지, 구름이 두꺼운지 종잇장인지, 전환선이 기준선을 막 넘었는지 한참 위인지를
 * 전부 같은 사실로 만든다 — 그 셋은 전부 다른 자리이고, 분할 진입에서 첫 칸을 어디 둘지를
 * 가른다. 근거는 {@code docs/spec/indicator-usage.md} § 4.1.
 *
 * <p><b>거리는 ATR 로 잰다.</b> "전환선이 기준선보다 300 위" 는 조용한 장에서는 큰 값이고
 * 급한 장에서는 아무것도 아니다. 그리고 이 저장소는 이미 대의 폭과 손절 버퍼를 그 단위로
 * 정해 두었다.
 *
 * <p><b>판정하지 않는다.</b> 구름이 두껍다는 것은 사실이고 "그러니 안 뚫린다" 는 예측이다.
 * 이 저장소는 그 부류를 7년 15,110봉에서 반증했고({@code docs/adr/021}), 여기 담기는 것 중
 * 어느 것도 아직 재 본 적이 없다.
 *
 * @param position 구름 대비 위치
 * @param conversionLine 전환선 (9)
 * @param baseLine 기준선 (26)
 * @param cloudTop 구름 위 모서리. 두 선행스팬 중 큰 쪽이다
 * @param cloudBottom 구름 아래 모서리
 * @param bullishCloud 선행스팬 1 이 2 위인가. <b>뒤집히는 것 자체가 전환으로 읽히는 자리다</b>
 * @param cloudThickness 구름 두께를 ATR 로 잰 값. 언제나 0 이상이다
 * @param conversionGap 전환선 − 기준선. <b>부호가 절반이다</b>
 * @param baseLineGap 종가 − 기준선. 기준선에서 얼마나 떨어져 있나
 * @param laggingSpanGap 후행스팬 확인 — 지금 종가가 변위만큼 전의 종가보다 얼마나 위인가.
 *     <b>봉이 모자라면 비어 있다</b>
 */
public record IchimokuReadout(
        BandPosition position,
        Price conversionLine,
        Price baseLine,
        Price cloudTop,
        Price cloudBottom,
        boolean bullishCloud,
        AtrMultiple cloudThickness,
        AtrMultiple conversionGap,
        AtrMultiple baseLineGap,
        Optional<AtrMultiple> laggingSpanGap) {

    public IchimokuReadout {
        DomainValues.required(position, "구름 위치");
        DomainValues.required(conversionLine, "전환선");
        DomainValues.required(baseLine, "기준선");
        DomainValues.required(cloudTop, "구름 상단");
        DomainValues.required(cloudBottom, "구름 하단");
        DomainValues.required(cloudThickness, "구름 두께");
        DomainValues.required(conversionGap, "전환·기준 간격");
        DomainValues.required(baseLineGap, "기준선까지 거리");
        DomainValues.required(laggingSpanGap, "후행스팬 간격");
    }

    /**
     * 일목 값을 지금 가격과 변동성 기준으로 읽는다.
     *
     * <p><b>위치와 구름 방향은 지표가 판정한다.</b> 여기서 가격과 선을 직접 비교하면 "경계는
     * 구간에 포함된다" 는 규칙이 두 곳에 생기고, 한쪽만 바뀌는 순간 화면과 백테스트가 다른
     * 답을 낸다.
     */
    public static IchimokuReadout of(
            IchimokuValue value, Price close, Money atr, Optional<Money> laggingGap) {
        DomainValues.required(value, "일목 값");
        DomainValues.required(close, "현재가");
        DomainValues.required(atr, "ATR");
        DomainValues.required(laggingGap, "후행스팬 간격");
        return new IchimokuReadout(
                value.positionOf(close),
                value.conversionLine(),
                value.baseLine(),
                value.cloud().upper(),
                value.cloud().lower(),
                value.bullishCloud(),
                AtrMultiple.of(value.cloud().width(), atr),
                AtrMultiple.between(value.conversionLine(), value.baseLine(), atr),
                AtrMultiple.between(close, value.baseLine(), atr),
                laggingGap.map(gap -> AtrMultiple.of(gap, atr)));
    }
}
