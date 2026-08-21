package com.coinwin.common.api;

import com.coinwin.common.domain.DomainException;
import com.coinwin.common.domain.ExternalDataUnavailableException;
import com.coinwin.common.domain.InvalidValueException;
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
 */
@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(InvalidValueException.class)
    public ProblemDetail onInvalidValue(InvalidValueException exception) {
        return problem(HttpStatus.BAD_REQUEST, "값이 유효하지 않다", exception);
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail onDomainRuleViolation(DomainException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "도메인 규칙 위반", exception);
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
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        detail.setTitle(title);
        return detail;
    }
}
