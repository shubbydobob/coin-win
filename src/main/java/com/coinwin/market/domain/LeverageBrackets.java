package com.coinwin.market.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * 한 종목의 레버리지 구간표. 청산가 공식의 유일한 외부 입력이다.
 *
 * <p>이 표가 조용히 틀리면 청산가가 조용히 틀린다. 그래서 표의 <b>내적 정합성</b>을 불러오는
 * 시점에 검사한다. 스냅샷 파일이 손상되거나 거래소 응답이 잘리면 여기서 터진다.
 *
 * <p>핵심 검사는 {@link #assertMaintenanceMarginIsContinuous} 다. 구간 상한 {@code C} 에서
 * 두 구간의 유지증거금이 같으려면 {@code a(i+1) = a(i) + C × (r(i+1) - r(i))} 여야 한다.
 * 이 식이 깨지면 명목가가 1 USDT 늘었을 뿐인데 청산가가 계단처럼 뛴다.
 */
public record LeverageBrackets(Symbol symbol, List<LeverageBracket> brackets) {

    public LeverageBrackets {
        DomainValues.required(symbol, "종목");
        DomainValues.required(brackets, "레버리지 구간표");
        assertNotEmpty(brackets);
        assertFirstDeductionIsZero(brackets);
        assertAscending(brackets);
        assertMaintenanceMarginIsContinuous(brackets);
        brackets = List.copyOf(brackets);
    }

    /**
     * 명목가가 속한 구간.
     *
     * @throws NotionalExceedsBracketsException 마지막 구간의 상한마저 넘는 경우
     */
    public LeverageBracket forNotional(Money notional) {
        DomainValues.required(notional, "명목가");
        return brackets.stream()
                .filter(bracket -> bracket.covers(notional))
                .findFirst()
                .orElseThrow(() -> new NotionalExceedsBracketsException(
                        notional, brackets.getLast().notionalCap()));
    }

    private static void assertNotEmpty(List<LeverageBracket> brackets) {
        if (brackets.isEmpty()) {
            throw new InvalidMarketDataException("구간이 하나도 없는 레버리지 구간표는 성립하지 않는다");
        }
    }

    /** 첫 구간에 공제액이 붙으면 소액 포지션의 유지증거금이 음수가 된다. */
    private static void assertFirstDeductionIsZero(List<LeverageBracket> brackets) {
        if (brackets.getFirst().maintenanceAmount().value().signum() != 0) {
            throw new InvalidMarketDataException("첫 구간의 유지증거금 공제액은 0 이어야 한다: "
                    + brackets.getFirst().maintenanceAmount().value());
        }
    }

    private static void assertAscending(List<LeverageBracket> brackets) {
        for (int i = 1; i < brackets.size(); i++) {
            LeverageBracket previous = brackets.get(i - 1);
            LeverageBracket current = brackets.get(i);
            if (!current.notionalCap().isGreaterThan(previous.notionalCap())) {
                throw new InvalidMarketDataException(
                        "구간 상한은 오름차순이어야 한다: 구간 " + current.tier());
            }
            if (!current.maintenanceMarginRate().isGreaterThan(previous.maintenanceMarginRate())) {
                throw new InvalidMarketDataException(
                        "유지증거금률은 오름차순이어야 한다: 구간 " + current.tier());
            }
        }
    }

    private static void assertMaintenanceMarginIsContinuous(List<LeverageBracket> brackets) {
        for (int i = 1; i < brackets.size(); i++) {
            LeverageBracket previous = brackets.get(i - 1);
            LeverageBracket current = brackets.get(i);
            Money expected = deductionContinuingFrom(previous, current);
            if (!expected.equals(current.maintenanceAmount())) {
                throw new InvalidMarketDataException(
                        "구간 %d 에서 유지증거금이 끊긴다: 공제액 %s 여야 하는데 %s 다".formatted(
                                current.tier(),
                                expected.value().toPlainString(),
                                current.maintenanceAmount().value().toPlainString()));
            }
        }
    }

    /** {@code a(i+1) = a(i) + C × (r(i+1) - r(i))}. */
    private static Money deductionContinuingFrom(
            LeverageBracket previous, LeverageBracket current) {
        BigDecimal rateStep = current.maintenanceMarginRate().asFraction()
                .subtract(previous.maintenanceMarginRate().asFraction());
        return Money.of(previous.maintenanceAmount().value()
                .add(previous.notionalCap().value().multiply(rateStep)));
    }
}
