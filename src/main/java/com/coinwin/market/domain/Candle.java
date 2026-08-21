package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import java.time.Instant;

/**
 * 한 구간의 시가·고가·저가·종가와 거래량.
 *
 * <p>{@code openTime} 만 들고 주기는 들지 않는다. 주기는 캔들 하나의 성질이 아니라 <b>묶음</b>의
 * 성질이고, 캔들마다 반복해 담으면 같은 값이 수천 번 복제되면서 서로 어긋날 여지가 생긴다.
 * 주기는 {@link CandleQuery} 가 들고 다닌다.
 *
 * <p>거래량이 {@link Quantity} 인 이유는 바이낸스 klines 의 volume 이 기초자산(BTC) 수량이기
 * 때문이다. 스케일 8 이 정확히 맞는다.
 */
public record Candle(
        Instant openTime,
        Price open,
        Price high,
        Price low,
        Price close,
        Quantity volume) {

    public Candle {
        DomainValues.required(openTime, "캔들 시각");
        DomainValues.required(open, "시가");
        DomainValues.required(high, "고가");
        DomainValues.required(low, "저가");
        DomainValues.required(close, "종가");
        DomainValues.required(volume, "거래량");
        assertHighLowEnclose(open, high, low, close);
    }

    /**
     * 고가와 저가는 나머지 셋을 실제로 감싸야 한다.
     *
     * <p>이 검사가 없으면 깨진 캔들이 Phase 4 의 지표 계산까지 조용히 흘러가고, 트레이딩뷰
     * 값과 어긋났을 때 원인이 "공식" 인지 "입력" 인지 구분되지 않는다.
     */
    private static void assertHighLowEnclose(Price open, Price high, Price low, Price close) {
        if (high.isBelow(low)) {
            throw new InvalidMarketDataException(
                    "고가는 저가보다 낮을 수 없다: 고가 %s, 저가 %s".formatted(high.value(), low.value()));
        }
        if (high.isBelow(open) || high.isBelow(close)) {
            throw new InvalidMarketDataException(
                    "고가는 시가·종가보다 낮을 수 없다: 고가 %s".formatted(high.value()));
        }
        if (low.isAbove(open) || low.isAbove(close)) {
            throw new InvalidMarketDataException(
                    "저가는 시가·종가보다 높을 수 없다: 저가 %s".formatted(low.value()));
        }
    }
}
