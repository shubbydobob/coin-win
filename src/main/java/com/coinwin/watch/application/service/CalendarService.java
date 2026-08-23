package com.coinwin.watch.application.service;

import com.coinwin.watch.application.port.in.LoadCalendarUseCase;
import com.coinwin.watch.application.port.out.LoadEventCalendarPort;
import com.coinwin.watch.domain.EventCalendar;
import org.springframework.stereotype.Service;

/**
 * 스냅샷을 읽어 그대로 돌려준다.
 *
 * <p>판정은 전부 도메인이 한다 — 무엇이 다가오는지도, 경고할 때인지도, 표가 낡았는지도.
 * 이 서비스에는 규칙이 하나도 없다.
 *
 * <p>처음에는 어댑터를 인터페이스 없이 직접 들려 했다. "구현체가 하나뿐이면 포트를 만들지
 * 않는다" 는 원칙을 따른 것인데 <b>ArchUnit 규칙 2 와 6 이 거부했다.</b> 이유는
 * {@link LoadEventCalendarPort} 에 적어 두었다.
 */
@Service
public class CalendarService implements LoadCalendarUseCase {

    private final LoadEventCalendarPort snapshot;

    public CalendarService(LoadEventCalendarPort snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public EventCalendar calendar() {
        return snapshot.load();
    }
}
