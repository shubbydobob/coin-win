package com.coinwin.journal.adapter.out.persistence;

/**
 * 저장된 거래의 상태. {@code Trade} 의 세 하위 타입에 하나씩 대응한다.
 *
 * <p>관계형 테이블에는 상속이 없으므로 어느 타입이었는지를 컬럼으로 남긴다. 채워진 칸으로
 * 추론할 수도 있지만("청산가가 있으면 닫힌 것") 그러면 상태 판정이 컬럼 여러 개의 조합에
 * 흩어지고, 조회 조건에 상태를 쓸 수도 없다.
 *
 * <p>도메인이 아니라 어댑터에 두는 이유는 이것이 <b>저장 형식의 일부</b>이기 때문이다.
 * 도메인에서 상태는 타입이지 값이 아니다.
 */
enum TradeState {
    PLANNED,
    OPEN,
    CLOSED
}
