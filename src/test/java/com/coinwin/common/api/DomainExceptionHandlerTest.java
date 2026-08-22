package com.coinwin.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.InvalidValueException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * 응답과 로그가 각각 무엇을 들고 가는가.
 *
 * <p>이 테스트가 생긴 이유가 실제 사고 둘이다. 바이낸스가 시계 어긋남({@code -1021})으로
 * 거절한 것과 키·권한({@code -2015})으로 거절한 것이 화면에서는 <b>같은 문장</b>이었고,
 * 원인은 어디에도 남지 않아 별도 프로브를 써야 알 수 있었다.
 */
class DomainExceptionHandlerTest {

    private final DomainExceptionHandler handler = new DomainExceptionHandler();

    private ListAppender<ILoggingEvent> recorded;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void captureLog() {
        logger = ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getLogger(DomainExceptionHandler.class);
        recorded = new ListAppender<>();
        recorded.start();
        logger.addAppender(recorded);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(recorded);
    }

    /**
     * 원인이 달려 있다는 것은 <b>우리가 아닌 무언가가 실패했다</b>는 뜻이다. 남기지 않으면
     * 고칠 수가 없다.
     */
    @Test
    void 원인이_있는_예외는_원인을_로그에_남긴다() {
        Throwable cause = new IllegalStateException("바이낸스가 -1021 로 거절했다");

        handler.onExternalDataUnavailable(
                new ExternalDataUnavailableException("포지션을 가져오지 못했다", cause));

        assertThat(recorded.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getThrowableProxy().getCause().getMessage())
                    .isEqualTo("바이낸스가 -1021 로 거절했다");
        });
    }

    /**
     * 계획이 규칙에 안 맞는 것은 <b>정상적인 판정 결과</b>이지 사고가 아니다. 그것까지 경고로
     * 찍으면 진짜 사고가 그 안에 묻힌다.
     */
    @Test
    void 원인이_없는_예외는_로그를_남기지_않는다() {
        handler.onInvalidValue(new InvalidValueException("가격은 음수일 수 없다"));

        assertThat(recorded.list).isEmpty();
    }

    /**
     * <b>응답에는 원인이 실리지 않는다.</b> 외부 시스템의 내부 사정이 부르는 쪽에 갈 이유가
     * 없고, 그 메시지에 계좌를 특정할 값이 섞일 수 있다.
     */
    @Test
    void 응답에는_원인을_싣지_않는다() {
        Throwable cause = new IllegalStateException("signature=abcdef&apiKey=SECRET");

        ProblemDetail detail = handler.onExternalDataUnavailable(
                new ExternalDataUnavailableException("포지션을 가져오지 못했다", cause));

        assertThat(detail.getDetail()).isEqualTo("포지션을 가져오지 못했다");
        assertThat(detail.getDetail()).doesNotContain("SECRET");
        assertThat(detail.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    void 상태와_제목은_예외_종류가_정한다() {
        assertThat(handler.onInvalidValue(new InvalidValueException("값")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(handler.onExternalDataUnavailable(
                        new ExternalDataUnavailableException("외부", null)).getTitle())
                .isEqualTo("외부 데이터를 가져오지 못했다");
    }
}
