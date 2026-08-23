package com.coinwin.watch.domain;

import com.coinwin.common.domain.DomainValues;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 예정된 이벤트 묶음.
 *
 * <p>이 타입이 답하는 것은 둘이다 — <b>무엇이 다가오고 있나</b>와 <b>이 표가 낡았나</b>.
 *
 * <p>뒤쪽이 요점이다. 일정표는 커밋된 스냅샷이라 사람이 갱신하지 않으면 조용히 낡는다. 그리고
 * <b>낡은 일정표의 증상은 오류가 아니라 침묵이다</b> — 과거 이벤트만 남으면 화면이 그냥
 * 비어 보이고, 그것은 "다가오는 큰 일이 없다" 와 구별되지 않는다. 정확히 그 상태가 이 도구를
 * 만든 이유(사전 경고)를 통째로 무력화한다. {@code LeverageBrackets} 의 연속성 검사와 같은
 * 자리이고, 거기서 배운 것을 그대로 옮긴 것이다.
 */
public record EventCalendar(List<ScheduledEvent> events) {

    /** 마지막 이벤트가 이보다 가까우면 낡은 것으로 본다. */
    public static final Duration STALE_HORIZON = Duration.ofDays(90);

    public EventCalendar {
        events = List.copyOf(DomainValues.required(events, "이벤트 목록"));
    }

    /** 아직 오지 않은 것들. 가까운 순서다. */
    public List<ScheduledEvent> upcoming(Instant now) {
        DomainValues.required(now, "현재 시각");
        return events.stream()
                .filter(event -> event.isUpcoming(now))
                .sorted(Comparator.comparing(ScheduledEvent::at))
                .toList();
    }

    /** 지금 경고해야 할 것들. 비어 있는 것이 정상이다. */
    public List<ScheduledEvent> warnings(Instant now) {
        return upcoming(now).stream().filter(event -> event.needsWarning(now)).toList();
    }

    /**
     * 표가 낡았나. <b>비어 있어도 낡은 것이다</b> — 채운 적이 없는 것과 다 지나간 것을 화면이
     * 구별할 필요는 없고, 둘 다 사람이 손대야 한다는 뜻은 같다.
     */
    public boolean isStale(Instant now) {
        DomainValues.required(now, "현재 시각");
        return upcoming(now).stream()
                .map(ScheduledEvent::at)
                .max(Comparator.naturalOrder())
                .map(last -> Duration.between(now, last).compareTo(STALE_HORIZON) < 0)
                .orElse(true);
    }
}
