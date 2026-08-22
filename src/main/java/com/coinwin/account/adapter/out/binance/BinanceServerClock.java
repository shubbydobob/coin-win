package com.coinwin.account.adapter.out.binance;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 거래소 시계에 맞춘 시각.
 *
 * <p><b>서명 타임스탬프는 우리 시계가 아니라 거래소 시계로 판정된다.</b> 바이낸스는 요청의
 * {@code timestamp} 가 자기 시각의 {@code recvWindow} 안에 있는지를 보고, 벗어나면 {@code -1021}
 * 로 거절한다. 그러므로 우리가 물어야 할 것은 "지금 몇 시인가" 가 아니라 <b>"거래소는 지금 몇
 * 시라고 하는가"</b> 다. 로컬 시계를 쓰는 것은 틀린 시계에 묻는 것이다.
 *
 * <p>실제로 이 자리에서 걸렸다 — 개발 기계가 33초 앞서 있었고 모든 요청이 거절됐다. 윈도우에서
 * 절전·복귀를 반복하면 흔한 폭이다. 사람이 시계를 맞춰서 해결할 수도 있지만, 그것은 <b>매번
 * 다시 어긋나는 조건</b>을 사람의 습관에 맡기는 것이다.
 *
 * <p><b>차이는 한 번 재고 재사용한다.</b> 요청마다 시각을 물으면 개인 엔드포인트 한 번에 왕복이
 * 두 번이 되고, 그 왕복 자체가 지연이라 정확도도 나아지지 않는다. 주기적으로 다시 잰다.
 *
 * <p><b>큰 차이는 경고로 남긴다.</b> 보정해서 요청은 통과하더라도 기계 시계가 어긋나 있다는
 * 사실 자체는 다른 곳에 영향을 준다 — 캔들 조회 구간, 기록의 체결 시각. 조용히 고쳐 주면
 * 그 사실이 사라진다.
 */
class BinanceServerClock {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceServerClock.class);

    private static final String SERVER_TIME = "/fapi/v1/time";

    /** 이 주기로 다시 잰다. 시계는 천천히 흐르므로 요청마다 물을 이유가 없다. */
    private static final Duration RESYNC_INTERVAL = Duration.ofMinutes(15);

    /** 이보다 크면 기계 시계 자체를 의심해야 한다. 보정은 하되 기록은 남긴다. */
    private static final Duration SUSPICIOUS_SKEW = Duration.ofSeconds(5);

    private final RestClient client;
    private final Clock local;

    private volatile Duration offset = Duration.ZERO;
    private volatile Instant resyncAfter = Instant.MIN;

    BinanceServerClock(RestClient binanceRestClient, Clock local) {
        this.client = binanceRestClient;
        this.local = local;
    }

    /** 거래소 기준 지금. 서명 타임스탬프와 관측 시각이 모두 이 값을 쓴다. */
    Instant now() {
        Instant here = local.instant();
        if (here.isAfter(resyncAfter)) {
            resync(here);
        }
        return local.instant().plus(offset);
    }

    private void resync(Instant here) {
        Duration measured = Duration.ofMillis(fetchServerTime() - here.toEpochMilli());
        offset = measured;
        resyncAfter = here.plus(RESYNC_INTERVAL);
        if (measured.abs().compareTo(SUSPICIOUS_SKEW) > 0) {
            LOG.warn("이 기계의 시계가 바이낸스와 {}ms 어긋나 있다. 서명은 보정해서 보내지만 "
                    + "캔들 구간과 체결 시각에도 같은 차이가 실린다.", measured.toMillis());
        }
    }

    private long fetchServerTime() {
        try {
            BinanceServerTime body = client.get()
                    .uri(SERVER_TIME)
                    .retrieve()
                    .body(BinanceServerTime.class);
            if (body == null || body.serverTime() == null) {
                throw new ExternalDataUnavailableException("바이낸스가 서버 시각을 주지 않았다");
            }
            return body.serverTime();
        } catch (RestClientException e) {
            throw new ExternalDataUnavailableException("바이낸스 서버 시각을 읽지 못했다", e);
        }
    }
}
