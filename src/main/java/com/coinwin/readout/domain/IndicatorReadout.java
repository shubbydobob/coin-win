package com.coinwin.readout.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Percentage;
import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.BandPosition;
import com.coinwin.indicator.domain.BollingerValue;
import com.coinwin.indicator.domain.IchimokuValue;

/**
 * 한 주기에서 지표가 말하는 것.
 *
 * <p><b>선 값을 함께 싣는다.</b> "구름 위" 만으로는 아슬아슬하게 위인지 한참 위인지 알 수
 * 없고, 그 차이가 분할 진입에서 첫 칸을 어디 둘지를 가른다. 위치는 요약이고 선은 근거다.
 *
 * <p><b>판정하지 않는다.</b> 구름 위라는 것은 사실이고 "그러니 롱" 은 예측이다. 이 저장소는
 * 그 예측을 7년 15,110봉에서 반증했다({@code docs/adr/021}). 그래서 여기 담기는 것은 위치와
 * 값까지이고, 방향은 사람이 정한다.
 *
 * @param ichimoku 구름 대비 위치
 * @param conversionLine 전환선 (9)
 * @param baseLine 기준선 (26)
 * @param cloudTop 구름 위 모서리. 두 선행스팬 중 큰 쪽이다
 * @param cloudBottom 구름 아래 모서리
 * @param bollinger 밴드 대비 위치
 * @param bollingerUpper 밴드 상단
 * @param bollingerMiddle 밴드 중심 (이동평균)
 * @param bollingerLower 밴드 하단
 * @param bandWidthPercent 밴드 폭. 좁으면 변동성이 죽어 있다는 뜻이다
 */
public record IndicatorReadout(
        BandPosition ichimoku,
        Price conversionLine,
        Price baseLine,
        Price cloudTop,
        Price cloudBottom,
        BandPosition bollinger,
        Price bollingerUpper,
        Price bollingerMiddle,
        Price bollingerLower,
        Percentage bandWidthPercent) {

    public IndicatorReadout {
        DomainValues.required(ichimoku, "구름 위치");
        DomainValues.required(conversionLine, "전환선");
        DomainValues.required(baseLine, "기준선");
        DomainValues.required(cloudTop, "구름 상단");
        DomainValues.required(cloudBottom, "구름 하단");
        DomainValues.required(bollinger, "밴드 위치");
        DomainValues.required(bollingerUpper, "밴드 상단");
        DomainValues.required(bollingerMiddle, "밴드 중심");
        DomainValues.required(bollingerLower, "밴드 하단");
        DomainValues.required(bandWidthPercent, "밴드 폭");
    }

    /**
     * 두 지표 값을 지금 가격 기준으로 읽는다.
     *
     * <p><b>위치는 지표가 판정한다.</b> 여기서 가격과 선을 직접 비교하면 "경계는 구간에
     * 포함된다" 는 규칙이 두 곳에 생기고, 한쪽만 바뀌는 순간 화면과 백테스트가 다른 답을 낸다.
     */
    public static IndicatorReadout of(IchimokuValue ichimoku, BollingerValue bollinger, Price close) {
        DomainValues.required(ichimoku, "일목 값");
        DomainValues.required(bollinger, "볼린저 값");
        DomainValues.required(close, "현재가");
        return new IndicatorReadout(
                ichimoku.positionOf(close),
                ichimoku.conversionLine(),
                ichimoku.baseLine(),
                ichimoku.cloud().upper(),
                ichimoku.cloud().lower(),
                bollinger.positionOf(close),
                bollinger.upper(),
                bollinger.middle(),
                bollinger.lower(),
                bollinger.bandWidth());
    }
}
