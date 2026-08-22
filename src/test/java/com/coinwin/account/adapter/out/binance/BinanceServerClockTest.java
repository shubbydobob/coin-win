package com.coinwin.account.adapter.out.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 거래소 시계 보정의 규칙.
 *
 * <p>이 클래스가 생긴 이유가 실제 사고다 — 개발 기계가 33초 앞서 있어 서명 요청이 전부
 * {@code -1021}("Timestamp for this request was ahead of the server's time") 로 거절됐다.
 *
 * <p><b>보정의 부호가 반대면 증상이 그대로 남는다.</b> 오히려 두 배로 어긋난다. 그래서 방향을
 * 앞뒤 양쪽으로 못 박는다.
 */
class BinanceServerClockTest {

    /** 우리 시계가 가리키는 시각. 거래소 시각을 이 값의 앞뒤에 놓는다. */
    private static final Instant LOCAL_NOW = Instant.parse("2026-08-23T02:00:00Z");

    private FakeExchange exchange;

    @BeforeEach
    void openExchange() {
        exchange = new FakeExchange();
    }

    @AfterEach
    void closeExchange() {
        exchange.close();
    }

    /**
     * <b>우리 시계가 앞서 있으면 뒤로 당긴다.</b> 실제로 겪은 방향이다 — 33초 앞선 기계가
     * "미래에서 온 요청" 을 보내 거절당했다.
     */
    @Test
    void 우리_시계가_앞서_있으면_거래소_시각으로_당긴다() {
        Instant serverNow = LOCAL_NOW.minusSeconds(33);
        exchange.enqueueServerTime(serverNow.toEpochMilli());

        assertThat(clockAt(LOCAL_NOW).now()).isEqualTo(serverNow);
    }

    @Test
    void 우리_시계가_뒤처져_있으면_거래소_시각으로_민다() {
        Instant serverNow = LOCAL_NOW.plusSeconds(12);
        exchange.enqueueServerTime(serverNow.toEpochMilli());

        assertThat(clockAt(LOCAL_NOW).now()).isEqualTo(serverNow);
    }

    @Test
    void 시계가_맞으면_그대로_둔다() {
        exchange.enqueueServerTime(LOCAL_NOW.toEpochMilli());

        assertThat(clockAt(LOCAL_NOW).now()).isEqualTo(LOCAL_NOW);
    }

    /** 서명이 필요 없는 공개 엔드포인트를 쓴다. 시각을 알려면 키가 있어야 한다면 순환이다. */
    @Test
    void 서명_없는_공개_엔드포인트로_묻는다() {
        exchange.enqueueServerTime(LOCAL_NOW.toEpochMilli());

        clockAt(LOCAL_NOW).now();

        assertThat(exchange.requestedPaths()).containsExactly("/fapi/v1/time");
    }

    /**
     * <b>요청마다 시각을 묻지 않는다.</b> 물으면 개인 엔드포인트 한 번에 왕복이 두 번이 되고,
     * 그 왕복 자체가 지연이라 정확도도 나아지지 않는다.
     */
    @Test
    void 차이는_한_번만_재고_재사용한다() {
        exchange.enqueueServerTime(LOCAL_NOW.minusSeconds(33).toEpochMilli());

        BinanceServerClock clock = clockAt(LOCAL_NOW);
        clock.now();
        clock.now();
        clock.now();

        assertThat(exchange.requestCount()).isEqualTo(1);
    }

    /** 재동기 주기가 지나면 다시 잰다. 시계는 천천히 흐르지만 흐르기는 한다. */
    @Test
    void 재동기_주기가_지나면_다시_잰다() {
        MovingClock local = new MovingClock(LOCAL_NOW);
        BinanceServerClock clock = new BinanceServerClock(exchange.client(), local);

        exchange.enqueueServerTime(LOCAL_NOW.minusSeconds(33).toEpochMilli());
        clock.now();

        local.advance(Duration.ofHours(1));
        exchange.enqueueServerTime(local.instant().minusSeconds(40).toEpochMilli());
        Instant after = clock.now();

        assertThat(exchange.requestCount()).isEqualTo(2);
        assertThat(after).isEqualTo(local.instant().minusSeconds(40));
    }

    /**
     * 시각을 못 읽으면 <b>조용히 0 으로 두지 않는다.</b> 보정 없이 보내면 어차피 거절당하고,
     * 그 거절의 원인이 "시계" 인지 "키" 인지 구분되지 않는다.
     */
    @Test
    void 거래소_시각을_읽지_못하면_던진다() {
        exchange.enqueue(500, "{\"msg\":\"고장\"}");

        assertThatThrownBy(() -> clockAt(LOCAL_NOW).now())
                .isInstanceOf(ExternalDataUnavailableException.class);
    }

    @Test
    void 응답에_시각이_없으면_던진다() {
        exchange.enqueue(200, "{}");

        assertThatThrownBy(() -> clockAt(LOCAL_NOW).now())
                .isInstanceOf(ExternalDataUnavailableException.class);
    }

    private BinanceServerClock clockAt(Instant local) {
        return new BinanceServerClock(exchange.client(), Clock.fixed(local, ZoneOffset.UTC));
    }

    /** 재동기 주기를 넘기려면 우리 시계가 흘러야 한다. {@code Clock.fixed} 로는 안 된다. */
    private static final class MovingClock extends Clock {

        private Instant at;

        private MovingClock(Instant at) {
            this.at = at;
        }

        void advance(Duration by) {
            at = at.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return at;
        }
    }
}
