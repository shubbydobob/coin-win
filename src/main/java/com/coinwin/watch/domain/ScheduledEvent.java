package com.coinwin.watch.domain;

import com.coinwin.common.domain.DomainValues;
import java.time.Duration;
import java.time.Instant;

/**
 * 날짜가 정해진 이벤트 하나.
 *
 * <p><b>남은 시간을 저장하지 않는다.</b> {@code at} 과 지금으로 매번 계산한다 — 파생값을
 * 저장하지 않는 것은 {@code journal} 이 진입 시각·평단·손익에 대해 세운 규칙과 같다.
 *
 * <p><b>{@code now} 를 인자로 받는다.</b> 안에서 {@code Instant.now()} 를 부르면 테스트가
 * 시계에 묶인다 — {@code crossCheck} 가 그 때문에 매일 다른 표를 내던 것을 로드맵이 이미
 * 적어 두었다.
 *
 * <p><b>이 타입은 방향을 말하지 않는다.</b> 경고가 뜻하는 것은 "그날은 명목을 줄여라" 이지
 * "오를 것이다" 가 아니다. 규모에 대한 조언과 방향에 대한 예측은 다른 것이고, 뒤쪽은
 * {@code scope.md} 가 하지 않기로 한 일이다.
 */
public record ScheduledEvent(EventKind kind, Instant at, String title, Importance importance) {

    /** 이만큼 안으로 들어오면 경고한다. */
    public static final Duration WARNING_WINDOW = Duration.ofDays(3);

    public ScheduledEvent {
        DomainValues.required(kind, "이벤트 종류");
        DomainValues.required(at, "이벤트 시각");
        DomainValues.required(title, "이벤트 제목");
        DomainValues.required(importance, "중요도");
    }

    /** 지금부터 남은 시간. 이미 지났으면 음수다. */
    public Duration until(Instant now) {
        DomainValues.required(now, "현재 시각");
        return Duration.between(now, at);
    }

    public boolean isUpcoming(Instant now) {
        return !until(now).isNegative();
    }

    /**
     * 경고할 때인가. <b>중요한 것이 아직 오지 않았고 사흘 안일 때만</b> 참이다.
     *
     * <p>이미 지난 것에 경고하지 않는 이유는 자명해 보이지만 적어 둔다 — 지난 이벤트를 목록에
     * 남겨 두는 구현에서 이 검사가 빠지면 <b>어제 끝난 FOMC 가 계속 경고를 띄운다.</b>
     */
    public boolean needsWarning(Instant now) {
        Duration remaining = until(now);
        return importance == Importance.HIGH
                && !remaining.isNegative()
                && remaining.compareTo(WARNING_WINDOW) <= 0;
    }
}
