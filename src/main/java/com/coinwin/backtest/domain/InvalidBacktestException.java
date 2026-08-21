package com.coinwin.backtest.domain;

import com.coinwin.common.domain.DomainException;

/**
 * 값은 각각 유효하지만 백테스트로는 성립하지 않을 때 던진다.
 *
 * <p>예: 확정 시각이 발생 시각보다 앞선 피벗, 최소 터치 횟수가 1 인 대 설정.
 *
 * <p><b>신호를 버리는 것은 예외가 아니다.</b> 손익비 미달이나 반대편 대 없음은 정상적인
 * 판정 결과이므로 {@code Optional.empty()} 로 돌려준다. 예외를 흘리면 백테스트가 중간에
 * 멈추고, 그러면 "거를 신호가 있었다" 는 사실이 결과에서 사라진다.
 */
public class InvalidBacktestException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidBacktestException(String message) {
        super(message);
    }
}
