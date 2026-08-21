package com.coinwin.market.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.Quantity;
import com.coinwin.market.domain.MarketMetrics;
import com.coinwin.market.domain.Symbol;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 세 엔드포인트를 하나의 시장 상태로 합치는 어댑터.
 *
 * <p>검사의 초점은 <b>단위 변환</b>이다. 바이낸스는 펀딩비를 비율(0.0001)로 주는데 사람이 읽는
 * 단위는 백분율(0.01%)이다. 100배가 어긋나도 숫자만 봐서는 이상해 보이지 않기 때문에,
 * 여기서 고정해 두지 않으면 화면에 나가기 전까지 아무도 모른다.
 */
class BinanceMarketMetricsAdapterTest {

    private FakeBinanceServer exchange;
    private BinanceMarketMetricsAdapter adapter;

    @BeforeEach
    void 페이크_거래소를_띄운다() {
        exchange = new FakeBinanceServer();
        adapter = new BinanceMarketMetricsAdapter(
                RestClient.builder().baseUrl(exchange.baseUrl()).build());
    }

    @AfterEach
    void 페이크_거래소를_닫는다() {
        exchange.close();
    }

    private void 거래소가_정상_응답을_준다(String lastFundingRate) {
        exchange.respondWith("/fapi/v1/premiumIndex", """
                {"symbol":"BTCUSDT","markPrice":"60123.40","indexPrice":"60120.00",
                 "lastFundingRate":"%s","nextFundingTime":1787299200000,
                 "interestRate":"0.00010000","time":1787270400000}"""
                .formatted(lastFundingRate));
        exchange.respondWith("/fapi/v1/openInterest",
                """
                {"openInterest":"81234.500","symbol":"BTCUSDT","time":1787270400000}""");
        exchange.respondWith("/futures/data/globalLongShortAccountRatio", """
                [{"symbol":"BTCUSDT","longShortRatio":"1.8342","longAccount":"0.6472",
                  "shortAccount":"0.3528","timestamp":"1787270400000"}]""");
    }

    @Test
    void 세_엔드포인트를_한_시점으로_묶는다() {
        거래소가_정상_응답을_준다("0.00010000");

        MarketMetrics metrics = adapter.metricsFor(Symbol.BTC_USDT);

        assertThat(metrics.symbol()).isEqualTo(Symbol.BTC_USDT);
        assertThat(metrics.at()).isEqualTo(Instant.ofEpochMilli(1787270400000L));
        assertThat(metrics.openInterest()).isEqualTo(Quantity.of("81234.5"));
        assertThat(metrics.longShortRatio()).isEqualByComparingTo("1.8342");
    }

    /** 비율 0.0001 은 백분율 0.01% 다. 이 100배를 놓치면 펀딩비가 100배로 표시된다. */
    @Test
    void 비율로_온_펀딩비를_백분율로_바꾼다() {
        거래소가_정상_응답을_준다("0.00010000");

        assertThat(adapter.metricsFor(Symbol.BTC_USDT).fundingRate().value())
                .isEqualByComparingTo("0.010000");
    }

    /** 숏이 우세하면 펀딩비가 음수다. 부호가 살아남아야 "누가 낸다" 가 보인다. */
    @Test
    void 음수_펀딩비의_부호가_살아남는다() {
        거래소가_정상_응답을_준다("-0.00012500");

        assertThat(adapter.metricsFor(Symbol.BTC_USDT).fundingRate().isNegative()).isTrue();
        assertThat(adapter.metricsFor(Symbol.BTC_USDT).fundingRate().value())
                .isEqualByComparingTo("-0.012500");
    }

    @Test
    void 롱숏비율이_비어_있으면_응답_예외를_던진다() {
        거래소가_정상_응답을_준다("0.00010000");
        exchange.respondWith("/futures/data/globalLongShortAccountRatio", "[]");

        assertThatThrownBy(() -> adapter.metricsFor(Symbol.BTC_USDT))
                .isInstanceOf(BinanceResponseException.class);
    }

    @Test
    void 거래소가_닿지_않으면_외부데이터_예외를_던진다() {
        exchange.close();

        assertThatThrownBy(() -> adapter.metricsFor(Symbol.BTC_USDT))
                .isInstanceOf(ExternalDataUnavailableException.class);
    }
}
