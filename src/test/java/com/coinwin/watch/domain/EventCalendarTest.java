package com.coinwin.watch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 예정 이벤트와 사전 경고.
 *
 * <p>시각을 전부 인자로 넘긴다. 도메인이 {@code Instant.now()} 를 부르면 이 테스트가 오늘
 * 날짜에 묶이고, 내일 다른 답을 낸다.
 */
class EventCalendarTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Test
    void 지난_이벤트는_다가오는_것에_들지_않는다() {
        EventCalendar calendar = new EventCalendar(List.of(어제(), 내일()));

        assertThat(calendar.upcoming(NOW)).extracting(ScheduledEvent::title).containsExactly("내일");
    }

    @Test
    void 다가오는_것은_가까운_순서다() {
        EventCalendar calendar = new EventCalendar(List.of(뒤에(10), 뒤에(1), 뒤에(5)));

        assertThat(calendar.upcoming(NOW))
                .extracting(event -> event.until(NOW).toDays())
                .containsExactly(1L, 5L, 10L);
    }

    /** 정각 경계를 못 박는다 — 사흘 정각은 경고이고 그보다 한 시간 뒤는 아니다. */
    @Test
    void 경고는_중요한_것이_사흘_안일_때만_뜬다() {
        assertThat(뒤에(3).needsWarning(NOW)).isTrue();
        assertThat(중요하지_않은(뒤에(1)).needsWarning(NOW)).isFalse();

        ScheduledEvent 사흘_한시간_뒤 = new ScheduledEvent(
                EventKind.FOMC, NOW.plus(Duration.ofDays(3).plusHours(1)), "그 밖", Importance.HIGH);
        assertThat(사흘_한시간_뒤.needsWarning(NOW)).isFalse();
    }

    /**
     * <b>어제 끝난 FOMC 가 계속 경고를 띄우면 안 된다.</b> 지난 이벤트를 목록에 남겨 두는
     * 구현에서 이 검사가 빠지면 정확히 그렇게 된다.
     */
    @Test
    void 이미_지난_것에는_경고하지_않는다() {
        assertThat(어제().needsWarning(NOW)).isFalse();
        assertThat(new EventCalendar(List.of(어제())).warnings(NOW)).isEmpty();
    }

    @Test
    void 경고할_것이_없는_것이_정상이다() {
        assertThat(new EventCalendar(List.of(뒤에(30))).warnings(NOW)).isEmpty();
    }

    /**
     * <b>낡은 일정표의 증상은 오류가 아니라 침묵이다.</b> 과거 이벤트만 남으면 화면이 그냥
     * 비어 보이고, 그것은 "다가오는 큰 일이 없다" 와 구별되지 않는다.
     */
    @Test
    void 마지막_이벤트가_90일_안이면_낡은_것이다() {
        assertThat(new EventCalendar(List.of(뒤에(89))).isStale(NOW)).isTrue();
        assertThat(new EventCalendar(List.of(뒤에(91))).isStale(NOW)).isFalse();
    }

    @Test
    void 비어_있어도_낡은_것이다() {
        assertThat(new EventCalendar(List.of()).isStale(NOW)).isTrue();
        assertThat(new EventCalendar(List.of(어제())).isStale(NOW)).isTrue();
    }

    @Test
    void 목록과_시각은_null_일_수_없다() {
        assertThatThrownBy(() -> new EventCalendar(null)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new EventCalendar(List.of()).upcoming(null))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ScheduledEvent(EventKind.CPI, null, "x", Importance.HIGH))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 넘어온_목록이_바뀌어도_일정은_그대로다() {
        List<ScheduledEvent> 원본 = new java.util.ArrayList<>(List.of(뒤에(1)));
        EventCalendar calendar = new EventCalendar(원본);

        원본.clear();

        assertThat(calendar.events()).hasSize(1);
    }

    private static ScheduledEvent 뒤에(int days) {
        return new ScheduledEvent(
                EventKind.FOMC, NOW.plus(Duration.ofDays(days)), "D+" + days, Importance.HIGH);
    }

    private static ScheduledEvent 어제() {
        return new ScheduledEvent(
                EventKind.FOMC, NOW.minus(Duration.ofDays(1)), "어제", Importance.HIGH);
    }

    private static ScheduledEvent 내일() {
        return new ScheduledEvent(
                EventKind.CPI, NOW.plus(Duration.ofDays(1)), "내일", Importance.HIGH);
    }

    private static ScheduledEvent 중요하지_않은(ScheduledEvent event) {
        return new ScheduledEvent(event.kind(), event.at(), event.title(), Importance.NORMAL);
    }
}
