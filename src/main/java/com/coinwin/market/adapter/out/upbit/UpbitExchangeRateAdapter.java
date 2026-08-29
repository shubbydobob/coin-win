package com.coinwin.market.adapter.out.upbit;

import com.coinwin.common.domain.ExchangeRate;
import com.coinwin.market.application.port.out.LoadExchangeRatePort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 원/USDT 환율을 업비트에서 읽는다. 키가 필요 없다.
 *
 * <p><b>바이낸스에는 원화 시장이 없다.</b> 다른 시세는 전부 바이낸스 안에서 해결되는데
 * (`MacroQuote` 가 그 판단이다) 환율만은 그럴 수 없어 거래소가 하나 늘었다.
 *
 * <p><b>실패를 삼키지 않고 값으로 낸다.</b> 던지면 복리 계산 전체가 503 이 되는데, 원화는
 * 곁들임이라 그럴 이유가 없다. 대신 <b>경고 로그에 남긴다</b> — "실패의 원인을 볼 수 있게"
 * 가 세운 규칙이고, 원화가 사라진 화면은 그것만 봐서는 이유를 알 수 없다.
 */
@Component
public class UpbitExchangeRateAdapter implements LoadExchangeRatePort {

    private static final Logger LOG = LoggerFactory.getLogger(UpbitExchangeRateAdapter.class);

    private static final String TICKER = "/v1/ticker";

    private static final ParameterizedTypeReference<List<UpbitTicker>> TICKERS =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    private final String market;

    public UpbitExchangeRateAdapter(RestClient upbitRestClient, UpbitProperties properties) {
        this.client = upbitRestClient;
        this.market = properties.market();
    }

    @Override
    public Optional<ExchangeRate> wonPerUsdt() {
        try {
            return first(client.get()
                    .uri(uri -> uri.path(TICKER).queryParam("markets", market).build())
                    .retrieve()
                    .body(TICKERS));
        } catch (RestClientException e) {
            LOG.warn("환율을 가져오지 못했다: {}", market, e);
            return Optional.empty();
        }
    }

    /** 시장 하나만 물었으므로 줄도 하나다. 비어 있거나 값이 없으면 환율이 없는 것이다. */
    private Optional<ExchangeRate> first(List<UpbitTicker> tickers) {
        if (tickers == null || tickers.isEmpty() || tickers.getFirst().tradePrice() == null) {
            LOG.warn("환율 응답이 비어 있다: {}", market);
            return Optional.empty();
        }
        UpbitTicker ticker = tickers.getFirst();
        return Optional.of(new ExchangeRate(ticker.tradePrice(), observedAt(ticker)));
    }

    /** 시각을 안 준 응답은 지금 잰 것으로 본다. 그 오차는 이 값의 쓰임에서 무의미하다. */
    private static Instant observedAt(UpbitTicker ticker) {
        return ticker.timestamp() == null
                ? Instant.now()
                : Instant.ofEpochMilli(ticker.timestamp());
    }
}
