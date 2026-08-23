package archfixture.r4w.watch.application.service;

import archfixture.r4w.watch.adapter.out.snapshot.ClasspathEventCalendarAdapter;

/**
 * 규칙 4(watch) 를 일부러 어긴다 — application 이 adapter 를 직접 든다.
 *
 * <p><b>이 픽스처가 필요한 이유는 실제로 그렇게 짰기 때문이다.</b> "구현체가 하나뿐이니
 * 포트를 두지 않는다" 는 원칙을 따라 CalendarService 가 스냅샷 어댑터를 직접 들게 했고,
 * ArchUnit 이 그것을 거부했다. 규칙 4 는 모듈 이름을 손으로 열거하므로 {@code watch} 를
 * 넣지 않으면 그 실수가 다시 들어와도 잡히지 않는다.
 */
public class LeakyCalendarService {

    private final ClasspathEventCalendarAdapter snapshot = new ClasspathEventCalendarAdapter();

    public String calendar() {
        return snapshot.load();
    }
}
