package com.coinwin.market.adapter.out.yahoo;

import com.coinwin.common.domain.Price;
import com.coinwin.market.application.port.out.LoadMacroQuotesPort;
import com.coinwin.market.domain.MacroQuote;
import com.coinwin.market.domain.MacroTicker;
import com.coinwin.market.domain.MacroWatchlist;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 바이낸스에 없는 셋을 야후에서 읽는다 — 나스닥 선물 · S&P 500 지수 · 달러지수.
 *
 * <p><b>왜 밖으로 나갔는가.</b> 이 셋은 바이낸스에 <b>없다</b>. 상장 목록을 값으로 훑어
 * 2만~4만 구간의 USDT 무기한이 하나도 없음을 확인했고, 나스닥은 ETF 셋(QQQ 711 · TQQQ ·
 * SQQQ)뿐이며 {@code SPXUSDT} 는 0.5달러짜리 코인이지 S&P 500 이 아니다. ETF 로 대신하면
 * 값의 크기가 달라지고(QQQ 711 대 나스닥 29,199) 달러지수는 대리물조차 없다.
 *
 * <p><b>대가 셋을 그대로 적어 둔다.</b>
 *
 * <ol>
 *   <li><b>문서화된 API 가 아니다.</b> 예고 없이 막히거나 모양이 바뀔 수 있다. 그래서 못
 *       읽으면 그 줄만 빠지고 나머지는 그대로 나간다 — 바이낸스 쪽과 같은 규칙이다.
 *   <li><b>시계가 다르다.</b> 지수는 미국 장 시간에만, 선물은 거의 24시간이되 주말은 쉰다.
 *       그 사실을 {@code venue} 로 들고 다니고 화면이 말한다.
 *   <li><b>키가 필요 없다.</b> 이것 하나는 다행이라 서명 경로가 늘지 않는다.
 * </ol>
 */
@Component
public class YahooMacroQuoteAdapter implements LoadMacroQuotesPort {

    private static final Logger LOG = LoggerFactory.getLogger(YahooMacroQuoteAdapter.class);

    private static final MathContext MC = new MathContext(12);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final RestClient client;

    public YahooMacroQuoteAdapter(RestClient yahooRestClient) {
        this.client = yahooRestClient;
    }

    @Override
    public List<MacroQuote> quotes() {
        return MacroWatchlist.orderedFrom(MacroWatchlist.Venue.YAHOO).stream()
                .map(this::quoteOf)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MacroQuote> quoteOf(MacroTicker ticker) {
        try {
            YahooChart chart = client.get()
                    .uri(uri -> uri.path("/v8/finance/chart/{ticker}")
                            .queryParam("range", "2d")
                            .queryParam("interval", "1d")
                            .build(ticker.value()))
                    .retrieve()
                    .body(YahooChart.class);
            return Optional.ofNullable(chart)
                    .flatMap(YahooChart::meta)
                    .flatMap(meta -> toQuote(ticker, meta));
        } catch (RestClientException e) {
            LOG.warn("야후 시세를 가져오지 못했다: {}", ticker.value(), e);
            return Optional.empty();
        }
    }

    /**
     * <b>둘 다 있어야 한 줄이 된다.</b> 값만 있고 전일 종가가 없으면 변동률을 말할 수 없고,
     * 그때 0% 로 적으면 "움직이지 않았다" 는 거짓말이 된다 — 없는 것과 0 을 가르는 이 저장소의
     * 규칙 그대로다.
     */
    private static Optional<MacroQuote> toQuote(MacroTicker ticker, YahooMeta meta) {
        if (meta.regularMarketPrice() == null || meta.chartPreviousClose() == null
                || meta.chartPreviousClose().signum() == 0) {
            LOG.warn("야후 시세에 값이나 전일 종가가 없다: {}", ticker.value());
            return Optional.empty();
        }
        BigDecimal change = meta.regularMarketPrice()
                .subtract(meta.chartPreviousClose())
                .divide(meta.chartPreviousClose(), MC)
                .multiply(HUNDRED, MC);
        return Optional.of(new MacroQuote(
                ticker,
                MacroWatchlist.labelOf(ticker),
                MacroWatchlist.groupOf(ticker),
                MacroWatchlist.Venue.YAHOO,
                Price.of(meta.regularMarketPrice()),
                change));
    }
}
