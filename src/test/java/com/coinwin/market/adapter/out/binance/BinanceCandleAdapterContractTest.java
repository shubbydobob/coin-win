package com.coinwin.market.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.application.port.out.LoadCandlesPortContract;
import com.coinwin.market.domain.CandleSeries;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 거래소 어댑터가 <b>인메모리·영속화와 같은 계약</b>을 통과하는지.
 *
 * <p>페이지 크기를 3 으로 낮춘다. 계약 스위트가 최대 10개를 다루므로 페이지 이어받기가
 * 반드시 여러 번 돈다. 1500 그대로 두면 이 어댑터에만 있는 로직이 한 번도 실행되지 않은 채
 * 계약이 초록이 된다.
 */
class BinanceCandleAdapterContractTest extends LoadCandlesPortContract {

    private static final int PAGE_SIZE = 3;

    private FakeBinanceServer exchange;
    private BinanceCandleAdapter adapter;

    @BeforeEach
    void 페이크_거래소를_띄운다() {
        exchange = new FakeBinanceServer();
        adapter = new BinanceCandleAdapter(
                RestClient.builder().baseUrl(exchange.baseUrl()).build(), properties(exchange));
    }

    @AfterEach
    void 페이크_거래소를_닫는다() {
        exchange.close();
    }

    private static BinanceProperties properties(FakeBinanceServer exchange) {
        return new BinanceProperties(exchange.baseUrl(), PAGE_SIZE,
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Override
    protected LoadCandlesPort loadPort() {
        return adapter;
    }

    @Override
    protected void givenCandlesExist(CandleSeries candles) {
        exchange.registerKlines(INTERVAL, candles);
    }

    /**
     * 계약에 없는 이 어댑터만의 성질. 한 페이지를 넘는 구간도 빠짐없이 이어 받아야 한다.
     * 계약 스위트가 이미 태우고 있지만, 깨졌을 때 원인이 곧바로 읽히도록 따로 둔다.
     */
    @Test
    void 페이지_크기를_넘는_구간도_빠짐없이_받는다() {
        givenCandlesExist(candles(0, 10));

        CandleSeries loaded = loadPort().load(query(0, 10));

        assertThat(loaded.size()).isEqualTo(10);
        assertThat(loaded.last().openTime()).isEqualTo(hour(9));
    }

    /** 거래소가 닿지 않는 것은 500 이 아니라 503 이어야 한다. 그 출발점이 이 예외다. */
    @Test
    void 거래소가_닿지_않으면_외부데이터_예외를_던진다() {
        exchange.close();

        assertThatThrownBy(() -> loadPort().load(query(0, 3)))
                .isInstanceOf(ExternalDataUnavailableException.class)
                .hasMessageContaining("BTCUSDT");
    }
}
