package com.coinwin.position.domain;

import com.coinwin.common.domain.Money;

/**
 * 포지션 크기에 맞는 유지증거금 규칙을 준다. 청산가 공식의 유일한 외부 입력이다.
 *
 * <p>인자가 명목가인 이유는 거래소의 실제 유지증거금률이 <b>포지션 크기에 따라 달라지기</b>
 * 때문이다. Phase 1 의 {@code rate()} 는 인자가 없었고, 그래서 구간별 구현이 들어올 자리가
 * 없었다. Phase 3 에서 시그니처를 바꾼 것이 그 때문이다.
 *
 * <p>구현체가 하나뿐인데도 Phase 1 부터 인터페이스로 둔 근거는 {@code docs/adr/008} 이다.
 * 요지는 교체 가능성이 아니라 <b>의존 방향 보존</b>이었다 — 구간별 MMR 은 {@code market} 에서
 * 오는데, {@code position/domain} 이 그 구현을 직접 들고 있으면 {@code position → market}
 * 방향 의존이 생긴다. Phase 3 에서 두 번째 구현체가 실제로 도착했으므로 ADR 008 의 철회
 * 조건은 발동하지 않는다.
 */
public interface MaintenanceMarginPolicy {

    /**
     * @param notional 포지션 명목가 ({@code 수량 × 평단})
     */
    MaintenanceMargin requirementFor(Money notional);
}
