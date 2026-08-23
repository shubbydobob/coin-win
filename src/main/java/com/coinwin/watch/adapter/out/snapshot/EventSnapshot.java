package com.coinwin.watch.adapter.out.snapshot;

import com.coinwin.watch.domain.EventCalendar;
import com.coinwin.watch.domain.EventKind;
import com.coinwin.watch.domain.Importance;
import com.coinwin.watch.domain.ScheduledEvent;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code watch/scheduled-events.json} 의 모양.
 *
 * <p>파일에 {@code _source} 가 함께 들어 있다 — 어느 기관 페이지에서 언제 받았는지. 그것을
 * 읽지 않고 무시하는 이유는 <b>사람이 읽으라고 적은 것</b>이기 때문이다. 일정을 갱신하는
 * 사람이 출처를 다시 찾지 않아도 되게 하는 것이 목적이고, 코드가 쓸 값은 아니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EventSnapshot(List<Entry> events) {

    EventCalendar toDomain() {
        return new EventCalendar(events.stream().map(Entry::toDomain).toList());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String kind, Instant at, String title, String importance) {

        ScheduledEvent toDomain() {
            return new ScheduledEvent(
                    EventKind.valueOf(kind), at, title, Importance.valueOf(importance));
        }
    }
}
