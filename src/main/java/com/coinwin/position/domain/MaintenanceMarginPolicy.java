package com.coinwin.position.domain;

import com.coinwin.common.domain.Percentage;

/**
 * 유지증거금률(MMR) 을 제공한다. 청산가 공식의 유일한 외부 입력이다.
 *
 * <p>구현체가 하나뿐인데도 인터페이스로 두는 근거는 {@code docs/adr/008} 에 있다. 요지는
 * 교체 가능성이 아니라 <b>의존 방향 보존</b>이다. Phase 3 의 구간별 MMR 은 {@code market}
 * 모듈에서 오는데, {@code position/domain} 이 그 구현을 직접 들고 있으면
 * {@code position → market} 방향 의존이 생긴다.
 *
 * <p>Phase 3 이 끝났는데도 구현체가 하나뿐이면 ADR 008 의 "한계" 절에 따라 이 인터페이스를
 * 인라인한다.
 */
public interface MaintenanceMarginPolicy {

    /** 유지증거금률. 0.4% 는 {@code Percentage.of("0.4")} 로 표현된다. */
    Percentage rate();
}
