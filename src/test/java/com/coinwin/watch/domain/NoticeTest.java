package com.coinwin.watch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coinwin.common.domain.InvalidValueException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 거래소 공지 하나. 담는 것은 셋뿐이고 분류하지 않는다. */
class NoticeTest {

    private static final Instant AT = Instant.parse("2026-08-23T08:10:09Z");

    @Test
    void 제목과_시각과_링크를_갖는다() {
        Notice notice = new Notice("상장 공지", AT, "https://example.test/a");

        assertThat(notice.title()).isEqualTo("상장 공지");
        assertThat(notice.at()).isEqualTo(AT);
        assertThat(notice.url()).isEqualTo("https://example.test/a");
    }

    /** 빈 제목은 목록에서 빈 줄이 된다 — 무엇이 왜 비었는지 알 수 없는 상태다. */
    @Test
    void 제목이_비면_공지가_아니다() {
        assertThatThrownBy(() -> new Notice("   ", AT, "https://example.test/a"))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void 셋_중_하나라도_null_이면_안_된다() {
        assertThatThrownBy(() -> new Notice(null, AT, "https://example.test/a"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Notice("제목", null, "https://example.test/a"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Notice("제목", AT, null))
                .isInstanceOf(InvalidValueException.class);
    }
}
