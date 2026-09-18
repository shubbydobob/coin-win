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
     *
     * <p><b>일부만 닫을 수 있다.</b> R3 가 절반만 가져가므로 이것이 없으면 1차 익절이
     * 포지션을 통째로 닫고, 그러면 <b>R4 가 옮길 손절도 남은 절반도 존재하지 않는다</b> —
     * 시나리오 테스트가 그 거짓 초록을 잡았다.
     *
     * @param part 닫을 수량. <b>비어 있으면 전량</b>이다. 남은 것보다 크면 남은 만큼만 닫는다
     */
    public PaperLedger closed(Price fill, Optional<Quantity> part, Money fees, LocalDate at) {
        DomainValues.required(fill, "체결가");
        DomainValues.required(part, "닫을 수량");
        DomainValues.required(fees, "수수료");
        DomainValues.required(at, "체결 날짜");
        return position.map(open -> reduce(open, new Closing(fill, closing(open, part), fees), at))
                .orElse(this);
    }

    /** 전량을 닫는다. */
    public PaperLedger closed(Price fill, Money fees, LocalDate at) {
        return closed(fill, Optional.empty(), fees, at);
    }

    /** 실제로 닫히는 수량. 남은 것보다 크게 주문돼 있어도 남은 만큼만 닫힌다. */
    private static Quantity closing(BotPosition open, Optional<Quantity> part) {
        return part.filter(asked -> asked.value().compareTo(open.quantity().value()) < 0)
                .orElse(open.quantity());
    }

    /**
     * 한 번의 청산이 말하는 것 — 얼마에 · 얼마나 · 비용은 얼마.
     *
     * <p>셋을 묶은 이유는 파라미터 한계(4) 때문만이 아니다. 셋은 <b>같은 체결의 세 얼굴</b>
     * 이라 따로 다니면 어느 체결의 수수료인지 헷갈릴 자리가 생긴다.
     */
    private record Closing(Price fill, Quantity part, Money fees) {
    }

    /**
     * 일부(또는 전부)를 닫는다.
     *
     * <p>남은 수량이 0 이면 포지션이 사라지고, 아니면 <b>같은 평단으로 줄어든 포지션</b>이
     * 남는다 — 평단은 진입에서 정해진 것이라 일부 청산으로 달라지지 않는다.
     */
    private PaperLedger reduce(BotPosition open, Closing closing, LocalDate at) {
        Money profit = pnlOf(open, closing.fill(), closing.part()).minus(closing.fees());
        BigDecimal left = open.quantity().value().subtract(closing.part().value());
        Optional<BotPosition> remaining = left.signum() <= 0
                ? Optional.<BotPosition>empty()
                : Optional.of(new BotPosition(
                        open.direction(), Quantity.of(left.toPlainString()), open.entry()));
        Money todayBefore = at.equals(day) ? realizedToday : Money.of("0");
        return new PaperLedger(startingEquity, remaining,
                realizedTotal.plus(profit), todayBefore.plus(profit), at);
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
     * 비용을 빼기 전 손익. {@code (청산가 − 평단) × 닫는 수량}, 숏이면 부호가 뒤집힌다.
     *
     * <p>{@code ClosedTrade.grossPnl()} 과 같은 식이다. 갈라지면 모의 기록과 매매 기록을
     * 나란히 놓을 수 없다. 수량은 <b>닫는 만큼</b>이지 보유 전량이 아니다.
     */
    private static Money pnlOf(BotPosition open, Price exit, Quantity part) {
        BigDecimal moved = exit.value().subtract(open.entry().value());
        BigDecimal signed = open.direction() == Direction.LONG ? moved : moved.negate();
        return Money.of(signed.multiply(part.value()));
    }
}
