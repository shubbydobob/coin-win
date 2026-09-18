package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Price;
import com.coinwin.common.domain.Quantity;
import com.coinwin.position.domain.Direction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 봇 자신의 장부. <b>모의 모드에서 "계좌" 라고 부르는 것의 전부다.</b>
 *
 * <p>이것이 없으면 안전장치가 헛돈다 — 열린 포지션을 세지 못하면 동시 포지션 한계가 언제나
 * 통과하고, 실현 손익을 모르면 일일·누적 손실 한계가 영원히 0 에 머문다. <b>전략을 꽂기 전에
 * 이 자리가 먼저 있어야 하는 이유가 그것이다.</b>
 *
 * <p><b>불변이다.</b> 체결마다 새 장부를 낸다 — 제자리에서 고치면 사이클 중간 상태가 밖으로
 * 새고, 그 순간 "이 사이클이 본 계좌" 가 하나가 아니게 된다.
 *
 * @param startingEquity 시작 자산. 지금 자산은 여기에 누적 실현 손익을 더한 것이다
 * @param position 열려 있는 포지션. 동시 하나만 갖는다 — 한계의 기본값과 같은 전제다
 * @param realizedTotal 누적 실현 손익. <b>음수가 손실이다</b>
 * @param realizedToday 오늘 실현 손익. 날이 바뀌면 0 으로 돌아간다
 * @param day {@code realizedToday} 가 가리키는 날짜. 이것이 없으면 날이 바뀐 것을 알 수 없다
 */
public record PaperLedger(
        Money startingEquity,
        Optional<BotPosition> position,
        Money realizedTotal,
        Money realizedToday,
        LocalDate day) {

    public PaperLedger {
        DomainValues.required(startingEquity, "시작 자산");
        DomainValues.required(position, "포지션");
        DomainValues.required(realizedTotal, "누적 실현 손익");
        DomainValues.required(realizedToday, "오늘 실현 손익");
        DomainValues.required(day, "날짜");
    }

    public static PaperLedger start(Money startingEquity, LocalDate day) {
        Money zero = Money.of("0");
        return new PaperLedger(startingEquity, Optional.empty(), zero, zero, day);
    }

    /** 포지션을 연다. 이미 열려 있으면 거부한다 — 동시 하나가 이 장부의 전제다. */
    public PaperLedger opened(Direction direction, Quantity quantity, Price fill) {
        if (position.isPresent()) {
            throw new InvalidOrderException("이미 열린 포지션이 있다 — 장부는 동시 하나만 든다");
        }
        return new PaperLedger(startingEquity,
                Optional.of(new BotPosition(direction, quantity, fill)),
                realizedTotal, realizedToday, day);
    }

    /**
     * 포지션을 닫고 손익을 실현한다. 열린 것이 없으면 그대로 둔다 — 없는 포지션을 닫는
     * 주문이 나가는 것은 <b>실패가 아니라 이미 원하던 상태</b>다.
     */
    public PaperLedger closed(Price fill, Money fees, LocalDate at) {
        DomainValues.required(fill, "체결가");
        DomainValues.required(fees, "수수료");
        DomainValues.required(at, "체결 날짜");
        return position
                .map(open -> realize(pnlOf(open, fill).minus(fees), at))
                .orElse(this);
    }

    /** 안전장치가 보는 네 수. 지금 자산은 시작 자산에 누적 실현을 더한 것이다. */
    public AccountState state() {
        return new AccountState(
                startingEquity.plus(realizedTotal),
                position.isPresent() ? 1 : 0,
                realizedToday,
                realizedTotal);
    }

    /**
     * 실현. <b>날이 바뀌면 오늘 치가 0 에서 다시 센다</b> — 일일 한계는 하루가 지나면
     * 풀리는 것이 정의이고, 그 초기화를 잊으면 한 번 걸린 봇이 영원히 안 들어간다.
     */
    private PaperLedger realize(Money profit, LocalDate at) {
        Money todayBefore = at.equals(day) ? realizedToday : Money.of("0");
        return new PaperLedger(startingEquity, Optional.empty(),
                realizedTotal.plus(profit), todayBefore.plus(profit), at);
    }

    /**
     * 비용을 빼기 전 손익. {@code (청산가 − 평단) × 수량}, 숏이면 부호가 뒤집힌다.
     *
     * <p>{@code ClosedTrade.grossPnl()} 과 같은 식이다. 갈라지면 모의 기록과 매매 기록을
     * 나란히 놓을 수 없다.
     */
    private static Money pnlOf(BotPosition open, Price exit) {
        BigDecimal moved = exit.value().subtract(open.entry().value());
        BigDecimal signed = open.direction() == Direction.LONG ? moved : moved.negate();
        return Money.of(signed.multiply(open.quantity().value()));
    }
}
