package com.coinwin.journal.domain;

import com.coinwin.position.domain.PositionPlan;
import java.time.Instant;

/**
 * 거래 한 건. 계획 → 체결 → 청산의 세 상태를 <b>세 타입</b>으로 나눈다.
 *
 * <p>한 타입에 {@code Optional<Money> realizedPnl} 을 두는 대신 이렇게 나눈 이유는 하나다 —
 * <b>청산되지 않은 거래에 손익을 물어볼 수 있는 경로 자체를 없애기 위해서다.</b> Optional 로
 * 두면 "아직 없음" 을 처리하는 분기가 집계·조회·표시 어디에나 생기고, 그중 한 곳에서
 * {@code orElse(ZERO)} 를 쓰는 순간 미청산 거래가 손익 0 인 거래로 집계에 섞인다.
 *
 * <p>{@link JournalSummary} 가 {@link ClosedTrade} 목록만 받는 것이 이 설계의 값이다.
 * 필터를 빠뜨릴 수가 없다 — 컴파일이 되지 않는다.
 *
 * <p>상태 전이는 앞 상태가 소유한다. {@link PlannedTrade#fill} 과 {@link OpenTrade#close} 뿐이고
 * 되돌리는 전이는 없다. 잘못 적었으면 기록을 고치는 것이지 상태를 되감는 것이 아니다.
 */
public sealed interface Trade permits PlannedTrade, OpenTrade, ClosedTrade {

    TradeId id();

    /** 진입 전에 세운 계획. 세 상태 모두가 들고 있다 — 체결과 대조할 기준이기 때문이다. */
    PositionPlan plan();

    /**
     * 계획을 세운 시각. 체결·청산을 지나도 바뀌지 않는다.
     *
     * <p>진입 시각({@code openedAt})과 다르다. 둘의 차이가 "계획을 세우고 얼마나 기다렸는가"
     * 이고, 그 값이 0 에 가까운 거래는 사실 계획 없이 들어간 거래다.
     */
    Instant plannedAt();
}
