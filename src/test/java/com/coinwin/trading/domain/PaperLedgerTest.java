package com.coinwin.trading.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.position.domain.Direction;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 봇 자신의 장부.
 *
 * <p>이것이 없으면 안전장치가 헛돈다 — 열린 포지션을 세지 못하면 동시 포지션 한계가 언제나
 * 통과하고, 실현 손익을 모르면 손실 한계가 영원히 0 에 머문다.
 */
class PaperLedgerTest {

    private static final Money EQUITY = Money.of("800");
    private static final LocalDate DAY = LocalDate.parse("2026-09-18");
    private static final LocalDate NEXT = LocalDate.parse("2026-09-19");

    private final PaperLedger fresh = PaperLedger.start(EQUITY, DAY);

    @Test
    void 처음에는_포지션도_손익도_없다() {
        assertThat(fresh.position()).isEmpty();
        assertThat(fresh.state().openPositions()).isZero();
        assertThat(fresh.state().equity()).isEqualTo(EQUITY);
    }

    @Test
    void 열면_포지션이_하나_생긴다() {
        PaperLedger open = fresh.opened(Direction.LONG, Quantity.of("0.1"), Price.of("78000"));

        assertThat(open.state().openPositions()).isEqualTo(1);
        assertThat(open.position()).map(BotPosition::entry).contains(Price.of("78000"));
    }

    /** 동시 하나가 이 장부의 전제다. 둘째가 들어오면 수량이 어느 쪽 것인지 알 수 없어진다. */
    @Test
    void 이미_열려_있으면_또_열_수_없다() {
        PaperLedger open = fresh.opened(Direction.LONG, Quantity.of("0.1"), Price.of("78000"));

        assertThatThrownBy(() ->
                open.opened(Direction.LONG, Quantity.of("0.1"), Price.of("79000")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("동시 하나");
    }

    /** 롱 78,000 → 80,000, 0.1 개면 +200. 수수료 1 을 빼면 199. */
    @Test
    void 롱은_오르면_이익이다() {
        PaperLedger closed = fresh
                .opened(Direction.LONG, Quantity.of("0.1"), Price.of("78000"))
                .closed(Price.of("80000"), Money.of("1"), DAY);

        assertThat(closed.realizedTotal()).isEqualTo(Money.of("199"));
        assertThat(closed.state().equity()).isEqualTo(Money.of("999"));
        assertThat(closed.position()).isEmpty();
    }

    /** 숏은 부호가 뒤집힌다. 78,000 → 80,000 이면 −200 이다. */
    @Test
    void 숏은_오르면_손실이다() {
        PaperLedger closed = fresh
                .opened(Direction.SHORT, Quantity.of("0.1"), Price.of("78000"))
                .closed(Price.of("80000"), Money.of("0"), DAY);

        assertThat(closed.realizedTotal()).isEqualTo(Money.of("-200"));
        assertThat(closed.state().lostTotal()).isEqualTo(Money.of("200"));
    }

    /** 없는 포지션을 닫는 것은 실패가 아니라 이미 원하던 상태다. */
    @Test
    void 열린_것이_없으면_닫아도_아무_일도_없다() {
        assertThat(fresh.closed(Price.of("80000"), Money.of("1"), DAY)).isEqualTo(fresh);
    }

    /**
     * <b>날이 바뀌면 오늘 치가 0 에서 다시 센다.</b> 일일 한계는 하루가 지나면 풀리는 것이
     * 정의이고, 그 초기화를 잊으면 한 번 걸린 봇이 영원히 안 들어간다.
     */
    @Test
    void 날이_바뀌면_오늘_손익이_다시_센다() {
        PaperLedger yesterday = fresh
                .opened(Direction.SHORT, Quantity.of("0.1"), Price.of("78000"))
                .closed(Price.of("80000"), Money.of("0"), DAY);

        PaperLedger today = yesterday
                .opened(Direction.LONG, Quantity.of("0.1"), Price.of("78000"))
                .closed(Price.of("77000"), Money.of("0"), NEXT);

        assertThat(today.realizedToday()).isEqualTo(Money.of("-100"));
        assertThat(today.realizedTotal()).isEqualTo(Money.of("-300"));
    }

    @Test
    void 같은_날이면_오늘_손익이_쌓인다() {
        PaperLedger first = fresh
                .opened(Direction.LONG, Quantity.of("0.1"), Price.of("78000"))
                .closed(Price.of("77000"), Money.of("0"), DAY);

        PaperLedger second = first
                .opened(Direction.LONG, Quantity.of("0.1"), Price.of("78000"))
                .closed(Price.of("77500"), Money.of("0"), DAY);

        assertThat(second.realizedToday()).isEqualTo(Money.of("-150"));
    }
}
