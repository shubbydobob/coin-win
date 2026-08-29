package com.coinwin.indicator;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.common.domain.Price;
import com.coinwin.indicator.domain.VolumeProfile;
import com.coinwin.indicator.domain.VolumeProfileSettings;
import com.coinwin.indicator.domain.VolumeShelf;
import com.coinwin.market.adapter.out.binance.BinanceCandleAdapter;
import com.coinwin.market.adapter.out.binance.BinanceClientConfig;
import com.coinwin.market.adapter.out.binance.BinanceProperties;
import com.coinwin.market.domain.CandleInterval;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import com.coinwin.market.domain.Symbol;
import com.coinwin.market.domain.TimeRange;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * 실제 캔들에서 매물대가 어떤 수를 내는지 찍는다.
 *
 * <p><b>단언이 아니라 눈으로 볼 표가 목적이다.</b> 단위 테스트는 손으로 풀 수 있는 합성 캔들만
 * 쓰고, 그것이 증명하는 것은 "구현이 정의를 따른다" 까지다. 정의 자체가 쓸모없는 수를 낼
 * 수도 있는데 — 24칸이 너무 잘거나 1.5배가 너무 헐거워 <b>화면의 절반이 매물대가 되는</b>
 * 경우 — 그것은 합성 캔들로 드러나지 않는다.
 *
 * <p>같이 찍는 성질 둘은 단언한다. <b>덩이가 지나치게 많으면 아무것도 가리키지 않는 것과
 * 같고</b>, 비중의 합이 100%를 넘으면 칸을 두 번 센 것이다.
 *
 * <p>{@code check} 는 네트워크 없이 돌아야 하므로 {@code crosscheck} 로 뗀다.
 */
@Tag("crosscheck")
@SpringBootTest(
        classes = BinanceClientConfig.class,
        properties = {
            "coinwin.market.binance.base-url=${COINWIN_BINANCE_URL:https://fapi.binance.com}",
            "coinwin.market.binance.connect-timeout=5s",
            "coinwin.market.binance.read-timeout=15s"
        })
class VolumeProfileCrossCheckTest {

    private static final Symbol SYMBOL = Symbol.BTC_USDT;

    /** 판독 화면이 보는 것과 같은 봉 수다. 다른 수로 찍으면 화면과 다른 표를 보게 된다. */
    private static final int BARS = 300;

    @Autowired
    private RestClient binanceRestClient;

    @Autowired
    private BinanceProperties properties;

    @Test
    void 실제_BTCUSDT_캔들의_매물대를_출력한다() {
        for (CandleInterval interval : List.of(
                CandleInterval.FIFTEEN_MINUTES, CandleInterval.ONE_HOUR, CandleInterval.FOUR_HOURS)) {
            표를_찍는다(interval, candles(interval));
        }
    }

    private void 표를_찍는다(CandleInterval interval, CandleSeries series) {
        VolumeProfile profile = VolumeProfile.over(series, VolumeProfileSettings.standard());
        Price close = series.last().close();
        List<VolumeShelf> shelves = profile.shelves();

        System.out.printf("%n=== %s %s — 매물대 (봉 %d개) ===%n",
                SYMBOL.value(), interval.code(), series.size());
        System.out.printf("종가 %s · POC %s · 덩이 %d개%n",
                close.value(), profile.pointOfControl().band().middle().value(), shelves.size());
        shelves.forEach(shelf -> System.out.printf("  %11s ~ %-11s %7s%%  %s%n",
                shelf.band().lower().value(), shelf.band().upper().value(),
                shelf.share().value(), 표시(shelf, close)));

        assertThat(shelves).as("덩이가 너무 많으면 아무것도 가리키지 않는다").hasSizeLessThan(8);
        assertThat(합계(shelves)).as("비중의 합은 100%를 넘을 수 없다").isLessThanOrEqualTo(100.0);
    }

    private static String 표시(VolumeShelf shelf, Price close) {
        if (!shelf.band().lower().isAbove(close) && !shelf.band().upper().isBelow(close)) {
            return "← 지금 이 안";
        }
        return shelf.band().upper().isBelow(close) ? "아래" : "위";
    }

    private static double 합계(List<VolumeShelf> shelves) {
        return shelves.stream().mapToDouble(shelf -> shelf.share().value().doubleValue()).sum();
    }

    /** 닫힌 봉만 받는다. 진행 중인 봉을 섞으면 대조 도중에 표가 달라진다. */
    private CandleSeries candles(CandleInterval interval) {
        Instant to = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant from = to.minus(Duration.ofSeconds(interval.length().toSeconds() * BARS));
        return new BinanceCandleAdapter(binanceRestClient, properties)
                .load(new CandleQuery(SYMBOL, interval, new TimeRange(from, to)));
    }
}
