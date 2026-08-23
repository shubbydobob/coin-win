package com.coinwin.watch.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.coinwin.watch.domain.Notice;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 공지 포트의 계약. 바이낸스 어댑터와 인메모리 어댑터가 모두 통과해야 한다.
 *
 * <p>값을 단언하지 않는다 — 공지는 매일 달라진다. 확인하는 것은 <b>어떤 구현이든 참인 성질</b>
 * 이다: 최근 것부터 오고, 요청한 수를 넘지 않고, 제목과 링크가 비어 있지 않다.
 */
public abstract class NoticePortContract {

    protected abstract LoadNoticesPort port();

    @Test
    void 요청한_수를_넘지_않는다() {
        assertThat(port().recent(2)).hasSizeLessThanOrEqualTo(2).isNotEmpty();
    }

    /** <b>최근 것부터</b>다. 공지에서 사람이 보는 것은 언제나 마지막에 일어난 일이다. */
    @Test
    void 최근_것부터_온다() {
        List<Notice> notices = port().recent(10);

        assertThat(notices).isSortedAccordingTo(Comparator.comparing(Notice::at).reversed());
    }

    @Test
    void 제목과_링크가_비어_있지_않다() {
        assertThat(port().recent(5)).allSatisfy(notice -> {
            assertThat(notice.title()).isNotBlank();
            assertThat(notice.url()).startsWith("https://");
            assertThat(notice.at()).isNotNull();
        });
    }
}
