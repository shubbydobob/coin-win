package com.coinwin.market.adapter.out.binance;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.market.application.port.out.ExchangeCandles;
import com.coinwin.market.application.port.out.LoadCandlesPort;
import com.coinwin.market.domain.CandleQuery;
import com.coinwin.market.domain.CandleSeries;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 바이낸스 공개 엔드포인트 {@code /fapi/v1/klines} 에서 캔들을 읽는 어댑터.
 *
 * <p>두 가지가 이 어댑터에만 있다.
 *
 * <ul>
 *   <li><b>구간 경계 변환.</b> 바이낸스의 {@code endTime} 은 <b>포함</b>이고 우리 구간은
 *       반열림 {@code [from, to)} 다. 1밀리초를 빼지 않으면 끝 시각의 캔들이 딸려 오고,
 *       연속 조회에서 그대로 중복이 된다.
 *   <li><b>페이지 이어받기.</b> 한 번에 1500개까지만 준다. 요청 구간이 그보다 길면 여러 번
 *       나눠 받아야 하는데, 이 사정이 포트 밖으로 새면 호출부가 거래소 상한을 알게 된다.
 * </ul>
 *
 * <p>저장은 하지 않는다. {@link com.coinwin.market.application.port.out.SaveCandlesPort} 를
 * 구현하지 않는 유일한 캔들 어댑터다.
 */
@Component
@ExchangeCandles
public class BinanceCandleAdapter implements LoadCandlesPort {

    private static final String KLINES = "/fapi/v1/klines";

    private final RestClient client;
    private final int pageSize;

    public BinanceCandleAdapter(RestClient binanceRestClient, BinanceProperties properties) {
        this.client = binanceRestClient;
        this.pageSize = properties.maxCandlesPerRequest();
    }

    @Override
    public CandleSeries load(CandleQuery query) {
        CandleSeries collected = CandleSeries.empty();
        Instant cursor = query.range().from();
        while (cursor.isBefore(query.range().to())) {
            CandleSeries page = fetchPage(query, cursor);
            if (page.isEmpty()) {
                break;
            }
            collected = collected.merge(page);
            cursor = page.last().openTime().plus(query.interval().length());
            if (page.size() < pageSize) {
                break;
            }
        }
        return collected.within(query.range());
    }

    private CandleSeries fetchPage(CandleQuery query, Instant from) {
        return BinanceKlines.toSeries(get(query, from));
    }

    /** {@code endTime} 이 포함 경계이므로 1밀리초를 뺀다. 이 한 줄이 반열림 구간을 지킨다. */
    private Object[][] get(CandleQuery query, Instant from) {
        try {
            return client.get()
                    .uri(uri -> uri.path(KLINES)
                            .queryParam("symbol", query.symbol().value())
                            .queryParam("interval", query.interval().code())
                            .queryParam("startTime", from.toEpochMilli())
                            .queryParam("endTime", query.range().to().toEpochMilli() - 1)
                            .queryParam("limit", pageSize)
                            .build())
                    .retrieve()
                    .body(Object[][].class);
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException(
                    "바이낸스에서 캔들을 가져오지 못했다: " + query.symbol().value(), e);
        }
    }
}
