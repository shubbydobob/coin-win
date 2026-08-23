package com.coinwin.watch.adapter.out.snapshot;

import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.watch.application.port.out.LoadEventCalendarPort;
import com.coinwin.watch.domain.EventCalendar;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커밋된 스냅샷에서 예정 이벤트를 읽는다.
 *
 * <p>{@code ClasspathLeverageBracketAdapter} 와 같은 자리다. 구현체가 하나뿐인데도 포트를
 * 갖는 이유는 {@link LoadEventCalendarPort} 에 적어 두었다 — ArchUnit 이 설계를 반박했다.
 *
 * <p>자동 갱신 경로를 만들지 않은 이유는 <b>출처가 셋이고 전부 기관 웹페이지</b>이기 때문이다.
 * 연준·BLS·재무부가 API 를 주지 않으므로 긁어야 하는데, 긁기가 조용히 깨지면 <b>일정표가 비고
 * 화면은 "다가오는 큰 일이 없다" 처럼 보인다.</b> 파일 교체는 사람이 하고, 낡았는지는
 * {@link EventCalendar#isStale} 이 매번 말한다.
 */
@Component
public class ClasspathEventCalendarAdapter implements LoadEventCalendarPort {

    private static final String LOCATION = "watch/scheduled-events.json";

    private final JsonMapper json = new JsonMapper();

    @Override
    public EventCalendar load() {
        try (InputStream snapshot = getClass().getClassLoader().getResourceAsStream(LOCATION)) {
            if (snapshot == null) {
                throw new ExternalDataUnavailableException("일정 스냅샷이 없다: " + LOCATION, null);
            }
            return json.readValue(snapshot, EventSnapshot.class).toDomain();
        } catch (IOException e) {
            throw new ExternalDataUnavailableException("일정 스냅샷을 읽지 못했다: " + LOCATION, e);
        }
    }
}
