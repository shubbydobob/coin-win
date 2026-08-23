package com.coinwin.watch.application.port.out;

import com.coinwin.watch.domain.EventCalendar;

/**
 * 예정 이벤트를 읽어 오는 곳.
 *
 * <p><b>구현체가 하나뿐인데도 포트를 두는 이유</b>는 아키텍처가 그것을 강제하기 때문이다.
 * 설계 문서에는 "구현체가 하나면 포트를 만들지 않는다" 는 원칙에 따라 서비스가 어댑터를 직접
 * 들도록 적었는데, <b>ArchUnit 규칙 2 와 6 이 그것을 거부했다</b> — 계층 의존은
 * {@code adapter → application → domain} 한 방향이고, {@code adapter.out} 의 구현체는 반드시
 * {@code application.port.out} 인터페이스를 구현해야 한다.
 *
 * <p>규칙이 옳다. 서비스가 어댑터를 직접 들면 "스냅샷에서 읽는다" 는 것이 응용 계층의 사실이
 * 되고, 나중에 출처가 바뀔 때 서비스가 함께 바뀐다. {@code ClasspathLeverageBracketAdapter}
 * 도 같은 이유로 {@code LoadLeverageBracketsPort} 를 구현하고 있었다 — 그 선례를 먼저 보지
 * 않고 설계를 쓴 것이 이번의 실수다.
 */
public interface LoadEventCalendarPort {

    EventCalendar load();
}
