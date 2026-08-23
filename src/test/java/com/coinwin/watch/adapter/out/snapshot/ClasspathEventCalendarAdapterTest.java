package com.coinwin.watch.adapter.out.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.watch.domain.EventCalendar;
import com.coinwin.watch.domain.EventKind;
import com.coinwin.watch.domain.Importance;
import com.coinwin.watch.domain.ScheduledEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 커밋된 일정 스냅샷이 읽히고, <b>낡지 않았는가</b>.
 *
 * <p>뒤쪽이 이 테스트의 요점이다. 일정표는 사람이 갱신하지 않으면 조용히 낡는데,
 * <b>낡은 일정표의 증상은 오류가 아니라 침묵</b>이다 — 과거 이벤트만 남으면 화면이 그냥
 * 비어 보이고 그것은 "다가오는 큰 일이 없다" 와 구별되지 않는다. 정확히 그 상태가 이 기능을
 * 만든 이유(사전 경고)를 통째로 무력화한다.
 */
class ClasspathEventCalendarAdapterTest {

    private final ClasspathEventCalendarAdapter adapter = new ClasspathEventCalendarAdapter();

    @Test
    void 스냅샷을_읽어_일정으로_옮긴다() {
        EventCalendar calendar = adapter.load();

        assertThat(calendar.events()).isNotEmpty();
        assertThat(calendar.events()).extracting(ScheduledEvent::kind).contains(EventKind.FOMC);
        assertThat(calendar.events()).allSatisfy(event -> {
            assertThat(event.at()).isNotNull();
            assertThat(event.title()).isNotBlank();
        });
    }

    /** 계기가 된 사건이 QRA 발표였으므로, 그 종류가 목록에 있는지를 못 박는다. */
    @Test
    void 재무부_자금조달계획이_목록에_있다() {
        assertThat(adapter.load().events())
                .filteredOn(event -> event.kind() == EventKind.QRA)
                .isNotEmpty();
    }

    /**
     * <b>커밋된 일정표가 오늘 기준으로 낡지 않았는가.</b> 마지막 이벤트가 90일 안이면 빌드가
     * 실패한다. {@code LeverageBrackets} 의 연속성 검사와 같은 자리다.
     *
     * <p>실패했다면 고칠 것은 코드가 아니라 {@code watch/scheduled-events.json} 이다 —
     * 파일 안의 {@code _source} 에 어느 기관 페이지에서 받았는지 적혀 있다.
     */
    @Test
    void 커밋된_일정표가_낡지_않았다() {
        assertThat(adapter.load().isStale(Clock.systemUTC().instant()))
                .describedAs("일정표가 낡았다 — watch/scheduled-events.json 의 _source 를 보고 갱신한다")
                .isFalse();
    }

    /**
     * 그리고 그 검사가 <b>실제로 발동하는지</b> 확인한다. 통과하는 것처럼 보이면서 아무것도
     * 매칭하지 않는 검사를 잡는 것이 이 저장소가 위반 픽스처를 상주시키는 이유다.
     */
    @Test
    void 낡은_일정표는_낡음으로_잡힌다() {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        EventCalendar 지나간것만 = new EventCalendar(List.of(new ScheduledEvent(
                EventKind.FOMC, Instant.parse("2026-08-01T00:00:00Z"), "지난 회의", Importance.HIGH)));

        assertThat(지나간것만.isStale(now)).isTrue();
    }
}
