package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import com.coinwin.common.domain.Percentage;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * 전략이 뚫을 수 없는 벽.
 *
 * <p><b>검사를 서비스에 두지 않는다.</b> 서비스가 검사하면 새 전략을 붙이는 경로가 그 검사를
 * 건너뛸 수 있고, 자동으로 도는 루프에서 그 구멍은 계좌를 비운다. 판정을 값 객체가 가지면
 * 주문은 {@link RiskVerdict} 를 거치지 않고는 브로커에 닿을 수 없다.
 *
 * <p><b>기본값은 근거 있는 수가 아니라 자리표시자다</b>({@link #placeholder()}). 슬리피지
 * 기본값과 같은 자리이고, 화면에도 그렇게 적는다. 자리표시자여도 없는 것보다 낫다 — 없으면
 * 한계가 무한대다.
 *
 * @param maxNotionalMultiple 한 포지션 명목이 계좌의 몇 배까지인가
 * @param maxConcurrentPositions 동시에 열 수 있는 포지션 수
 * @param maxDailyLoss 하루에 잃을 수 있는 계좌 대비 비율. 넘으면 그날 진입이 멈춘다
 * @param maxTotalLoss 봇이 켜진 뒤 잃을 수 있는 누적 비율. 넘으면 사람이 켜야 다시 돈다
 */
public record RiskLimits(
        BigDecimal maxNotionalMultiple,
        int maxConcurrentPositions,
        Percentage maxDailyLoss,
        Percentage maxTotalLoss) {

    public RiskLimits {
        DomainValues.required(maxNotionalMultiple, "명목 배수 한계");
        DomainValues.required(maxDailyLoss, "일일 손실 한계");
        DomainValues.required(maxTotalLoss, "누적 손실 한계");
        if (maxNotionalMultiple.signum() <= 0) {
            throw new InvalidOrderException("명목 배수 한계는 0 보다 커야 한다");
        }
        DomainValues.atLeast(maxConcurrentPositions, 1, "동시 포지션 한계");
    }

    /**
     * 자리표시자 기본값. 명목 2배 · 동시 1개 · 하루 5% · 누적 20%.
     *
     * <p>2배인 이유는 <b>이 프로젝트를 만든 거래의 실효 배율이 3.15배였기</b> 때문이다
     * ({@code market-watch.md} § 0). 그보다 낮다는 것 말고 이 수를 고를 근거는 없다.
     */
    public static RiskLimits placeholder() {
        return new RiskLimits(
                new BigDecimal("2"), 1, Percentage.of("5"), Percentage.of("20"));
    }

    /**
     * 이 주문을 내도 되는가.
     *
     * <p><b>줄이는 주문은 언제나 통과한다.</b> 손실 한계에 걸렸다고 손절을 못 걸면 그 한계가
     * 정확히 막으려던 일이 일어난다 — 한계는 <b>새 위험</b>을 막는 것이지 이미 열린 위험을
     * 가두는 것이 아니다.
     */
    public RiskVerdict judge(OrderIntent intent, AccountState state) {
        DomainValues.required(intent, "주문 의도");
        DomainValues.required(state, "계좌 상태");
        if (intent.kind().reducesPosition()) {
            return new RiskVerdict.Allowed(intent);
        }
        return breach(intent, state)
                .<RiskVerdict>map(reason -> new RiskVerdict.Rejected(intent, reason))
                .orElseGet(() -> new RiskVerdict.Allowed(intent));
    }

    /**
     * 봇을 아예 세워야 하는가. <b>누적 손실만 본다.</b>
     *
     * <p>하루 손실은 날이 바뀌면 풀리지만 이것은 사람이 켜야 풀린다 — 누적으로 계좌의 20% 를
     * 잃었다는 것은 전략이 틀렸다는 뜻이고, 그 판단은 코드가 아니라 사람이 한다.
     */
    public boolean halts(AccountState state) {
        DomainValues.required(state, "계좌 상태");
        return exceeds(state.lostTotal(), maxTotalLoss, state.equity());
    }

    private Optional<String> breach(OrderIntent intent, AccountState state) {
        if (state.openPositions() >= maxConcurrentPositions) {
            return Optional.of("동시 포지션 한계 %d 개에 도달했다".formatted(maxConcurrentPositions));
        }
        if (intent.notional().isGreaterThan(state.equity().times(maxNotionalMultiple))) {
            return Optional.of("명목 %s 가 계좌의 %s배 한계를 넘는다"
                    .formatted(intent.notional().value().toPlainString(),
                            maxNotionalMultiple.toPlainString()));
        }
        if (exceeds(state.lostToday(), maxDailyLoss, state.equity())) {
            return Optional.of("오늘 손실이 계좌의 %s%% 한계를 넘었다"
                    .formatted(maxDailyLoss.value().toPlainString()));
        }
        if (halts(state)) {
            return Optional.of("누적 손실이 계좌의 %s%% 한계를 넘었다. 사람이 켜야 다시 돈다"
                    .formatted(maxTotalLoss.value().toPlainString()));
        }
        return Optional.empty();
    }

    private static boolean exceeds(Money lost, Percentage limit, Money equity) {
        return lost.isGreaterThan(limit.applyTo(equity));
    }
}
