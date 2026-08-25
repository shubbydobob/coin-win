package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.Price;
import com.coinwin.market.application.port.out.LoadMacroQuotesPort;
import com.coinwin.market.domain.MacroQuote;
import com.coinwin.market.domain.MacroWatchlist;
import com.coinwin.market.domain.MacroTicker;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 거시 자산 다섯의 시세를 바이낸스에서 읽는다. 키가 필요 없다.
 *
 * <p><b>못 읽은 것은 목록에서 빠진다.</b> 다섯 종목을 각각 부르므로 하나가 실패해도 나머지
 * 넷은 보인다 — 한 번에 다 실패시키면 상장폐지된 종목 하나가 이 블록 전체를 죽인다.
 * 빠진 것은 조용히 사라지지 않고 <b>경고 로그에 남는다</b>: "실패의 원인을 볼 수 있게" 가
 * 세운 규칙이고, 다섯이 셋으로 줄어든 화면은 그것만 봐서는 이유를 알 수 없다.
 *
 * <p>다섯 번 부르는 것이 이 어댑터의 사정이다. 종목 없이 부르면 744종이 한꺼번에 오고
 * 가중치가 40 이라, 다섯 번(가중치 5)이 오히려 싸다.
 *
 * <p><b>비트코인 현물만 다른 곳에서 읽는다.</b> 무기한과 호스트도 경로도 다르다
 * ({@code fapi}/{@code fapi/v1} 대 {@code api}/{@code api/v3}). 어느 쪽인지는 관심 목록이
 * 알고({@code MacroWatchlist.Venue}) 어댑터는 그것을 보고 클라이언트를 고른다 — 심볼로
 * 판단하면 "BTCUSDT 는 현물" 이라는 규칙이 여기에도 생기고, 그 심볼은 무기한에도 있다.
 */
@Component
public class BinanceMacroQuoteAdapter implements LoadMacroQuotesPort {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceMacroQuoteAdapter.class);

    private static final String TICKER = "/fapi/v1/ticker/24hr";

    /** 현물은 경로도 다르다. {@code v1} 이 아니라 {@code v3} 다. */
    private static final String SPOT_TICKER = "/api/v3/ticker/24hr";

    private final RestClient client;

    private final RestClient spotClient;

    public BinanceMacroQuoteAdapter(RestClient binanceRestClient, RestClient binanceSpotRestClient) {
        this.client = binanceRestClient;
        this.spotClient = binanceSpotRestClient;
    }

    @Override
    public List<MacroQuote> quotes() {
        // **자기 몫만 읽는다.** 야후에서 오는 셋은 이 어댑터가 부를 수 있는 것이 아니다.
        return java.util.stream.Stream.concat(
                        MacroWatchlist.orderedFrom(MacroWatchlist.Venue.PERPETUAL).stream(),
                        MacroWatchlist.orderedFrom(MacroWatchlist.Venue.SPOT).stream())
                .map(this::quoteOf)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<MacroQuote> quoteOf(MacroTicker symbol) {
        boolean spot = MacroWatchlist.venueOf(symbol) == MacroWatchlist.Venue.SPOT;
        try {
            BinanceTicker ticker = (spot ? spotClient : client).get()
                    .uri(uri -> uri.path(spot ? SPOT_TICKER : TICKER)
                            .queryParam("symbol", symbol.value())
                            .build())
                    .retrieve()
                    .body(BinanceTicker.class);
            if (ticker == null || ticker.lastPrice() == null) {
                LOG.warn("거시 시세가 비어 있다: {}", symbol.value());
                return Optional.empty();
            }
            return Optional.of(toQuote(symbol, ticker));
        } catch (RestClientException e) {
            LOG.warn("거시 시세를 가져오지 못했다: {}", symbol.value(), e);
            return Optional.empty();
        }
    }

    /** 이름과 묶음은 관심 목록이 안다. 어댑터는 값만 옮긴다. */
    private static MacroQuote toQuote(MacroTicker symbol, BinanceTicker ticker) {
        return new MacroQuote(
                symbol,
                MacroWatchlist.labelOf(symbol),
                MacroWatchlist.groupOf(symbol),
                MacroWatchlist.venueOf(symbol),
                Price.of(ticker.lastPrice()),
                new BigDecimal(ticker.priceChangePercent()));
    }
}
