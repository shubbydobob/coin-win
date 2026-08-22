package com.coinwin.common.api;

import com.coinwin.common.domain.DomainException;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.InvalidValueException;
import com.coinwin.common.domain.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 도메인 예외를 HTTP 로 옮기는 유일한 지점. 응답 형식은 RFC 7807 {@code ProblemDetail} 이다.
 *
 * <p>모듈마다 예외 타입이 늘어나도 이 클래스는 커지지 않는다. {@link DomainException} 하나만
 * 알기 때문이다. 하위 모듈 예외를 여기서 열거하면 {@code common} 이 모든 모듈을 참조하게 되어
 * 의존 방향이 뒤집힌다.
 *
 * <p>400 과 422 를 가르는 기준은 <b>값이 잘못됐는가, 조합이 잘못됐는가</b> 다. 음수 가격은
 * 값의 문제라 400 이고, 롱인데 손절가가 진입가보다 높은 것은 각 값은 멀쩡한데 계획으로
 * 성립하지 않는 경우라 422 다.
 *
 * <p><b>원인이 있는 예외는 원인을 로그에 남긴다.</b> 원인이 달려 있다는 것은 <b>우리가 아닌
 * 무언가가 실패했다</b>는 뜻이다 — 거래소가 거절했거나 모델이 형식을 어겼거나. 그 원인을
 * 응답에 싣지 않는 이유는 외부 시스템의 내부 사정이 부르는 쪽에 갈 이유가 없고 메시지에
 * 계좌를 특정할 값이 섞일 수 있어서인데, <b>그렇다고 어디에도 안 남기면 고칠 수가 없다.</b>
 *
 * <p>이 규칙이 없어서 실제로 두 번 막혔다. 바이낸스가 {@code -1021}(시계 어긋남)로 거절한
 * 것과 {@code -2015}(키·권한)로 거절한 것이 둘 다 "외부 데이터를 가져오지 못했다" 로만 보였고,
 * AI 가 422 를 낸 원인도 마찬가지였다. 둘 다 별도 프로브를 써서야 알아냈다.
 *
 * <p>원인이 <b>없는</b> 예외는 남기지 않는다. 계획이 규칙에 안 맞는 것은 정상적인 판정
 * 결과이지 사고가 아니다. 그것까지 경고로 찍으면 진짜 사고가 그 안에 묻힌다.
 */
@RestControllerAdvice
public class DomainExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DomainExceptionHandler.class);

    @ExceptionHandler(InvalidValueException.class)
    public ProblemDetail onInvalidValue(InvalidValueException exception) {
        return problem(HttpStatus.BAD_REQUEST, "값이 유효하지 않다", exception);
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail onDomainRuleViolation(DomainException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "도메인 규칙 위반", exception);
    }

    /**
     * 요청은 흠이 없고 가리키는 대상만 없는 경우. 400 도 422 도 아니라 404 다.
     *
     * <p>없는 거래 식별자로 조회한 것을 422 로 돌려주면 "규칙을 어겼다" 는 뜻이 되어, 부르는
     * 쪽이 재시도해야 할지 요청을 고쳐야 할지 판단할 수 없다.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "대상을 찾을 수 없다", exception);
    }

    /**
     * 거래소가 닿지 않는 것은 요청이 잘못된 것도(400) 계획이 성립하지 않는 것도(422) 아니다.
     * 잠시 뒤 다시 하면 되는 일이므로 503 이다.
     */
    @ExceptionHandler(ExternalDataUnavailableException.class)
    public ProblemDetail onExternalDataUnavailable(ExternalDataUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "외부 데이터를 가져오지 못했다", exception);
    }

    private ProblemDetail problem(HttpStatus status, String title, DomainException exception) {
        logCauseOf(exception, status);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }

    /** 응답에는 우리 메시지만 가고, 스택은 여기 남는다. */
    private static void logCauseOf(DomainException exception, HttpStatus status) {
        if (exception.getCause() != null) {
            LOG.warn("{} 로 응답한다: {}", status.value(), exception.getMessage(), exception);
        }
    }
}
