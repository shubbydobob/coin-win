package com.coinwin.trading.domain;

import com.coinwin.common.domain.DomainValues;
import com.coinwin.common.domain.Money;

/**
 * 안전장치가 판단하는 데 필요한 계좌의 전부.
 *
 * <p><b>포지션 목록이 아니라 수 네 개다.</b> 한계는 "무엇을 들고 있나" 가 아니라 "얼마나
 * 걸려 있고 얼마를 잃었나" 로 정해지고, 도메인이 포지션 타입을 알면 {@code trading} 이
 * {@code account} 를 참조하게 된다.
 *
 * @param equity 지금 계좌의 크기. 한계는 전부 이것의 비율이다
 * @param openPositions 열려 있는 포지션 수
 * @param realizedToday 오늘 실현 손익. <b>음수가 손실이다</b>
 * @param realizedTotal 봇이 켜진 뒤 누적 실현 손익. 음수가 손실이다
 */
public record AccountState(
        Money equity, int openPositions, Money realizedToday, Money realizedTotal) {

    public AccountState {
        DomainValues.required(equity, "계좌 크기");
        DomainValues.required(realizedToday, "오늘 실현 손익");
        DomainValues.required(realizedTotal, "누적 실현 손익");
        if (equity.value().signum() <= 0) {
            throw new InvalidOrderException("계좌 크기는 0 보다 커야 한다");
        }
        DomainValues.atLeast(openPositions, 0, "열린 포지션 수");
    }

    /** 아무것도 안 하고 있는 계좌. 손실 0 에서 시작한다. */
    public static AccountState flat(Money equity) {
        return new AccountState(equity, 0, Money.of("0"), Money.of("0"));
    }

    /** 오늘 잃은 돈. 이익이면 0 이다 — 한계는 손실만 본다. */
    public Money lostToday() {
        return loss(realizedToday);
    }

    /** 봇이 켜진 뒤 잃은 돈. 이익이면 0 이다. */
    public Money lostTotal() {
        return loss(realizedTotal);
    }

    private static Money loss(Money realized) {
        return realized.isNegative()
                ? Money.of(realized.value().negate())
                : Money.of("0");
    }
}
