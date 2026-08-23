package com.coinwin.watch.adapter.out.memory;

import com.coinwin.watch.application.port.out.LoadNoticesPort;
import com.coinwin.watch.domain.Notice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 공지를 메모리에 담는 어댑터. 거래소 없이 서비스와 화면 테스트를 돌리기 위해 있다.
 *
 * <p>Spring 애너테이션이 없다. 테스트가 직접 {@code new} 로 만든다 — 컨텍스트에 두 구현이
 * 함께 올라가 어느 쪽이 주입될지 설정에 따라 갈리는 상황을 만들지 않는다.
 */
public class InMemoryNoticeAdapter implements LoadNoticesPort {

    private final List<Notice> notices = new CopyOnWriteArrayList<>();

    public static InMemoryNoticeAdapter withSample(Instant at) {
        InMemoryNoticeAdapter adapter = new InMemoryNoticeAdapter();
        adapter.add(new Notice(
                "Binance Futures Will Launch UNITREEUSDT USDⓈ-Margined Perpetual Contract",
                at,
                "https://www.binance.com/support/announcement/abc123"));
        adapter.add(new Notice(
                "Notice on New Trading Pairs",
                at.minusSeconds(3600),
                "https://www.binance.com/support/announcement/def456"));
        return adapter;
    }

    public void add(Notice notice) {
        notices.add(notice);
    }

    /** <b>최근 것부터</b> 돌려준다. 공지에서 사람이 보는 것은 언제나 마지막에 일어난 일이다. */
    @Override
    public List<Notice> recent(int limit) {
        List<Notice> sorted = new ArrayList<>(notices);
        sorted.sort(Comparator.comparing(Notice::at).reversed());
        return List.copyOf(sorted.subList(0, Math.min(limit, sorted.size())));
    }
}
